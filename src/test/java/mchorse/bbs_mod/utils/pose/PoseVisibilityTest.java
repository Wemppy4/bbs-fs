package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.Interpolations;

/** Standalone sanity checks; run main with the test runtime classpath. */
public class PoseVisibilityTest
{
    public static void main(String[] args)
    {
        PoseTransform hidden = new PoseTransform();
        hidden.visible = false;

        check(!hidden.isDefault(), "A visibility-only edit must survive pose pruning");
        check(hidden.contentHash() != new PoseTransform().contentHash(), "Visibility must invalidate cached poses");

        Pose pose = new Pose();
        pose.transforms.put("parent", hidden);
        check(!pose.copy().get("parent").visible, "Copy must retain hidden bones");
        Pose loaded = new Pose();
        loaded.fromData(pose.toData());
        check(!loaded.get("parent").visible && loaded.equals(pose), "Visibility must round-trip");

        hidden.fromData(new MapType());
        check(hidden.visible, "Old poses without visibility must load as visible");
        hidden.visible = false;
        hidden.identity();
        check(hidden.visible && hidden.isDefault(), "Reset must restore visibility");

        PoseTransform shown = new PoseTransform();
        hidden.visible = false;
        PoseTransform result = new PoseTransform();

        for (float x : new float[] {0F, 0.5F, 0.999F, 1F})
        {
            result.lerp(shown, shown, hidden, hidden, Interpolations.LINEAR, x);
            check(result.visible == (x < 1F), "Linear keys must hold visibility until the next key");
            result.autoLerp(hidden, hidden, shown, shown, -10F, 0F, 10F, 20F, false, x);
            check(result.visible == (x >= 1F), "Auto keys must hold visibility in the opposite direction");
        }

        result.copy(shown);
        result.add(hidden);
        result.add(shown);
        check(!result.visible, "Neutral additive layers must not re-enable a hidden bone");

        Model model = new Model(null);
        ModelGroup parent = new ModelGroup("parent");
        ModelGroup child = new ModelGroup("child");
        parent.children.add(child);
        model.topGroups.add(parent);
        model.initialize();
        model.applyPose(pose);
        check(!parent.isVisible() && child.isVisible(), "Hiding a bone must leave its children visible");
        parent.visible = true;
        check(!parent.isVisible(), "CEM/model visibility must not override the pose");
        model.resetPose();
        check(parent.isVisible(), "Visibility must not leak into the next pose");
        parent.visible = false;
        model.applyPose(new Pose());
        check(!parent.isVisible(), "A neutral pose must respect model visibility");

        System.out.println("Pose visibility checks passed");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
