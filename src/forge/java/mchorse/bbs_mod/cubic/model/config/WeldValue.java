package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * A single weld entry in a model's config, mirroring {@link ModelWeld} as an editable value tree:
 * glue the {@code source} bone's face onto the {@code target} bone's so a bending joint stays sealed.
 */
public class WeldValue extends ValueGroup
{
    public final ValueString sourceBone = new ValueString("source_bone", "");
    public final ValueString sourceFace = new ValueString("source_face", "");
    public final ValueString targetBone = new ValueString("target_bone", "");
    public final ValueString targetFace = new ValueString("target_face", "");
    public final ValueFloat maxAngle = new ValueFloat("max_angle", 120F);
    public final ValueFloat seamFalloff = new ValueFloat("seam_falloff", 0.35F, 0F, 1F);

    /** The PARENT bone's share of the joint's deformation; 0.5 (the default) is the old bisector split. */
    public final ValueFloat parentShare = new ValueFloat("parent_share", 0.5F, 0F, 1F);

    /** Off by default so scenes tuned before twist distribution existed keep rendering identically. */
    public final ValueBoolean twist = new ValueBoolean("twist", false);

    /** Off by default: older configs keep their hard crease, and a rounded look is a choice per joint. */
    public final ValueBoolean smooth = new ValueBoolean("smooth", false);

    public WeldValue(String id)
    {
        super(id);

        this.add(this.sourceBone);
        this.add(this.sourceFace);
        this.add(this.targetBone);
        this.add(this.targetFace);
        this.add(this.maxAngle);
        this.add(this.seamFalloff);
        this.add(this.parentShare);
        this.add(this.twist);
        this.add(this.smooth);
    }

    public ModelWeld toWeld()
    {
        return new ModelWeld(
            this.sourceBone.get(),
            this.sourceFace.get(),
            this.targetBone.get(),
            this.targetFace.get(),
            this.maxAngle.get(),
            this.seamFalloff.get(),
            this.parentShare.get(),
            this.twist.get(),
            this.smooth.get()
        );
    }
}
