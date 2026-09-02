package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2fStack;

import java.util.Arrays;
import java.util.List;

/**
 * Draws film subtitles over the frame.
 *
 * <p>Rebuilt for 1.21.11. The 1.21.1 renderer drew each subtitle into a private off-screen
 * framebuffer and composited that texture through the {@code subtitles} blur shader with a full 3D
 * transform. Both legs of that path died in the render rewrite: {@code Framebuffer.beginWrite} is
 * gone (the batcher records into the two-phase GUI — the "offscreen" content was actually landing
 * in the screen corner at framebuffer-local coordinates), and the recorded GUI path cannot carry
 * the blur pipeline's custom UBO. So the subtitle now draws DIRECTLY at its screen position through
 * the recorded GUI, applying the transform as the 2D affine the GUI stack supports (translate,
 * scale, Z rotation — the components subtitles actually animate).
 *
 * <p>TODO(1.21.11 render): the blur (subtitle.shadow) is the one part not restored — it needs the
 * whole subtitle as a texture, i.e. an off-screen text render, which on the two-phase GUI means a
 * {@code SpecialGuiElementRenderer} (the BbsFormGuiElementRenderer mechanism). The migrated
 * {@code bbs:core/subtitles} pipeline + SubtitlesInfo UBO + ScreenQuadPass dispatch are all ready
 * for it. Until then the text draws sharp, with its vanilla shadow flag.
 */
public class UISubtitleRenderer
{
    public static void renderSubtitles(MatrixStack stack, Batcher2D batcher, List<Subtitle> subtitles)
    {
        if (subtitles.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        /* Overlay placement is authored on a virtual frame Placement.HEIGHT units tall (see
         * UIImageRenderer#getUnitWidth). 1.21.1 gave that canvas its own ortho projection; the
         * recorded GUI has no projection to set, so the unit canvas is mapped onto the GUI by a
         * single scale on the 2D stack instead. */
        float width = UIImageRenderer.getUnitWidth();
        float height = Placement.HEIGHT;
        float unitScale = mc.getWindow().getScaledHeight() / height;

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
            int h;
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

                    if (imgH <= 0)
                    {
                        imgH = 0;
                    }

                    if (imgTex.height > 0)
                    {
                        imgW = imgTex.width * (imgH / imgTex.height);
                    }
                }
            }

            float contentW = w + (imgTex != null && imgH > 0 ? (gap + imgW) : 0);
            float contentH = Math.max(h, imgH);
            float fw = contentW + 10;
            float fh = contentH + 10;

            /* The subtitle's animated pose. The GUI stack is a 2D affine, so the transform's
             * translate.x/y, scale.x/y and rotate.z apply — the components subtitle animations
             * actually drive. The placement's own scale folds into the same scale. */
            Transform transform = new Transform();

            transform.lerp(subtitle.transform, 1F - subtitle.factor);

            Matrix3x2fStack matrices = batcher.getContext().getMatrices();

            matrices.pushMatrix();
            matrices.scale(unitScale, unitScale);
            matrices.translate(x + transform.translate.x, y + transform.translate.y);

            if (transform.rotate.z != 0)
            {
                matrices.rotate(MathUtils.toRad(transform.rotate.z));
            }

            matrices.scale(transform.scale.x * scaleX, transform.scale.y * scaleY);

            /* Anchor: the content box hangs off the anchor point the way the 1.21.1 composite did. */
            matrices.translate(-fw * placement.anchorX, -fh * placement.anchorY);

            float baseX = 5F;
            float baseY = 5F;
            float textLeft = baseX + ((imgTex != null && imgH > 0 && !subtitle.imageRight) ? (imgW + gap) : 0F);
            float textAreaW = w;
            float yy = baseY + (contentH - h) / 2F;

            if (Colors.getA(subtitle.backgroundColor) > 0)
            {
                float o = subtitle.backgroundOffset;

                batcher.box(baseX - o, yy - o, baseX + contentW + o - 1F, yy + h + o, Colors.mulA(subtitle.backgroundColor, alpha));
            }

            if (imgTex != null && imgH > 0)
            {
                float imgX = subtitle.imageRight ? baseX + contentW - imgW : baseX;
                float imgY = baseY + (contentH - imgH) / 2F;

                batcher.texturedBox(imgTex, Colors.mulA(Colors.WHITE, alpha), imgX, imgY, imgW, imgH, 0, 0, imgTex.width, imgTex.height, imgTex.width, imgTex.height);
            }

            FontRenderer previousFont = batcher.setFont(font);

            try
            {
                for (String string : strings)
                {
                    string = string.trim();

                    int xx = (int) (textLeft + (textAreaW - font.getWidth(string)) / 2F);

                    batcher.text(string, xx, (int) yy, Colors.mulA(subColor, alpha), subtitle.textShadow);

                    yy += lineHeight;
                }
            }
            finally
            {
                batcher.setFont(previousFont);
            }

            matrices.popMatrix();

            /* Where the overlay ended up, in unit space — what the placement editor drags. */
            subtitle.box.set(x - fw * scaleX * placement.anchorX, y - fh * scaleY * placement.anchorY, fw * scaleX, fh * scaleY, width);
        }
    }
}
