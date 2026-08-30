package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.pose.Pose;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IModel
{
    public Pose createPose();

    public void resetPose();

    public void applyPose(Pose pose);

    /**
     * Record every bone's channels-phase orient/offset, right after the channels evaluate, so a
     * skipped re-evaluation can rewind the constraint stack's writes with {@link #restoreChannels()}
     * — IK/physics blend FROM the evaluated state and must not stack on their own previous output.
     */
    public void snapshotChannels();

    public void restoreChannels();

    public Set<String> getShapeKeys();

    public String getAnchor();

    public Collection<String> getAllGroupKeys();

    public Collection<String> getAllChildrenKeys(String key);

    /**
     * The bone that name addresses, or null when this model has no such bone. The one lookup
     * every poser needs: what used to be "is this a cubic model or a BOBJ one, and which of the
     * two bone maps do I reach into" is now this call plus {@link RigBone}.
     */
    public RigBone getBone(String name);

    /**
     * Every bone of the skeleton, in the order it is evaluated (parents before children). The
     * counterpart of {@link #getBone} for stages that sweep the whole rig — limits, physics,
     * debug overlays — and the reason they no longer need one loop per model flavour.
     */
    /**
     * Whether the skeleton is authored facing the other way, so anything drawn in model space
     * over it — the IK and physics overlays — has to turn 180° about Y to line up. True for BOBJ.
     */
    public default boolean isFacingFlipped()
    {
        return false;
    }

    public Collection<? extends RigBone> getRigBones();

    public Collection<ModelGroup> getAllGroups();

    public Collection<BOBJBone> getAllBOBJBones();

    public Collection<String> getAdjacentGroups(String groupName);

    public Collection<String> getHierarchyGroups(String groupName);

    public Collection<String> getRootGroupKeys();

    public Collection<String> getDirectChildrenKeys(String key);

    public String getParentGroupKey(String key);

    public default List<String> getGroupKeysInHierarchyOrder()
    {
        List<String> out = new ArrayList<>();

        for (String root : this.getRootGroupKeys())
        {
            this.collectGroupAndDescendants(root, out);
        }

        return out;
    }

    default void collectGroupAndDescendants(String name, List<String> out)
    {
        out.add(name);
        for (String child : this.getDirectChildrenKeys(name))
        {
            this.collectGroupAndDescendants(child, out);
        }
    }

    public void apply(IEntity target, Animation action, float tick, float blend, float transition, boolean skipInitial);

    public void postApply(IEntity target, Animation action, float tick, float transition);
}