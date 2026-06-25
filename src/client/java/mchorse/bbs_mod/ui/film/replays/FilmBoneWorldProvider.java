package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.utils.IWorldTransformProvider;
import mchorse.bbs_mod.utils.Pair;
import org.joml.Matrix4f;

/**
 * World-transform source for the film's pose editor: resolves the currently edited bone and hands
 * back its absolute Minecraft-world matrix for the bone's current pose, so the world paste can drive
 * the pose toward a captured matrix by finite differences.
 *
 * <p>The matrix is sampled with the camera at the origin, so it is in absolute world coordinates —
 * independent of where the camera is and therefore stable across ticks (the whole point of a
 * world-space paste). Before sampling, the (possibly just-perturbed) keyframe pose is force-applied
 * to the model via the replay's properties — exactly like the gizmo sampler in
 * {@code UIReplaysEditorUtils.buildFilmGizmoDrag} — so a nudge to the pose shows up in the next
 * sample. It reuses {@link BaseFilmController#getBoneCompositeMatrix}, the same composite the
 * viewport draws the bone gizmo with, just keeping scale.
 */
public class FilmBoneWorldProvider implements IWorldTransformProvider
{
    private final UIFilmPanel panel;

    public FilmBoneWorldProvider(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    @Override
    public boolean getWorldMatrix(Matrix4f out)
    {
        UIReplaysEditor replayEditor = this.panel.replayEditor;
        UIKeyframeEditor keyframeEditor = replayEditor.keyframeEditor;

        if (keyframeEditor == null)
        {
            return false;
        }

        Pair<String, Boolean> bone = keyframeEditor.getBone();
        Replay replay = replayEditor.getReplay();
        IEntity entity = this.panel.getController().getCurrentEntity();

        if (bone == null || bone.a == null || replay == null || entity == null)
        {
            return false;
        }

        float transition = this.panel.getRunner().isRunning() && replayEditor.getContext() != null
            ? replayEditor.getContext().getTransition()
            : 0F;
        float tick = this.panel.getCursor() + transition;
        Form form = entity.getForm();

        /* Push the (possibly perturbed) pose into the model so the matrix cache reflects it. */
        if (form != null)
        {
            replay.properties.applyProperties(form, tick);
        }

        Matrix4f matrix = BaseFilmController.getBoneCompositeMatrix(
            this.panel.getController().getEntities(), entity, replay, 0D, 0D, 0D, transition, bone.a, true
        );

        if (matrix == null)
        {
            return false;
        }

        out.set(matrix);

        return true;
    }
}
