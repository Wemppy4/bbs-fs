package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.BezierUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3d;

import java.util.List;

public class CubicModelAnimator
{
    private static Vector3d p = new Vector3d();
    private static Vector3d s = new Vector3d();
    private static Vector3d r = new Vector3d();

    public static Vector3d interpolateList(Vector3d output, KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, float frame, double defaultValue, boolean looping)
    {
        output.x = interpolateChannel(x, frame, defaultValue, looping);
        output.y = interpolateChannel(y, frame, defaultValue, looping);
        output.z = interpolateChannel(z, frame, defaultValue, looping);

        return output;
    }

    private static double interpolateChannel(KeyframeChannel<MolangExpression> channel, float frame, double defaultValue, boolean looping)
    {
        KeyframeSegment<MolangExpression> segment = channel.findSegment(frame);

        if (segment == null)
        {
            return defaultValue;
        }

        double start = segment.a.getValue().get();
        double destination = segment.b.getValue().get();

        if (segment.b.getInterpolation().getInterp() == Interpolations.BEZIER)
        {
            return BezierUtils.get(start, destination,
                segment.a.getTick(), segment.b.getTick(),
                segment.a.rx, segment.a.ry,
                segment.b.lx, segment.b.ly,
                segment.getInterpolationProgress(true)
            );
        }

        double pre = segment.preA.getValue().get();
        double post = segment.postB.getValue().get();

        /* A looping action restarts at its first key, so the spline's outer neighbours wrap
         * around the seam the way Blockbench's loop mode does: the first key leans on the one
         * before the last, the last key on the second. Otherwise the speed kinks on restart. */
        List<Keyframe<MolangExpression>> keyframes = channel.getKeyframes();

        if (looping && keyframes.size() >= 3)
        {
            if (segment.preA == segment.a) pre = keyframes.get(keyframes.size() - 2).getValue().get();
            if (segment.postB == segment.b) post = keyframes.get(1).getValue().get();
        }

        double duration = IInterp.context.duration;
        double startTick = IInterp.context.startTick;

        IInterp.context.segment(segment.duration, segment.a.getTick());

        try
        {
            return segment.b.getInterpolation().interpolate(IInterp.context.set(pre, start, destination, post, segment.getInterpolationProgress(true)));
        }
        finally
        {
            IInterp.context.segment(duration, startTick);
        }
    }

    public static void animate(Model model, Animation animation, float frame, float blend, boolean skipInitial, boolean looping)
    {
        for (ModelGroup group : model.topGroups)
        {
            animateGroup(group, animation, frame, blend, skipInitial, looping);
        }
    }

    private static void animateGroup(ModelGroup group, Animation animation, float frame, float blend, boolean skipInitial, boolean looping)
    {
        boolean applied = false;

        AnimationPart part = animation.parts.get(group.id);

        if (part != null)
        {
            applyGroupAnimation(group, part, frame, blend, looping);

            applied = true;
        }

        if (!applied && !skipInitial)
        {
            Transform initial = group.initial;
            Transform current = group.current;

            current.translate.lerp(initial.translate, blend);
            current.scale.lerp(initial.scale, blend);

            current.rotate.x = (float) Lerps.lerpYaw(current.rotate.x, initial.rotate.x, blend);
            current.rotate.y = (float) Lerps.lerpYaw(current.rotate.y, initial.rotate.y, blend);
            current.rotate.z = (float) Lerps.lerpYaw(current.rotate.z, initial.rotate.z, blend);
        }

        for (ModelGroup childGroup : group.children)
        {
            animateGroup(childGroup, animation, frame, blend, skipInitial, looping);
        }
    }

    private static void applyGroupAnimation(ModelGroup group, AnimationPart animation, float frame, float blend, boolean looping)
    {
        Vector3d position = interpolateList(p, animation.x, animation.y, animation.z, frame, 0D, looping);
        Vector3d scale = interpolateList(s, animation.sx, animation.sy, animation.sz, frame, 1D, looping);
        Vector3d rotation = interpolateList(r, animation.rx, animation.ry, animation.rz, frame, 0D, looping);

        scale.sub(1, 1, 1);

        rotation.x *= -1;
        rotation.y *= -1;

        Transform initial = group.initial;
        Transform current = group.current;

        current.translate.x = Lerps.lerp(current.translate.x, (float) position.x + initial.translate.x, blend);
        current.translate.y = Lerps.lerp(current.translate.y, (float) position.y + initial.translate.y, blend);
        current.translate.z = Lerps.lerp(current.translate.z, (float) position.z + initial.translate.z, blend);

        current.scale.x = Lerps.lerp(current.scale.x, (float) scale.x + initial.scale.x, blend);
        current.scale.y = Lerps.lerp(current.scale.y, (float) scale.y + initial.scale.y, blend);
        current.scale.z = Lerps.lerp(current.scale.z, (float) scale.z + initial.scale.z, blend);

        current.rotate.x = (float) Lerps.lerpYaw(current.rotate.x, (float) rotation.x + initial.rotate.x, blend);
        current.rotate.y = (float) Lerps.lerpYaw(current.rotate.y, (float) rotation.y + initial.rotate.y, blend);
        current.rotate.z = (float) Lerps.lerpYaw(current.rotate.z, (float) rotation.z + initial.rotate.z, blend);
    }

    public static void postAnimate(Model model, Animation animation, float tick, boolean looping)
    {
        for (ModelGroup group : model.topGroups)
        {
            animateGroupPost(group, animation, tick, looping);
        }
    }

    private static void animateGroupPost(ModelGroup group, Animation animation, float frame, boolean looping)
    {
        AnimationPart part = animation.parts.get(group.id);

        if (part != null)
        {
            applyGroupAnimationPost(group, part, frame, looping);
        }

        for (ModelGroup childGroup : group.children)
        {
            animateGroupPost(childGroup, animation, frame, looping);
        }
    }

    private static void applyGroupAnimationPost(ModelGroup group, AnimationPart animation, float frame, boolean looping)
    {
        Vector3d position = interpolateList(p, animation.x, animation.y, animation.z, frame, 0D, looping);
        Vector3d scale = interpolateList(s, animation.sx, animation.sy, animation.sz, frame, 1D, looping);
        Vector3d rotation = interpolateList(r, animation.rx, animation.ry, animation.rz, frame, 0D, looping);

        scale.sub(1, 1, 1);

        rotation.x *= -1;
        rotation.y *= -1;

        Transform current = group.current;

        current.translate.x += (float) position.x;
        current.translate.y += (float) position.y;
        current.translate.z += (float) position.z;

        current.scale.x += (float) scale.x;
        current.scale.y += (float) scale.y;
        current.scale.z += (float) scale.z;

        current.rotate.x += (float) rotation.x;
        current.rotate.y += (float) rotation.y;
        current.rotate.z += (float) rotation.z;

        /* Compose this action's rotation into the bone's orientation quaternion (evaluated in matrices), so
         * stacked layers don't euler-add through the pole. The euler readback above is kept for gizmo/IK. */
        group.composeOrient(Matrices.toQuaternionZYXDegrees((float) rotation.x, (float) rotation.y, (float) rotation.z));
    }
}
