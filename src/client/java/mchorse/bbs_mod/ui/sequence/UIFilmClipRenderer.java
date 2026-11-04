package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.renderer.UIClipRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A film on a sequence's timeline: named after its film and coloured by that name, so the same
 * film always reads the same and neighbours rarely match. Drawn solid while its recorded piece is
 * current, pale while it is stale or there is none.
 */
public class UIFilmClipRenderer extends UIClipRenderer<FilmClip>
{
    /** Yellow belongs to the audio clips: the hues from here... */
    private static final int YELLOW_FROM = 40;
    /** ...to here are skipped, so a film never reads as sound. */
    private static final int YELLOW_RANGE = 30;

    private static final float SATURATION = 0.6F;
    private static final float VALUE = 0.85F;
    private static final float PALE = 0.45F;

    /**
     * The colour of a film named so: a hue from the name, one saturation and brightness for all,
     * so white text reads on every one of them.
     */
    public static int getFilmColor(String name)
    {
        int degrees = Math.abs(name.hashCode() % 330);

        if (degrees >= YELLOW_FROM)
        {
            degrees += YELLOW_RANGE;
        }

        return Colors.HSVtoRGB(degrees / 360F, SATURATION, VALUE).getRGBColor();
    }

    /**
     * The film's own name, without the folders it is in.
     */
    public static String getFilmName(FilmClip clip)
    {
        String id = clip.film.get();

        return id.substring(id.lastIndexOf('/') + 1);
    }

    @Override
    protected int getColor(UIClips clips, FilmClip clip)
    {
        String name = getFilmName(clip);

        return name.isEmpty() ? super.getColor(clips, clip) : getFilmColor(name);
    }

    @Override
    protected float getOpacity(UIClips clips, FilmClip clip)
    {
        UISequencePanel panel = getPanel(clips);

        return panel == null || panel.isRecorded(clip) ? 1F : PALE;
    }

    @Override
    public String getDefaultLabel(UIClips clips, FilmClip clip)
    {
        String name = getFilmName(clip);

        return name.isEmpty() ? super.getDefaultLabel(clips, clip) : name;
    }

    private static UISequencePanel getPanel(UIClips clips)
    {
        return clips.getDelegate() instanceof UIClipsPanel panel && panel.owner instanceof UISequencePanel sequence ? sequence : null;
    }
}
