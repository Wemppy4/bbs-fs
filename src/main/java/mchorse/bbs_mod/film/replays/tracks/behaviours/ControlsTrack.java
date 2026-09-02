package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.Map;

/**
 * The solver scalars of a whole form — one track each for IK, physics and wind. Their keyframe
 * value is a container of the per-chain settings (or, for wind, the one global set); at playback
 * each entry drives the matching bone's own property ({@code ik}/{@code physics}, keyed by the
 * chain's tip/root bone) through its runtime value, which is where the solver reads its scalars.
 *
 * <p>The overrides are laid fresh every frame the track has keyframes and dropped when it runs
 * out (or when an animation state lets go), so a deleted track stops driving the form — no
 * transient override map, no separate clearing pass.</p>
 */
public class ControlsTrack implements TrackBehaviour
{
    private final TrackKind kind;

    public ControlsTrack(TrackKind kind)
    {
        this.kind = kind;
    }

    @Override
    public IKeyframeFactory factory(TrackId track)
    {
        return switch (this.kind)
        {
            case IK_CONTROLS -> KeyframeFactories.IK;
            case PHYSICS_CONTROLS -> KeyframeFactories.PHYSICS;
            default -> KeyframeFactories.WIND;
        };
    }

    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        /* Solver scalars only apply inside a film's frame, the pass their per-frame overrides
         * belong to — a preview or an animation state never drives them. */
        if (!context.solvers())
        {
            return;
        }

        if (!(FormUtils.getForm(context.root(), track.formPath()) instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment<?> segment = channel.find(tick);

        /* Lay the frame's overrides from scratch: drop every override of this kind first, then
         * set the ones the keyframe holds. A chain absent from the keyframe (the union of keys
         * differs between keyframes) falls back to its static scalars, same as before. */
        this.drop(modelForm);

        if (segment == null)
        {
            return;
        }

        Object value = segment.createInterpolated();

        if (value instanceof IKControls controls)
        {
            for (Map.Entry<String, IKControl> entry : controls.controls.entrySet())
            {
                modelForm.bones.getOrCreate(entry.getKey()).ik.setRuntimeValue(entry.getValue());
            }
        }
        else if (value instanceof PhysicsControls controls)
        {
            for (Map.Entry<String, PhysicsControl> entry : controls.controls.entrySet())
            {
                modelForm.bones.getOrCreate(entry.getKey()).physics.setRuntimeValue(entry.getValue());
            }
        }
        else if (value instanceof WindControl control)
        {
            modelForm.wind.setRuntimeValue(control);
        }
    }

    @Override
    public void reset(Form root, TrackId track)
    {
        if (FormUtils.getForm(root, track.formPath()) instanceof ModelForm modelForm)
        {
            this.drop(modelForm);
        }
    }

    /** Drops every runtime override this kind of track leaves on the form's properties. */
    private void drop(ModelForm form)
    {
        if (this.kind == TrackKind.WIND_CONTROLS)
        {
            form.wind.setRuntimeValue(null);

            return;
        }

        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone)
            {
                if (this.kind == TrackKind.IK_CONTROLS)
                {
                    bone.ik.setRuntimeValue(null);
                }
                else
                {
                    bone.physics.setRuntimeValue(null);
                }
            }
        }
    }
}
