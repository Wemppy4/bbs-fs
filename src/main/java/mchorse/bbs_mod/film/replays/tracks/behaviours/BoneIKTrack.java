package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBlend;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * One bone's IK scalars — the bone's "ik" property. Behaves exactly like {@link PropertyTrack}:
 * the track drives the property's runtime value, which the IK solve reads each frame; off the
 * end of the track the runtime value is dropped and the chain runs on its static scalars again.
 * The value type is the property's own ({@link IKControl}), so there is no mirror class and no
 * override map between the track and the solver.
 */
public class BoneIKTrack implements TrackBehaviour
{
    @Override
    public IKeyframeFactory factory(TrackId track)
    {
        return KeyframeFactories.BONE_IK;
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
            /* The bone may hold no static chain on this form — a track alone must still drive it.
             * An all-neutral bone is not persisted, so this leaves no trace in saved data. */
            FormBone bone = modelForm.bones.getOrCreate(track.subject());

            bone.ik.setRuntimeValue((IKControl) TrackBlend.value(channel, bone.ik.get(), segment, blend));
        }
        else if (blend >= 1F)
        {
            FormBone bone = modelForm.bones.getBone(track.subject());

            if (bone != null)
            {
                bone.ik.setRuntimeValue(null);
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
                bone.ik.setRuntimeValue(null);
            }
        }
    }
}
