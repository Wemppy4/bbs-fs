package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValuePose;

/**
 * A form whose skeleton is posed by a {@link mchorse.bbs_mod.utils.pose.Pose}.
 *
 * <p>Two forms answer to this — the model form and the mob form — and everything that drives a
 * pose from the outside (the per-bone tracks, the whole-pose track's copy-on-write reset, the
 * gizmo's additive rotation base) used to ask for a model form by name and therefore did nothing
 * at all for a mob. What those callers actually need is the pose stack, which is this.</p>
 */
public interface IPosedForm
{
    public ValuePose getPose();

    public ValuePose getPoseOverlay();

    /** Whether the timeline should offer this form's bones as tracks of their own. */
    public boolean hasBoneTracks();
}
