package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.api.client.editor.TrackCategories;
import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hangs a timeline's tracks under their owners — the recording, each addon tab with channels of the
 * replay (BBSFP's first person) and each part on the timeline (the ones picked in the parts list,
 * see {@link TimelineBodyPartSelection}) — and, inside a part's owner, under its headings (Pose, Rig,
 * Transform, Look; see {@link FormTrackGroup}).
 */
public class TrackTree
{
    /** The recording's own heading: the replay's channels that no addon tab claims. */
    public static final String RECORD = "owner/record";

    /**
     * What the heading of an addon tab's replay channels starts with, the tab's id after it. They
     * are owned apart from the recording, so the tab shows them under a heading of their own.
     */
    private static final String CATEGORY_OWNER = "owner/category/";

    /** Headings each timeline's folds have already seen, so only a new one is handed out to be laid out. Folds are kept per replay. */
    private final Map<FoldState<String>, Set<String>> knownGroups = new WeakHashMap<>();

    /** What every form's own heading starts with, its path after it. */
    public static final String FORM_OWNER = "owner/form/";

    public static String ownerId(String path)
    {
        return FORM_OWNER + path;
    }

    /**
     * Gives every row its heading and reorders the rows so each heading's rows follow it: the
     * recording first, then the addon tabs' channels, then the parts in the form's own order. A
     * folding tree stays whole: its rows take the heading of the row they fold under.
     *
     * @param parts  the form and its parts, root first — see {@link UIForms#entries}
     * @param record whether the rows of no form, a replay's own channels, go under the recording and the addon tabs
     * @return the headings these folds see for the first time; how they start folded is up to the
     *         caller — see {@code CategoryFold#settle}
     */
    public Set<String> groupSheets(List<UIKeyframeSheet> sheets, List<UIForms.FormEntry> parts, FoldState<String> folds, boolean record)
    {
        Map<String, String> names = UIForms.names(parts);
        Map<String, Integer> partOrder = new HashMap<>();
        Map<String, UIKeyframeSheet.Section> owners = new HashMap<>();
        Map<String, UIKeyframeSheet.Section> groups = new HashMap<>();
        Map<String, UIKeyframeSheet.Section> rehung = new HashMap<>();
        Map<UIKeyframeSheet, Integer> rank = new HashMap<>();
        int slots = FormTrackGroup.values().length + 1;
        UIKeyframeSheet.Section recording = record ? UIKeyframeSheet.Section.owner(RECORD, TrackCategory.REPLAY.label, Icons.PLAYER) : null;
        Map<TrackCategory, UIKeyframeSheet.Section> addons = new HashMap<>();
        List<TrackCategory> categories = TrackCategories.values();

        for (UIForms.FormEntry entry : parts)
        {
            partOrder.putIfAbsent(entry.getPath(), partOrder.size());
        }

        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.parent != null && sheet.parent.section != null && (rank.containsKey(sheet.parent) || sheet.parent.descriptor == null))
            {
                sheet.section = sheet.parent.section;

                if (rank.containsKey(sheet.parent))
                {
                    rank.put(sheet, rank.get(sheet.parent));
                }

                continue;
            }

            if (sheet.descriptor == null)
            {
                /* Keeps its place in front, in the order it was handed over in: the recording's
                 * rows first, then each addon's in the order of the tabs. */
                if (recording != null)
                {
                    TrackCategory category = TrackCategories.categoryOf(TrackId.parse(sheet.id), false);
                    boolean addon = category != TrackCategory.REPLAY && category != TrackCategory.FORM;
                    UIKeyframeSheet.Section owner = addon ? addons.computeIfAbsent(category, c -> UIKeyframeSheet.Section.owner(CATEGORY_OWNER + c.id, c.label, c.icon)) : recording;

                    sheet.section = rehang(sheet.section, owner, rehung);
                    rank.put(sheet, (addon ? categories.indexOf(category) : 0) - categories.size());
                }

                continue;
            }

            String path = sheet.descriptor.id().formPath();
            FormTrackGroup group = FormTrackGroup.of(sheet.descriptor.id());
            UIKeyframeSheet.Section owner = owners.computeIfAbsent(path, key -> UIKeyframeSheet.Section.owner(ownerId(key),
                IKey.constant(names.getOrDefault(key, key)), sheet.descriptor.owner().getIcon()));

            if (sheet.section == null)
            {
                sheet.section = group == null ? owner : groups.computeIfAbsent(path + "/" + group.id, key -> group.section(path, owner));
            }
            else
            {
                sheet.section = rehang(sheet.section, owner, rehung);
            }

            /* An addon's track has no heading of ours; it closes its owner's list. */
            int partIndex = partOrder.getOrDefault(path, partOrder.size());

            rank.put(sheet, partIndex * slots + (group == null ? slots - 1 : group.ordinal()));
        }

        Set<String> known = this.knownGroups.computeIfAbsent(folds, k -> new HashSet<>());
        Set<String> fresh = new HashSet<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                if (known.add(section.id()))
                {
                    fresh.add(section.id());
                }
            }
        }

        /* Stable, so a heading keeps its rows' order and a tree its shape. Rows of no form stay in front. */
        sheets.sort(Comparator.comparingInt(sheet -> rank.getOrDefault(sheet, -1)));

        return fresh;
    }

    /** A heading built by someone else, hung under the owner it belongs to; owners and what already hangs there stay. */
    private static UIKeyframeSheet.Section rehang(UIKeyframeSheet.Section section, UIKeyframeSheet.Section owner, Map<String, UIKeyframeSheet.Section> rehung)
    {
        if (section == null)
        {
            return owner;
        }

        if (section.owner() || section.isIn(owner))
        {
            return section;
        }

        String key = owner.id() + "|" + section.id();
        UIKeyframeSheet.Section hung = rehung.get(key);

        if (hung == null)
        {
            hung = section.under(rehang(section.parent(), owner, rehung));
            rehung.put(key, hung);
        }

        return hung;
    }
}
