package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.WorldScope;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.io.File;
import java.security.MessageDigest;

/**
 * A film on a sequence's timeline: which film of which world, from which of its ticks it plays,
 * whether its own sound is heard, and the piece of video it was last recorded into.
 *
 * <p>Films live in their worlds, so a clip remembers the world it was added in: films of the
 * world the game is in play live, the others only as their recorded piece.
 *
 * <p>The piece is the whole film, whatever part of it the clip shows: the clip is trimmed when
 * the pieces are put together, so moving and cutting the clip never asks for a new recording. A
 * piece stays good while the film and the picture settings it was recorded with stay the same -
 * the clip keeps a stamp of both ({@link #fingerprint(Film)}, {@link SequenceExport#getPictureStamp()}).
 */
public class FilmClip extends CameraClip
{
    public final ValueString film = new ValueString("film", "");
    public final ValueString world = new ValueString("world", "");
    /** The film's tick the clip starts on */
    public final ValueInt offset = new ValueInt("offset", 0, 0, Integer.MAX_VALUE);
    public final ValueBoolean filmAudio = new ValueBoolean("film_audio", true);

    /** The recorded piece of video (absolute path), empty while there is none */
    public final ValueString part = new ValueString("part", "");
    /** The film's fingerprint when the piece was recorded */
    public final ValueString partFilm = new ValueString("part_film", "");
    /** The picture settings' stamp when the piece was recorded */
    public final ValueString partPicture = new ValueString("part_picture", "");

    public FilmClip()
    {
        super();

        this.add(this.film);
        this.add(this.world);
        this.add(this.offset);
        this.add(this.filmAudio);
        this.add(this.part);
        this.add(this.partFilm);
        this.add(this.partPicture);
    }

    /**
     * Whether the film is one of the world the game is in, and so can play live.
     */
    public boolean isInCurrentWorld()
    {
        return WorldScope.isCurrent(this.world.get());
    }

    /**
     * The tick of the film shown on the given tick of the sequence.
     */
    public int toFilmTick(int sequenceTick)
    {
        return sequenceTick - this.tick.get() + this.offset.get();
    }

    public File getPartFile()
    {
        String path = this.part.get();

        return path.isEmpty() ? null : new File(path);
    }

    public boolean hasPart()
    {
        File file = this.getPartFile();

        return file != null && file.isFile();
    }

    /**
     * Whether the recorded piece shows the film as it is now with the given picture settings.
     * The film's fingerprint is null when it is not known (a film of another world): then only
     * the picture settings can make the piece stale.
     */
    public boolean isRecorded(String filmFingerprint, String pictureStamp)
    {
        if (!this.hasPart() || !this.partPicture.get().equals(pictureStamp))
        {
            return false;
        }

        return filmFingerprint == null || this.partFilm.get().equals(filmFingerprint);
    }

    public void setRecorded(File file, String filmFingerprint, String pictureStamp)
    {
        this.part.set(file.getAbsolutePath());
        this.partFilm.set(filmFingerprint);
        this.partPicture.set(pictureStamp);
    }

    /**
     * Forget that the piece is current, so it gets recorded again (the world, the models or the
     * shaders changed, which the film's fingerprint doesn't see). The piece itself stays to be
     * looked at until then.
     */
    public void markStale()
    {
        this.partFilm.set("");
    }

    /**
     * What the film is made of, boiled down: the same film gives the same fingerprint, any change
     * to it gives another. The editing time is left out - merely looking at a film adds to it.
     */
    public static String fingerprint(Film film)
    {
        MapType data = film.toData().asMap();

        data.remove(film.timeSpentActive.getId());

        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(DataStorageUtils.writeToBytes(data));
            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < 8; i++)
            {
                builder.append(String.format("%02x", hash[i]));
            }

            return builder.toString();
        }
        catch (Exception e)
        {
            return Integer.toHexString(data.hashCode());
        }
    }

    @Override
    public void shiftLeft(int tick)
    {
        super.shiftLeft(tick);

        this.offset.set(Math.max(0, this.offset.get() - (this.tick.get() - tick)));
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        this.offset.set(this.offset.get() + offset);
    }

    /** The sequence plays its films itself; on a camera they place nothing. */
    @Override
    protected void applyClip(ClipContext context, Position position)
    {}

    @Override
    protected Clip create()
    {
        return new FilmClip();
    }
}
