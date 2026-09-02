package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.forms.forms.Form;

/**
 * What a track needs besides its own keyframes to be applied: the form its address is relative to,
 * how far into the tick the frame is, and — for the solver tracks — a way to turn an anchor into a
 * world position.
 *
 * @param root       form the track addresses are relative to
 * @param transition fraction of the current tick already played, 0..1
 * @param anchors    resolves an anchor against the film's live entities; null when there is no world
 *                   to point at, which makes the target tracks no-ops rather than a special case at
 *                   every call site
 * @param solvers    whether this pass owns the solver overrides — it drops them at the start of the
 *                   frame (see {@code TrackBehaviours.clearOverrides}) and so is allowed to write
 *                   them. A pass that only lays a form's own values over it (an animation state, a
 *                   preview) says false: nothing would ever clear what it wrote, and the overrides
 *                   would stick for good.
 */
public record TrackContext(Form root, float transition, AnchorResolver anchors, boolean solvers)
{
    /** A pass over a form's own values — properties, bones, materials — with no solver tracks. */
    public static TrackContext of(Form root)
    {
        return new TrackContext(root, 0F, null, false);
    }

    /** A film frame: every kind of track applies, and the solver overrides are this pass's to keep clean. */
    public static TrackContext frame(Form root, float transition, AnchorResolver anchors)
    {
        return new TrackContext(root, transition, anchors, true);
    }
}
