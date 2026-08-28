package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.video.VideoPlayer;

import java.util.List;

public class VideoClientClip extends VideoClip
{
    @Override
    public boolean isGlobal()
    {
        return true;
    }

    @Override
    public void shutdown(ClipContext context)
    {
        Link link = this.audio.get();

        if (link != null)
        {
            BBSModClient.getSounds().stop(link);
        }

        BBSModClient.getVideos().release(this.overlay);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        Link link = this.audio.get();

        if (link == null)
        {
            return;
        }

        float factor = this.envelope.factorEnabled(this.duration.get(), context.relativeTick + context.transition);
        float tickTime = (context.relativeTick + context.transition) / 20F;

        /* A video clip is GLOBAL (inherited from the audio clip, whose player has to be
         * kept paused and in sync outside the clip too), so applyClip runs on every tick
         * of the film. Only the SOUND wants that: the picture must not be drawn outside
         * the clip, and the decoder must not be kept awake by ticks that don't show it. */
        boolean inside = tickTime >= 0F && context.relativeTick < this.duration.get();
        float loopSeconds = 0F;

        if (this.loop.get() && inside)
        {
            /* The clip's OWN player, the one that decodes for it - the link-keyed
             * player is metadata only and never gets probed by anybody. */
            VideoPlayer player = BBSModClient.getVideos().getPlayer(this.overlay, link);

            if (player != null && player.isValid())
            {
                loopSeconds = player.getDuration();
            }
        }

        AudioClientClip.scheduleAudio(context, this, this.volume.get() * factor, loopSeconds);

        if (!inside)
        {
            return;
        }

        List<ImageOverlay> images = ImageClip.getImages(context);
        int color = Colors.setA(this.color.get(), factor * Colors.getA(this.color.get()));
        float seconds = TimeUtils.toSeconds(this.offset.get()) + tickTime;

        if (loopSeconds > 0F)
        {
            seconds = seconds % loopSeconds;

            if (seconds < 0F)
            {
                seconds += loopSeconds;
            }
        }

        this.overlay.update(null, this.placement.get(), color, this.fullscreen.get(), this.smooth.get());
        this.overlay.updateTransform(this.transform.get(), factor);
        this.overlay.updateVideo(link, seconds);
        images.add(this.overlay);
    }

    @Override
    protected Clip create()
    {
        return new VideoClientClip();
    }
}
