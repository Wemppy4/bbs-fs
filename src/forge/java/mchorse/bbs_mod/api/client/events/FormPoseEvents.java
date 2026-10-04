package mchorse.bbs_mod.api.client.events;

import java.util.Objects;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;

/** Client-thread pose extension points shared by drawing and attachment matrix walks.
 * Never advance a simulation here: a frame may evaluate the same form several times.
 * Matrices supplied to observers are borrowed read-only values. Transform and model pose
 * callbacks may modify their output, but must not edit the form's saved animation.
 * Listeners run in registration order. */
public final class FormPoseEvents
{
    public enum Pass { RENDER, MATRICES }

    /** Mutable local transform, after overlays, before either matrix-stack path uses it. */
    public static final OrderedEvent<TransformPose> TRANSFORM = new OrderedEvent<>(
        listeners -> (form, transform, transition) ->
        { for (TransformPose listener : listeners) listener.apply(form, transform, transition); });

    /** Parent frame before the form's own transform, for each matrix walk (including models). */
    public static final OrderedEvent<ParentFrame> PARENT_FRAME = new OrderedEvent<>(
        listeners -> (form, entity, parent, path, transition) ->
        { for (ParentFrame listener : listeners) listener.capture(form, entity, parent, path, transition); });

    /** After animation and IK, before built-in chain physics in render and bone capture in a walk. */
    public static final OrderedEvent<ModelPose> MODEL_POSE = new OrderedEvent<>(
        listeners -> (form, entity, model, transition, base, pass) ->
        { for (ModelPose listener : listeners) listener.apply(form, entity, model, transition, base, pass); });

    /** A claimed chain is excluded from the built-in solver. Any listener may claim it.
     * Called during compilation; change ownership together with the form's physics settings. */
    public static final OrderedEvent<ChainClaim> CLAIM_CHAIN = new OrderedEvent<>(
        listeners -> (form, model, bone) ->
        {
            for (ChainClaim listener : listeners) if (listener.claims(form, model, bone)) return true;
            return false;
        });

    /** Include externally authored bone offsets when collecting default pivot frames. */
    public static final OrderedEvent<PivotOffsets> PIVOT_OFFSETS = new OrderedEvent<>(
        listeners -> model ->
        {
            for (PivotOffsets listener : listeners) if (listener.include(model)) return true;
            return false;
        });

    /** Resolve a temporary anchor without changing the stored one. Return a non-null anchor. */
    public static final OrderedEvent<ResolveAnchor> ANCHOR = new OrderedEvent<>(
        listeners -> anchor ->
        {
            for (ResolveAnchor listener : listeners) anchor = Objects.requireNonNull(listener.resolve(anchor));
            return anchor;
        });

    /** Actor context is established, before anchors and form transforms are resolved. */
    public static final OrderedEvent<ActorPrepare> ACTOR_BEFORE = new OrderedEvent<>(
        listeners -> context ->
        { for (ActorPrepare listener : listeners) listener.prepare(context); });

    public interface TransformPose { void apply(Form form, Transform transform, float transition); }
    public interface ParentFrame { void capture(Form form, IEntity entity, Matrix4f parent, String path, float transition); }
    public interface ModelPose { void apply(ModelForm form, IEntity entity, ModelInstance model, float transition, Matrix4f base, Pass pass); }
    public interface ChainClaim { boolean claims(ModelForm form, IModel model, FormBone bone); }
    public interface PivotOffsets { boolean include(IModel model); }
    public interface ResolveAnchor { Anchor resolve(Anchor anchor); }
    public interface ActorPrepare { void prepare(FilmControllerContext context); }
    private FormPoseEvents() {}
}
