package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.film.replays.tracks.behaviours.BoneTrack;
import mchorse.bbs_mod.film.replays.tracks.behaviours.ControlsTrack;
import mchorse.bbs_mod.film.replays.tracks.behaviours.MaterialPropTrack;
import mchorse.bbs_mod.film.replays.tracks.behaviours.MaterialTextureTrack;
import mchorse.bbs_mod.film.replays.tracks.behaviours.PropertyTrack;
import mchorse.bbs_mod.film.replays.tracks.behaviours.TargetTrack;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.EnumMap;
import java.util.Map;

/** What each kind of track does — the one table the whole track system asks. */
public class TrackBehaviours
{
    private static final Map<TrackKind, TrackBehaviour> REGISTRY = new EnumMap<>(TrackKind.class);

    static
    {
        REGISTRY.put(TrackKind.PROPERTY, new PropertyTrack());
        REGISTRY.put(TrackKind.BONE, new BoneTrack());
        REGISTRY.put(TrackKind.MATERIAL_TEXTURE, new MaterialTextureTrack());
        REGISTRY.put(TrackKind.MATERIAL_PROP, new MaterialPropTrack());
        REGISTRY.put(TrackKind.IK_TARGET, new TargetTrack(TrackKind.IK_TARGET));
        REGISTRY.put(TrackKind.POLE_TARGET, new TargetTrack(TrackKind.POLE_TARGET));
        REGISTRY.put(TrackKind.PHYSICS_TARGET, new TargetTrack(TrackKind.PHYSICS_TARGET));
        REGISTRY.put(TrackKind.IK_CONTROLS, new ControlsTrack(TrackKind.IK_CONTROLS));
        REGISTRY.put(TrackKind.PHYSICS_CONTROLS, new ControlsTrack(TrackKind.PHYSICS_CONTROLS));
        REGISTRY.put(TrackKind.WIND_CONTROLS, new ControlsTrack(TrackKind.WIND_CONTROLS));
    }

    public static TrackBehaviour of(TrackKind kind)
    {
        return REGISTRY.get(kind);
    }

    public static TrackBehaviour of(TrackId track)
    {
        return track == null ? null : REGISTRY.get(track.kind());
    }

    /**
     * The factory of a track's value, or null for a plain property — that one keys whatever its own
     * value keys, so it comes from the form rather than from the kind.
     */
    public static IKeyframeFactory factory(TrackId track)
    {
        TrackBehaviour behaviour = of(track);

        return behaviour == null ? null : behaviour.factory(track);
    }

    /**
     * Drop every per-frame override the track kinds leave on this form and the forms under it, so
     * the frame about to be applied starts from the form's own configuration. Tracks that were
     * deleted, or whose keyframes have run out, stop driving the form here.
     */
    public static void clearOverrides(Form form)
    {
        if (form == null)
        {
            return;
        }

        if (form instanceof ModelForm modelForm)
        {
            for (TrackBehaviour behaviour : REGISTRY.values())
            {
                behaviour.clear(modelForm);
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            clearOverrides(part.getForm());
        }
    }
}
