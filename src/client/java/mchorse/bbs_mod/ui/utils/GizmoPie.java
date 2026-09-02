package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy;
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
import org.joml.Matrix3f;
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
 * <p>Two shapes, because the two rotations are measured differently: an axis ring turns in
 * its own plane, while the view ring turns in the screen plane and is therefore built from
 * the cursor's screen angles.
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
     *                    way the wedge sweeps; ignored for the view ring.
     */
    public static void draw(MatrixStack stack, UIPropTransform transform, DragStrategy ringGesture)
    {
        if (transform == null || !transform.isEditing() || transform.getOp() != TransformOp.ROTATE)
        {
            return;
        }

        if (transform.isSphereRotate())
        {
            return;
        }

        if (transform.isViewRotate())
        {
            drawView(stack, transform);

            return;
        }

        Axis axis = transform.getAxis();

        if (axis != null)
        {
            drawAxis(stack, transform, ringGesture, axis);
        }
    }

    /**
     * Sweep pie for the view (screen-plane) ring. Built straight from the cursor's screen
     * angles using the gizmo's local directions that map to screen right and down, so it
     * starts exactly under the grab, its leading edge follows the cursor, and — being in the
     * gizmo's own (distance-scaled) frame — its radius rides the ring at any FOV.
     */
    private static void drawView(MatrixStack stack, UIPropTransform transform)
    {
        float sweepRad = transform.getViewScreenSweepRad();

        if (Math.abs(sweepRad) < 1.0E-4F)
        {
            return;
        }

        Matrix4f mat = stack.peek().getPositionMatrix();
        Matrix3f basis = mat.get3x3(new Matrix3f());

        if (Math.abs(basis.determinant()) < 1.0E-8F)
        {
            return;
        }

        /* Local directions mapping to screen right and screen down. Unit vectors,
         * so a step of {@code radius} along them lands on the ring. */
        Matrix3f inverse = basis.invert();
        Vector3f right = inverse.transform(new Vector3f(1F, 0F, 0F)).normalize();
        Vector3f down = inverse.transform(new Vector3f(0F, -1F, 0F)).normalize();

        float startRad = transform.getViewGrabScreenAngle();
        float scale = BBSSettings.axesScale.get();
        float radius = 0.22F * scale * GizmoRings.VIEW_RING_SCALE;

        int color = Colors.LIGHTEST_GRAY;
        float r = Colors.getR(color);
        float g = Colors.getG(color);
        float b = Colors.getB(color);


        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableCull();

        int segments = Math.max(2, (int) (Math.abs(sweepRad) / (float) (2D * Math.PI) * 64F));
        float step = sweepRad / segments;
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segments; i++)
        {
            rimPoint(p1, right, down, startRad + step * i, radius);
            rimPoint(p2, right, down, startRad + step * (i + 1), radius);

            builder.vertex(mat, 0, 0, 0).color(r, g, b, 0.25F);
            builder.vertex(mat, p1.x, p1.y, p1.z).color(r, g, b, 0.25F);
            builder.vertex(mat, p2.x, p2.y, p2.z).color(r, g, b, 0.25F);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        /* Bright radial edges at the grab angle and the leading angle, like the axis pie. */
        float thickness = 0.005F * scale;
        builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        edge(builder, mat, right, down, startRad, radius, thickness, r, g, b);
        edge(builder, mat, right, down, startRad + sweepRad, radius, thickness, r, g, b);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Point at screen angle {@code angle} and {@code radius} in the screen right/down
     *  basis, written into {@code out}. */
    private static void rimPoint(Vector3f out, Vector3f right, Vector3f down, float angle, float radius)
    {
        float c = (float) Math.cos(angle) * radius;
        float s = (float) Math.sin(angle) * radius;

        out.set(right.x * c + down.x * s, right.y * c + down.y * s, right.z * c + down.z * s);
    }

    /** One radial boundary line of the view pie: a thin quad from centre to the rim at
     *  screen {@code angle}, built from the screen right/down basis. */
    private static void edge(BufferBuilder builder, Matrix4f mat, Vector3f right, Vector3f down, float angle, float radius, float thickness, float r, float g, float b)
    {
        Vector3f rim = new Vector3f();
        Vector3f perp = new Vector3f();

        rimPoint(rim, right, down, angle, radius);
        rimPoint(perp, right, down, angle + (float) (Math.PI / 2D), thickness);

        builder.vertex(mat, perp.x, perp.y, perp.z).color(r, g, b, 1F);
        builder.vertex(mat, -perp.x, -perp.y, -perp.z).color(r, g, b, 1F);
        builder.vertex(mat, rim.x - perp.x, rim.y - perp.y, rim.z - perp.z).color(r, g, b, 1F);

        builder.vertex(mat, perp.x, perp.y, perp.z).color(r, g, b, 1F);
        builder.vertex(mat, rim.x - perp.x, rim.y - perp.y, rim.z - perp.z).color(r, g, b, 1F);
        builder.vertex(mat, rim.x + perp.x, rim.y + perp.y, rim.z + perp.z).color(r, g, b, 1F);
    }

    /** Sweep pie for one of the three axis rings, drawn in the ring's own plane. */
    private static void drawAxis(MatrixStack stack, UIPropTransform transform, DragStrategy ringGesture, Axis axis)
    {
        if (transform.getDrag() == null) return;

        float scale = BBSSettings.axesScale.get();
        float radius = 0.22F * scale;

        Vector3f initialVec = transform.getInitialDragRingVec();

        Vector3f axisX = transform.getDrag().gizmoWorldAxes.getColumn(0, new Vector3f());
        Vector3f axisY = transform.getDrag().gizmoWorldAxes.getColumn(1, new Vector3f());
        Vector3f axisZ = transform.getDrag().gizmoWorldAxes.getColumn(2, new Vector3f());
        /* The ring's actual world rotation axis in the active space — the same
         * basis the ring is drawn in (Gizmo.reorientForSpace) and the drag turns
         * about. The axis comes from the GESTURE itself (its anchored turn axis),
         * so the pie can never disagree with the rotation — the drawn frame axis
         * and the real turn axis differ on the channel path (PARENT / the pole
         * fallback), where cubic models flip the channels' X/Z response. */
        Vector3f dragAxisDir = ringGesture != null ? ringGesture.ringAxisDir() : null;

        if (dragAxisDir == null)
        {
            dragAxisDir = transform.getDrag().frameBasis(transform.getSpace()).getColumn(axis.ordinal(), new Vector3f());
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

        Matrix4f mat = stack.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

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

            builder.vertex(mat, 0, 0, 0).color(r, g, b, a);

            if (sweepDeg > 0)
            {
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a);
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a);
            }
            else
            {
                builder.vertex(mat, x2, 0, z2).color(r, g, b, a);
                builder.vertex(mat, x1, 0, z1).color(r, g, b, a);
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        float lineThickness = 0.005F * scale;
        builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float endDeg = startDeg + sweepDeg;

        float sx = (float) Math.cos(MathUtils.toRad(startDeg)) * radius;
        float sz = (float) Math.sin(MathUtils.toRad(startDeg)) * radius;
        float ex = (float) Math.cos(MathUtils.toRad(endDeg)) * radius;
        float ez = (float) Math.sin(MathUtils.toRad(endDeg)) * radius;

        Vector3f p1 = new Vector3f(-sz, 0, sx).normalize().mul(lineThickness);

        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, 1F);
        builder.vertex(mat, -p1.x, 0, -p1.z).color(r, g, b, 1F);
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, 1F);

        builder.vertex(mat, p1.x, 0, p1.z).color(r, g, b, 1F);
        builder.vertex(mat, sx - p1.x, 0, sz - p1.z).color(r, g, b, 1F);
        builder.vertex(mat, sx + p1.x, 0, sz + p1.z).color(r, g, b, 1F);

        Vector3f p2 = new Vector3f(-ez, 0, ex).normalize().mul(lineThickness);
        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, 1F);
        builder.vertex(mat, -p2.x, 0, -p2.z).color(r, g, b, 1F);
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, 1F);

        builder.vertex(mat, p2.x, 0, p2.z).color(r, g, b, 1F);
        builder.vertex(mat, ex - p2.x, 0, ez - p2.z).color(r, g, b, 1F);
        builder.vertex(mat, ex + p2.x, 0, ez + p2.z).color(r, g, b, 1F);

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();

        stack.pop();
    }
}
