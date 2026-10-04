package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.BBSSettings;

import java.util.Collection;

/**
 * Decides whether the editors hide a model's factory-disabled bones from selection. Models flag some
 * bones as disabled (eyes, helper bones), which suits most users, but the global
 * {@link BBSSettings#poseShowDisabledBones} setting lets power users override that and pick any bone.
 *
 * <p>The decision lives here, in the UI layer, on purpose: the model only carries the list of
 * disabled bones — it stays unaware of the user's global preference (no coupling to model code).
 */
public class PoseBones
{
    private PoseBones()
    {}

    /** Whether {@code bone} should be hidden from selection: it's disabled and the override is off. */
    public static boolean isHidden(Collection<String> disabledBones, String bone)
    {
        return disabledBones != null
            && !disabledBones.isEmpty()
            && !BBSSettings.poseShowDisabledBones.get()
            && disabledBones.contains(bone);
    }
}
