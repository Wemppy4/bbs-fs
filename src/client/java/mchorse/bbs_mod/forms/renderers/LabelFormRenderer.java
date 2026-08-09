package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class LabelFormRenderer extends FormRenderer<LabelForm>
{
    /* ----------------------------------------------------------------------------------------
     * 1.21.11 render: the label background box used GameRenderer::getPositionColorProgram via
     * RenderSystem.setShader + BufferRenderer.drawWithGlobalProgram (both removed). It is now drawn
     * through a BBS-owned POSITION_COLOR pipeline wrapped in a RenderLayer.
     * ---------------------------------------------------------------------------------------- */
    private static final RenderPipeline SHADOW_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/label_shadow"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer shadowLayer;

    private static RenderLayer getShadowLayer()
    {
        if (shadowLayer == null)
        {
            shadowLayer = RenderLayer.of(BBSMod.MOD_ID + "_label_shadow",
                RenderSetup.builder(SHADOW_PIPELINE).translucent().build());
        }

        return shadowLayer;
    }

    public static void fillQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a).texture(0F, 0F);
    }

    public LabelFormRenderer(LabelForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int color = this.form.color.get().getARGBColor();
        String text = StringUtils.processColoredText(this.form.text.get());
        List<String> wrap = context.batcher.getFont().wrap(text, x2 - x1 - 4);

        int th = context.batcher.getFont().getHeight();
        int lineHeight = th + 4;
        int h = th + (wrap.size() - 1) * lineHeight;
        int y = (y2 + y1) / 2 - h / 2;

        for (String s : wrap)
        {
            context.batcher.textShadow(s, x1 + 2, y, color);

            y += lineHeight;
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        context.stack.push();

        if (this.form.billboard.get())
        {
            MatrixStackUtils.billboard(context.stack);
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        float scale = 1F / 16F;
        int light = context.light;

        MatrixStackUtils.scaleStack(context.stack, scale, -scale, scale);

        /* TODO(1.21.11 render): RenderSystem.disableCull/enableCull removed; cull is now per-pipeline
         * state. Text renders through the vanilla text RenderLayer which sets its own cull. */

        if (context.isPicking())
        {
            /* TODO(1.21.11 render): picking via RenderSystem.setShader(getPickerModelsProgram) and
             * setupTarget(ShaderProgram) is gone (ShaderProgram/GlUniform removed). The picker
             * pipeline (BBSShaders.getPickerModelsLayer) must be applied through the new
             * CustomVertexConsumerProvider layer override + a per-pass Target UBO uniform once the
             * picking foundation lands. Neutralized so the label still renders normally. */
            light = 0;
        }

        /* The whole label (text + background) records as ONE deferred group: its parts rely on
         * each other's depth (glyphs over the background quad), so they replay together in
         * original order, while the group as a whole sorts against other translucent forms. */
        boolean grouped = !context.isPicking() && FormTranslucentQueue.isActive();

        if (grouped)
        {
            Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.beginGroup(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin), false);
        }

        if (this.form.max.get() <= 10)
        {
            this.renderString(context, consumers, renderer, light);
        }
        else
        {
            this.renderLimitedString(context, consumers, renderer, light);
        }

        if (grouped)
        {
            FormTranslucentQueue.endGroup();
        }

        CustomVertexConsumerProvider.clearRunnables();

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest/enableCull removed (per-pipeline now). */

        context.stack.pop();
    }

    private void renderString(FormRenderingContext context, CustomVertexConsumerProvider consumers, TextRenderer renderer, int light)
    {
        String content = StringUtils.processColoredText(this.form.text.get());
        float transition = context.getTransition();
        int w = renderer.getWidth(content) - 1;
        int h = renderer.fontHeight - 2;
        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());

        Color shadowColor = this.form.shadowColor.get().copy();
        Color color = new Color().set(context.color, true);

        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());
        shadowColor.mul(context.color);

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);
            renderer.draw(
                content,
                x + this.form.shadowX.get(),
                y + this.form.shadowY.get(),
                shadowColor.getARGBColor(), false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                light
            );
            context.stack.pop();
        }

        renderer.draw(
            content,
            x,
            y,
            color.getARGBColor(), false,
            context.stack.peek().getPositionMatrix(),
            consumers,
            TextRenderer.TextLayerType.NORMAL,
            0,
            light
        );

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest removed (per-pipeline now). */

        consumers.draw();

        this.renderShadow(context, x, y, w, h);
    }

    private void renderLimitedString(FormRenderingContext context, CustomVertexConsumerProvider consumers, TextRenderer renderer, int light)
    {
        float transition = context.getTransition();
        int w = 0;
        int h = renderer.fontHeight - 2;
        String content = StringUtils.processColoredText(this.form.text.get());
        List<String> lines = FontRenderer.wrap(renderer, content, this.form.max.get());

        if (lines.size() <= 1)
        {
            this.renderString(context, consumers, renderer, light);

            return;
        }

        for (int i = 0; i < lines.size(); i++)
        {
            lines.set(i, lines.get(i).trim());
        }

        for (String line : lines)
        {
            w = Math.max(renderer.getWidth(line) - 1, w);
            h += 12;
        }

        h -= 12;

        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());
        int y2 = y;

        Color shadowColor = this.form.shadowColor.get().copy();

        shadowColor.mul(context.color);

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);

            for (String line : lines)
            {
                int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get()) : 0);

                renderer.draw(
                    line,
                    x2 + this.form.shadowX.get(),
                    y2 + this.form.shadowY.get(),
                    shadowColor.getARGBColor(), false,
                    context.stack.peek().getPositionMatrix(),
                    consumers,
                    TextRenderer.TextLayerType.NORMAL,
                    0,
                    light
                );

                y2 += 12;
            }

            context.stack.pop();

            y2 = y;
        }

        Color cColor = new Color().set(context.color, true);

        FormColorBlend.blend(cColor, this.form.color.get(), this.form.additiveColor.get());

        int color = cColor.getARGBColor();

        for (String line : lines)
        {
            int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get()) : 0);

            renderer.draw(
                line,
                x2,
                y2,
                color, false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                light
            );

            y2 += 12;
        }

        consumers.draw();

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest removed (per-pipeline now). */

        this.renderShadow(context, x, y, w, h);
    }

    private void renderShadow(FormRenderingContext context, int x, int y, int w, int h)
    {
        float offset = this.form.offset.get();
        Color color = this.form.background.get().copy();

        color.mul(context.color);

        if (color.a <= 0)
        {
            return;
        }

        context.stack.push();
        context.stack.translate(0, 0, -0.2F);


        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        fillQuad(
            builder, context.stack,
            x + w + offset, y - offset, 0,
            x - offset, y - offset, 0,
            x - offset, y + h + offset, 0,
            x + w + offset, y + h + offset, 0,
            color.r, color.g, color.b, color.a
        );

        /* Was: enableBlend + enableDepthTest + setShader(getPositionColorProgram) +
         * drawWithGlobalProgram. The POSITION_COLOR pipeline now encodes blend + depth test. */
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            getShadowLayer().draw(built);
        }

        context.stack.pop();
    }
}