package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The visible body-part scope is independent of the one part focused by the viewport. */
public class TimelineBodyPartSelection
{
    private final Set<String> paths = new LinkedHashSet<>(java.util.Arrays.asList(""));
    private final Set<String> knownGroups = new LinkedHashSet<>();
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

    /** Keep track-specific sections and trees; add owner headings for the remaining tracks when several parts show. */
    public void groupSheets(List<UIKeyframeSheet> sheets, UIForms forms, FoldState<String> folds)
    {
        Set<String> shown = new LinkedHashSet<>();
        for (UIForms.FormEntry entry : forms.getList())
        {
            if (this.includes(entry.getPath()) && entry.getForm() != null) shown.add(entry.getPath());
        }

        Map<String, String> names = forms.selectedNames(shown);
        Map<String, UIKeyframeSheet.Section> sections = new LinkedHashMap<>();
        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.descriptor == null) continue;
            String path = sheet.descriptor.id().formPath();
            if (sheet.section == null && shown.size() > 1)
            {
                sheet.section = sections.computeIfAbsent(path, key -> new UIKeyframeSheet.Section(
                    "body_part/" + key, IKey.constant(names.getOrDefault(key, key)), sheet.descriptor.owner().getIcon(), 0x40bfff));
            }
            /* A new heading must not hide tracks that were already accessible. */
            if (sheet.section != null && this.knownGroups.add(sheet.section.id())) folds.set(sheet.section.id(), true);
        }
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
