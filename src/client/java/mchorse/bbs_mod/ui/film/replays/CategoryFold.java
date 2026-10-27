package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.tracks.TrackTree;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;

import java.util.List;
import java.util.Set;

/**
 * How a timeline's headings start the first time it shows them. A category doesn't fold: its tab
 * shows only its tracks, still under their owners, and "all tracks" shows every owner. The folds
 * are shared by all of them, so a heading folded under one tab stays folded under another.
 *
 * <p>The name stays from when a tab folded the owners: the physics addon hooks {@link #settle}.
 */
public final class CategoryFold
{
    private CategoryFold()
    {}

    /**
     * Fold the headings a timeline shows for the first time. Every owner starts open, and so does
     * every heading inside — what a part has is in reach once it is shown — but for the recording's,
     * which start folded but for its Position.
     *
     * @param fresh the headings shown for the first time, see {@link TrackTree#groupSheets}
     */
    public static void settle(List<UIKeyframeSheet> sheets, FoldState<String> folds, Set<String> fresh)
    {
        if (fresh.isEmpty())
        {
            return;
        }

        for (UIKeyframeSheet sheet : sheets)
        {
            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                if (fresh.contains(section.id()))
                {
                    folds.set(section.id(), opensFirst(section));
                }
            }
        }
    }

    /** Open, but for the recording's own headings, folded but for its Position. */
    private static boolean opensFirst(UIKeyframeSheet.Section section)
    {
        for (UIKeyframeSheet.Section parent = section.parent(); parent != null; parent = parent.parent())
        {
            if (parent.id().equals(TrackTree.RECORD))
            {
                return section.id().equals(UIReplaysEditor.POSITION_SECTION);
            }
        }

        return true;
    }
}
