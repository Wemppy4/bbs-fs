package mchorse.bbs_mod.film;

import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.Axis;
import java.util.EnumSet;

/** Replay placement can edit translation, yaw and pitch, not roll or scale.
 * Shared by the renderer and the future original replay properties panel. */
public final class FilmGizmoMasks
{
    public static final Gizmo.HandleMask REPLAY_TRANSFORM = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN, Gizmo.Op.ROTATE), EnumSet.of(Axis.X, Axis.Y));
    private FilmGizmoMasks() {}
}
