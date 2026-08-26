package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.HashSet;
import java.util.Set;

public class PoseKeyframeFactory implements IKeyframeFactory<Pose>
{
    private static Set<String> keys = new HashSet<>();

    /**
     * Stands in for a bone one of the four keyframes says nothing about. The keys are the
     * UNION of all four poses, so most of them are missing from most of the poses, and
     * interpolating against the rest transform is what "silent about this bone" means.
     *
     * <p>Read-only by contract: it is only ever passed in as a source. Reading a pose used
     * to insert, so interpolation silently grew every keyframe it played through — a pose
     * ended up holding every bone any of its neighbours mentioned.
     */
    private static final PoseTransform REST = new PoseTransform();

    private Pose i = new Pose();

    @Override
    public Pose fromData(BaseType data)
    {
        Pose pose = new Pose();

        if (data.isMap())
        {
            pose.fromData(data.asMap());
        }

        return pose;
    }

    @Override
    public BaseType toData(Pose value)
    {
        return value.toData();
    }

    @Override
    public Pose createEmpty()
    {
        return new Pose();
    }

    @Override
    public Pose copy(Pose value)
    {
        return value.copy();
    }

    @Override
    public Pose interpolate(Keyframe<Pose> preA, Keyframe<Pose> a, Keyframe<Pose> b, Keyframe<Pose> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            Pose preAp = preA.getValue();
            Pose ap = a.getValue();
            Pose bp = b.getValue();
            Pose postBp = postB.getValue();

            this.collect(preAp, ap, bp, postBp);

            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);
            float pt = preA.getTick();
            float at = a.getTick();
            float bt = b.getTick();
            float qt = postB.getTick();

            for (String key : keys)
            {
                this.i.getOrCreate(key).autoLerp(at(preAp, key), at(ap, key), at(bp, key), at(postBp, key), pt, at, bt, qt, clamped, x);
            }

            return this.i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public Pose interpolate(Pose preA, Pose a, Pose b, Pose postB, IInterp interpolation, float x)
    {
        this.collect(preA, a, b, postB);

        for (String key : keys)
        {
            this.i.getOrCreate(key).lerp(at(preA, key), at(a, key), at(b, key), at(postB, key), interpolation, x);
        }

        return this.i;
    }

    /** The bone's transform in that pose, or the rest one when the pose is silent about it. */
    private static PoseTransform at(Pose pose, String key)
    {
        PoseTransform transform = pose == null ? null : pose.get(key);

        return transform == null ? REST : transform;
    }

    private void collect(Pose preA, Pose a, Pose b, Pose postB)
    {
        keys.clear();

        if (preA != a && preA != null) keys.addAll(preA.transforms.keySet());
        if (a != null) keys.addAll(a.transforms.keySet());
        if (b != null) keys.addAll(b.transforms.keySet());
        if (postB != b && postB != null) keys.addAll(postB.transforms.keySet());

        for (PoseTransform value : this.i.transforms.values())
        {
            value.identity();
        }
    }
}