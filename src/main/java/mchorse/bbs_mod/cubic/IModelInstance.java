package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.utils.pose.Pose;

public interface IModelInstance
{
    public IModel getModel();

    public Pose getSneakingPose();

    public Animations getAnimations();

    default java.util.Map<String, String> getProceduralBones()
    {
        return java.util.Collections.emptyMap();
    }
}
