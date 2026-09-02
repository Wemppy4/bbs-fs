package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBlend;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * One bone's rotation limits — the bone's "constraints" property. Behaves exactly like
 * {@link PropertyTrack}: the track drives the property's runtime value, which the constraints
 * runtime reads each frame; off the end of the track the runtime value is dropped and the bone
 * limits by its own static setting again. The value type is the property's own
 * ({@code BoneConstraint}), so there is no mirror class between the track and the form.
 */
public class BoneConstraintTrack implements TrackBehaviour
{
    @Override
    public IKeyframeFactory factory(TrackId track)
    {
        return KeyframeFactories.BONE_CONSTRAINT;
    }

    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        if (!(FormUtils.getForm(context.root(), track.formPath()) instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment segment = channel.find(tick);

        if (segment != null)
        {
            /* The bone may have never been touched statically — a track alone must still drive it.
             * An all-neutral bone is not persisted, so this leaves no trace in saved data. */
            FormBone bone = modelForm.bones.getOrCreate(track.subject());

            bone.constraints.setRuntimeValue((BoneConstraint) TrackBlend.value(channel, bone.constraints.get(), segment, blend));
        }
        else if (blend >= 1F)
        {
            FormBone bone = modelForm.bones.getBone(track.subject());

            if (bone != null)
            {
                bone.constraints.setRuntimeValue(null);
            }
        }
    }

    @Override
    public void reset(Form root, TrackId track)
    {
        if (FormUtils.getForm(root, track.formPath()) instanceof ModelForm modelForm)
        {
            FormBone bone = modelForm.bones.getBone(track.subject());

            if (bone != null)
            {
                bone.constraints.setRuntimeValue(null);
            }
        }
    }
}
