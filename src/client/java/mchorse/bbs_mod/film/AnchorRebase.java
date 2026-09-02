package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Moving an anchor onto another target without moving the form.
 *
 * <p>An anchor's transform lives in its target's frame ({@code target · transform}, see
 * {@link FilmMatrices#getAnchorMatrix}), so changing the target normally throws the form wherever
 * that frame happens to be. Keeping it in place is a matter of rebasing that transform:</p>
 *
 * <pre>transform = target(new)⁻¹ · world(old)</pre>
 *
 * <p>Both sides come out of {@link FilmMatrices} rather than being composed here, which is the
 * whole point — the parent frame an anchor resolves to depends on the target's pose, on its own
 * anchor chain and on which components this anchor inherits, and a second implementation of that
 * would drift from the one the renderer uses. So the answer is measured, not derived.</p>
 *
 * <p>Measured <em>at one moment</em>, though: everything above is the target's pose at the film's
 * current frame, the only pose the live entities can be asked about. An anchor whose transform is
 * animated is rebased for the frame under the cursor and stays approximate everywhere else.</p>
 */
public class AnchorRebase
{
    /**
     * Rebase {@code to}'s transform so a form hanging off it sits exactly where {@code from} had
     * it at this moment. Returns whether it could — a replay in relative mode ignores anchors
     * altogether, and a target squashed to zero scale has no frame to invert.
     *
     * <p>Neither anchor is read for its interpolation state ({@code previous}/{@code x}): both
     * sides are measured as the settled value, so a rebase performed mid-blend still answers about
     * the anchors themselves rather than about the crossfade between them.</p>
     */
    public static boolean keepWorldTransform(Map<String, IEntity> entities, IEntity entity, Replay replay, float transition, Anchor from, Anchor to)
    {
        if (replay != null && replay.relative.get())
        {
            return false;
        }

        Anchor parent = to.copy();

        parent.transform.identity();

        /* Both frames are asked for against the world origin rather than the camera: the camera
         * offset is the same term on both sides and cancels in the product below. */
        Matrix4f world = FilmMatrices.getAnchorMatrix(entities, entity, replay, 0D, 0D, 0D, transition, from.copy());
        Matrix4f target = FilmMatrices.getAnchorMatrix(entities, entity, replay, 0D, 0D, 0D, transition, parent);

        if (world == null || target == null || Math.abs(target.determinant()) < 1E-9F)
        {
            return false;
        }

        to.transform.fromMatrix(target.invert().mul(world));

        return true;
    }
}
