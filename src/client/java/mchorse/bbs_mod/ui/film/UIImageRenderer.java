package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.ImageOverlay;
import mchorse.bbs_mod.camera.clips.misc.VideoOverlay;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.List;

public class UIImageRenderer
{
    public static void renderImages(MatrixStack stack, Batcher2D batcher, List<ImageOverlay> images)
    {
        if (images.isEmpty())
        {
            return;
        }

        net.minecraft.client.gl.Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        int width = fb.textureWidth / 2;
        int height = fb.textureHeight / 2;

        Matrix4f cache = new Matrix4f(RenderSystem.getProjectionMatrix());

        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, width, height, 0, -100, 100), VertexSorter.BY_Z);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

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
                texture = video.video == null ? null : BBSModClient.getVideos().getFrame(video.video, video.seconds);

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
                w = texture.width * image.scale;
                h = texture.height * image.scale;
                x = width * image.windowX + image.x;
                y = height * image.windowY + image.y;
                anchorX = image.anchorX;
                anchorY = image.anchorY;
            }

            Transform transform = new Transform();

            transform.lerp(image.transform, 1F - image.factor);

            stack.push();
            stack.translate(x, y, 0);
            MatrixStackUtils.applyTransform(stack, transform);

            batcher.texturedBox(texture, image.color, -w * anchorX, -h * anchorY, w, h, 0, 0, texture.width, texture.height, texture.width, texture.height);

            stack.pop();
        }

        RenderSystem.setProjectionMatrix(cache, VertexSorter.BY_Z);
        RenderSystem.enableCull();
    }
}
