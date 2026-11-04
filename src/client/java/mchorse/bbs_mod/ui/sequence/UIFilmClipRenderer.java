package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.renderer.UIClipRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A film on a sequence's timeline: named after its film and coloured by its own hue, picked when
 * the clip was made (film names are often just numbers, so a colour from the name repeated).
 * Drawn solid while its recorded piece is current, pale while it is stale or there is none.
 */
public class UIFilmClipRenderer extends UIClipRenderer<FilmClip>
{
    private static final float SATURATION = 0.6F;
    private static final float VALUE = 0.85F;
    private static final float PALE = 0.45F;

    /**
     * The clip's colour: its hue, one saturation and brightness for all, so white text reads on
     * every one of them.
     */
    public static int getFilmColor(FilmClip clip)
    {
        return Colors.HSVtoRGB(clip.hue.get() / 360F, SATURATION, VALUE).getRGBColor();
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
        return getFilmColor(clip);
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
