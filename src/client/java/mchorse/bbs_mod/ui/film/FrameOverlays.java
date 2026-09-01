package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.util.math.MatrixStack;

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
        for (IFrameOverlayRenderer renderer : RENDERERS)
        {
            renderer.render(stack, batcher, context);
        }
    }

    public static interface IFrameOverlayRenderer
    {
        public void render(MatrixStack stack, Batcher2D batcher, ClipContext context);
    }
}
