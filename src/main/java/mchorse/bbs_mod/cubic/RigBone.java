package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One bone of a posable model, whichever kind of model it came from.
 *
 * <p>The mod carries two skeletons — the cubic model's groups and the BOBJ armature's bones —
 * and everything that poses a skeleton (IK, physics, limits, welds, the gizmo) needs the same
 * handful of things from either: what the bone is called, where it rests, the transform being
 * edited, and the rotation as evaluated so far. That list is this interface. Consumers used to
 * ask "are you a cubic model or a BOBJ one?" and then reach for the matching field, which is
 * why the same routine existed twice in a dozen places.
 *
 * <p>Both bones implement it DIRECTLY — no wrappers. The two classes had already grown the same
 * members under the same names ({@code orient}, {@code evaluatedRotation}, {@code composeOrient},
 * {@code offset}, {@code reset}), with their javadoc pointing at each other; the interface only
 * writes that agreement down.
 */
public interface RigBone
{
    /** The name the bone is addressed by — a cubic group's id, a BOBJ bone's name. */
    String getBoneName();

    /** The parent bone, or null at the root of the skeleton. */
    RigBone getParentBone();

    /**
     * The transform the pose stack edits: channels, then IK, physics and limits on top. This is
     * the live object, not a copy.
     */
    Transform getBoneTransform();

    /** Where the bone sits before anything poses it, in its parent's frame. */
    Vector3f getRestTranslation();

    /**
     * The bone's evaluated local rotation as of this point in the pipeline: {@link #getOrient()}
     * when a stage has composed one, otherwise the rotation its channels describe. THE read for
     * every constraint-stack stage, so stages stack instead of overwriting each other. Returns a
     * fresh instance safe to mutate.
     */
    Quaternionf evaluatedRotation();

    /**
     * Composes one rotation layer into the orient quaternion. The first layer seeds it from the
     * euler accumulated so far, so a single layer stays byte-identical to the euler path; later
     * layers multiply their delta, so stacked layers compose without the euler-pole flip.
     */
    void composeOrient(Quaternionf delta);

    /** The composed rotation, or null while the bone is still described by its euler channels. */
    Quaternionf getOrient();

    void setOrient(Quaternionf orient);

    /**
     * Whether this skeleton keeps its euler channels in DEGREES. The cubic model does, BOBJ
     * keeps radians, and mixing the two up is the classic way to get a limb spinning a hundred
     * times too far — so every consumer goes through the two helpers below rather than reading
     * {@code rotate} and guessing.
     */
    boolean isRotationInDegrees();

    /** The bone's euler channels in radians, whatever the skeleton stores them in. */
    default Vector3f getChannelRotation(Vector3f dest)
    {
        dest.set(this.getBoneTransform().rotate);

        return this.isRotationInDegrees() ? dest.mul((float) (Math.PI / 180D)) : dest;
    }

    /** Writes euler channels given in radians, converting to whatever the skeleton stores. */
    default void setChannelRotation(float x, float y, float z)
    {
        float factor = this.isRotationInDegrees() ? (float) (180D / Math.PI) : 1F;

        this.getBoneTransform().rotate.set(x * factor, y * factor, z * factor);
    }

    /**
     * The evaluated rotation decomposed against the bone's own channels, in the skeleton's unit
     * — the read every limit stage starts from, so a clamp lands on angles the animator would
     * recognise instead of on a fresh decomposition that may have wound differently.
     */
    default Vector3f toCompatibleEuler(Vector3f dest)
    {
        Vector3f reference = this.getBoneTransform().rotate;

        return this.isRotationInDegrees()
            ? Matrices.toCompatibleEulerZYXDegrees(this.evaluatedRotation(), reference, dest)
            : Matrices.toCompatibleEulerZYXRadians(this.evaluatedRotation(), reference, dest);
    }

    /** Builds an orient quaternion from euler angles given in the skeleton's own unit. */
    default Quaternionf orientFromEuler(Vector3f euler)
    {
        return this.isRotationInDegrees()
            ? Matrices.toLocalRotationZYXDegrees(euler)
            : Matrices.toLocalRotationZYXRadians(euler);
    }

    /** What to multiply a value authored in DEGREES by to express it in the skeleton's unit. */
    default float fromDegrees()
    {
        return this.isRotationInDegrees() ? 1F : (float) (Math.PI / 180D);
    }

    /**
     * Whether a stretch offset is stored in WORLD space (BOBJ, which accumulates it along the
     * chain) rather than in the bone's own local space with the pivot scale divided out (cubic).
     * The two skeletons genuinely disagree here, so the writer asks instead of assuming.
     */
    default boolean usesWorldStretchOffset()
    {
        return !this.isRotationInDegrees();
    }

    /** A translation offset stages add on top of the channels, or null when there is none. */
    Vector3f getOffset();

    void setOffset(Vector3f offset);
}
