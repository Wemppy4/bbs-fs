package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
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

    public WeldValue(String id)
    {
        super(id);

        this.add(this.sourceBone);
        this.add(this.sourceFace);
        this.add(this.targetBone);
        this.add(this.targetFace);
        this.add(this.maxAngle);
        this.add(this.seamFalloff);
    }

    public ModelWeld toWeld()
    {
        return new ModelWeld(
            this.sourceBone.get(),
            this.sourceFace.get(),
            this.targetBone.get(),
            this.targetFace.get(),
            this.maxAngle.get(),
            this.seamFalloff.get()
        );
    }
}
