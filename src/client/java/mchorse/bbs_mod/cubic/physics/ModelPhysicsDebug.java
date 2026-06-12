package mchorse.bbs_mod.cubic.physics;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
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
 * Minimal physics overlay, the same language as the IK one: each chain is a run
 * of thin wires through round joint dots, the pinned anchor picked out in a warm
 * accent and the simulated tip in a cool one, with the attach bone shown as a
 * small green dot bridged to the chain's end. It reads the model-local pivot
 * frames the renderer already produced — physics is applied to the rig before
 * this runs, so the frames are the settled chain; only the virtual tip point
 * (the solver simulates one segment past the last bone) is reconstructed from
 * the last bone's solved rotation. Gated globally by {@link #enabled}.
 *
 * <p>{@link #renderStencil} mirrors the attach markers into the picking pass so
 * a click on a green marker selects its bone, exactly as if its (often
 * mesh-less) bone had been clicked directly.
 */
public final class ModelPhysicsDebug
{
    private static final float[] WIRE = {0.90F, 0.92F, 0.95F};
    private static final float[] TIP = {0.30F, 0.64F, 1.00F};
    private static final float[] TARGET = {0.22F, 0.84F, 0.55F};
    private static final float[] ANCHOR = {1.00F, 0.55F, 0.15F};

    private static final float EPS = 1.0e-6f;

    public static boolean enabled;

    private ModelPhysicsDebug()
    {
    }

    public static void render(MatrixStack stack, IModel model, MapType physicsData, String selectedRoot)
    {
        if (!enabled || model == null || physicsData == null)
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = ModelPhysicsCache.getFromData(model, physicsData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, compiled);

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        stack.push();

        if (model instanceof BOBJModel)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        for (ModelPhysicsCache.CompiledChain chain : compiled.chains())
        {
            drawChain(stack, model, frames, chain, selectedRoot);
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
     * Mirrors the attach bones into the picking pass: a pickable cube at each
     * one, registered under that bone. Must run after the model's bones are
     * registered so the marker ids fall right after them — the cube encodes
     * {@code stencilMap.objectIndex} as its colour and {@code addPicking} then
     * claims that same id. The matrix matches the visual overlay's.
     */
    public static void renderStencil(MatrixStack stack, IModel model, MapType physicsData, StencilMap stencilMap, Form form)
    {
        if (!enabled || model == null || physicsData == null || stencilMap == null)
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = ModelPhysicsCache.getFromData(model, physicsData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, compiled);

        RenderSystem.disableDepthTest();
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
                float s = segmentUnit(chain.restLengths()) * 0.2F;

                pickMarker(builder, stack, stencilMap, form, target, s, chain.targetBone());
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        stack.pop();

        RenderSystem.enableDepthTest();
    }

    /** Draws one pickable cube encoding the next stencil id and claims it for {@code bone}, so a click selects that bone. */
    private static void pickMarker(BufferBuilder builder, MatrixStack stack, StencilMap stencilMap, Form form, Vector3f p, float s, String bone)
    {
        int id = stencilMap.objectIndex;

        Draw.fillBox(builder, stack, p.x - s, p.y - s, p.z - s, p.x + s, p.y + s, p.z + s, (id & 0xFF) / 255F, (id >> 8 & 0xFF) / 255F, (id >> 16 & 0xFF) / 255F, 1F);

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

    private static void drawChain(MatrixStack stack, IModel model, Map<String, PivotFrame> frames, ModelPhysicsCache.CompiledChain chain, String selectedRoot)
    {
        List<String> ids = chain.chainRootToEnd();
        int n = ids.size();

        if (n < 1)
        {
            return;
        }

        List<Vector3f> pts = new ArrayList<>(n + 1);

        for (int i = 0; i < n; i++)
        {
            Vector3f p = position(frames, ids.get(i));

            if (p == null)
            {
                return;
            }

            pts.add(p);
        }

        Vector3f tip = tipPosition(model, ids, frames, chain.restLengths());

        if (tip == null)
        {
            return;
        }

        pts.add(tip);

        Vector3f target = chain.targetBone() == null || chain.targetBone().isEmpty() ? null : position(frames, chain.targetBone());

        float unit = segmentUnit(chain.restLengths());
        boolean sel = selectedRoot == null || selectedRoot.isEmpty() || chain.attach().equals(selectedRoot);
        float a = sel ? 1F : 0.4F;

        Matrix4f matrix = stack.peek().getPositionMatrix();

        /* Lines: the bone chain plus a faint bridge to the attach bone. */
        BufferBuilder lines = Tessellator.getInstance().getBuffer();
        lines.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < pts.size() - 1; i++)
        {
            addLine(lines, matrix, pts.get(i), pts.get(i + 1), WIRE, 0.9F * a);
        }

        if (target != null)
        {
            addLine(lines, matrix, tip, target, TARGET, 0.4F * a);
        }

        BufferRenderer.drawWithGlobalProgram(lines.end());

        /* Solid spheres: the pinned anchor, joints, the simulated tip and the attach bone. */
        BufferBuilder dots = Tessellator.getInstance().getBuffer();
        dots.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        orb(dots, stack, pts.get(0), unit * 0.1F, ANCHOR, a);

        for (int i = 1; i < pts.size() - 1; i++)
        {
            orb(dots, stack, pts.get(i), unit * 0.07F, WIRE, a);
        }

        orb(dots, stack, tip, unit * 0.1F, TIP, a);

        if (target != null)
        {
            orb(dots, stack, target, unit * 0.12F, TARGET, a);
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

        Vector3f dir = tipRestDirection(model, ids);

        if (dir == null || dir.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        Quaternionf rotation = new Quaternionf(frame.worldRotation());

        rotation.transform(dir.normalize()).mul(lengths[n - 1]);

        return new Vector3f(frame.position()).add(dir);
    }

    private static Vector3f tipRestDirection(IModel model, List<String> ids)
    {
        String last = ids.get(ids.size() - 1);

        if (model instanceof Model cubic)
        {
            ModelGroup bone = cubic.getGroup(last);

            if (bone == null)
            {
                return null;
            }

            if (ids.size() >= 2)
            {
                ModelGroup parent = cubic.getGroup(ids.get(ids.size() - 2));

                return parent == null ? null : new Vector3f(bone.initial.translate).sub(parent.initial.translate).mul(1F / 16F);
            }

            if (bone.children != null && !bone.children.isEmpty())
            {
                return new Vector3f(bone.children.get(0).initial.translate).sub(bone.initial.translate).mul(1F / 16F);
            }

            return new Vector3f(0F, -1F, 0F);
        }

        if (model instanceof BOBJModel bobj)
        {
            BOBJBone bone = bobj.getArmature().bones.get(last);

            return bone == null ? null : ModelRotationBlender.getBobjRestDirection(bobj, bone, null, ids, ids.size() - 1);
        }

        return null;
    }

    private static void orb(BufferBuilder builder, MatrixStack stack, Vector3f p, float radius, float[] col, float a)
    {
        stack.push();
        stack.translate(p.x, p.y, p.z);
        Draw.sphere(builder, stack, radius, 9, 9, col[0], col[1], col[2], a);
        stack.pop();
    }

    private static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }

    private static void addLine(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p2.x, p2.y, p2.z).color(col[0], col[1], col[2], a).next();
    }
}
