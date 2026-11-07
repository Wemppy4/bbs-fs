package mchorse.bbs_mod.ui.film.controller;

import java.util.Map;
import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
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

    /*
     * The tick the film last put its actors at, and the one the anchor last saw. The pose follows
     * the cursor in the very frame it moves, but the actors take their place only on the film's
     * next tick (see UIFilmController#update), up to a tick later - so a jump in time reaches the
     * anchor in two steps, and the second one is time going by just as much as the first.
     */
    private int placedTick;
    private int anchorPlacedTick;

    /*
     * The body the anchor last read, and whether a new one is still waiting for the film's next
     * tick. Rebuilding the actors (a restart on seek, an undo, toggling a replay) swaps the body
     * under the same subject, and until the film has placed it once its place says nothing: a
     * body seen standing elsewhere was neither edited nor taken there by time, and whichever of
     * the two the orbit took it for, the camera stayed behind by that much for good.
     */
    private IEntity anchorEntity;
    private boolean awaitingPlacement;

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

    /* The wall cut */

    /** Whether the blocks between the camera and the orbit centre are left out of the picture. */
    public boolean isCuttingWalls()
    {
        return BBSSettings.editorOrbitWallCut.get();
    }

    public void toggleWallCut()
    {
        BBSSettings.editorOrbitWallCut.set(!BBSSettings.editorOrbitWallCut.get());
    }

    /** How many blocks in front of the orbit centre are still drawn. */
    public float getWallCutDepth()
    {
        return BBSSettings.editorOrbitWallCutDepth.get();
    }

    public void setWallCutDepth(float depth)
    {
        BBSSettings.editorOrbitWallCutDepth.set(depth);
    }

    /** The point the cut is measured from, right now. */
    public Vector3d getWallCutCenter()
    {
        return this.getOrbitCenter(this.getCurrentTransition());
    }

    /** The cut is armed only for the frames the orbit places the camera, so it never outlives it. */
    @Override
    public void setup(Camera camera, float transition)
    {
        super.setup(camera, transition);

        if (this.isCuttingWalls())
        {
            Vector3d center = this.getOrbitCenter(transition);

            if (center != null)
            {
                BBSRendering.setTerrainCut(center, this.getWallCutDepth());
            }
        }

        this.traceCamera(camera, transition, "");
    }

    @Override
    public void follow(Camera camera, float transition)
    {
        super.follow(camera, transition);

        this.traceCamera(camera, transition, "F");
    }

    /* TEMPORARY diagnostics (OrbitTrace): remove with it */

    private final OrbitTrace trace = new OrbitTrace();
    private boolean traceFallback;

    @Override
    protected void noteInput(String what)
    {
        if (this.trace != null)
        {
            this.trace.input(what);
        }
    }

    private void traceAnchor(Subject subject, String key, boolean keyChanged, boolean moved, boolean follow, boolean timeMoved, boolean placedMoved, Vector3d position, Quaternionf rotation, Matrix4f frame, float time, boolean turning)
    {
        if (this.trace == null)
        {
            return;
        }

        String decision = keyChanged ? "KEY" : this.awaitingPlacement ? "HOLD" : !moved ? "-" : follow ? "FOLLOW" : "REBASE";
        String why = (timeMoved ? "t" : "") + (placedMoved ? "p" : "") + (this.controller.isControlling() ? "h" : "") + (this.controller.isRecording() ? "r" : "");
        float dot = Math.abs(rotation.x * this.anchorRotation.x + rotation.y * this.anchorRotation.y + rotation.z * this.anchorRotation.z + rotation.w * this.anchorRotation.w);
        float dr = (float) Math.toDegrees(2D * Math.acos(Math.min(1F, dot)));
        String name = subject == null ? "none" : (subject.path() == null ? "actor" : subject.path()) + (turning ? "+turn" : "");
        float det = frame == null ? 0F : frame.determinant3x3();
        float sx = frame == null ? 0F : (float) Math.sqrt(frame.m00() * frame.m00() + frame.m01() * frame.m01() + frame.m02() * frame.m02());
        float sy = frame == null ? 0F : (float) Math.sqrt(frame.m10() * frame.m10() + frame.m11() * frame.m11() + frame.m12() * frame.m12());
        float sz = frame == null ? 0F : (float) Math.sqrt(frame.m20() * frame.m20() + frame.m21() * frame.m21() + frame.m22() * frame.m22());

        this.trace.anchor(decision, why, position.distance(this.anchorPosition), dr, time, this.placedTick, name, this.traceFallback, det, sx, sy, sz);
    }

    private void traceCamera(Camera camera, float transition, String extra)
    {
        if (this.trace == null)
        {
            return;
        }

        Vector3f subject = this.getSubjectPivot(transition);
        Vector3f local = null;

        if (subject != null)
        {
            Quaternionf turn = new Quaternionf(this.anchorRotation).mul(orbitRotation(this.rotation.y, this.rotation.x));

            local = turn.transformInverse(subject.sub((float) camera.position.x, (float) camera.position.y, (float) camera.position.z));
        }

        String flags = (this.isAttached() ? "A" : "") + (this.isTurningWithAnchor() ? "W" : "")
            + (this.controller.isPlaying() ? "P" : "") + (this.controller.isControlling() ? "H" : "")
            + (this.controller.isRecording() ? "R" : "") + extra;
        float smoothing = this.pivot.distance(this.targetPivot) + Math.abs(this.rotation.x - this.targetRotation.x) + Math.abs(this.rotation.y - this.targetRotation.y);

        this.trace.camera(local, flags, this.distance, smoothing);
    }

    /**
     * The camera rides along only as time goes by - playback, scrubbing, a jump to a keyframe -
     * or as the actor is steered live. An edit on the same frame (a gizmo drag, a controller
     * handle, a typed value, an undo) moves the subject under a camera that stays where it is:
     * the orbit is rebased instead, so it goes on from the new place without a jump.
     *
     * <p>Time moves the actor's place a tick later than its pose (see {@link #placedTick}).
     * Taking that late step for an edit left the camera where the jump started, and the subject
     * walked out of the middle of the screen by however far it had gone.</p>
     *
     * <p>A body the actors were just rebuilt with waits for the film's next tick
     * (see {@link #awaitingPlacement}): the anchor holds until then and judges the place it gets.</p>
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
        boolean timeMoved = time != this.anchorTime;
        boolean placedMoved = this.placedTick != this.anchorPlacedTick;
        boolean follow = timeMoved || placedMoved || this.controller.isControlling() || this.controller.isRecording();

        this.anchorTime = time;
        this.anchorPlacedTick = this.placedTick;

        Matrix4f frame = null;

        this.traceFallback = false;

        if (subject != null)
        {
            frame = this.getFrame(subject, transition);
            Vector3f translation = frame.getTranslation(new Vector3f());

            position.set(translation.x, translation.y, translation.z);

            if (turning)
            {
                frame.getNormalizedRotation(rotation);
            }
        }

        boolean moved = !position.equals(this.anchorPosition) || !rotation.equals(this.anchorRotation);
        boolean keyChanged = !Objects.equals(key, this.anchorKey);
        IEntity entity = subject == null ? null : subject.entity();

        /* A new subject is a rebase whatever its body, so only the same one can be waiting. */
        this.awaitingPlacement = !keyChanged && (this.awaitingPlacement || entity != this.anchorEntity);
        this.anchorEntity = entity;

        this.traceAnchor(subject, key, keyChanged, moved, follow, timeMoved, placedMoved, position, rotation, frame, time, turning);

        if (this.awaitingPlacement)
        {
            return;
        }

        if (keyChanged || (moved && !follow))
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

        this.traceFallback = subject.path() != null;

        IEntity entity = subject.entity();
        FormRenderer renderer = FormUtilsClient.getRenderer(entity.getForm());

        matrix = FilmMatrices.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);

        if (renderer != null)
        {
            matrix.mul(renderer.createMatrix(renderer.createTransform()));
        }

        return MatrixStackUtils.stripScale(matrix);
    }

    /** The film has just put its actors at the cursor's tick. */
    public void markActorsPlaced()
    {
        this.placedTick = this.controller.panel.getCursor();
        this.awaitingPlacement = false;
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

        this.noteInput("reset");
        this.anchorKey = null;
        this.anchorReplay = null;
        this.anchorTime = Float.NaN;
        this.anchorEntity = null;
        this.awaitingPlacement = false;
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
