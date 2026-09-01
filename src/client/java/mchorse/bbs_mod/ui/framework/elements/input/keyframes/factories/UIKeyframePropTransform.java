package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Delta transform editor whose selection spans keyframes, plus the auto-keyframing layer: while
 * auto-keyframing is on the fields mirror the keyframe at the playhead ({@link
 * #getAutoKeyTransform}) instead of the edited one, so a drag is measured against the pose that is
 * actually on screen at that tick.
 *
 * <p>The write itself needs nothing here — every {@link #applyToSelection} implementation goes
 * through {@code UIReplaysEditorUtils.forEachSelectedKeyframe}, which redirects to the playhead's
 * keyframe on its own.
 */
public abstract class UIKeyframePropTransform extends UIDeltaPropTransform
{
    /** The timeline the edited keyframe lives in, so this editor can ask about the playhead. */
    protected abstract UIKeyframes getKeyframes();

    /**
     * The transform of this track's keyframe at the given tick, created if the track has none
     * there. Null when the editor cannot produce one, which leaves the fields on the edited
     * keyframe.
     */
    protected Transform getAutoKeyTransform(int tick)
    {
        return null;
    }

    @Override
    protected Transform getTargetTransform()
    {
        UIKeyframes keyframes = this.getKeyframes();
        Integer tick = keyframes == null ? null : keyframes.getAutoKeyframeTick();

        if (tick != null)
        {
            Transform transform = this.getAutoKeyTransform(tick);

            if (transform != null)
            {
                return transform;
            }
        }

        return this.getTransform();
    }
}
