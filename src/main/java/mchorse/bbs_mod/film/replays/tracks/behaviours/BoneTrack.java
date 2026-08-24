package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBlend;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * One bone of a model form's pose. Its transform is added onto whatever the pose already holds, so
 * a bone track layers over the form's own pose and over the form's whole-pose track — several
 * tracks can drive the same bone and they sum.
 */
public class BoneTrack implements TrackBehaviour
{
    @Override
    public IKeyframeFactory factory(TrackId track)
    {
        return KeyframeFactories.POSE_TRANSFORM;
    }

    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        if (!(FormUtils.getForm(context.root(), track.formPath()) instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment segment = channel.find(tick);

        if (segment == null)
        {
            return;
        }

        /* Copy on write */
        if (modelForm.pose.getRuntimeValue() == null)
        {
            modelForm.pose.setRuntimeValue(modelForm.pose.getOriginalValue().copy());
        }

        PoseTransform transform = modelForm.pose.get().getOrCreate(track.subject());

        transform.add((Transform) TrackBlend.value(channel, new PoseTransform(), segment, blend));
    }

    /*
     * No reset. A bone track ADDS onto the pose, and the pose is copied on write as a whole, so the
     * only way to take one contribution back would be to drop the whole runtime pose — which belongs
     * to whoever else is also driving it. That matters because an animation state releases after
     * EVERY render of the form (see FormRenderer#render), while a film writes its pose once per
     * frame: dropping the pose here would leave the form unposed for every further pass of the same
     * frame — the shadow pass, a second viewport, a preview.
     *
     * Undoing one contribution properly needs the pose to be a stack of contributions rather than one
     * value written in place, which is a separate piece of work.
     */
}
