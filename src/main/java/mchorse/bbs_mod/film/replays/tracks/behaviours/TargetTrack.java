package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.tracks.AnchorResolver;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Where a solver chain is pulled to: the IK target, its pole, or a physics chain's tip. The keyframe
 * value is an anchor — some bone of some replay — and the track hands the solver that bone's world
 * position plus how present the binding is.
 *
 * <p>The weight is what a plain anchor interpolation cannot express. Crossing a keyframe that binds
 * to nothing, the anchor's own lerp would drag the resolved matrix towards the world origin and yank
 * the chain to (0,0,0). Instead the bound side is resolved at its full position and the fade becomes
 * a 0..1 weight, so the chain eases in and out from where the bone already is.</p>
 */
public class TargetTrack implements TrackBehaviour
{
    private final TrackKind kind;

    public TargetTrack(TrackKind kind)
    {
        this.kind = kind;
    }

    @Override
    public IKeyframeFactory factory(TrackId track)
    {
        return KeyframeFactories.ANCHOR;
    }

    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        /* Only the pass that clears these overrides may write them. */
        if (!context.solvers())
        {
            return;
        }

        AnchorResolver anchors = context.anchors();

        /* Outside a film there is no world to point at, so the target tracks simply do not apply. */
        if (anchors == null || !(FormUtils.getForm(context.root(), track.formPath()) instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment<?> segment = channel.find(tick);

        if (segment == null || !(segment.createInterpolated() instanceof Anchor anchor))
        {
            return;
        }

        Anchor bound;
        float weight;

        if (anchor.previous != null && anchor.isFadeIn())
        {
            bound = anchor.copy();
            weight = anchor.x;
        }
        else if (anchor.previous != null && anchor.isFadeOut())
        {
            bound = anchor.previous;
            weight = 1F - anchor.x;
        }
        else
        {
            bound = anchor;
            weight = 1F;
        }

        if (weight <= 0F || bound.replay == Anchor.NO_ATTACHMENT)
        {
            return;
        }

        Vector3f position = anchors.resolve(bound, context.transition());

        if (position == null)
        {
            return;
        }

        this.positions(modelForm).computeIfAbsent(track.subject(), (k) -> new Vector3f()).set(position);
        this.weights(modelForm).put(track.subject(), weight);
    }

    @Override
    public void clear(ModelForm form)
    {
        this.positions(form).clear();
        this.weights(form).clear();
    }

    private Map<String, Vector3f> positions(ModelForm form)
    {
        return switch (this.kind)
        {
            case IK_TARGET -> form.ikTargetOverrides;
            case POLE_TARGET -> form.poleTargetOverrides;
            default -> form.physicsTargetOverrides;
        };
    }

    private Map<String, Float> weights(ModelForm form)
    {
        return switch (this.kind)
        {
            case IK_TARGET -> form.ikTargetWeights;
            case POLE_TARGET -> form.poleTargetWeights;
            default -> form.physicsTargetWeights;
        };
    }
}
