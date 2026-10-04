package mchorse.bbs_mod.cubic.ik;

import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.graphics.NativeColorProgram;
import org.lwjgl.opengl.GL11;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.DebugOverlay;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;




import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import mchorse.bbs_mod.graphics.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal IK overlay: each chain is a clean run of wires through joint
 * markers, the effector picked out in a cool accent, and the target bridged to
 * the chain's tip by a dashed relationship line. Every part's look —
 * visibility, colour, size, shape, line thickness, dashing, opacity, drawing
 * through the model — comes from {@link ValueIKDebug}
 * ({@code BBSSettings.ikDebug}), which also holds the overlay's toggle. It
 * reads the model-local pivot frames the renderer already produced — IK is
 * applied to the rig before this runs, so the frames are the solved chain
 * (re-solving would double-apply the pole angle).
 *
 * <p>{@link #renderStencil} mirrors the goal markers into the picking pass so a
 * click on a target selects its bone, exactly as if its (often mesh-less)
 * bone had been clicked directly. The pick geometry is the same shape and size
 * as the visible marker — no oversized hitboxes in the hover highlight, and
 * hidden markers are not pickable at all.
 */
public final class ModelIKDebug
{
    private ModelIKDebug()
    {
    }

    public static void render(MatrixStack stack, IModel model, ModelForm form, String selectedTip)
    {
        ValueIKDebug config = BBSSettings.ikDebug;

        if (!config.enabled.get() || model == null || form == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);
        List<ModelIKCache.CompiledChain> chains = compiled == null ? java.util.Collections.emptyList() : compiled.chains();
        Set<String> controllers = collectControllers(model, form, chains, config);

        if (chains.isEmpty() && controllers.isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, chains, controllers);

        if (config.xray.get())
        {
            GlStateManager.disableDepth();
        }

        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        

        stack.push();

        if (model.isFacingFlipped())
        {
            stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
        }

        float unit = DebugOverlay.modelUnit(model);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            FormBone bone = form.bones.getBone(chain.tip());

            if (bone == null || !form.ik.get().get(chain.tip()).enabled)
            {
                continue;
            }

            drawChain(stack, frames, chain, selectedTip, config, unit);
        }

        if (!controllers.isEmpty())
        {
            UIVertexBuffer builder = UIVertexBuffer.immediate();
            builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

            for (String controller : controllers)
            {
                Vector3f point = position(frames, controller);

                if (point != null)
                {
                    DebugOverlay.marker(builder, stack, config.target.shape.get(), point,
                        unit * config.target.size.get(), DebugOverlay.rgb(config.target.color.get()), config.opacity.get());
                }
            }

            NativeColorProgram.draw(builder);
        }

        stack.pop();

        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    /** Reuse the target style; an existing visible IK marker already selects the same bone. */
    private static Set<String> collectControllers(IModel model, ModelForm form,
        List<ModelIKCache.CompiledChain> chains, ValueIKDebug config)
    {
        Set<String> controllers = new HashSet<>();

        if (!config.target.visible.get())
        {
            return controllers;
        }

        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone && bone.boneController.get()
                && model.getAllGroupKeys().contains(bone.getId()))
            {
                controllers.add(bone.getId());
            }
        }

        for (ModelIKCache.CompiledChain chain : chains)
        {
            if (form.ik.get().get(chain.tip()).enabled)
            {
                controllers.remove(chain.target());

                if (config.pole.visible.get())
                {
                    controllers.remove(chain.poleTarget());
                }
            }
        }

        return controllers;
    }

    private static Map<String, PivotFrame> collectFrames(IModel model, List<ModelIKCache.CompiledChain> chains, Set<String> controllers)
    {
        Set<String> wanted = new HashSet<>(controllers);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                wanted.add(chain.poleTarget());
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);

        ModelPivotFrames.collect(model, wanted, frames, null, true);

        return frames;
    }

    /**
     * Mirrors the goals into the picking pass: a pickable marker at each goal,
     * registered under the chain's target bone. Must run after the model's bones
     * are registered so the goal ids fall right after them — the marker encodes
     * {@code stencilMap.objectIndex} as its colour and {@code addPicking} then
     * claims that same id. The matrix matches the visual overlay's.
     */
    public static void renderStencil(MatrixStack stack, IModel model, ModelForm modelForm, StencilMap stencilMap, Form form)
    {
        ValueIKDebug config = BBSSettings.ikDebug;
        boolean targets = config.target.visible.get();
        boolean poles = config.pole.visible.get();

        if (!config.enabled.get() || (!targets && !poles) || model == null || modelForm == null || stencilMap == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, modelForm);
        List<ModelIKCache.CompiledChain> chains = compiled == null ? java.util.Collections.emptyList() : compiled.chains();
        Set<String> controllers = collectControllers(model, modelForm, chains, config);

        if (chains.isEmpty() && controllers.isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, chains, controllers);

        if (config.xray.get())
        {
            GlStateManager.disableDepth();
        }

        GlStateManager.disableCull();
        

        stack.push();

        if (model.isFacingFlipped())
        {
            stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
        }

        UIVertexBuffer builder = UIVertexBuffer.immediate();
        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        float unit = DebugOverlay.modelUnit(model);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            FormBone bone = modelForm.bones.getBone(chain.tip());

            if (bone == null || !modelForm.ik.get().get(chain.tip()).enabled)
            {
                continue;
            }

            if (targets)
            {
                Vector3f goal = position(frames, chain.target());

                if (goal != null)
                {
                    pickMarker(builder, stack, stencilMap, form, config.target, goal, unit, chain.target());
                }
            }

            if (poles && chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                Vector3f pole = position(frames, chain.poleTarget());

                if (pole != null)
                {
                    pickMarker(builder, stack, stencilMap, form, config.pole, pole, unit, chain.poleTarget());
                }
            }
        }

        for (String controller : controllers)
        {
            Vector3f point = position(frames, controller);

            if (point != null)
            {
                pickMarker(builder, stack, stencilMap, form, config.target, point, unit, controller);
            }
        }

        NativeColorProgram.draw(builder);

        stack.pop();

        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    /** Draws one pickable marker — the element's own shape and size — encoding the next stencil id, and claims it for {@code bone}. */
    private static void pickMarker(UIVertexBuffer builder, MatrixStack stack, StencilMap stencilMap, Form form, ValueDebugElement element, Vector3f p, float unit, String bone)
    {
        int id = stencilMap.objectIndex;
        float[] col = {(id & 0xFF) / 255F, (id >> 8 & 0xFF) / 255F, (id >> 16 & 0xFF) / 255F};

        DebugOverlay.marker(builder, stack, element.shape.get(), p, unit * element.size.get(), col, 1F);

        stencilMap.addPicking(form, bone);
    }

    private static void drawChain(MatrixStack stack, Map<String, PivotFrame> frames, ModelIKCache.CompiledChain chain, String selectedTip, ValueIKDebug config, float unit)
    {
        List<String> ids = chain.chainRootToEffector();
        int n = ids.size();

        if (n < 2)
        {
            return;
        }

        List<Vector3f> pts = new ArrayList<>(n);

        for (int i = 0; i < n; i++)
        {
            Vector3f p = position(frames, ids.get(i));

            if (p == null)
            {
                return;
            }

            pts.add(p);
        }

        Vector3f target = position(frames, chain.target());

        if (target == null)
        {
            return;
        }

        Vector3f pole = chain.poleTarget() == null || chain.poleTarget().isEmpty() ? null : position(frames, chain.poleTarget());
        Vector3f tip = pts.get(n - 1);

        boolean sel = selectedTip == null || selectedTip.isEmpty() || chain.tip().equals(selectedTip);
        float a = (sel ? 1F : 0.4F) * config.opacity.get();

        boolean joints = config.joints.visible.get();
        boolean tipDot = config.tip.visible.get();
        boolean targetDot = config.target.visible.get();
        boolean poleDot = pole != null && config.pole.visible.get();

        boolean anyLine = config.lines.visible.get() || targetDot || poleDot;
        float thickness = unit * config.lines.size.get();
        boolean boxes = anyLine && thickness > 0F;
        boolean anyDot = joints || tipDot || targetDot || poleDot;

        Matrix4f matrix = stack.peek().getPositionMatrix();
        float dash = unit * 0.12F;

        /* Lines: hairline GL lines by default, boxes once a thickness is set. */
        if (anyLine && !boxes)
        {
            UIVertexBuffer lines = UIVertexBuffer.immediate();
            lines.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

            emitLines(lines, matrix, 0F, dash, pts, target, pole, a, config);

            NativeColorProgram.draw(lines);
        }

        if (!anyDot && !boxes)
        {
            return;
        }

        /* Solid geometry: joint/accent markers, plus the thick lines. */
        UIVertexBuffer dots = UIVertexBuffer.immediate();
        dots.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        if (boxes)
        {
            emitLines(dots, matrix, thickness, dash, pts, target, pole, a, config);
        }

        if (joints)
        {
            float[] color = DebugOverlay.rgb(config.joints.color.get());
            float radius = unit * config.joints.size.get();
            int shape = config.joints.shape.get();

            for (int i = 0; i < n - 1; i++)
            {
                DebugOverlay.marker(dots, stack, shape, pts.get(i), radius, color, a);
            }
        }

        if (tipDot)
        {
            DebugOverlay.marker(dots, stack, config.tip.shape.get(), tip, unit * config.tip.size.get(), DebugOverlay.rgb(config.tip.color.get()), a);
        }

        if (targetDot)
        {
            DebugOverlay.marker(dots, stack, config.target.shape.get(), target, unit * config.target.size.get(), DebugOverlay.rgb(config.target.color.get()), a);
        }

        if (poleDot)
        {
            DebugOverlay.marker(dots, stack, config.pole.shape.get(), pole, unit * config.pole.size.get(), DebugOverlay.rgb(config.pole.color.get()), a);
        }

        NativeColorProgram.draw(dots);
    }

    /** The chain's wires plus the bridges to the goal and the pole — the bridges are always dashed relationship lines. */
    private static void emitLines(UIVertexBuffer builder, Matrix4f matrix, float thickness, float dash, List<Vector3f> pts, Vector3f target, Vector3f pole, float a, ValueIKDebug config)
    {
        if (config.lines.visible.get())
        {
            float[] wire = DebugOverlay.rgb(config.lines.color.get());
            boolean dashed = config.dashed.get();

            for (int i = 0; i < pts.size() - 1; i++)
            {
                DebugOverlay.segment(builder, matrix, thickness, dashed, dash, pts.get(i), pts.get(i + 1), wire, 0.9F * a);
            }
        }

        if (config.target.visible.get())
        {
            DebugOverlay.segment(builder, matrix, thickness, true, dash, pts.get(pts.size() - 1), target, DebugOverlay.rgb(config.target.color.get()), 0.4F * a);
        }

        if (pole != null && config.pole.visible.get())
        {
            DebugOverlay.segment(builder, matrix, thickness, true, dash, pts.get(1), pole, DebugOverlay.rgb(config.pole.color.get()), 0.4F * a);
        }
    }

    private static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }
}
