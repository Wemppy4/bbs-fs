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
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.Map;

/**
 * The solver scalars of a whole form — one track each for IK, physics and wind. Their keyframe value
 * is a container of the per-chain settings (or, for wind, the one global set), laid over the form's
 * own IK/physics config for the frame.
 *
 * <p>All three write into transient override maps and never remove from them, so they are dropped
 * wholesale by {@link #clear} at the start of every frame: a track that was deleted, or whose
 * keyframes ran out, must stop driving the form.</p>
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
        /* Only the pass that clears these overrides may write them. */
        if (!context.solvers())
        {
            return;
        }

        if (!(FormUtils.getForm(context.root(), track.formPath()) instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment<?> segment = channel.find(tick);

        if (segment == null)
        {
            return;
        }

        Object value = segment.createInterpolated();

        if (value instanceof IKControls controls)
        {
            for (Map.Entry<String, IKControl> entry : controls.controls.entrySet())
            {
                modelForm.ikControlOverrides.computeIfAbsent(entry.getKey(), (k) -> new IKControl()).copy(entry.getValue());
            }
        }
        else if (value instanceof PhysicsControls controls)
        {
            for (Map.Entry<String, PhysicsControl> entry : controls.controls.entrySet())
            {
                modelForm.physicsControlOverrides.computeIfAbsent(entry.getKey(), (k) -> new PhysicsControl()).copy(entry.getValue());
            }
        }
        else if (value instanceof WindControl control)
        {
            if (modelForm.windControlOverride == null)
            {
                modelForm.windControlOverride = new WindControl();
            }

            modelForm.windControlOverride.copy(control);
        }
    }

    @Override
    public void clear(ModelForm form)
    {
        switch (this.kind)
        {
            case IK_CONTROLS -> form.ikControlOverrides.clear();
            case PHYSICS_CONTROLS -> form.physicsControlOverrides.clear();
            default -> form.windControlOverride = null;
        }
    }
}
