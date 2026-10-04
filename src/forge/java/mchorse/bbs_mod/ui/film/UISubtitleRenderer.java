package mchorse.bbs_mod.ui.film;

import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.graphics.shader.GlUniform;
import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class UISubtitleRenderer
{
    private static Framebuffer getTextFramebuffer()
    {
        return BBSModClient.getFramebuffers().getFramebuffer(Link.bbs("camera_subtitles"), (f) ->
        {
            Texture texture = BBSModClient.getTextures().createTexture(Link.bbs("test"));

            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);

            f.deleteTextures();
            f.attach(texture, GL30.GL_COLOR_ATTACHMENT0);

            f.unbind();
        });
    }

    public static void renderSubtitles(MatrixStack stack, Batcher2D batcher, List<Subtitle> subtitles)
    {
        if (subtitles.isEmpty())
        {
            return;
        }

        ShaderProgram program = BBSShaders.getSubtitlesProgram();
        GlUniform blur = program.getUniform("Blur");
        GlUniform textureSize = program.getUniform("TextureSize");
        Supplier<ShaderProgram> supplier = () -> program;

        net.minecraft.client.shader.Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        float width = UIImageRenderer.getUnitWidth();
        float height = Placement.HEIGHT;

        Matrix4f cache = new Matrix4f(RenderSystem.getProjectionMatrix());

        int targetFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        java.nio.IntBuffer targetViewport = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, targetViewport);
        Framebuffer framebuffer = getTextFramebuffer();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
        Texture texture = framebuffer.getMainTexture();
        Matrix4f ortho = new Matrix4f().ortho(0, width, height, 0, -100, 100);

        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();

        for (Subtitle subtitle : subtitles)
        {
            float alpha = Colors.getA(subtitle.color);

            if (alpha <= 0)
            {
                continue;
            }

            String label = StringUtils.processColoredText(subtitle.label);
            /* The framebuffer is the subtitle's own size times its scale, so that scale is
             * exactly how many pixels a unit of the layout below covers. */
            FontRenderer font = BBSModClient.getFonts().get(subtitle.font, subtitle.fontSize, subtitle.placement.scaleX);

            if (font == null)
            {
                font = Batcher2D.getDefaultTextRenderer();
            }

            /* Line spacing of 0 has always drawn every line on top of the previous one,
             * so nothing out there means it - it's free to stand for "ask the font". */
            int lineHeight = subtitle.lineHeight > 0 ? subtitle.lineHeight : font.getLineHeight();
            Placement placement = subtitle.placement;
            int w = 0;
            int h = 0;
            float x = width * placement.windowX + placement.offsetX;
            float y = height * placement.windowY + placement.offsetY;
            float scaleX = placement.scaleX;
            float scaleY = placement.scaleY;
            int subColor = subtitle.color;

            List<String> strings = subtitle.maxWidth <= 10 ? Arrays.asList(label) : font.wrap(label, subtitle.maxWidth);

            for (String string : strings)
            {
                w = Math.max(w, font.getWidth(string.trim()));
            }

            h = (strings.size() - 1) * lineHeight + font.getHeight();

            Texture imgTex = null;
            float gap = 6F;
            float imgW = 0F;
            float imgH = 0F;

            if (subtitle.image != null && BBSModClient.getTextures().has(subtitle.image))
            {
                imgTex = BBSModClient.getTextures().getTexture(subtitle.image);

                if (imgTex != BBSModClient.getTextures().getError())
                {
                    int base = lineHeight;
                    imgH = base * subtitle.imageScale;
                    if (imgH <= 0) imgH = 0;
                    if (imgTex.height > 0)
                    {
                        imgW = imgTex.width * (imgH / imgTex.height);
                    }
                }
            }

            float contentW = w + (imgTex != null && imgH > 0 ? (gap + imgW) : 0);
            float contentH = Math.max(h, imgH);

            int fw = (int) ((contentW + 10) * scaleX);
            int fh = (int) ((contentH + 10) * scaleY);

            mchorse.bbs_mod.utils.MatrixStackUtils.loadProjection(new Matrix4f().ortho(0, contentW + 10, 0, contentH + 10, -100, 100));

            framebuffer.resize(fw, fh);
            framebuffer.applyClear();

            float baseX = 5F;
            float baseY = 5F;
            float textLeft = baseX + ((imgTex != null && imgH > 0 && !subtitle.imageRight) ? (imgW + gap) : 0F);
            float textAreaW = w;
            float yy = baseY + (contentH - h) / 2F;

            if (Colors.getA(subtitle.backgroundColor) > 0)
            {
                float o = subtitle.backgroundOffset;
                float bgX1 = baseX - o;
                float bgY1 = yy - o;
                float bgX2 = baseX + contentW + o - 1F;
                float bgY2 = yy + h + o;

                batcher.box(bgX1, bgY1, bgX2, bgY2, Colors.mulA(subtitle.backgroundColor, alpha));
            }

            if (imgTex != null && imgH > 0)
            {
                float imgX = subtitle.imageRight ? baseX + contentW - imgW : baseX;
                float imgY = baseY + (contentH - imgH) / 2F;

                batcher.texturedBox(imgTex, Colors.setA(Colors.WHITE, 1F), imgX, imgY, imgW, imgH, 0, 0, imgTex.width, imgTex.height, imgTex.width, imgTex.height);
            }

            FontRenderer previousFont = batcher.setFont(font);

            try
            {
                for (String string : strings)
                {
                    string = string.trim();

                    int xx = (int) (textLeft + (textAreaW - font.getWidth(string)) / 2F);
                    batcher.text(string, xx, (int) yy, Colors.setA(subColor, 1F), subtitle.textShadow);

                    yy += lineHeight;
                }
            }
            finally
            {
                batcher.setFont(previousFont);
            }

            /* Render the texture */
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
            GlStateManager.viewport(targetViewport.get(0), targetViewport.get(1), targetViewport.get(2), targetViewport.get(3));

            subtitle.box.set(x - fw * placement.anchorX, y - fh * placement.anchorY, fw, fh, width);

            mchorse.bbs_mod.utils.MatrixStackUtils.loadProjection(ortho);

            Transform transform = new Transform();

            transform.lerp(subtitle.transform, 1F - subtitle.factor);

            stack.push();
            stack.translate(x, y, 0);
            MatrixStackUtils.applyTransform(stack, transform);

            if (blur != null)
            {
                blur.set(subtitle.shadow, subtitle.shadowOpaque ? 1F : 0F);
            }

            if (textureSize != null)
            {
                textureSize.set((float) texture.width, (float) texture.height);
            }

            RenderSystem.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            batcher.texturedBox(supplier, texture.id, Colors.setA(Colors.WHITE, alpha), -fw * placement.anchorX, -fh * placement.anchorY, texture.width, texture.height, 0, 0, texture.width, texture.height, texture.width, texture.height);

            stack.pop();
        }

        mchorse.bbs_mod.utils.MatrixStackUtils.loadProjection(cache);
        RenderSystem.enableCull();
    }
}
