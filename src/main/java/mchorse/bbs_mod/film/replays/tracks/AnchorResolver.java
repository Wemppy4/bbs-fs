package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.forms.forms.utils.Anchor;
import org.joml.Vector3f;

/**
 * Turns an anchor (a bone of some other replay) into a world position.
 *
 * <p>The only thing the solver target tracks need that lives outside the data: resolving one means
 * walking the film's live entities and composing their bone matrices, which is the renderer's job.
 * Behind this interface the tracks themselves stay plain data behaviour.</p>
 */
public interface AnchorResolver
{
    /** World position the anchor points at, or null when it points at nothing right now. */
    public Vector3f resolve(Anchor anchor, float transition);
}
