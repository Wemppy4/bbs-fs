package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGesture;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
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
import org.lwjgl.opengl.GL11;

/**
 * The sweep pie a rotation drag leaves behind it: the wedge from where the ring was grabbed
 * to where it is now, with bright edges at both ends. Split out of {@link Gizmo} because it
 * belongs to the GESTURE rather than to the gizmo — it exists only while a drag runs, it is
 * built from the drag's own numbers, and it is composited last, over handles that are
 * already drawn.
 *
 * <p>Stateless — everything it needs arrives per call.
 */
public class GizmoPie
{
    /**
     * Draws the pie for the rotation drag in progress, if the running edit is one that has
     * a pie at all.
     *
     * @param ringGesture the axis drag's own gesture, whose anchored turn axis decides which
     *                    way the wedge sweeps.
     */
    public static void draw(MatrixStack stack, TransformGesture transform, DragStrategy ringGesture)
    {
        if (transform == null || !transform.isEditing() || transform.getOp() != TransformOp.ROTATE)
        {
            return;
        }

        if (transform.isSphereRotate() || transform.isViewRotate())
        {
            return;
        }

        Axis axis = transform.getAxis();

        if (axis != null)
        {
            drawAxis(stack, transform, ringGesture, axis);
        }
    }

    /** Sweep pie for one of the three axis rings, drawn in the ring's own plane. */
    private static void drawAxis(MatrixStack stack, TransformGesture transform, DragStrategy ringGesture, Axis axis)
    {
        if (transform.drag() == null) return;

        float scale = BBSSettings.axesScale.get();
        float radius = 0.22F * scale;

        Vector3f initialVec = transform.getInitialDragRingVec();

        Vector3f axisX = transform.drag().gizmoWorldAxes.getColumn(0, new Vector3f());
        Vector3f axisY = transform.drag().gizmoWorldAxes.getColumn(1, new Vector3f());
        Vector3f axisZ = transform.drag().gizmoWorldAxes.getColumn(2, new Vector3f());
        /* The ring's actual world rotation axis in the active space — the same
         * basis the ring is drawn in (Gizmo.reorientForSpace) and the drag turns
         * about. The axis comes from the GESTURE itself (its anchored turn axis),
         * so the pie can never disagree with the rotation — the drawn frame axis
         * and the real turn axis differ on the channel path (PARENT / the pole
         * fallback), where cubic models flip the channels' X/Z response. */
        Vector3f dragAxisDir = ringGesture != null ? ringGesture.ringAxisDir() : null;

        if (dragAxisDir == null)
        {
            dragAxisDir = transform.drag().frameBasis(transform.space()).getColumn(axis.ordinal(), new Vector3f());
        }

        float gx = initialVec.dot(axisX);
        float gy = initialVec.dot(axisY);
        float gz = initialVec.dot(axisZ);

        float px = 0;
        float pz = 0;
        float sweepDir = 1;

        if (axis == Axis.Y)
        {
            px = gx;
            pz = gz;
            sweepDir = Math.signum(dragAxisDir.dot(new Vector3f(axisY).mul(-1)));
        }
        else if (axis == Axis.X)
        {
            px = gy;
            pz = gz;
            sweepDir = Math.signum(dragAxisDir.dot(axisX));
        }
        else if (axis == Axis.Z)
        {
            px = gx;
            pz = -gy;
            sweepDir = Math.signum(dragAxisDir.dot(new Vector3f(axisZ).mul(-1)));
        }

        if (sweepDir == 0) sweepDir = 1;

        /* The ring is baked static for the whole drag (see applyBakedRotation),
         * so the pie grows from the fixed grab angle in every space — no
         * counter-rotation to cancel a live-rotating frame is needed. */
        float startDeg = MathUtils.toDeg((float) Math.atan2(pz, px));
        float sweepDeg = transform.getAccumulatedRotateDeg() * sweepDir;

        stack.push();

        if (axis == Axis.X) stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.multiply(RotationAxis.POSITIVE_X.rotation(MathUtils.PI / 2F));

        int color = axis == Axis.X ? Colors.RED : (axis == Axis.Y ? Colors.GREEN : Colors.BLUE);
        float r = Colors.getR(color);
        float g = Colors.getG(color);
        float b = Colors.getB(color);
        float a = 0.25F;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f mat = stack.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int segments = Math.max(12, (int) (Math.abs(sweepDeg) / 360F * 64F));
        float step = sweepDeg / segments;

        for (int i = 0; i < segments; i++)
        {
            float a1 = MathUtils.toRad(startDeg + step * i);
            float a2 = MathUtils.toRad(startDeg + step * (i + 1));

            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            float x2 = (float) Math.cos(a2) * radius;
            float z2 = (float) Math.sin(a2) * radius;

            builder.vertex(mat, 0, 0, 0).color(r, g, b, a).next();

            if (sweepDeg > 0)
            {
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a).next();
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a).next();
            }
            else
            {
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a).next();
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a).next();
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        float lineThickness = 0.005F * scale;
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float endDeg = startDeg + sweepDeg;

        float sx = (float) Math.cos(MathUtils.toRad(startDeg)) * radius;
        float sz = (float) Math.sin(MathUtils.toRad(startDeg)) * radius;
        float ex = (float) Math.cos(MathUtils.toRad(endDeg)) * radius;
        float ez = (float) Math.sin(MathUtils.toRad(endDeg)) * radius;

        Vector3f p1 = new Vector3f(-sz, 0, sx).normalize().mul(lineThickness);

        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, -p1.x, 0, -p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, 1F).next();

        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, 1F).next();
        builder.vertex(mat, sx + p1.x, 0, sz + p1.z).color(r, g, b, 1F).next();

        Vector3f p2 = new Vector3f(-ez, 0, ex).normalize().mul(lineThickness);
        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, -p2.x, 0, -p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, 1F).next();

        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, 1F).next();
        builder.vertex(mat, ex + p2.x, 0, ez + p2.z).color(r, g, b, 1F).next();

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();

        stack.pop();
    }
}
