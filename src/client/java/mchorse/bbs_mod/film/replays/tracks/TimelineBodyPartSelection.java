package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** The visible body-part scope is independent of the one part focused by the viewport. */
public class TimelineBodyPartSelection
{
    private final Set<String> paths = new LinkedHashSet<>(List.of(""));
    /** Headings each timeline's folds have already seen, so only a new one starts unfolded. Folds are kept per replay. */
    private final Map<FoldState<String>, Set<String>> knownGroups = new WeakHashMap<>();
    public String activePart = "";

    public static boolean allParts()
    {
        return BBSSettings.timelineAllBodyParts != null && BBSSettings.timelineAllBodyParts.get();
    }

    public boolean includes(String path)
    {
        return allParts() || this.paths.contains(path);
    }

    public void select(List<UIForms.FormEntry> entries, String active)
    {
        this.paths.clear();
        for (UIForms.FormEntry entry : entries) this.paths.add(entry.getPath());
        if (this.paths.isEmpty()) this.paths.add("");
        this.activePart = this.paths.contains(active) ? active : this.paths.iterator().next();
    }

    /** Focusing a member keeps the other selected parts; an outside part becomes the new scope. */
    public void focus(String path)
    {
        if (!allParts() && !this.paths.contains(path))
        {
            this.paths.clear();
            this.paths.add(path);
        }
        this.activePart = path;
    }

    public void restore(UIForms forms)
    {
        Set<String> available = new LinkedHashSet<>();
        for (UIForms.FormEntry entry : forms.getList()) available.add(entry.getPath());
        this.paths.retainAll(available);
        if (this.paths.isEmpty()) this.paths.add("");
        if (!available.contains(this.activePart) || (!allParts() && !this.paths.contains(this.activePart)))
        {
            this.activePart = this.paths.iterator().next();
        }
        forms.setCurrentPaths(allParts() ? available : this.paths, this.activePart);
    }

    public String title(UIForms forms)
    {
        if (allParts()) return L10n.lang("bbs.ui.timeline.all_parts").get();
        if (this.paths.size() > 1) return L10n.lang("bbs.ui.timeline.selected_parts").format(this.paths.size()).get();
        for (UIForms.FormEntry entry : forms.getList())
        {
            if (entry.getPath().equals(this.activePart)) return entry.toString();
        }
        return "-";
    }

    /**
     * Puts the form's tracks under headings — Pose, Transform, Look, Rig (see {@link FormTrackGroup}) —
     * inside a heading per part when several parts show, and reorders the rows so each heading's rows
     * follow it. A folding tree stays whole: its rows take the heading of the row they fold under.
     */
    public void groupSheets(List<UIKeyframeSheet> sheets, UIForms forms, FoldState<String> folds)
    {
        Set<String> shown = new LinkedHashSet<>();
        for (UIForms.FormEntry entry : forms.getList())
        {
            if (this.includes(entry.getPath()) && entry.getForm() != null) shown.add(entry.getPath());
        }

        Map<String, String> names = forms.selectedNames(shown);
        Map<String, UIKeyframeSheet.Section> parts = new HashMap<>();
        Map<String, UIKeyframeSheet.Section> groups = new HashMap<>();
        Map<String, Integer> partOrder = new HashMap<>();
        Map<UIKeyframeSheet, Integer> rank = new HashMap<>();
        Set<String> known = this.knownGroups.computeIfAbsent(folds, k -> new HashSet<>());
        int slots = FormTrackGroup.values().length + 1;

        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.descriptor == null) continue;

            if (sheet.parent != null && rank.containsKey(sheet.parent))
            {
                sheet.section = sheet.parent.section;
                rank.put(sheet, rank.get(sheet.parent));
                continue;
            }

            String path = sheet.descriptor.id().formPath();
            FormTrackGroup group = FormTrackGroup.of(sheet.descriptor.id());

            if (sheet.section == null)
            {
                UIKeyframeSheet.Section part = shown.size() > 1 ? parts.computeIfAbsent(path, key -> new UIKeyframeSheet.Section(
                    "body_part/" + key, IKey.constant(names.getOrDefault(key, key)), sheet.descriptor.owner().getIcon(), 0x40bfff)) : null;
                sheet.section = group == null ? part : groups.computeIfAbsent(path + "/" + group.id, key -> group.section(path, part));
            }

            /* An addon's track in "all tracks" has no group of ours; it closes its part's list. */
            int partIndex = partOrder.computeIfAbsent(path, key -> partOrder.size());
            rank.put(sheet, partIndex * slots + (group == null ? slots - 1 : group.ordinal()));

            /* A new heading must not hide tracks that were already accessible. */
            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                if (known.add(section.id())) folds.set(section.id(), true);
            }
        }

        /* Stable, so a heading keeps its rows' order and a tree its shape. Rows of no form — the
         * replay's own channels — stay in front. */
        sheets.sort(Comparator.comparingInt(sheet -> rank.getOrDefault(sheet, -1)));
    }

    public void write(MapType data)
    {
        data.putString("body_part", this.activePart);
        ListType selected = new ListType();
        this.paths.forEach(selected::addString);
        data.put("body_parts", selected);
    }

    public void read(MapType data)
    {
        this.activePart = data.getString("body_part");
        this.paths.clear();
        for (var item : data.getList("body_parts")) this.paths.add(item.asString());
        if (this.paths.isEmpty()) this.paths.add(this.activePart);
    }
}
