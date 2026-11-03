package mchorse.bbs_mod.ui.film.controller;

import java.util.Map;
import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.camera.OrbitViewportController;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Lerps;

/**
 * The film's orbit: what it turns around is what is selected - the bone picked in the editor,
 * else the body part open on the timeline, else the selected replay as a whole - and, while
 * attached, the orbit hangs off that same thing, so the camera rides along as it moves.
 * Everything about flying the viewport itself lives in {@link OrbitViewportController}.
 */
public class OrbitFilmCameraController extends OrbitViewportController
{
    private final UIFilmController controller;

    /*
     * When attached, pivot and rotation are stored relative to the subject's frame: where it
     * is, and, when the orbit turns with it, which way it is turned. World values are composed
     * on the fly. Detached, the frame is identity, so the same math passes world values through
     * untouched. Rebasing between frames preserves the world state, so attaching, detaching,
     * picking another bone or switching replays never moves the camera.
     * Both switches are global settings, so they survive restarts.
     */
    private String anchorKey;
    private Replay anchorReplay;
    private float anchorTime = Float.NaN;

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

    /* What it turns around */

    /**
     * What is selected in the replay editor: the body part the timeline shows, else the replay
     * itself ({@code path} null). A part counts only below the form's root - the root is the
     * replay. A bone picked in a pose doesn't count: the centre would jump with every pick.
     */
    private record Subject(Replay replay, IEntity entity, String path)
    {
        public String key(boolean turning)
        {
            return this.replay.getId() + "|" + this.path + "|" + turning;
        }
    }

    private Subject getSubject()
    {
        Replay replay = this.controller.panel.replayEditor.getReplay();
        IEntity entity = replay == null ? null : this.resolveEntity(replay);

        if (entity == null)
        {
            return null;
        }

        String part = this.controller.panel.replayEditor.getActiveBodyPart();

        return new Subject(replay, entity, part.isEmpty() ? null : part);
    }

    @Override
    protected Vector3f getSubjectPivot(float transition)
    {
        Subject subject = this.getSubject();

        if (subject == null)
        {
            return null;
        }

        Vector3f center = this.getSelectionCenter(subject, transition);

        if (center != null)
        {
            return center;
        }

        Vector3d target = this.getPoseCenter(subject.entity(), "", transition);

        return new Vector3f((float) target.x, (float) target.y, (float) target.z);
    }

    /** A body part is turned around and zoomed into; the replay as a whole leaves that to the pivot. */
    @Override
    protected Vector3f getTurnCenter(float transition)
    {
        Subject subject = this.getSubject();

        return subject == null ? null : this.getSelectionCenter(subject, transition);
    }

    /** The marker shows what the camera turns around, which is the selection when there is one. */
    @Override
    public Vector3d getOrbitCenter(float transition)
    {
        Vector3f center = this.getTurnCenter(transition);

        return center == null ? super.getOrbitCenter(transition) : new Vector3d(center);
    }

    /** The middle of the selected body part; null for the replay itself. */
    private Vector3f getSelectionCenter(Subject subject, float transition)
    {
        if (subject.path() == null)
        {
            return null;
        }

        Vector3d center = this.getPoseCenter(subject.entity(), subject.path(), transition);

        return center == null ? null : new Vector3f((float) center.x, (float) center.y, (float) center.z);
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

    /* What it hangs off */

    public boolean isAttached()
    {
        return BBSSettings.editorOrbitAttached.get();
    }

    public void toggleAttachment()
    {
        BBSSettings.editorOrbitAttached.set(!BBSSettings.editorOrbitAttached.get());

        this.updateAnchor(this.getCurrentTransition());
    }

    /** Whether the attached orbit turns with what it hangs off, or only goes where it goes. */
    public boolean isTurningWithAnchor()
    {
        return BBSSettings.editorOrbitTurnsWithAnchor.get();
    }

    public void toggleTurningWithAnchor()
    {
        BBSSettings.editorOrbitTurnsWithAnchor.set(!BBSSettings.editorOrbitTurnsWithAnchor.get());

        this.updateAnchor(this.getCurrentTransition());
    }

    /**
     * The camera rides along only as time goes by - playback, scrubbing, a jump to a keyframe -
     * or as the actor is steered live. An edit on the same frame (a gizmo drag, a controller
     * handle, a typed value, an undo) moves the subject under a camera that stays where it is:
     * the orbit is rebased instead, so it goes on from the new place without a jump.
     */
    @Override
    protected void updateAnchor(float transition)
    {
        Subject subject = null;

        if (this.isAttached())
        {
            subject = this.getSubject();

            if (subject == null && this.anchorReplay != null)
            {
                /* The selected replay isn't in the scene right now: stay with the last one. */
                IEntity entity = this.resolveEntity(this.anchorReplay);

                subject = entity == null ? null : new Subject(this.anchorReplay, entity, null);
            }
        }

        boolean turning = this.isTurningWithAnchor();
        String key = subject == null ? null : subject.key(turning);
        Vector3d position = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        float time = this.controller.panel.getCursor(transition);
        boolean follow = time != this.anchorTime || this.controller.isControlling() || this.controller.isRecording();

        this.anchorTime = time;

        if (subject != null)
        {
            Matrix4f frame = this.getFrame(subject, transition);
            Vector3f translation = frame.getTranslation(new Vector3f());

            position.set(translation.x, translation.y, translation.z);

            if (turning)
            {
                frame.getNormalizedRotation(rotation);
            }
        }

        boolean moved = !position.equals(this.anchorPosition) || !rotation.equals(this.anchorRotation);

        if (!Objects.equals(key, this.anchorKey) || (moved && !follow))
        {
            this.anchorKey = key;
            this.anchorReplay = subject == null ? null : subject.replay();

            if (moved)
            {
                this.rebaseAnchor(() ->
                {
                    this.anchorPosition.set(position);
                    this.anchorRotation.set(rotation);
                });
            }
        }
        else if (moved)
        {
            this.anchorPosition.set(position);
            this.anchorRotation.set(rotation);
        }
    }

    /**
     * The frame the camera hangs off, scale taken out. A body part gives its own frame, where
     * its gizmo is placed. The replay gives where it stands and which way it faces,
     * its own transform folded in - so a form moved or turned by its transform (keyframed or
     * not) carries the camera along instead of sliding out from under it.
     */
    private Matrix4f getFrame(Subject subject, float transition)
    {
        Matrix4f matrix = subject.path() == null ? null : this.getBoneFrame(subject, transition);

        if (matrix != null)
        {
            return matrix;
        }

        IEntity entity = subject.entity();
        FormRenderer renderer = FormUtilsClient.getRenderer(entity.getForm());

        matrix = FilmMatrices.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);

        if (renderer != null)
        {
            matrix.mul(renderer.createMatrix(renderer.createTransform()));
        }

        return MatrixStackUtils.stripScale(matrix);
    }

    private Matrix4f getBoneFrame(Subject subject, float transition)
    {
        return FilmMatrices.getGizmoBoneCompositeMatrix(this.controller.getEntities(), subject.entity(), subject.replay(), 0D, 0D, 0D, transition, subject.path(), true);
    }

    private IEntity resolveEntity(Replay replay)
    {
        return this.controller.getEntities().get(replay.getId());
    }

    @Override
    public void reset()
    {
        super.reset();

        this.anchorKey = null;
        this.anchorReplay = null;
        this.anchorTime = Float.NaN;
    }

    /**
     * Where the replay's model, or one of its body parts, actually is: the middle of the pose
     * it is standing in, carried into the world by the replay's own frame (so a form hung off
     * another replay is found where it is drawn).
     *
     * <p>The middle is read from the frames themselves and from nothing else - not from the
     * picking hitbox, which is a separate number the author sets by hand and which says nothing
     * about a form that never declared one, and not from the anchor bone, whose pivot sits
     * wherever the model editor left it (for a rig anchored at the waist, already half way up;
     * for a head, at the neck rather than at the eyes worn on it).
     * The pose is the one thing that always describes the form as it is right now.</p>
     *
     * @param part the body part's path, or empty for the whole form
     * @return null only for a body part that isn't in the form; the whole form falls back to
     *         where the replay stands
     */
    private Vector3d getPoseCenter(IEntity entity, String part, float transition)
    {
        Form form = entity.getForm();
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition);
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

        if (form != null)
        {
            /* Shared with the anchor resolution below (nothing between them touches the pose): a form
             * anchored within its own tree would otherwise evaluate the same pose twice. */
            FormFrameCache frame = new FormFrameCache();
            MatrixCache map = FormFrameCache.collect(frame, form, entity, transition);
            Vector3f center = this.getPoseCenter(map, part);

            if (center == null && !part.isEmpty())
            {
                return null;
            }

            if (center != null)
            {
                Anchor v = FilmMatrices.getAnchor(entity);
                Matrix4f defaultMatrix = FilmMatrices.getMatrixForRenderWithRotation(entity, x, y, z, transition);
                Pair<Matrix4f, Float> totalMatrix = FilmMatrices.getTotalMatrix(this.controller.getEntities(), v, defaultMatrix, x, y, z, transition, 0, false, frame);

                if (totalMatrix.a != null)
                {
                    defaultMatrix = totalMatrix.a;
                }

                defaultMatrix.transformPosition(center);

                x += center.x;
                y += center.y;
                z += center.z;
            }
        }

        return new Vector3d(x, y, z);
    }

    private Vector3f getPoseCenter(MatrixCache map, String part)
    {
        String prefix = part + "/";
        Vector3f min = null;
        Vector3f max = null;

        for (Map.Entry<String, MatrixCacheEntry> entry : map.entrySet())
        {
            Matrix4f matrix = entry.getValue().matrix();
            String key = entry.getKey();

            if (matrix == null || (part.isEmpty() ? key.isEmpty() : !key.startsWith(prefix)))
            {
                continue;
            }

            Vector3f point = matrix.getTranslation(new Vector3f());

            if (min == null)
            {
                min = new Vector3f(point);
                max = new Vector3f(point);
            }
            else
            {
                min.min(point);
                max.max(point);
            }
        }

        if (min == null)
        {
            Matrix4f root = map.get(part).matrix();

            return root == null ? null : root.getTranslation(new Vector3f());
        }

        return min.add(max).mul(0.5F);
    }
}
