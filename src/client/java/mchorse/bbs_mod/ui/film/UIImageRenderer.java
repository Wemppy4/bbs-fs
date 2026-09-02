package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.camera.clips.misc.ImageOverlay;
import mchorse.bbs_mod.camera.clips.misc.VideoOverlay;
import mchorse.bbs_mod.video.VideoPlayer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.List;

public class UIImageRenderer
{
    /**
     * The virtual frame's width in units: always {@link Placement#HEIGHT} tall,
     * as wide as the frame's aspect ratio makes it.
     */
    public static float getUnitWidth()
    {
        net.minecraft.client.gl.Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();

        return fb.textureWidth * Placement.HEIGHT / fb.textureHeight;
    }

    public static void renderImages(MatrixStack stack, Batcher2D batcher, List<ImageOverlay> images)
    {
        if (images.isEmpty())
        {
            return;
        }

        float width = getUnitWidth();
        float height = Placement.HEIGHT;

        /* 1.21.11: the unit frame arrives as a scale on the GUI's own 2D stack rather than as a
         * global ortho projection. There is no global projection left to swap, and Batcher2D draws
         * through DrawContext#getMatrices() (a Matrix3x2fStack), so a projection swap would not
         * reach it anyway; scaling unit coordinates onto the GUI's is what the ortho did. The
         * depth/cull/blend bracket goes with it — the GUI pipeline owns that state now. */
        Matrix3x2fStack matrices = batcher.getContext().getMatrices();

        matrices.pushMatrix();
        matrices.scale(batcher.getContext().getScaledWindowWidth() / width,
            batcher.getContext().getScaledWindowHeight() / height);

        for (ImageOverlay image : images)
        {
            float alpha = Colors.getA(image.color);

            if (alpha <= 0)
            {
                continue;
            }

            Texture texture;

            if (image instanceof VideoOverlay video)
            {
                /* The overlay is the clip's own object, so it IS the decoder's owner:
                 * two clips playing the same file sit on different timestamps and
                 * cannot share one. */
                VideoPlayer player = video.video == null ? null : BBSModClient.getVideos().getPlayer(video, video.video);

                texture = player == null ? null : player.getFrame(video.seconds);

                if (texture == null)
                {
                    continue;
                }
            }
            else
            {
                if (image.texture == null || !BBSModClient.getTextures().has(image.texture))
                {
                    continue;
                }

                texture = BBSModClient.getTextures().getTexture(image.texture);

                if (texture == BBSModClient.getTextures().getError())
                {
                    continue;
                }
            }

            texture.bind();
            texture.setFilter(image.smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            Placement placement = image.placement;
            float w;
            float h;
            float x;
            float y;
            float anchorX;
            float anchorY;

            if (image.fullscreen)
            {
                w = width;
                h = height;
                x = width / 2F;
                y = height / 2F;
                anchorX = 0.5F;
                anchorY = 0.5F;
            }
            else
            {
                w = texture.width * placement.scaleX;
                h = texture.height * placement.scaleY;
                x = width * placement.windowX + placement.offsetX;
                y = height * placement.windowY + placement.offsetY;
                anchorX = placement.anchorX;
                anchorY = placement.anchorY;
            }

            image.box.set(x - w * anchorX, y - h * anchorY, w, h, width);

            Transform transform = new Transform();

            transform.lerp(image.transform, 1F - image.factor);

            matrices.pushMatrix();
            matrices.translate(x, y);

            /* The overlay's placement transform, flattened onto the screen plane: an overlay lives
             * in the frame, so of its three rotations only the one about the view axis has any
             * meaning here, and the 2D GUI stack carries exactly that. Out-of-plane rotation of
             * overlays was deferred when placement was built, so nothing is lost that worked. */
            matrices.translate(transform.translate.x, transform.translate.y);
            matrices.rotate(transform.rotate.z);
            matrices.scale(transform.scale.x, transform.scale.y);

            batcher.texturedBox(texture, image.color, -w * anchorX, -h * anchorY, w, h, 0, 0, texture.width, texture.height, texture.width, texture.height);

            matrices.popMatrix();
        }

        matrices.popMatrix();
    }
}
