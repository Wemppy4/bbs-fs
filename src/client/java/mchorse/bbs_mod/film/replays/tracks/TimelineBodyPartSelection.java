package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.forms.editors.UIForms;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The body parts a timeline shows — picked in the parts list under the replays, each under its
 * own heading (see {@link TrackTree}) — and the one in focus, the last picked there or in the viewport.
 */
public class TimelineBodyPartSelection
{
    private final Set<String> paths = new LinkedHashSet<>(List.of(""));
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
