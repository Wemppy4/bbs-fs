package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioClientClip extends AudioClip
{
    static final class Playback
    {
        final Link link;
        final float seconds;
        final float gain;

        Playback(Link link, float seconds, float gain)
        {
            this.link = link;
            this.seconds = seconds;
            this.gain = gain;
        }
    }

    public AudioClientClip()
    {
        super();
    }

    /** Keyed by the CLIP, so two clips of the same file each keep their own playback. */
    public static Map<Object, Playback> getPlayback(ClipContext context)
    {
        return context.clipData.get("audio", ConcurrentHashMap::new);
    }

    public static void manageSounds(ClipContext context)
    {
        Map<Object, Playback> playback = getPlayback(context);
        boolean muteFilmAudioDuringVideoCapture = BBSSettings.videoMuteAudioWhileRender.get()
            && BBSModClient.getVideoRecorder().isRecording();

        for (Map.Entry<Object, Playback> entry : playback.entrySet())
        {
            Playback state = entry.getValue();
            float tickTime = state.seconds;
            SoundPlayer player = BBSModClient.getSounds().playUnique(entry.getKey(), state.link);

            if (player == null)
            {
                continue;
            }

            player.setVolume(muteFilmAudioDuringVideoCapture ? 0F : state.gain);

            if (tickTime < 0 || tickTime >= player.getBuffer().getDuration())
            {
                if (player.isPlaying())
                {
                    player.pause();
                }

                continue;
            }

            float time = player.getPlaybackPosition();
            float diff = Math.abs(tickTime - time);

            if (context.playing && !player.isPlaying())
            {
                player.play();
            }
            else if (!context.playing && player.isPlaying())
            {
                player.pause();
            }

            if (diff > 0.05F)
            {
                player.setPlaybackPosition(tickTime);
            }
        }
    }

    @Override
    public boolean isGlobal()
    {
        return true;
    }

    @Override
    public void shutdown(ClipContext context)
    {
        BBSModClient.getSounds().stopOwned(this);
    }

    /**
     * Schedule the clip's audio file for this frame. Shared between the audio clip itself
     * and the video clip, whose audio track goes through the same playback machinery.
     */
    public static void scheduleAudio(ClipContext context, AudioClip clip, float gain)
    {
        scheduleAudio(context, clip, gain, 0F);
    }

    /**
     * @param loopSeconds when positive, the playback position wraps around this
     *                    period (the video clip loops its audio with its picture)
     */
    public static void scheduleAudio(ClipContext context, AudioClip clip, float gain, float loopSeconds)
    {
        Link link = clip.audio.get();

        if (link != null)
        {
            SoundPlayer player = BBSModClient.getSounds().playUnique(clip, link);

            if (player == null)
            {
                return;
            }

            float tickTime = (context.relativeTick + context.transition) / 20F;
            Map<Object, Playback> playback = getPlayback(context);

            if (context.relativeTick >= clip.duration.get() || tickTime < 0)
            {
                playback.putIfAbsent(clip, new Playback(link, -1F, gain));
            }
            else
            {
                float position = TimeUtils.toSeconds(clip.offset.get()) + tickTime;

                if (loopSeconds > 0F)
                {
                    position = position % loopSeconds;

                    if (position < 0F)
                    {
                        position += loopSeconds;
                    }
                }

                playback.put(clip, new Playback(link, position, gain));
            }
        }
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        scheduleAudio(context, this, this.volume.get());
    }

    @Override
    protected Clip create()
    {
        return new AudioClientClip();
    }
}