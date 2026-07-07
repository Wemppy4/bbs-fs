package mchorse.bbs_mod.cubic.physics;

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
import mchorse.bbs_mod.settings.values.ui.ValuePhysicsDebug;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal physics overlay, the same language as the IK one: each chain is a
 * run of wires through joint markers, the pinned root picked out in a warm
 * accent and the simulated tip in a cool one, with the attach bone bridged to
 * the chain's end by a dashed relationship line. Every part's look —
 * visibility, colour, size, shape, line thickness, dashing, opacity, drawing
 * through the model — comes from {@link ValuePhysicsDebug}
 * ({@code BBSSettings.physicsDebug}), which also holds the overlay's toggle.
 * It reads the model-local pivot frames the renderer already produced —
 * physics is applied to the rig before this runs, so the frames are the
 * settled chain; only the virtual tip point (the solver simulates one segment
 * past the last bone) is reconstructed from the last bone's solved rotation.
 *
 * <p>{@link #renderStencil} mirrors the attach markers into the picking pass so
 * a click on an attach marker selects its bone, exactly as if its (often
 * mesh-less) bone had been clicked directly. The pick geometry is the same
 * shape and size as the visible marker, and hidden markers are not pickable.
 *
 * <p>When wind is configured, an arrow is drawn at each free point showing the
 * force it currently feels — sampled from the same noise the solver uses, so
 * the arrows pulse with the gusts and vary along a chain exactly as the ripple
 * does. The arrows point in the displayed-world wind direction (the world
 * vector is carried into the overlay's drawing space); the wind element's size
 * is the arrow length per unit of force.
 */
public final class ModelPhysicsDebug
{
    private static final float EPS = 1.0e-6f;

    private ModelPhysicsDebug()
    {
    }

    public static void render(MatrixStack stack, IModel model, MapType physicsData, int age, String selectedRoot)
    {
        ValuePhysicsDebug config = BBSSettings.physicsDebug;

        if (!config.enabled.get() || model == null || physicsData == null)
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = ModelPhysicsCache.getFromData(model, physicsData);

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

        /* The wind is one field for the whole model: resolve its world direction and base magnitude once,
         * plus the inverse of the current draw matrix so the world-space force can be carried back into the
         * overlay's local drawing space for each arrow. */
        Vector3f windDir = new Vector3f();
        float windMagnitude = config.wind.visible.get() ? PhysicsForces.prepareWind(compiled.wind(), 1F, windDir) : 0F;
        Matrix4f matrix = new Matrix4f(stack.peek().getPositionMatrix());
        Matrix4f inverse = windMagnitude > 0F ? new Matrix4f(matrix).invert() : null;

        for (ModelPhysicsCache.CompiledChain chain : compiled.chains())
        {
            drawChain(stack, model, frames, chain, selectedRoot, config);

            if (inverse != null)
            {
                drawWind(stack, model, frames, chain, selectedRoot, compiled.wind(), windDir, windMagnitude, age, matrix, inverse, config);
            }
        }

        stack.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static Map<String, PivotFrame> collectFrames(IModel model, ModelPhysicsCache.Compiled compiled)
    {
        Set<String> wanted = new HashSet<>();

        for (ModelPhysicsCache.CompiledChain chain : compiled.chains())
        {
            wanted.addAll(chain.chainRootToEnd());

            if (chain.targetBone() != null && !chain.targetBone().isEmpty())
            {
                wanted.add(chain.targetBone());
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames);

        return frames;
    }

    /**
     * Mirrors the attach bones into the picking pass: a pickable marker at each
     * one, registered under that bone. Must run after the model's bones are
     * registered so the marker ids fall right after them — the marker encodes
     * {@code stencilMap.objectIndex} as its colour and {@code addPicking} then
     * claims that same id. The matrix matches the visual overlay's.
     */
    public static void renderStencil(MatrixStack stack, IModel model, MapType physicsData, StencilMap stencilMap, Form form)
    {
        ValuePhysicsDebug config = BBSSettings.physicsDebug;

        if (!config.enabled.get() || !config.attach.visible.get() || model == null || physicsData == null || stencilMap == null)
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = ModelPhysicsCache.getFromData(model, physicsData);

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

        for (ModelPhysicsCache.CompiledChain chain : compiled.chains())
        {
            if (chain.targetBone() == null || chain.targetBone().isEmpty())
            {
                continue;
            }

            Vector3f target = position(frames, chain.targetBone());

            if (target != null)
            {
                pickMarker(builder, stack, stencilMap, form, config.attach, target, segmentUnit(chain.restLengths()), chain.targetBone());
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

    private static float segmentUnit(float[] lengths)
    {
        if (lengths == null || lengths.length == 0)
        {
            return 0.25F;
        }

        float total = 0F;

        for (float length : lengths)
        {
            total += length;
        }

        return Math.max(total / lengths.length, EPS);
    }

    /** The chain's drawn points: each bone's pivot, then the reconstructed virtual tip. Null if any is missing. */
    private static List<Vector3f> chainPoints(IModel model, Map<String, PivotFrame> frames, ModelPhysicsCache.CompiledChain chain)
    {
        List<String> ids = chain.chainRootToEnd();
        int n = ids.size();

        if (n < 1)
        {
            return null;
        }

        List<Vector3f> pts = new ArrayList<>(n + 1);

        for (int i = 0; i < n; i++)
        {
            Vector3f p = position(frames, ids.get(i));

            if (p == null)
            {
                return null;
            }

            pts.add(p);
        }

        Vector3f tip = tipPosition(model, ids, frames, chain.restLengths());

        if (tip == null)
        {
            return null;
        }

        pts.add(tip);

        return pts;
    }

    private static void drawChain(MatrixStack stack, IModel model, Map<String, PivotFrame> frames, ModelPhysicsCache.CompiledChain chain, String selectedRoot, ValuePhysicsDebug config)
    {
        List<Vector3f> pts = chainPoints(model, frames, chain);

        if (pts == null)
        {
            return;
        }

        Vector3f tip = pts.get(pts.size() - 1);

        Vector3f target = chain.targetBone() == null || chain.targetBone().isEmpty() ? null : position(frames, chain.targetBone());

        float unit = segmentUnit(chain.restLengths());
        boolean sel = selectedRoot == null || selectedRoot.isEmpty() || chain.attach().equals(selectedRoot);
        float a = (sel ? 1F : 0.4F) * config.opacity.get();

        boolean rootDot = config.root.visible.get();
        boolean joints = config.joints.visible.get() && pts.size() > 2;
        boolean tipDot = config.tip.visible.get();
        boolean attachDot = target != null && config.attach.visible.get();

        boolean anyLine = config.lines.visible.get() || attachDot;
        float thickness = unit * config.lines.size.get();
        boolean boxes = anyLine && thickness > 0F;
        boolean anyDot = rootDot || joints || tipDot || attachDot;

        Matrix4f matrix = stack.peek().getPositionMatrix();
        float dash = unit * 0.12F;

        /* Lines: hairline GL lines by default, boxes once a thickness is set. */
        if (anyLine && !boxes)
        {
            BufferBuilder lines = Tessellator.getInstance().getBuffer();
            lines.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            emitLines(lines, matrix, 0F, dash, pts, target, a, config);

            BufferRenderer.drawWithGlobalProgram(lines.end());
        }

        if (!anyDot && !boxes)
        {
            return;
        }

        /* Solid geometry: the pinned root, joints, the simulated tip and the attach bone, plus the thick lines. */
        BufferBuilder dots = Tessellator.getInstance().getBuffer();
        dots.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        if (boxes)
        {
            emitLines(dots, matrix, thickness, dash, pts, target, a, config);
        }

        if (rootDot)
        {
            DebugOverlay.marker(dots, stack, config.root.shape.get(), pts.get(0), unit * config.root.size.get(), DebugOverlay.rgb(config.root.color.get()), a);
        }

        if (joints)
        {
            float[] color = DebugOverlay.rgb(config.joints.color.get());
            float radius = unit * config.joints.size.get();
            int shape = config.joints.shape.get();

            for (int i = 1; i < pts.size() - 1; i++)
            {
                DebugOverlay.marker(dots, stack, shape, pts.get(i), radius, color, a);
            }
        }

        if (tipDot)
        {
            DebugOverlay.marker(dots, stack, config.tip.shape.get(), tip, unit * config.tip.size.get(), DebugOverlay.rgb(config.tip.color.get()), a);
        }

        if (attachDot)
        {
            DebugOverlay.marker(dots, stack, config.attach.shape.get(), target, unit * config.attach.size.get(), DebugOverlay.rgb(config.attach.color.get()), a);
        }

        BufferRenderer.drawWithGlobalProgram(dots.end());
    }

    /** The chain's wires plus the bridge to the attach bone — the bridge is always a dashed relationship line. */
    private static void emitLines(BufferBuilder builder, Matrix4f matrix, float thickness, float dash, List<Vector3f> pts, Vector3f target, float a, ValuePhysicsDebug config)
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

        if (target != null && config.attach.visible.get())
        {
            DebugOverlay.segment(builder, matrix, thickness, true, dash, pts.get(pts.size() - 1), target, DebugOverlay.rgb(config.attach.color.get()), 0.4F * a);
        }
    }

    /**
     * Draws the wind arrow at each free point of a chain: the force it currently feels, sampled from the same
     * noise the solver uses, so the arrows pulse with the gusts and vary along the chain just like the ripple.
     * Each arrow points in the displayed-world wind direction. The pinned root (point 0) feels no wind, so
     * it is skipped. Length is proportional to the force, scaled to the chain's segment length.
     */
    private static void drawWind(MatrixStack stack, IModel model, Map<String, PivotFrame> frames, ModelPhysicsCache.CompiledChain chain, String selectedRoot, ModelPhysicsConfig.Wind wind, Vector3f windDir, float windMagnitude, int age, Matrix4f matrix, Matrix4f inverse, ValuePhysicsDebug config)
    {
        List<Vector3f> pts = chainPoints(model, frames, chain);

        if (pts == null || pts.size() < 2)
        {
            return;
        }

        boolean sel = selectedRoot == null || selectedRoot.isEmpty() || chain.attach().equals(selectedRoot);
        float a = (sel ? 1F : 0.4F) * config.opacity.get();
        float unit = segmentUnit(chain.restLengths());
        float[] color = DebugOverlay.rgb(config.wind.color.get());

        Vector3f world = new Vector3f();
        Vector3f force = new Vector3f();
        List<Vector3f> tips = new ArrayList<>(pts.size());

        BufferBuilder lines = Tessellator.getInstance().getBuffer();
        lines.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 1; i < pts.size(); i++)
        {
            Vector3f p = pts.get(i);

            /* Sample in the solver's world space (carry the local point out through the draw matrix), then
             * carry the world-space force back into local space so the arrow points the right way on screen. */
            matrix.transformPosition(world.set(p));
            PhysicsForces.windForceAt(windDir, windMagnitude, wind, age, world, force);
            inverse.transformDirection(force).mul(unit * config.wind.size.get());

            Vector3f end = new Vector3f(p).add(force);

            DebugOverlay.line(lines, matrix, p, end, color, 0.85F * a);
            tips.add(end);
        }

        BufferRenderer.drawWithGlobalProgram(lines.end());

        BufferBuilder dots = Tessellator.getInstance().getBuffer();
        dots.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (Vector3f end : tips)
        {
            DebugOverlay.marker(dots, stack, ValueDebugElement.SHAPE_SPHERE, end, unit * 0.05F, color, a);
        }

        BufferRenderer.drawWithGlobalProgram(dots.end());
    }

    /**
     * The solver simulates one virtual point past the last bone, and the last bone is rotated toward
     * it. Rebuilds that point from the bone's solved world rotation and the same rest direction the
     * rotation appliers used, so the drawn tip is exactly where the bone is pointing.
     */
    private static Vector3f tipPosition(IModel model, List<String> ids, Map<String, PivotFrame> frames, float[] lengths)
    {
        int n = ids.size();
        PivotFrame frame = frames.get(ids.get(n - 1));

        if (frame == null || lengths == null || lengths.length < n)
        {
            return null;
        }

        Vector3f dir = PhysicsRig.tipRestDirectionLocal(model, ids);

        if (dir == null || dir.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        Quaternionf rotation = new Quaternionf(frame.worldRotation());

        rotation.transform(dir.normalize()).mul(lengths[n - 1]);

        return new Vector3f(frame.position()).add(dir);
    }

    private static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }
}
