package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.camera.clips.misc.OverlayBox;
import mchorse.bbs_mod.settings.values.core.ValuePlacement;

/**
 * A clip that draws something into the frame at a spot the user can grab.
 *
 * <p>While such a clip is selected and under the timeline's cursor, the preview puts a draggable
 * frame around it — the body moves the placement, the corners scale it. Which clips those are used
 * to be two parallel {@code if/else if} chains naming BBS's three overlay clips, so an addon's
 * overlay could be drawn but never moved by hand.</p>
 */
public interface IPlaceableClip
{
    /**
     * The placement the gizmo edits, or null while there is nothing to move — an overlay filling
     * the whole frame has no placement to speak of.
     */
    public ValuePlacement getPlacement();

    /**
     * Where the overlay actually ended up in the last frame that was drawn.
     *
     * <p>Read, never recomputed: the renderer and the UI run against different framebuffers, so a
     * second opinion here would drift away from what the user is looking at.</p>
     */
    public OverlayBox getOverlayBox();
}
