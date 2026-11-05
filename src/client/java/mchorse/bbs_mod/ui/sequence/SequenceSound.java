package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

/**
 * The sequence's sound, brought along into the film editor when a film is opened from a
 * sequence: the voice over and the music play on the film's time the way they meet it in the
 * sequence, so the film is animated to the sound it will have in the video.
 *
 * <p>A copy of the sequence's audio clips, taken when the film was opened; it plays only while
 * that film is the one open.
 */
public class SequenceSound
{
    public final String filmId;

    private final CameraClipContext context = new CameraClipContext();
    private final Position position = new Position();

    /** The sequence's tick on the film's tick 0 */
    private final int shift;

    public SequenceSound(Sequence sequence, FilmClip clip)
    {
        this.filmId = clip.film.get();
        this.shift = clip.tick.get() - clip.offset.get();
        this.context.clips = new Clips("sequence_sound", BBSModClient.getSequenceClipPalette());

        for (Clip audio : sequence.clips.get())
        {
            if (audio instanceof AudioClip && audio.enabled.get())
            {
                this.context.clips.addClip(audio.copy());
            }
        }
    }

    public boolean isEmpty()
    {
        return this.context.clips.get().isEmpty();
    }

    /**
     * Play the sound that the sequence has on the given tick of the film.
     */
    public void apply(int filmTick, float transition, boolean playing)
    {
        int tick = filmTick + this.shift;

        this.context.clipData.clear();
        this.context.playing = playing;
        this.context.setup(tick, transition);

        for (Clip clip : this.context.clips.getClips(tick))
        {
            this.context.apply(clip, this.position);
        }

        AudioClientClip.manageSounds(this.context);
    }

    public void stop()
    {
        this.context.shutdown();
    }
}
