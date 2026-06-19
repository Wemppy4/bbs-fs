package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
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
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

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

        RenderPipeline program = BBSShaders.getSubtitlesProgram();
        Supplier<RenderPipeline> supplier = () -> program;

        net.minecraft.client.gl.Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        int width = fb.textureWidth;
        int height = fb.textureHeight;

        /* TODO(1.21.11 render): projection matrix is GPU-owned now (RenderSystem.getProjectionMatrix/setProjectionMatrix(Matrix4f) removed).
         * The subtitle compositing previously cached + swapped the projection; restore via the new pipeline foundation. */

        width /= 2;
        height /= 2;

        Framebuffer framebuffer = getTextFramebuffer();
        Texture texture = framebuffer.getMainTexture();
        Matrix4f ortho = new Matrix4f().ortho(0, width, height, 0, -100, 100);
        FontRenderer font = Batcher2D.getDefaultTextRenderer();

        /* TODO(1.21.11 render): depth/cull state now lives in the RenderPipeline/RenderLayer; removed RenderSystem.depthFunc(GL_ALWAYS)/disableCull() */

        for (Subtitle subtitle : subtitles)
        {
            float alpha = Colors.getA(subtitle.color);

            if (alpha <= 0)
            {
                continue;
            }

            String label = StringUtils.processColoredText(subtitle.label);
            int w = 0;
            int h = 0;
            int x = (int) (width * subtitle.windowX + subtitle.x);
            int y = (int) (height * subtitle.windowY + subtitle.y);
            float scale = subtitle.size;
            int subColor = subtitle.color;

            List<String> strings = subtitle.maxWidth <= 10 ? Arrays.asList(label) : font.wrap(label, subtitle.maxWidth);

            for (String string : strings)
            {
                w = Math.max(w, font.getWidth(string.trim()));
            }

            h = (strings.size() - 1) * subtitle.lineHeight + font.getHeight();

            Texture imgTex = null;
            float gap = 6F;
            float imgW = 0F;
            float imgH = 0F;

            if (subtitle.image != null && BBSModClient.getTextures().has(subtitle.image))
            {
                imgTex = BBSModClient.getTextures().getTexture(subtitle.image);

                if (imgTex != BBSModClient.getTextures().getError())
                {
                    int base = subtitle.lineHeight > 0 ? subtitle.lineHeight : font.getHeight();
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

            int fw = (int) ((contentW + 10) * scale);
            int fh = (int) ((contentH + 10) * scale);

            /* TODO(1.21.11 render): projection is GPU-owned; previously set an ortho projection for the offscreen
             * subtitle framebuffer here via RenderSystem.setProjectionMatrix(Matrix4f). Restore via new foundation. */

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

            for (String string : strings)
            {
                string = string.trim();

                int xx = (int) (textLeft + (textAreaW - font.getWidth(string)) / 2F);
                batcher.text(string, xx, (int) yy, Colors.setA(subColor, 1F), subtitle.textShadow);

                yy += subtitle.lineHeight;
            }

            /* Render the texture */
            /* TODO(1.21.11 render): Framebuffer.beginWrite(boolean) removed; rebind MC main framebuffer as the
             * draw target + restore the screen-ortho projection (was RenderSystem.setProjectionMatrix(ortho))
             * via the new pipeline foundation before compositing the subtitle texture. */

            Transform transform = new Transform();

            transform.lerp(subtitle.transform, 1F - subtitle.factor);

            stack.push();
            stack.translate(x, y, 0);
            MatrixStackUtils.applyTransform(stack, transform);

            /* TODO(1.21.11 render): the subtitles blur shader's per-draw uniforms ("Blur" = subtitle.shadow/
             * shadowOpaque, "TextureSize" = texture.width/height). The bbs:core/subtitles GLSL is now migrated to
             * #version 330 std140 and its pipeline (BBSShaders.getSubtitlesProgram()/getSubtitlesLayer()) declares
             * the builtin DynamicTransforms/Projection UBOs + the custom SubtitlesInfo UBO (Blur/TextureSize) +
             * Sampler0. What is STILL missing is the per-draw dispatch: the texturedBox(Supplier,...) below routes
             * through the AdoptedTexture -> GUI_TEXTURED bridge (the Supplier/pipeline is IGNORED), so the blur is
             * NOT applied and SubtitlesInfo is never uploaded. Reviving it needs a manual RenderPass binding
             * getSubtitlesProgram(), setUniform("SubtitlesInfo", <Std140 slice of Blur/TextureSize>) + Sampler0,
             * applied as an off-screen full-screen-quad pass over the text FBO before compositing the result via
             * the bridge (cf. BbsFormGuiElementRenderer's manual GPU path). Tracked as the separate "revive GUI
             * custom shaders" work; until then the subtitle text composites without the blur. */

            /* TODO(1.21.11 render): blend state now lives in the RenderPipeline/RenderLayer; removed
             * RenderSystem.enableBlend()/blendFuncSeparate(...) */
            batcher.texturedBox(supplier, texture.id, Colors.setA(Colors.WHITE, alpha), -fw * subtitle.anchorX, -fh * subtitle.anchorY, texture.width, texture.height, 0, 0, texture.width, texture.height, texture.width, texture.height);

            stack.pop();
        }

        /* TODO(1.21.11 render): restore the cached projection + cull state via the new pipeline foundation
         * (was RenderSystem.setProjectionMatrix(cache)/enableCull()). */
    }
}
