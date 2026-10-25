package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.film.replays.tracks.TrackTree;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a category's key does to a timeline. A category doesn't filter, it switches the owners —
 * the recording, the parts on the timeline, an addon's — and leaves what is inside them alone:
 * <ul>
 *   <li>The recording's key opens the recording and folds every other owner.</li>
 *   <li>Form's opens every part on the timeline (the ones picked in the parts list) and folds the recording.</li>
 *   <li>An addon's opens the owners with its tracks, and its own headings in them, and folds the rest.
 *   Its channels of the replay have an owner of their own (BBSFP's first person, see {@link TrackTree}),
 *   its tracks of a form stay in that part's owner.</li>
 * </ul>
 * The headings inside an owner keep their folds, so unfolding one brings them back the way they
 * were. Pressed again, a key changes nothing, and the view doesn't scroll. A category with
 * nothing on the timeline (BBSFP's clip tab shows clips instead) leaves the folds be.
 *
 * <p>The tab lit also decides how an owner starts the first time a timeline shows it — and lays
 * the whole timeline out the first time it has owners there — and the headings inside start open
 * but for the recording's: see {@link #settle}.
 */
public final class CategoryFold
{
    private CategoryFold()
    {}

    public static void apply(UIKeyframeEditor editor, FoldState<String> folds, TrackCategory category)
    {
        if (editor == null)
        {
            return;
        }

        List<UIKeyframeSheet> sheets = editor.view.getSheets();
        Set<UIKeyframeSheet.Section> all = sections(sheets);
        Set<UIKeyframeSheet.Section> with = sectionsOf(sheets, category);
        List<UIKeyframeSheet.Section> targets = targets(all, with, category);

        if (targets.isEmpty())
        {
            return;
        }

        layout(all, with, targets, category).forEach(folds::set);

        editor.view.getDopeSheet().setExpanded(folds);
    }

    /**
     * Fold the headings a timeline shows for the first time. An owner starts the way the key of the
     * tab lit would switch it, as if just pressed; with nothing of that category on the timeline it
     * starts open. A heading inside starts open — what a part has is in reach once it is unfolded —
     * but for the recording's, which start folded but for its Position, and an addon's own headings
     * open with its tab lit.
     *
     * <p>The first time the key has owners of its own on a timeline, it lays the whole timeline out,
     * the owners already there too: a replay is added before its form is picked, and the recording
     * that opened while Form's key had nothing to open folds once the form turns up. After that only
     * what is new follows the key — a part picked later in the parts list doesn't fold what is open.
     *
     * @param fresh the headings shown for the first time, see {@link TrackTree#groupSheets}
     * @param seen  every heading these folds have shown so far, the fresh ones among them, see {@link TrackTree#seen}
     */
    public static void settle(List<UIKeyframeSheet> sheets, FoldState<String> folds, TrackCategory category, Set<String> fresh, Set<String> seen)
    {
        if (fresh.isEmpty())
        {
            return;
        }

        Set<UIKeyframeSheet.Section> all = sections(sheets);
        Set<UIKeyframeSheet.Section> with = sectionsOf(sheets, category);
        List<UIKeyframeSheet.Section> targets = targets(all, with, category);
        Map<String, Boolean> laid = targets.isEmpty() ? Collections.emptyMap() : layout(all, with, targets, category);
        Set<String> targetIds = ids(targets);
        boolean first = !targets.isEmpty();

        for (String id : seen)
        {
            if (!fresh.contains(id) && opens(id, category, targetIds))
            {
                first = false;
                break;
            }
        }

        for (UIKeyframeSheet.Section section : all)
        {
            Boolean expanded = laid.get(section.id());

            if (fresh.contains(section.id()))
            {
                folds.set(section.id(), expanded == null ? opensFirst(section) : expanded);
            }
            else if (first && expanded != null)
            {
                folds.set(section.id(), expanded);
            }
        }
    }

    /** How a heading starts when no key has a say in it: open, but for the recording's own, folded but for its Position. */
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

    /** What a key sets: its owners open, every other owner folded; an addon's own headings open too. */
    private static Map<String, Boolean> layout(Set<UIKeyframeSheet.Section> all, Set<UIKeyframeSheet.Section> with, List<UIKeyframeSheet.Section> targets, TrackCategory category)
    {
        Map<String, Boolean> layout = new HashMap<>();
        boolean builtin = category == TrackCategory.REPLAY || category == TrackCategory.FORM;

        for (UIKeyframeSheet.Section section : all)
        {
            if (section.owner())
            {
                layout.put(section.id(), targets.contains(section));
            }
            else if (!builtin && with.contains(section))
            {
                layout.put(section.id(), true);
            }
        }

        return layout;
    }

    /** Every heading the rows sit under, and the headings those sit in, in the order they stand. */
    private static Set<UIKeyframeSheet.Section> sections(List<UIKeyframeSheet> sheets)
    {
        Set<UIKeyframeSheet.Section> sections = new LinkedHashSet<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                sections.add(section);
            }
        }

        return sections;
    }

    /** The headings with the category's rows in them. */
    private static Set<UIKeyframeSheet.Section> sectionsOf(List<UIKeyframeSheet> sheets, TrackCategory category)
    {
        Set<UIKeyframeSheet.Section> sections = new HashSet<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            if (UIReplaysEditor.categoryOf(sheet) != category)
            {
                continue;
            }

            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                sections.add(section);
            }
        }

        return sections;
    }

    /** The owners a key opens: the recording, every part on the timeline, or every owner with an addon's tracks. */
    private static List<UIKeyframeSheet.Section> targets(Set<UIKeyframeSheet.Section> all, Set<UIKeyframeSheet.Section> with, TrackCategory category)
    {
        List<UIKeyframeSheet.Section> targets = new ArrayList<>();
        Set<String> withIds = ids(with);

        for (UIKeyframeSheet.Section section : all)
        {
            if (section.owner() && opens(section.id(), category, withIds))
            {
                targets.add(section);
            }
        }

        return targets;
    }

    /**
     * Whether a key opens the owner of this id: the recording's key the recording, Form's every part,
     * an addon's the owners with its tracks — which only the timeline shown now can tell, so they are
     * handed in. An id of a heading inside an owner is never opened.
     */
    private static boolean opens(String id, TrackCategory category, Set<String> withTracks)
    {
        if (category == TrackCategory.REPLAY)
        {
            return id.equals(TrackTree.RECORD);
        }

        if (category == TrackCategory.FORM)
        {
            return id.startsWith(TrackTree.FORM_OWNER);
        }

        return withTracks.contains(id);
    }

    private static Set<String> ids(Collection<UIKeyframeSheet.Section> sections)
    {
        Set<String> ids = new HashSet<>();

        for (UIKeyframeSheet.Section section : sections)
        {
            ids.add(section.id());
        }

        return ids;
    }
}
