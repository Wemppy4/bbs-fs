package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal IK overlay: each chain is a clean run of thin wires through round
 * joint dots, the effector picked out in a warm accent, and the target shown as
 * a small dot inside a caged sphere. It reads the model-local pivot frames the
 * renderer already produced — IK is applied to the rig before this runs, so the
 * frames are the solved chain (re-solving would double-apply the pole angle).
 * Gated globally by {@link #enabled}.
 *
 * <p>{@link #renderStencil} mirrors the goal markers into the picking pass so a
 * click on a green target selects its bone, exactly as if its (often mesh-less)
 * bone had been clicked directly.
 */
public final class ModelIKDebug
{
    private static final float[] WIRE = {0.90F, 0.92F, 0.95F};
    private static final float[] EFFECTOR = {0.30F, 0.64F, 1.00F};
    private static final float[] GOAL = {0.22F, 0.84F, 0.55F};
    private static final float[] POLE = {1.00F, 0.55F, 0.15F};

    public static boolean enabled;

    private ModelIKDebug()
    {
    }

    public static void render(MatrixStack stack, IModel model, MapType ikData, String selectedTip)
    {
        if (!enabled || model == null || ikData == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

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

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            drawChain(stack, frames, chain, selectedTip);
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
     * Mirrors the goals into the picking pass: a pickable cube at each goal,
     * registered under the chain's target bone. Must run after the model's bones
     * are registered so the goal ids fall right after them — the cube encodes
     * {@code stencilMap.objectIndex} as its colour and {@code addPicking} then
     * claims that same id. The matrix matches the visual overlay's.
     */
    public static void renderStencil(MatrixStack stack, IModel model, MapType ikData, StencilMap stencilMap, Form form)
    {
        if (!enabled || model == null || ikData == null || stencilMap == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

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

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            float s = goalRadius(frames, chain.chainRootToEffector());
            Vector3f goal = position(frames, chain.target());

            if (goal != null)
            {
                pickMarker(builder, stack, stencilMap, form, goal, s, chain.target());
            }

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                Vector3f pole = position(frames, chain.poleTarget());

                if (pole != null)
                {
                    pickMarker(builder, stack, stencilMap, form, pole, s, chain.poleTarget());
                }
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

    /** Clickable goal half-size, scaled to the bone span so it fits any rig. */
    private static float goalRadius(Map<String, PivotFrame> frames, List<String> ids)
    {
        Vector3f root = ids.isEmpty() ? null : position(frames, ids.get(0));
        Vector3f tip = ids.isEmpty() ? null : position(frames, ids.get(ids.size() - 1));
        float span = root != null && tip != null ? root.distance(tip) : 0.5F;

        return span / Math.max(1, ids.size() - 1) * 0.2F;
    }

    private static void drawChain(MatrixStack stack, Map<String, PivotFrame> frames, ModelIKCache.CompiledChain chain, String selectedTip)
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
        float a = sel ? 1F : 0.4F;

        Matrix4f matrix = stack.peek().getPositionMatrix();

        /* Lines: the bone chain plus a faint bridge to the goal. */
        BufferBuilder lines = Tessellator.getInstance().getBuffer();
        lines.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n - 1; i++)
        {
            addLine(lines, matrix, pts.get(i), pts.get(i + 1), WIRE, 0.9F * a);
        }

        addLine(lines, matrix, tip, target, GOAL, 0.4F * a);

        if (pole != null)
        {
            addLine(lines, matrix, pts.get(1), pole, POLE, 0.4F * a);
        }

        BufferRenderer.drawWithGlobalProgram(lines.end());

        /* Solid spheres: joints, the accented effector, and the goal. */
        BufferBuilder dots = Tessellator.getInstance().getBuffer();
        dots.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n - 1; i++)
        {
            orb(dots, stack, pts.get(i), unit * 0.07F, WIRE, a);
        }

        orb(dots, stack, tip, unit * 0.1F, EFFECTOR, a);
        orb(dots, stack, target, unit * 0.12F, GOAL, a);

        if (pole != null)
        {
            orb(dots, stack, pole, unit * 0.1F, POLE, a);
        }

        BufferRenderer.drawWithGlobalProgram(dots.end());
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
