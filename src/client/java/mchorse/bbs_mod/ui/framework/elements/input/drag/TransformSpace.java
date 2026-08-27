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
 * It drives both which axes an X/Y/Z-constrained drag turns/slides along and,
 * for the gizmo, which frame its handles are drawn in.
 *
 * <p>{@link #LOCAL} is the historical behaviour: the gizmo aligns to the bone's
 * own axes and a constrained edit runs along them (the panel also switches to
 * relative local nudges here). {@link #GLOBAL} aligns to the scene's flat axes
 * (the replay's own facing in a film, the model block's rotation when editing a
 * form inside one), {@link #WORLD} to the map's fixed axes regardless, and
 * {@link #VIEW} to the camera's right/up/forward. {@link #PARENT} aligns to
 * the frame the bone's channels compose in &mdash; its parent bone (or the
 * model root / world for a top-level bone). The non-local gizmo placement
 * already carries that frame: the matrix cache's origin flavour is the bone's
 * frame BEFORE its own rotation, so PARENT simply keeps the placed axes
 * (see {@code Gizmo.reorientForSpace}); the model block maps it to GLOBAL,
 * its transform composing straight onto the world. The four-way cycle
 * replaces the old local/global boolean, so {@code space == LOCAL} is exactly
 * the former {@code local} flag and every consumer that only distinguished
 * local from not-local keeps working with {@link #isLocal()}.
 */
public enum TransformSpace
{
    /** The bone's own axes — the gizmo and constrained edits follow the pose. */
    LOCAL(true, Icons.SPACE_LOCAL, UIKeys.TRANSFORMS_SPACE_LOCAL),

    /** The scene's flat axes — a constrained edit runs along fixed X/Y/Z that
     *  never follow the pose. In a film those are the edited replay's OWN axes:
     *  the world frame turned by the replay's facing
     *  ({@code BaseFilmController.getReplayWorldAxes}), so X stays the actor's
     *  left/right however the actor was placed on the map. Hosts with no replay
     *  to face (form editor, model blocks) keep the plain world axes. */
    GLOBAL(true, Icons.SPACE_GLOBAL, UIKeys.TRANSFORMS_SPACE_GLOBAL),

    /** The camera's right/up/forward — a constrained edit runs in screen space.
     *  The handles are additionally drawn facing the eye rather than merely
     *  parallel to the screen, so an off-centre gizmo reads dead flat instead of
     *  slightly turned away (see {@code Gizmo.applyViewShear}); the edit frame
     *  itself is the plain camera basis. */
    VIEW(true, Icons.SPACE_VIEW, UIKeys.TRANSFORMS_SPACE_VIEW),

    /** The parent's frame — the frame the bone's own channels compose in, and
     *  the frame the parent bone is drawn in (the gizmo is placed on the cache's
     *  origin flavour, which IS the parent bone's own rendered frame). A ring
     *  turns the bone about that parent axis like any other space, so a single
     *  ring generally moves more than one euler channel: those channels are a
     *  nested stack, and only its outermost one is a plain parent axis. Bumping
     *  the driven channel instead — the pre-spaces behaviour this used to keep
     *  — is Blender's GIMBAL orientation, a different frame that only coincides
     *  with the parent's for that one channel; it survives as the euler pole
     *  fallback in {@code RingRotateDrag}. */
    PARENT(true, Icons.SPACE_PARENT, UIKeys.TRANSFORMS_SPACE_PARENT),

    /** The map's own axes, indifferent to what the edited thing sits inside —
     *  north stays north however the replay is turned or the model block is
     *  rotated. This is what {@link #GLOBAL} used to be before it was tied to
     *  the container; kept as its own frame because both are genuinely useful:
     *  GLOBAL to work along the actor, WORLD to line something up with the
     *  scene. Declared LAST on purpose — {@code BBSSettings.transformSpace}
     *  persists the ordinal, so a new constant may only be appended. */
    WORLD(true, Icons.GLOBE, UIKeys.TRANSFORMS_SPACE_WORLD);

    /** Whether the frame math is wired up; unimplemented spaces are shown but not selectable. */
    public final boolean implemented;

    /** How the frame shows up in a picker. WORLD borrows the globe: no dedicated space_*
     *  sprite exists for it, and a globe reads as "the map itself" better than a new flat glyph. */
    public final Icon icon;

    public final IKey label;

    TransformSpace(boolean implemented, Icon icon, IKey label)
    {
        this.implemented = implemented;
        this.icon = icon;
        this.label = label;
    }

    /**
     * The order the picker lists the frames in: {@link #PARENT} leads (it is the
     * default, and the frame the channels natively compose in), then the rest,
     * with {@link #WORLD} last as the specialist of the set. Deliberately NOT
     * the enum's own order — {@code BBSSettings.transformSpace} persists the
     * ordinal, so reordering the constants would silently remap everyone's
     * stored choice, while this list is free to change.
     */
    public static final List<TransformSpace> DISPLAY_ORDER = List.of(PARENT, LOCAL, GLOBAL, VIEW, WORLD);

    /** Whether this is the local frame; the single distinction older consumers make. */
    public boolean isLocal()
    {
        return this == LOCAL;
    }

    /**
     * The frame remembered from the last session, guarded against an out-of-range or
     * not-yet-implemented stored value (then falls back to the default, {@link #PARENT}).
     *
     * <p>The choice is GLOBAL to the mod, not per-editor: picking a frame in one transform
     * editor is the frame every other one opens in.
     */
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
