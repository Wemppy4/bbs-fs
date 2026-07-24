package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * Form capability for editable poses and per-bone animation tracks.
 */
public interface PoseForm
{
    public ValuePose getPose();

    public ValuePose getPoseOverlay();

    public ValueBoolean getBoneTracks();
}
