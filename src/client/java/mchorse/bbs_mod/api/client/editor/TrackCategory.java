package mchorse.bbs_mod.api.client.editor;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import java.util.Objects;

/** A timeline category. Register addon categories during RegisterTrackCategoriesEvent. */
public final class TrackCategory
{
    public static final TrackCategory REPLAY = builtin("replay", Icons.PLAYER);
    public static final TrackCategory FORM = builtin("form", Icons.MORPH);
    /**
     * Bone tracks. Not just a filter like the others: picking it is how the animator says they key
     * bones one by one — a bone click lands on its own track and the panel becomes the pose of the
     * frame under the cursor. That is why "All tracks" leaves these out.
     */
    public static final TrackCategory BONES = builtin("bones", Icons.POSE);

    public final String id;
    public final Icon icon;
    public final IKey label;
    public final IKey tooltip;

    public TrackCategory(String id, Icon icon, IKey label, IKey tooltip)
    {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected namespaced category id: " + id);
        this.id = id;
        this.icon = Objects.requireNonNull(icon);
        this.label = Objects.requireNonNull(label);
        this.tooltip = Objects.requireNonNull(tooltip);
    }

    private static TrackCategory builtin(String id, Icon icon)
    {
        return new TrackCategory("bbs:" + id, icon, () -> L10n.lang("bbs.ui.film.replays.category." + id).get(),
            () -> L10n.lang("bbs.ui.film.replays.category." + id + ".tooltip").get());
    }
}
