package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * One pose evaluation shared for the span of a render pass — the first piece of "the frame is an object".
 *
 * <p>A form's bone matrices are not stored anywhere: {@link mchorse.bbs_mod.cubic.ModelInstance} is a single
 * globally cached asset per model id, and the evaluated pose lives in the asset's own
 * {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#current} fields. So the only way to <em>read</em> a pose
 * is to re-run the whole pipeline (reset &rarr; actions &rarr; pose stack &rarr; IK) via
 * {@link mchorse.bbs_mod.forms.renderers.FormRenderer#collectMatrices(IEntity, float)}, which is what every
 * consumer does today — the anchor chain, the gizmo, the axes preview, trackers, the motion path. In a scene
 * with anchors that is the same full evaluation several times per form per frame, and twice again for the
 * stencil picking pass.</p>
 *
 * <p>This class holds the result of that evaluation so a stretch of code that needs it more than once pays
 * for it once. It is deliberately <b>not</b> an ambient global with a frame counter: the pose is mutable
 * global state that several stages write mid-frame (the animation states applied inside
 * {@link mchorse.bbs_mod.forms.renderers.FormRenderer#render}, the IK/physics target overrides filled by the
 * film controller), so "the last evaluation this frame" is not a well-defined value. Instead a cache is
 * created explicitly for a span in which nothing can mutate the pose — adjacent statements — and passed down
 * as a parameter. Every entry point keeps a cache-less overload that behaves exactly as before, so a caller
 * that has not been reviewed for such a span simply passes {@code null} and re-evaluates.</p>
 *
 * <p>When the pose moves out of the asset and into a per-instance buffer, this is the seam it lands on: the
 * cache becomes the frame's pose storage and the "evaluate" call below becomes a pure function of the
 * document, at which point the pass scoping can widen safely.</p>
 */
public final class FormFrameCache
{
    private final Map<Form, Entry> entries = new IdentityHashMap<>();

    /**
     * Evaluate {@code form} for {@code entity}, reusing {@code cache}'s result when it already holds one for
     * the same form, entity and transition. A {@code null} cache means "no sharing" and evaluates directly,
     * which is the pre-existing behaviour of every call site.
     */
    public static MatrixCache collect(FormFrameCache cache, Form form, IEntity entity, float transition)
    {
        /* The cache-less branch lands on the frame-wide cache, so every caller that never
         * carried an explicit span — the gizmo rotation, tracker frames, the UI's double
         * reads — shares this frame's evaluation for free. See RenderFrame on why that is
         * safe now (the pose version) when it wasn't before. */
        if (cache == null)
        {
            return RenderFrame.collect(form, entity, transition);
        }

        return cache.collect(form, entity, transition);
    }

    public MatrixCache collect(Form form, IEntity entity, float transition)
    {
        Entry entry = this.entries.get(form);

        /* Identity on the entity too: the same form is evaluated against different entities (a body part's
         * render entity, an anchor target), and the pose it produces differs per entity. */
        if (entry != null && entry.entity == entity && Float.compare(entry.transition, transition) == 0)
        {
            return entry.matrices;
        }

        MatrixCache matrices = RenderFrame.collect(form, entity, transition);

        this.entries.put(form, new Entry(entity, transition, matrices));

        return matrices;
    }

    private record Entry(IEntity entity, float transition, MatrixCache matrices)
    {}
}
