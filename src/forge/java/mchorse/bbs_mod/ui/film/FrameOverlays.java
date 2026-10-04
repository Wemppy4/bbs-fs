package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.graphics.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What gets drawn over the finished frame: the images, the subtitles, and whatever an addon adds.
 *
 * <p>Every overlay family used to be a named call, written twice — once for the game and once for
 * the preview inside the editor — so adding a third meant finding both copies, and an addon could
 * add none. Now both callers ask this, and the list is the only thing that knows what families
 * there are.</p>
 *
 * <p>Note that an addon usually does not need one of these: a clip can push its overlay into an
 * existing family from {@code applyClip} — {@link ImageClip#getImages} and
 * {@link SubtitleClip#getSubtitles} hand out the lists — and be drawn by the family's renderer.
 * A renderer of its own is for something neither of them can draw.</p>
 */
public class FrameOverlays
{
    private static final List<IFrameOverlayRenderer> RENDERERS = new ArrayList<>();

    /**
     * Fills the registry. Called by BBS while it initialises, and followed by the event that lets
     * addons add to it.
     */
    public static void setup()
    {
        register((stack, batcher, context) -> UIImageRenderer.renderImages(stack, batcher, ImageClip.getImages(context)));
        register((stack, batcher, context) -> UISubtitleRenderer.renderSubtitles(stack, batcher, SubtitleClip.getSubtitles(context)));
    }

    public static void register(IFrameOverlayRenderer renderer)
    {
        RENDERERS.add(renderer);
    }

    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        boolean batching = batcher.isBatching();
        batcher.endBatch();
        try (OverlayState state = new OverlayState())
        {
            for (IFrameOverlayRenderer renderer : RENDERERS)
            {
                renderer.render(stack, batcher, context);
            }
        }
        finally { if (batching) batcher.beginBatch(); }
    }

    /** Native overlays replace both projection and target size within this scope. */
    private static final class OverlayState implements AutoCloseable
    {
        private final int framebuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        private final java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
        private final java.nio.FloatBuffer clear = org.lwjgl.BufferUtils.createFloatBuffer(16);
        private final boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        private final boolean cull = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        private final boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        private final int depthFunc = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        OverlayState()
        {
            org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
            org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE, clear);
            mchorse.bbs_mod.utils.MatrixStackUtils.cacheMatrices();
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            net.minecraft.client.renderer.GlStateManager.clearColor(0, 0, 0, 0);
        }
        public void close()
        {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, framebuffer);
            net.minecraft.client.renderer.GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            net.minecraft.client.renderer.GlStateManager.clearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
            net.minecraft.client.renderer.GlStateManager.depthFunc(depthFunc);
            if (depth) net.minecraft.client.renderer.GlStateManager.enableDepth(); else net.minecraft.client.renderer.GlStateManager.disableDepth();
            if (cull) net.minecraft.client.renderer.GlStateManager.enableCull(); else net.minecraft.client.renderer.GlStateManager.disableCull();
            if (scissor) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            mchorse.bbs_mod.utils.MatrixStackUtils.restoreMatrices();
        }
    }

    public static interface IFrameOverlayRenderer
    {
        public void render(MatrixStack stack, Batcher2D batcher, ClipContext context);
    }
}
