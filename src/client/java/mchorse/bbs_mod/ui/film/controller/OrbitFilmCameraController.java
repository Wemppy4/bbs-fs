package mchorse.bbs_mod.ui.film.controller;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.camera.OrbitViewportController;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;

/**
 * The film's orbit: what it turns around is the selected replay, and — while attached — the
 * orbit hangs off that replay's own frame, so the camera rides along as the actor moves and
 * turns. Everything about flying the viewport itself lives in {@link OrbitViewportController}.
 */
public class OrbitFilmCameraController extends OrbitViewportController
{
    private final UIFilmController controller;

    /*
     * When attached, pivot and rotation are stored relative to the selected
     * replay's anchor (where the replay is and which way it faces, its own
     * transform included), and world values are composed on the fly. Detached,
     * the anchor is identity, so the same math passes world values through
     * untouched. Rebasing between anchors preserves the world state, so
     * attaching, detaching and switching replays never moves the camera.
     */
    private boolean attached = true;
    private Replay anchorReplay;

    public OrbitFilmCameraController(UIFilmController controller)
    {
        super();

        this.controller = controller;
    }

    /* What the viewport is */

    @Override
    protected UIContext getContext()
    {
        return this.controller.getContext();
    }

    @Override
    protected Area getViewport()
    {
        return this.controller.panel.preview.getViewport();
    }

    @Override
    protected Camera getViewportCamera()
    {
        return this.controller.panel.getCamera();
    }

    @Override
    protected float getSpeed()
    {
        return this.controller.panel.dashboard.orbit.getSpeed();
    }

    /**
     * Flight moves the camera itself, so WASD walks the pivot only when it is off - and by
     * default not even then: walking the viewport is what flight is for, and the setting keeps
     * WASD out of the orbit until it is asked for.
     */
    @Override
    protected boolean canMove()
    {
        return !this.controller.panel.isFlying() && !BBSSettings.editorOrbitMovementRequiresFlight.get();
    }

    /** While flying, the wheel is the flight camera's speed dial, not the orbit's zoom. */
    @Override
    protected boolean canZoom()
    {
        return !this.controller.panel.isFlying();
    }

    /** While flying, every button belongs to the flight camera: free look, roll and FOV. */
    @Override
    protected boolean canStart(UIContext context)
    {
        return !this.controller.panel.isFlying() && super.canStart(context);
    }

    /* The replay it turns around */

    @Override
    protected Vector3f getSubjectPivot(float transition)
    {
        Vector3d target = this.getOrbitTarget(transition);

        return target == null ? null : new Vector3f((float) target.x, (float) target.y, (float) target.z);
    }

    @Override
    protected boolean hasSubject()
    {
        return this.controller.panel.getData() != null && !this.controller.panel.getData().replays.getList().isEmpty();
    }

    /** Kept for the call sites that speak of replays; the orbit itself only knows subjects. */
    public void teleportPivotToReplay()
    {
        this.teleportPivotToSubject();
    }

    /* The replay it hangs off */

    public boolean isAttached()
    {
        return this.attached;
    }

    public void toggleAttachment()
    {
        this.attached = !this.attached;

        this.updateAnchor(this.getCurrentTransition());
    }

    @Override
    protected void updateAnchor(float transition)
    {
        Replay target = null;
        IEntity entity = null;

        if (this.attached)
        {
            target = this.controller.panel.replayEditor.getReplay();
            entity = target == null ? null : this.resolveEntity(target);

            if (entity == null)
            {
                target = this.anchorReplay;
                entity = target == null ? null : this.resolveEntity(target);
            }

            if (entity == null)
            {
                target = null;
            }
        }

        if (target != this.anchorReplay)
        {
            this.rebase(target, entity, transition);
        }
        else if (entity != null)
        {
            this.writeAnchor(entity, transition);
        }
    }

    private void rebase(Replay replay, IEntity entity, float transition)
    {
        this.rebaseAnchor(() ->
        {
            this.anchorReplay = replay;

            if (entity == null)
            {
                this.anchorPosition.set(0D, 0D, 0D);
                this.anchorYaw = 0F;
            }
            else
            {
                this.writeAnchor(entity, transition);
            }
        });
    }

    /**
     * The frame the camera hangs off: where the replay stands and which way it faces, its own
     * transform folded in - so a form moved or turned by its transform (keyframed or not)
     * carries the camera along instead of sliding out from under it.
     *
     * <p>Only the turn about the vertical is taken. The pivot and the rotation are stored in
     * this frame and the camera has no roll of its own, so a form tipped on its side would tip
     * the horizon with it; following the yaw is what "the camera stays with the actor" means,
     * and the horizon staying level is what every other frame of this editor promises.</p>
     */
    private void writeAnchor(IEntity entity, float transition)
    {
        Matrix4f matrix = FilmMatrices.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);
        FormRenderer renderer = FormUtilsClient.getRenderer(entity.getForm());

        if (renderer != null)
        {
            matrix.mul(renderer.createTransform().createMatrix());
        }

        Vector3f translation = matrix.getTranslation(new Vector3f());
        Matrix4f axes = MatrixStackUtils.stripScale(matrix);

        this.anchorPosition.set(translation.x, translation.y, translation.z);
        this.anchorYaw = (float) Math.atan2(-axes.m02(), axes.m00());
    }

    private IEntity resolveEntity(Replay replay)
    {
        return this.controller.getEntities().get(replay.getId());
    }

    @Override
    public void reset()
    {
        super.reset();

        this.anchorReplay = null;
    }

    /**
     * Where the replay's model actually is: the point its anchor bone stands at (so a form
     * hung off another replay is found where it is drawn), raised to the middle of the
     * picking hitbox. The bone alone is usually at the actor's feet, and an orbit around the
     * feet swings the whole body through the frame.
     */
    private Vector3d getOrbitTarget(float transition)
    {
        IEntity entity = this.controller.getCurrentEntity();

        if (entity == null)
        {
            return null;
        }

        Form form = entity.getForm();
        double h = entity.getPickingHitbox().h / 2;
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition);
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

        if (form != null)
        {
            /* Shared with the anchor resolution below (nothing between them touches the pose): a form
             * anchored within its own tree would otherwise evaluate the same pose twice. */
            FormFrameCache frame = new FormFrameCache();
            MatrixCache map = FormFrameCache.collect(frame, form, entity, transition);
            String group = "anchor";

            if (form instanceof ModelForm modelForm)
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    String anchor = model.getAnchor();

                    group = anchor.isEmpty() ? group : anchor;
                }
            }

            Matrix4f anchor = map.get(group).matrix();

            if (anchor != null)
            {
                Anchor v = form.anchor.get();
                Matrix4f defaultMatrix = FilmMatrices.getMatrixForRenderWithRotation(entity, x, y, z, transition);
                Pair<Matrix4f, Float> totalMatrix = FilmMatrices.getTotalMatrix(this.controller.getEntities(), v, defaultMatrix, x, y, z, transition, 0, false, frame);

                if (totalMatrix.a != null)
                {
                    defaultMatrix = totalMatrix.a;
                }

                defaultMatrix.mul(anchor);

                Vector3f translate = defaultMatrix.getTranslation(Vectors.TEMP_3F);

                x += translate.x;
                y += translate.y;
                z += translate.z;
            }
        }

        return new Vector3d(x, y + h, z);
    }
}
