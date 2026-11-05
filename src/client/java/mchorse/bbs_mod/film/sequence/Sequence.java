package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.ArrayList;
import java.util.List;

/**
 * Films put in order on one timeline, the way they make up a video: each film is a clip
 * ({@link FilmClip}), next to the sound that goes over all of them. The sequence plays the films
 * one after another and records them into one video, with its own export settings
 * ({@link SequenceExport}).
 *
 * <p>Sequences are not kept in a world: the films of one video are often spread over several
 * worlds, so each film clip remembers its world instead.
 */
public class Sequence extends ValueGroup
{
    public final Clips clips = new Clips("clips", BBSModClient.getSequenceClipPalette());
    public final SequenceExport export = new SequenceExport("export");

    public Sequence()
    {
        super("");

        this.add(this.clips);
        this.add(this.export);
    }

    public List<FilmClip> getFilmClips()
    {
        List<FilmClip> films = new ArrayList<>();

        for (Clip clip : this.clips.get())
        {
            if (clip instanceof FilmClip film)
            {
                films.add(film);
            }
        }

        return films;
    }

    /**
     * The film shown on the given tick: the enabled film clip on the highest layer there.
     */
    public FilmClip getFilmClipAt(int tick)
    {
        FilmClip found = null;

        for (Clip clip : this.clips.get())
        {
            if (clip instanceof FilmClip film && film.enabled.get() && film.isInside(tick))
            {
                if (found == null || film.layer.get() > found.layer.get())
                {
                    found = film;
                }
            }
        }

        return found;
    }

    public int calculateDuration()
    {
        return this.clips.calculateDuration();
    }
}
