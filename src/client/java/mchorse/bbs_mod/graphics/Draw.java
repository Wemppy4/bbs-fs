package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * 3D debug/gizmo drawing helpers.
 *
 * Ported to 1.21.11. The geometry-building API is unchanged ({@link MatrixStack} + {@link Matrix4f}
 * + {@link BufferBuilder} still exist and behave as before), so all public method signatures are
 * preserved. What changed is the draw-flush: 1.21.5 removed {@code RenderSystem.setShader(...)} and
 * {@code BufferRenderer.drawWithGlobalProgram(...)}. Immediate-mode geometry is now built into a
 * {@link BufferBuilder}, finished into a {@link BuiltBuffer}, and submitted through a
 * {@link RenderLayer} that carries a {@link RenderPipeline}.
 *
 * These two BBS-owned POSITION_COLOR pipelines (one depth-tested, one not) replace the old
 * {@code GameRenderer::getPositionColorProgram} usage. They are seeded from the vanilla
 * {@code POSITION_COLOR_SNIPPET} (same vertex/fragment shader + transform UBO setup as the GUI/world
 * position-color path) so the model-view/projection matrices flow through unchanged.
 *
 * TODO(1.21.11 render): verify at runtime that drawing through RenderLayer.draw() here picks up the
 * current world model-view/projection. These helpers are invoked from within world/entity render
 * passes where the global transform stack is already configured; if the transforms come out wrong,
 * the pipeline may need the world transform snippet instead of the bare position-color snippet.
 */
public class Draw
{
    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    /* POSITION_COLOR / TRIANGLES, depth-tested (faithful to the old position-color program used for
     * boxes/arcs/spheres inside the world). */
    private static final RenderPipeline POSITION_COLOR_TRIS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/draw_position_color"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    /* POSITION_COLOR / TRIANGLES, no depth test (coolerAxes did RenderSystem.disableDepthTest()). */
    private static final RenderPipeline POSITION_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/draw_position_color_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    /* POSITION_COLOR / DEBUG_LINES, depth-tested. Replaces the old DEBUG_LINES + GameRenderer::getPositionColorProgram
     * path (e.g. the 3D model-preview ground grid); GL_LINES width 1, no cull, drawn under LEQUAL depth as the
     * original did via RenderSystem.depthFunc(GL_LEQUAL). */
    private static final RenderPipeline POSITION_COLOR_LINES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/draw_position_color_lines"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer positionColorLayer;
    private static RenderLayer positionColorNoDepthLayer;
    private static RenderLayer positionColorLinesLayer;

    private static RenderLayer getPositionColorLayer()
    {
        if (positionColorLayer == null)
        {
            positionColorLayer = RenderLayer.of(BBSMod.MOD_ID + "_draw_position_color",
                RenderSetup.builder(POSITION_COLOR_TRIS).translucent().build());
        }

        return positionColorLayer;
    }

    private static RenderLayer getPositionColorNoDepthLayer()
    {
        if (positionColorNoDepthLayer == null)
        {
            positionColorNoDepthLayer = RenderLayer.of(BBSMod.MOD_ID + "_draw_position_color_no_depth",
                RenderSetup.builder(POSITION_COLOR_TRIS_NO_DEPTH).translucent().build());
        }

        return positionColorNoDepthLayer;
    }

    private static RenderLayer getPositionColorLinesLayer()
    {
        if (positionColorLinesLayer == null)
        {
            positionColorLinesLayer = RenderLayer.of(BBSMod.MOD_ID + "_draw_position_color_lines",
                RenderSetup.builder(POSITION_COLOR_LINES).translucent().build());
        }

        return positionColorLinesLayer;
    }

    /**
     * Submit a DEBUG_LINES POSITION_COLOR buffer through the BBS line pipeline. Replacement for the old
     * {@code BufferRenderer.drawWithGlobalProgram} flush used by immediate-mode line geometry (e.g. the
     * model-preview ground grid). No-op on an empty buffer.
     */
    public static void flushLines(BufferBuilder builder)
    {
        flush(builder, getPositionColorLinesLayer());
    }

    /**
     * Submit a TRIANGLES POSITION_COLOR buffer through the BBS depth-tested triangle pipeline.
     * Replacement for the old {@code BufferRenderer.drawWithGlobalProgram} used by immediate-mode
     * world overlays (e.g. the film motion path). No-op on an empty buffer.
     */
    public static void flushTriangles(BufferBuilder builder)
    {
        flush(builder, getPositionColorLayer());
    }

    /** Finish a buffer and submit it through the given layer (no-op on an empty buffer). */
    private static void flush(BufferBuilder builder, RenderLayer layer)
    {
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            /* TODO(1.21.11 render): verify at runtime. RenderLayer.draw uploads + draws with the
             * layer pipeline; previously this was BufferRenderer.drawWithGlobalProgram. */
            layer.draw(built);
        }
    }

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

        flush(builder, getPositionColorLayer());

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

        /* The old code did RenderSystem.disableDepthTest() before drawing; depth state now lives in
         * the pipeline, so this draws through the no-depth POSITION_COLOR layer instead. */
        flush(builder, getPositionColorNoDepthLayer());
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
        int segU = 96;
        int segV = 24;
        double u0 = Math.toRadians(startDeg);
        double uStep = Math.toRadians(sweepDeg / (double) segU);
        double vStep = Math.PI * 2D / (double) segV;

        stack.push();

        if (axis == Axis.X) stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.multiply(RotationAxis.POSITIVE_X.rotation(MathUtils.PI / 2F));

        float tubeR = thickness * 0.5F;
        Matrix4f mat = stack.peek().getPositionMatrix();

        for (int iu = 0; iu < segU; iu++)
        {
            double u1 = u0 + uStep * iu;
            double u2 = u0 + uStep * (iu + 1);

            for (int iv = 0; iv < segV; iv++)
            {
                double v1 = vStep * iv;
                double v2 = vStep * (iv + 1);
                double cos1 = radius + tubeR * Math.cos(v1);
                double cos2 = radius + tubeR * Math.cos(v2);

                float x11 = (float) (cos1 * Math.cos(u1));
                float z11 = (float) (cos1 * Math.sin(u1));
                float y11 = (float) (tubeR * Math.sin(v1));

                float x12 = (float) (cos2 * Math.cos(u1));
                float z12 = (float) (cos2 * Math.sin(u1));
                float y12 = (float) (tubeR * Math.sin(v2));

                float x21 = (float) (cos1 * Math.cos(u2));
                float z21 = (float) (cos1 * Math.sin(u2));
                float y21 = (float) (tubeR * Math.sin(v1));

                float x22 = (float) (cos2 * Math.cos(u2));
                float z22 = (float) (cos2 * Math.sin(u2));
                float y22 = (float) (tubeR * Math.sin(v2));

                builder.vertex(mat, x11, y11, z11).color(r, g, b, 1F);
                builder.vertex(mat, x12, y12, z12).color(r, g, b, 1F);
                builder.vertex(mat, x22, y22, z22).color(r, g, b, 1F);

                builder.vertex(mat, x11, y11, z11).color(r, g, b, 1F);
                builder.vertex(mat, x22, y22, z22).color(r, g, b, 1F);
                builder.vertex(mat, x21, y21, z21).color(r, g, b, 1F);
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
