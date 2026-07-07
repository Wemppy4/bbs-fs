package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The shared drawing language of the model debug overlays (IK and physics):
 * lines that can be hairline or volumetric, solid or dashed, and markers in
 * the {@link ValueDebugElement} shapes. Everything is emitted in the overlay's
 * drawing space through the given matrix — no Euler orientation anywhere
 * ({@code Draw.fillBoxTo}'s camera-convention angles diverge under the model
 * stack and tubes fly off the joints), thick lines and shapes are built from
 * cross-product bases and raw vertices instead.
 */
public final class DebugOverlay
{
    private DebugOverlay()
    {
    }

    public static float[] rgb(int color)
    {
        return new float[] {Colors.getR(color), Colors.getG(color), Colors.getB(color)};
    }

    /** A line segment: hairline vertices when {@code thickness} is zero, a prism otherwise; split into dashes when asked. */
    public static void segment(BufferBuilder builder, Matrix4f matrix, float thickness, boolean dashed, float dash, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        if (!dashed || dash <= 0F)
        {
            solid(builder, matrix, thickness, p1, p2, col, a);

            return;
        }

        int count = Math.max(1, Math.round(p1.distance(p2) / dash));

        /* An odd dash count keeps both endpoints solid. */
        if (count % 2 == 0)
        {
            count++;
        }

        for (int i = 0; i < count; i += 2)
        {
            Vector3f d1 = new Vector3f(p1).lerp(p2, i / (float) count);
            Vector3f d2 = new Vector3f(p1).lerp(p2, (i + 1) / (float) count);

            solid(builder, matrix, thickness, d1, d2, col, a);
        }
    }

    public static void line(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p2.x, p2.y, p2.z).color(col[0], col[1], col[2], a).next();
    }

    private static void solid(BufferBuilder builder, Matrix4f matrix, float thickness, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        if (thickness > 0F)
        {
            prism(builder, matrix, p1, p2, thickness, col, a);
        }
        else
        {
            line(builder, matrix, p1, p2, col, a);
        }
    }

    /** A thick line as a box built from cross-product basis vectors. */
    private static void prism(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, float thickness, float[] col, float a)
    {
        Vector3f d = new Vector3f(p2).sub(p1);
        float len2 = d.lengthSquared();

        if (len2 < 1e-12F)
        {
            return;
        }

        Vector3f ref = d.y * d.y > 0.98F * len2 ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
        Vector3f u = new Vector3f(d).cross(ref).normalize().mul(thickness * 0.5F);
        Vector3f v = new Vector3f(d).cross(u).normalize().mul(thickness * 0.5F);

        Vector3f c1 = new Vector3f(p1).add(u).add(v);
        Vector3f c2 = new Vector3f(p1).sub(u).add(v);
        Vector3f c3 = new Vector3f(p1).sub(u).sub(v);
        Vector3f c4 = new Vector3f(p1).add(u).sub(v);
        Vector3f c5 = new Vector3f(p2).add(u).add(v);
        Vector3f c6 = new Vector3f(p2).sub(u).add(v);
        Vector3f c7 = new Vector3f(p2).sub(u).sub(v);
        Vector3f c8 = new Vector3f(p2).add(u).sub(v);

        quad(builder, matrix, c1, c2, c6, c5, col, a);
        quad(builder, matrix, c2, c3, c7, c6, col, a);
        quad(builder, matrix, c3, c4, c8, c7, col, a);
        quad(builder, matrix, c4, c1, c5, c8, col, a);
        quad(builder, matrix, c1, c2, c3, c4, col, a);
        quad(builder, matrix, c5, c6, c7, c8, col, a);
    }

    /** A marker of the element's shape at {@code p}. The factors even out the shapes' visual footprint at equal sizes. */
    public static void marker(BufferBuilder builder, MatrixStack stack, int shape, Vector3f p, float radius, float[] col, float a)
    {
        stack.push();
        stack.translate(p.x, p.y, p.z);

        if (shape == ValueDebugElement.SHAPE_CUBE)
        {
            float s = radius * 0.8F;

            Draw.fillBox(builder, stack, -s, -s, -s, s, s, s, col[0], col[1], col[2], a);
        }
        else if (shape == ValueDebugElement.SHAPE_DIAMOND)
        {
            diamond(builder, stack, radius * 1.3F, col, a);
        }
        else if (shape == ValueDebugElement.SHAPE_RING)
        {
            ring(builder, stack, radius * 1.05F, radius * 0.22F, col, a);
        }
        else if (shape == ValueDebugElement.SHAPE_CROSS)
        {
            cross(builder, stack, radius, col, a);
        }
        else
        {
            Draw.sphere(builder, stack, radius, 9, 9, col[0], col[1], col[2], a);
        }

        stack.pop();
    }

    /** An octahedron with apexes at {@code s} along every axis. */
    private static void diamond(BufferBuilder builder, MatrixStack stack, float s, float[] col, float a)
    {
        Matrix4f m = stack.peek().getPositionMatrix();

        tri(builder, m, 0, s, 0, s, 0, 0, 0, 0, s, col, a);
        tri(builder, m, 0, s, 0, 0, 0, s, -s, 0, 0, col, a);
        tri(builder, m, 0, s, 0, -s, 0, 0, 0, 0, -s, col, a);
        tri(builder, m, 0, s, 0, 0, 0, -s, s, 0, 0, col, a);
        tri(builder, m, 0, -s, 0, 0, 0, s, s, 0, 0, col, a);
        tri(builder, m, 0, -s, 0, -s, 0, 0, 0, 0, s, col, a);
        tri(builder, m, 0, -s, 0, 0, 0, -s, -s, 0, 0, col, a);
        tri(builder, m, 0, -s, 0, s, 0, 0, 0, 0, -s, col, a);
    }

    /** A thin torus around the Y axis. */
    private static void ring(BufferBuilder builder, MatrixStack stack, float radius, float tube, float[] col, float a)
    {
        Matrix4f m = stack.peek().getPositionMatrix();
        int segU = 20;
        int segV = 8;

        for (int iu = 0; iu < segU; iu++)
        {
            float u1 = MathUtils.PI * 2F * iu / segU;
            float u2 = MathUtils.PI * 2F * (iu + 1) / segU;

            for (int iv = 0; iv < segV; iv++)
            {
                float v1 = MathUtils.PI * 2F * iv / segV;
                float v2 = MathUtils.PI * 2F * (iv + 1) / segV;

                Vector3f p11 = torusPoint(radius, tube, u1, v1);
                Vector3f p12 = torusPoint(radius, tube, u1, v2);
                Vector3f p21 = torusPoint(radius, tube, u2, v1);
                Vector3f p22 = torusPoint(radius, tube, u2, v2);

                quad(builder, m, p11, p12, p22, p21, col, a);
            }
        }
    }

    private static Vector3f torusPoint(float radius, float tube, float u, float v)
    {
        float ring = radius + tube * (float) Math.cos(v);

        return new Vector3f(ring * (float) Math.cos(u), tube * (float) Math.sin(v), ring * (float) Math.sin(u));
    }

    /** Three thin axis-aligned bars through the centre, Blender's plain-axes empty. */
    private static void cross(BufferBuilder builder, MatrixStack stack, float radius, float[] col, float a)
    {
        float l = radius * 1.4F;
        float t = radius * 0.16F;

        Draw.fillBox(builder, stack, -l, -t, -t, l, t, t, col[0], col[1], col[2], a);
        Draw.fillBox(builder, stack, -t, -l, -t, t, l, t, col[0], col[1], col[2], a);
        Draw.fillBox(builder, stack, -t, -t, -l, t, t, l, col[0], col[1], col[2], a);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, float[] col, float a)
    {
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p2.x, p2.y, p2.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p3.x, p3.y, p3.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p3.x, p3.y, p3.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p4.x, p4.y, p4.z).color(col[0], col[1], col[2], a).next();
    }

    private static void tri(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float[] col, float a)
    {
        builder.vertex(matrix, x1, y1, z1).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, x2, y2, z2).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, x3, y3, z3).color(col[0], col[1], col[2], a).next();
    }
}
