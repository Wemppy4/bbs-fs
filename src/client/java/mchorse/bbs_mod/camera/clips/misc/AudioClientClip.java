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
        final float seconds;
        final float gain;

        Playback(float seconds, float gain)
        {
            this.seconds = seconds;
            this.gain = gain;
        }
    }

    public AudioClientClip()
    {
        super();
    }

    public static Map<Link, Playback> getPlayback(ClipContext context)
    {
        return context.clipData.get("audio", ConcurrentHashMap::new);
    }

    public static void manageSounds(ClipContext context)
    {
        Map<Link, Playback> playback = getPlayback(context);
        boolean muteFilmAudioDuringVideoCapture = BBSSettings.videoMuteAudioWhileRender.get()
            && BBSModClient.getVideoRecorder().isRecording();

        for (Map.Entry<Link, Playback> entry : playback.entrySet())
        {
            Playback state = entry.getValue();
            float tickTime = state.seconds;
            SoundPlayer player = BBSModClient.getSounds().playUnique(entry.getKey());

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
        Link link = this.audio.get();

        if (link != null)
        {
            BBSModClient.getSounds().stop(link);
        }
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        Link link = this.audio.get();

        if (link != null)
        {
            SoundPlayer player = BBSModClient.getSounds().playUnique(link);

            if (player == null)
            {
                return;
            }

            float tickTime = (context.relativeTick + context.transition) / 20F;
            Map<Link, Playback> playback = getPlayback(context);
            float gain = this.volume.get();

            if (context.relativeTick >= this.duration.get() || tickTime < 0)
            {
                playback.putIfAbsent(link, new Playback(-1F, gain));
            }
            else
            {
                playback.put(link, new Playback(TimeUtils.toSeconds(this.offset.get()) + tickTime, gain));
            }
        }
    }

    @Override
    protected Clip create()
    {
        return new AudioClientClip();
    }
}