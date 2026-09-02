package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.List;

/**
 * The reference frame a gizmo edit operates in &mdash; Blender's transform
 * orientation, reduced to the frames that make sense for a per-bone editor.
 * Drives which axes an X/Y/Z-constrained drag runs along and which frame the
 * handles are drawn in.
 */
public enum TransformSpace
{
    /** The bone's own axes — the gizmo and constrained edits follow the pose. */
    LOCAL(true, Icons.SPACE_LOCAL, UIKeys.TRANSFORMS_SPACE_LOCAL),

    /** The scene's flat axes, which never follow the pose. In a film they are the
     *  replay's OWN axes (world turned by its facing), so X stays the actor's
     *  left/right; hosts with no replay keep plain world axes. */
    GLOBAL(true, Icons.SPACE_GLOBAL, UIKeys.TRANSFORMS_SPACE_GLOBAL),

    /** The camera's right/up/forward. The handles are additionally drawn facing the
     *  eye ({@code Gizmo.applyViewShear}) so an off-centre gizmo reads flat; the edit
     *  frame itself is the plain camera basis. */
    VIEW(true, Icons.SPACE_VIEW, UIKeys.TRANSFORMS_SPACE_VIEW),

    /** The frame the bone's channels compose in — its parent bone's rendered frame
     *  (the matrix cache's origin flavour). A ring turns about the parent axis, so it
     *  generally moves MORE THAN ONE euler channel: the channels are a nested stack and
     *  only the outermost is a plain parent axis. Bumping the one like-named channel is
     *  Blender's GIMBAL instead, alive only as the euler pole fallback in
     *  {@code RingRotateDrag}. */
    PARENT(true, Icons.SPACE_PARENT, UIKeys.TRANSFORMS_SPACE_PARENT),

    /** The map's own axes, indifferent to what the edited thing sits inside: north
     *  stays north however the replay or the block is turned. Declared LAST on purpose
     *  — {@code BBSSettings.transformSpace} persists the ordinal, so a new constant may
     *  only be APPENDED. */
    WORLD(true, Icons.GLOBE, UIKeys.TRANSFORMS_SPACE_WORLD);

    /** Whether the frame math is wired up; unimplemented spaces are shown but not selectable. */
    public final boolean implemented;

    /** How the frame shows up in a picker (WORLD borrows the globe; it has no space_* sprite). */
    public final Icon icon;

    public final IKey label;

    TransformSpace(boolean implemented, Icon icon, IKey label)
    {
        this.implemented = implemented;
        this.icon = icon;
        this.label = label;
    }

    /** The picker's order, with the default {@link #PARENT} leading. Deliberately NOT
     *  the enum's own order: the ordinal is persisted, so reordering the constants would
     *  silently remap everyone's stored choice, while this list is free to change. */
    public static final List<TransformSpace> DISPLAY_ORDER = List.of(PARENT, LOCAL, GLOBAL, VIEW, WORLD);

    /** Whether this is the local frame. */
    public boolean isLocal()
    {
        return this == LOCAL;
    }

    /**
     * Whether a gizmo in this frame is PLACED on the edited thing's own matrix rather
     * than on its origin flavour (which {@code Gizmo#reorientForSpace} then turns for
     * GLOBAL/VIEW/WORLD and leaves alone for PARENT). THE placement convention of every
     * editor, and the ONLY place the question is answered: callers pass the frame and
     * ask here, so no two of them can answer it differently.
     */
    public boolean placesOnOwnFrame()
    {
        return this == LOCAL;
    }

    /** The frame remembered from the last session, guarded against a stale stored value.
     *  The choice is mod-wide, not per-editor. */
    public static TransformSpace load()
    {
        TransformSpace[] values = values();
        TransformSpace space = values[MathUtils.clamp(BBSSettings.transformSpace.get(), 0, values.length - 1)];

        return space.implemented ? space : PARENT;
    }

    /** Remembers this frame as the one every transform editor opens in. */
    public void remember()
    {
        BBSSettings.transformSpace.set(this.ordinal());
    }
}
