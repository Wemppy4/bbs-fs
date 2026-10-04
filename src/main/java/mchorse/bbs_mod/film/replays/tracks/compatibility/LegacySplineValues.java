package mchorse.bbs_mod.film.replays.tracks.compatibility;

import mchorse.bbs_mod.cubic.spline.SplineControls;
import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.cubic.spline.SplinePoint;
import mchorse.bbs_mod.cubic.spline.ValueSplineIKs;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Evaluate old independent curves once per playback pass, outside ordinary property reads. */
public final class LegacySplineValues
{
    private LegacySplineValues() {}

    @com.github.bsideup.jabel.Desugar
private record Overlay(SplineControls base, SplineControls applied) {}
    private static final Map<ModelForm, Overlay> overlays = new WeakHashMap<>();

    public static Set<ModelForm> begin(Form root, Iterable<TrackId> tracks)
    {
        Set<ModelForm> forms = new HashSet<>();
        for (TrackId track : tracks)
        {
            if (!track.is(TrackKind.PROPERTY)) continue;
            boolean legacy = track.subject().startsWith("splines/");
            if (!legacy && !track.subject().equals("spline_ik")) continue;
            if (!(FormUtils.getForm(root, track.formPath()) instanceof ModelForm form)) continue;
            if (!legacy && !overlays.containsKey(form)) continue;
            if (!forms.add(form)) continue;
            Overlay overlay = overlays.remove(form);
            if (overlay != null && form.splineIK.getRuntimeValue() == overlay.applied)
                form.splineIK.setRuntimeValue(overlay.base);
        }
        return forms;
    }

    public static void finish(Set<ModelForm> forms)
    {
        for (ModelForm form : forms)
        {
            SplineControls base = form.splineIK.getRuntimeValue();
            SplineControls state = form.splineIK.get();
            SplineControls result = resolve(form.splines, state);
            if (result == state) continue;
            form.splineIK.setRuntimeValue(result);
            overlays.put(form, new Overlay(base, form.splineIK.getRuntimeValue()));
        }
    }

    private static SplineControls resolve(ValueSplineIKs rig, SplineControls state)
    {
        SplineControls result = state;
        for (SplineIK chain : rig.getAllTyped())
        {
            Float influence = chain.influence.getRuntimeValue();
            Float progress = chain.progress.getRuntimeValue();
            Float twist = chain.twist.getRuntimeValue();
            if (influence != null || progress != null || twist != null)
            {
                if (result == state) result = state.copy();
                var control = result.get(chain.getId());
                if (influence != null) control.influence = influence;
                if (progress != null) control.progress = progress;
                if (twist != null) control.twist = twist;
            }
            for (SplinePoint point : chain.points.getAllTyped())
            {
                if (point.position.getRuntimeValue() == null) continue;
                if (result == state) result = state.copy();
                result.get(chain.getId()).point(point.getId()).copy(point.position.getRuntimeValue());
            }
        }
        return result;
    }
}
