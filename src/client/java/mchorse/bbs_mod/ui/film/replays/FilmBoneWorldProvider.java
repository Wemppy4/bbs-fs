package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.utils.IWorldTransformProvider;
import mchorse.bbs_mod.utils.Pair;
import org.joml.Matrix4f;

/**
 * World-transform source for the film's pose editor: the edited bone's absolute world matrix,
 * so the world paste can drive the pose toward a captured matrix by finite differences.
 *
 * <p>🔴 Sampled with the camera at the ORIGIN, so the matrix is absolute and stable across ticks
 * — the whole point of a world-space paste. The (possibly perturbed) keyframe pose is
 * force-applied first, like the gizmo sampler does, or a nudge would not show up in the sample.
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
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;
        Pair<String, TransformSpace> bone = keyframeEditor == null ? null : keyframeEditor.getBone();

        return bone != null && this.getWorldMatrix(bone.a, out);
    }

    /** The sample itself, by bone path — split out so the lookup above reads clean. */
    private boolean getWorldMatrix(String bone, Matrix4f out)
    {
        UIReplaysEditor replayEditor = this.panel.replayEditor;
        Replay replay = replayEditor.getReplay();
        IEntity entity = this.panel.getController().getCurrentEntity();

        if (bone == null || replay == null || entity == null)
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

        Matrix4f matrix = FilmMatrices.getBoneCompositeMatrix(
            this.panel.getController().getEntities(), entity, replay, 0D, 0D, 0D, transition, bone, true
        );

        if (matrix == null)
        {
            return false;
        }

        out.set(matrix);

        return true;
    }
}
