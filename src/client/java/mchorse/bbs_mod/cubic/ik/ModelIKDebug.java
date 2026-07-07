package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.DebugOverlay;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
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

    public static void render(MatrixStack stack, IModel model, MapType ikData, String selectedTip)
    {
        ValueIKDebug config = BBSSettings.ikDebug;

        if (!config.enabled.get() || model == null || ikData == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, compiled);

        if (config.xray.get())
        {
            RenderSystem.disableDepthTest();
        }

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        stack.push();

        if (model instanceof BOBJModel)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            drawChain(stack, frames, chain, selectedTip, config);
        }

        stack.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static Map<String, PivotFrame> collectFrames(IModel model, ModelIKCache.Compiled compiled)
    {
        Set<String> wanted = new HashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                wanted.add(chain.poleTarget());
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames);

        return frames;
    }

    /**
     * Mirrors the goals into the picking pass: a pickable marker at each goal,
     * registered under the chain's target bone. Must run after the model's bones
     * are registered so the goal ids fall right after them — the marker encodes
     * {@code stencilMap.objectIndex} as its colour and {@code addPicking} then
     * claims that same id. The matrix matches the visual overlay's.
     */
    public static void renderStencil(MatrixStack stack, IModel model, MapType ikData, StencilMap stencilMap, Form form)
    {
        ValueIKDebug config = BBSSettings.ikDebug;
        boolean targets = config.target.visible.get();
        boolean poles = config.pole.visible.get();

        if (!config.enabled.get() || (!targets && !poles) || model == null || ikData == null || stencilMap == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, compiled);

        if (config.xray.get())
        {
            RenderSystem.disableDepthTest();
        }

        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        stack.push();

        if (model instanceof BOBJModel)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            float unit = chainUnit(frames, chain.chainRootToEffector());

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

        BufferRenderer.drawWithGlobalProgram(builder.end());

        stack.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /** Draws one pickable marker — the element's own shape and size — encoding the next stencil id, and claims it for {@code bone}. */
    private static void pickMarker(BufferBuilder builder, MatrixStack stack, StencilMap stencilMap, Form form, ValueDebugElement element, Vector3f p, float unit, String bone)
    {
        int id = stencilMap.objectIndex;
        float[] col = {(id & 0xFF) / 255F, (id >> 8 & 0xFF) / 255F, (id >> 16 & 0xFF) / 255F};

        DebugOverlay.marker(builder, stack, element.shape.get(), p, unit * element.size.get(), col, 1F);

        stencilMap.addPicking(form, bone);
    }

    /** The chain's average segment length from the solved positions, the same scale the visual pass draws with. */
    private static float chainUnit(Map<String, PivotFrame> frames, List<String> ids)
    {
        float total = 0F;
        int segments = 0;
        Vector3f prev = null;

        for (String id : ids)
        {
            Vector3f p = position(frames, id);

            if (p == null)
            {
                prev = null;

                continue;
            }

            if (prev != null)
            {
                total += prev.distance(p);
                segments++;
            }

            prev = p;
        }

        return segments > 0 ? total / segments : 0.5F;
    }

    private static void drawChain(MatrixStack stack, Map<String, PivotFrame> frames, ModelIKCache.CompiledChain chain, String selectedTip, ValueIKDebug config)
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

        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            total += pts.get(i).distance(pts.get(i + 1));
        }

        float unit = total / (n - 1);
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
            BufferBuilder lines = Tessellator.getInstance().getBuffer();
            lines.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            emitLines(lines, matrix, 0F, dash, pts, target, pole, a, config);

            BufferRenderer.drawWithGlobalProgram(lines.end());
        }

        if (!anyDot && !boxes)
        {
            return;
        }

        /* Solid geometry: joint/accent markers, plus the thick lines. */
        BufferBuilder dots = Tessellator.getInstance().getBuffer();
        dots.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

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

        BufferRenderer.drawWithGlobalProgram(dots.end());
    }

    /** The chain's wires plus the bridges to the goal and the pole — the bridges are always dashed relationship lines. */
    private static void emitLines(BufferBuilder builder, Matrix4f matrix, float thickness, float dash, List<Vector3f> pts, Vector3f target, Vector3f pole, float a, ValueIKDebug config)
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
