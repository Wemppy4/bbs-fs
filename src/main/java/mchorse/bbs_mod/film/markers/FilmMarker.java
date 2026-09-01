package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A named point in time on the film's timeline — "the music comes in here", "recut from here".
 *
 * <p>A marker means nothing to playback: it is an author's note that happens to be addressed by
 * tick rather than by name, which is why it carries no behaviour beyond where it sits and how it
 * reads. It belongs to the film as a whole, not to a clip, a track or a replay.
 */
public class FilmMarker extends ValueGroup
{
    public static final int DEFAULT_COLOR = 0xffcc44;

    public final ValueInt tick = new ValueInt("tick", 0, 0, Integer.MAX_VALUE);
    public final ValueString title = new ValueString("title", "");
    /** RGB without alpha, like every other authored colour in the tree. */
    public final ValueInt color = new ValueInt("color", DEFAULT_COLOR).color();

    public FilmMarker(String id)
    {
        super(id);

        this.add(this.tick);
        this.add(this.title);
        this.add(this.color);
    }

    /**
     * A fresh marker gets a random hue rather than one shared colour: markers are told apart at a
     * glance on the ruler, and a row of identical pins defeats that before anyone gets round to
     * naming them. Saturation and value are pinned high so every draw reads on the dark strip.
     *
     * <p>{@link #DEFAULT_COLOR} stays the value's own default, so resetting the field lands on
     * something sane instead of rolling again.
     */
    public static int randomBrightColor()
    {
        return Colors.HSVtoRGB((float) Math.random(), 0.75F, 1F).getRGBColor();
    }
}
