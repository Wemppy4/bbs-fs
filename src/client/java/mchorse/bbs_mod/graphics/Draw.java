package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.colors.Colors;
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

public class Draw
{
    public static void renderBox(MatrixStack stack, double x, double y, double z, double w, double h, double d)
    {
        renderBox(stack, x, y, z, w, h, d, 1, 1, 1);
    }

    public static void renderBox(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b)
    {
        renderBox(stack, x, y, z, w, h, d, r, g, b, 1F);
    }

    public static void renderBox(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b, float a)
    {
        stack.push();
        stack.translate(x, y, z);
        float fw = (float) w;
        float fh = (float) h;
        float fd = (float) d;
        float t = 1 / 96F + (float) (Math.sqrt(w * w + h + h + d + d) / 2000);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* Pillars: fillBox(builder, -t, -t, -t, t, t, t, r, g, b, a); */
        fillBox(builder, stack, -t, -t, -t, t, t + fh, t, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t, -t, t + fw, t + fh, t, r, g, b, a);
        fillBox(builder, stack, -t, -t, -t + fd, t, t + fh, t + fd, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);

        /* Top */
        fillBox(builder, stack, -t, -t + fh, -t, t + fw, t + fh, t, r, g, b, a);
        fillBox(builder, stack, -t, -t + fh, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);
        fillBox(builder, stack, -t, -t + fh, -t, t, t + fh, t + fd, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t + fh, -t, t + fw, t + fh, t + fd, r, g, b, a);

        /* Bottom */
        fillBox(builder, stack, -t, -t, -t, t + fw, t, t, r, g, b, a);
        fillBox(builder, stack, -t, -t, -t + fd, t + fw, t, t + fd, r, g, b, a);
        fillBox(builder, stack, -t, -t, -t, t, t, t + fd, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t, -t, t + fw, t, t + fd, r, g, b, a);

        { net.minecraft.client.render.BuiltBuffer __bbsBuilt = builder.endNullable(); if (__bbsBuilt != null) BufferRenderer.drawWithGlobalProgram(__bbsBuilt); }

        stack.pop();
    }

    /**
     * Fill a quad for {@link net.minecraft.client.render.VertexFormats#POSITION_TEXTURE_COLOR_NORMAL}. Points should
     * be supplied in this order:
     *
     *     3 -------> 4
     *     ^
     *     |
     *     |
     *     2 <------- 1
     *
     * I.e. bottom left, bottom right, top left, top right, where left is -X and right is +X,
     * in case of a quad on fixed on Z axis.
     */
    public static void fillTexturedNormalQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float r, float g, float b, float a, float nx, float ny, float nz)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BL, 2 - BR, 3 - TR, 4 - TL */
        builder.vertex(matrix4f, x2, y2, z2).texture(u1, v2).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x1, y1, z1).texture(u2, v2).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x4, y4, z4).texture(u2, v1).color(r, g, b, a).normal(nx, ny, nz);

        builder.vertex(matrix4f, x2, y2, z2).texture(u1, v2).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x4, y4, z4).texture(u2, v1).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x3, y3, z3).texture(u1, v1).color(r, g, b, a).normal(nx, ny, nz);
    }

    public static void fillQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a);
    }

    public static void fillBoxTo(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Angle angle = Angle.angle(dx, dy, dz);

        stack.push();

        stack.translate(x1, y1, z1);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle.yaw));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle.pitch));

        fillBox(builder, stack, -thickness / 2, -thickness / 2, 0, thickness / 2, thickness / 2, (float) distance, r, g, b, a);

        stack.pop();
    }

    public static void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, int color)
    {
        float alpha = Colors.getA(color);

        if (alpha <= 0F)
        {
            alpha = 1F;
        }

        fillBox(builder, stack, x1, y1, z1, x2, y2, z2, Colors.getR(color), Colors.getG(color), Colors.getB(color), alpha);
    }

    public static void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b)
    {
        fillBox(builder, stack, x1, y1, z1, x2, y2, z2, r, g, b, 1F);
    }

    public static void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a)
    {
        /* X */
        fillQuad(builder, stack, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
        fillQuad(builder, stack, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);

        /* Y */
        fillQuad(builder, stack, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        fillQuad(builder, stack, x2, y2, z1, x1, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a);

        /* Z */
        fillQuad(builder, stack, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        fillQuad(builder, stack, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
    }

    public static void coolerAxes(MatrixStack stack, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        axisSize *= scale;
        axisOffset *= scale * thickness;


        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, Colors.RED);
        fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, Colors.GREEN);
        fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, Colors.BLUE);
        fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, Colors.WHITE);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();

        { net.minecraft.client.render.BuiltBuffer __bbsBuilt = builder.endNullable(); if (__bbsBuilt != null) BufferRenderer.drawWithGlobalProgram(__bbsBuilt); }
    }

    public static void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, int color)
    {
        arc3D(builder, stack, axis, radius, thickness, Colors.getR(color), Colors.getG(color), Colors.getB(color), 0F, 360F);
    }

    public static void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b)
    {
        arc3D(builder, stack, axis, radius, thickness, r, g, b, 0F, 360F);
    }

    /**
     * Based on ElGatoPro300's code from BBS mod CML edition
     */
    public static void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float startDeg, float sweepDeg)
    {
        arc3D(builder, stack, axis, radius, thickness, r, g, b, startDeg, sweepDeg, 64, 12);
    }

    /**
     * Tessellate an arc of a torus. The tube's cross-section circle is precomputed once and the
     * ring-angle trig lives outside the inner loop — the old shape recomputed both per quad,
     * which put ~18k trig calls into a single ring.
     */
    public static void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float startDeg, float sweepDeg, int segU, int segV)
    {
        double u0 = Math.toRadians(startDeg);
        double uStep = Math.toRadians(sweepDeg / (double) segU);
        double vStep = Math.PI * 2D / (double) segV;

        stack.push();

        if (axis == Axis.X) stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.multiply(RotationAxis.POSITIVE_X.rotation(MathUtils.PI / 2F));

        float tubeR = thickness * 0.5F;
        Matrix4f mat = stack.peek().getPositionMatrix();

        /* The tube cross-section: ring-of-the-tube radii and heights, shared by every u step. */
        double[] ringR = new double[segV + 1];
        float[] ringY = new float[segV + 1];

        for (int iv = 0; iv <= segV; iv++)
        {
            double v = vStep * iv;

            ringR[iv] = radius + tubeR * Math.cos(v);
            ringY[iv] = (float) (tubeR * Math.sin(v));
        }

        double cosU2 = Math.cos(u0);
        double sinU2 = Math.sin(u0);

        for (int iu = 0; iu < segU; iu++)
        {
            double cosU1 = cosU2;
            double sinU1 = sinU2;
            double u2 = u0 + uStep * (iu + 1);

            cosU2 = Math.cos(u2);
            sinU2 = Math.sin(u2);

            for (int iv = 0; iv < segV; iv++)
            {
                double r1 = ringR[iv];
                double r2 = ringR[iv + 1];
                float y1 = ringY[iv];
                float y2 = ringY[iv + 1];

                float x11 = (float) (r1 * cosU1);
                float z11 = (float) (r1 * sinU1);

                float x12 = (float) (r2 * cosU1);
                float z12 = (float) (r2 * sinU1);

                float x21 = (float) (r1 * cosU2);
                float z21 = (float) (r1 * sinU2);

                float x22 = (float) (r2 * cosU2);
                float z22 = (float) (r2 * sinU2);

                builder.vertex(mat, x11, y1, z11).color(r, g, b, 1F);
                builder.vertex(mat, x12, y2, z12).color(r, g, b, 1F);
                builder.vertex(mat, x22, y2, z22).color(r, g, b, 1F);

                builder.vertex(mat, x11, y1, z11).color(r, g, b, 1F);
                builder.vertex(mat, x22, y2, z22).color(r, g, b, 1F);
                builder.vertex(mat, x21, y1, z21).color(r, g, b, 1F);
            }
        }

        stack.pop();
    }

    public static void sphere(BufferBuilder builder, MatrixStack stack, float radius, int rings, int sectors, float r, float g, float b, float a)
    {
        float constR = 1.0F / (float) (rings - 1);
        float constS = 1.0F / (float) (sectors - 1);
        
        Matrix4f mat = stack.peek().getPositionMatrix();
        
        for (int i = 0; i < rings - 1; i++)
        {
            for (int j = 0; j < sectors - 1; j++)
            {
                float y0 = (float) Math.sin(-Math.PI / 2 + Math.PI * i * constR);
                float x0 = (float) Math.cos(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * i * constR);
                float z0 = (float) Math.sin(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * i * constR);
                
                float y1 = (float) Math.sin(-Math.PI / 2 + Math.PI * (i + 1) * constR);
                float x1 = (float) Math.cos(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                float z1 = (float) Math.sin(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                
                float y2 = (float) Math.sin(-Math.PI / 2 + Math.PI * (i + 1) * constR);
                float x2 = (float) Math.cos(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                float z2 = (float) Math.sin(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                
                float y3 = (float) Math.sin(-Math.PI / 2 + Math.PI * i * constR);
                float x3 = (float) Math.cos(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * i * constR);
                float z3 = (float) Math.sin(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * i * constR);
                
                builder.vertex(mat, x0 * radius, y0 * radius, z0 * radius).color(r, g, b, a);
                builder.vertex(mat, x1 * radius, y1 * radius, z1 * radius).color(r, g, b, a);
                builder.vertex(mat, x2 * radius, y2 * radius, z2 * radius).color(r, g, b, a);
                
                builder.vertex(mat, x0 * radius, y0 * radius, z0 * radius).color(r, g, b, a);
                builder.vertex(mat, x2 * radius, y2 * radius, z2 * radius).color(r, g, b, a);
                builder.vertex(mat, x3 * radius, y3 * radius, z3 * radius).color(r, g, b, a);
            }
        }
    }
}
