package mchorse.bbs_mod.cubic.weld;

/**
 * A weld declared in a model's {@code config.json}: the {@code source} bone's face gets glued onto the
 * {@code target} bone's face, so a two-cube limb (e.g. forearm + fist) keeps its joint sealed when it
 * bends instead of opening the usual box-model gap. Resolved against a model into a {@link WeldBinding}.
 */
public class ModelWeld
{
    public final String sourceBone;
    public final String sourceFace;
    public final String targetBone;
    public final String targetFace;

    /** Largest bend (degrees) the seam keeps following; past it the shear holds so extreme poses don't blow it out. */
    public final float maxAngle;

    public ModelWeld(String sourceBone, String sourceFace, String targetBone, String targetFace, float maxAngle)
    {
        this.sourceBone = sourceBone;
        this.sourceFace = sourceFace;
        this.targetBone = targetBone;
        this.targetFace = targetFace;
        this.maxAngle = maxAngle;
    }
}
