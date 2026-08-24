package mchorse.bbs_mod.film.replays.tracks;

/**
 * Every kind of track a film can animate. The kind is what a track <em>is</em> — until now it was
 * re-derived from the channel id by string matching in seven different places, each knowing a
 * different subset of the kinds (see {@link TrackId}).
 *
 * <p>A kind carries no behaviour yet; that arrives with the track registry, which moves the value
 * factory, the apply/reset step, the seed and the presentation onto the kind itself.</p>
 */
public enum TrackKind
{
    /**
     * A plain form property, addressed by its own id ({@code color}, {@code texture}, {@code pose},
     * {@code transform}...). The pose of a form is one of these — it is a real property on the form,
     * animated as a whole, next to the per-bone tracks.
     */
    PROPERTY("property"),

    /** One bone of a model form's pose, the per-limb track that folds under the form's pose track. */
    BONE("bone"),

    /** One bone's rotation limits — the bone's "constraints" property, folding under the bone's pose track. */
    BONE_CONSTRAINT("bone_constraint"),

    /** The texture override of one material of a model form. */
    MATERIAL_TEXTURE("material_texture"),

    /** One appearance property (color, glow, culling, a PBR slider) of one material of a model form. */
    MATERIAL_PROP("material_prop"),

    /** The world-space target of one IK controller. */
    IK_TARGET("ik_target"),

    /** The world-space pole target of one IK controller. */
    POLE_TARGET("pole_target"),

    /** The world-space target of one physics chain, addressed by its root bone. */
    PHYSICS_TARGET("physics_target"),

    /** The per-chain IK scalars of a whole form (weight, softness, pole, enabled) — one track per
     * form, its keyframe holding every chain keyed by its tip bone. At playback it drives the
     * per-bone {@code ik} properties for the frame. */
    IK_CONTROLS("ik_controls"),

    /** The per-chain physics scalars of a whole form (weight, gravity, damping, stiffness) — one
     * track per form, its keyframe holding every chain keyed by its root bone. At playback it
     * drives the per-bone {@code physics} properties for the frame. */
    PHYSICS_CONTROLS("physics_controls"),

    /** The global wind of a whole form's physics — one track per form, not keyed by a chain. At
     * playback it drives the form's {@code wind} property for the frame. */
    WIND_CONTROLS("wind_controls");

    /** Stable name of the kind in saved data. Never derive it from {@link #name()} — renaming the constant would break films. */
    public final String key;

    private TrackKind(String key)
    {
        this.key = key;
    }

    public static TrackKind byKey(String key)
    {
        for (TrackKind kind : values())
        {
            if (kind.key.equals(key))
            {
                return kind;
            }
        }

        return null;
    }

    /**
     * Whether this kind drives a solver (IK, physics, wind) rather than a value of the form itself.
     * Those only apply inside a film — they are laid over the form's solver config for one frame and
     * dropped again — so a timeline that is not a film's does not offer them.
     */
    public boolean isSolver()
    {
        return this != PROPERTY && this != BONE && this != BONE_CONSTRAINT && this != MATERIAL_TEXTURE && this != MATERIAL_PROP;
    }

    /** Whether this kind addresses a whole form rather than something inside it, so it has no subject. */
    public boolean isWholeForm()
    {
        return this == IK_CONTROLS || this == PHYSICS_CONTROLS || this == WIND_CONTROLS;
    }
}
