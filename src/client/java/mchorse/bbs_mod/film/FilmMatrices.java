package mchorse.bbs_mod.film;

import mchorse.bbs_mod.forms.entities.ReplayEntity;

import mchorse.bbs_mod.cubic.physics.PhysicsCarrier;
import mchorse.bbs_mod.cubic.spline.SplinePath;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;

import mchorse.bbs_mod.api.client.events.FormPoseEvents;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;

import java.util.Map;
import java.util.Objects;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Where a replay, a form or one of its bones ends up in the world, as matrices.
 *
 * <p>Split out of {@link BaseFilmController}: none of this is about running a film. It is the
 * geometry every consumer of a film asks for — the renderer to place an actor, the gizmo to
 * place its handles, the drag to capture a world transform — and it was the larger half of the
 * controller by weight while being entirely static.
 *
 * <p>Anchoring is resolved here too: a form anchored to another replay (or to a bone of one)
 * composes onto that replay's matrix, which is why these take the whole entity map.
 */
public class FilmMatrices
{
    public static Anchor getAnchor(IEntity entity)
    {
        return entity instanceof ReplayEntity actor && !actor.replay.relative.get() ? actor.replay.anchor.get() : null;
    }

    public static Anchor getAnchor(IEntity entity, Replay replay)
    {
        return entity instanceof ReplayEntity ? getAnchor(entity)
            : replay == null || replay.relative.get() ? null : replay.anchor.get();
    }

    /** Full linear frame of a spline's point coordinates, including actor and anchor. */
    public static Matrix4f getSplineParentCompositeMatrix(Map<String, IEntity> entities, IEntity entity, Replay replay,
        double cameraX, double cameraY, double cameraZ, float transition, SplineEditorUtils.Point point)
    {
        if (entity == null || entity.getForm() == null || point == null) return null;
        Form form = entity.getForm();
        boolean relative = replay != null && replay.relative.get();
        Vector3d origin = replayOrigin(replay, relative, cameraX, cameraY, cameraZ);
        Matrix4f target = getMatrixForRenderWithRotation(entity, origin.x, origin.y, origin.z, transition);
        if (!relative)
        {
            Pair<Matrix4f, Float> anchor = getTotalMatrix(entities, getAnchor(entity, replay), target, origin.x, origin.y, origin.z, transition, 0);
            if (anchor.a != null) target = anchor.a;
        }
        Matrix4f parent = SplineEditorUtils.parentMatrix(FormUtils.getRoot(form), entity, transition, point.form(), point.chain());
        return parent == null ? null : new Matrix4f(target).mul(parent);
    }

    public static Pair<Matrix4f, Float> getTotalMatrix(Map<String, IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i)
    {
        return getTotalMatrix(entities, value, defaultMatrix, cx, cy, cz, transition, i, false);
    }

    public static Pair<Matrix4f, Float> getTotalMatrix(Map<String, IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i, boolean fullMatrix)
    {
        return getTotalMatrix(entities, value, defaultMatrix, cx, cy, cz, transition, i, fullMatrix, null);
    }

    /**
     * The anchor's resolved matrix. {@code frame} shares the pose evaluation this walk needs with the rest of
     * the caller's pass — the anchor chain re-evaluates the target's whole pose at every level, and a caller
     * that resolves the same anchor twice (the camera-relative and world matrices of {@link #renderEntity})
     * would otherwise pay for it twice. Pass {@code null} to evaluate fresh, which is what a caller that has
     * not established such a span must do; see {@link FormFrameCache}.
     */
    public static Pair<Matrix4f, Float> getTotalMatrix(Map<String, IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i, boolean fullMatrix, FormFrameCache frame)
    {
        if (value == null) return new Pair<>(null, 1F);
        value = FormPoseEvents.ANCHOR.invoker().resolve(value);

        /* Stupid recursion stop, I don't think anyone would need more than that */
        if (i > 5)
        {
            return new Pair<>(defaultMatrix, 1F);
        }

        boolean same = value.previous == null || Objects.equals(value, value.previous);
        boolean only = value.previous != null && (value.x == 0F || value.x == 1F);
        Pair<Matrix4f, Float> result = new Pair<>(null, 1F);

        if (same || only)
        {
            Anchor anchor = same || value.x == 1F ? value : value.previous;
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, anchor, defaultMatrix, transition, i, fullMatrix, frame);

            result.b = matrix == defaultMatrix ? 1F : 0F;
            matrix = applyAnchorTransform(matrix, anchor);

            if (matrix != defaultMatrix)
            {
                result.a = matrix;
            }
        }
        else
        {
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, value, defaultMatrix, transition, i, fullMatrix, frame);
            Matrix4f lastMatrix = getEntityMatrix(entities, cx, cy, cz, value.previous, defaultMatrix, transition, i, fullMatrix, frame);

            /* Shadow opacity follows whether each endpoint resolved to the actor's own frame.
             * Clamp opacity, but let Back/Elastic curves overshoot the spatial transition. */
            result.b = MathUtils.clamp(Lerps.lerp(lastMatrix == defaultMatrix ? 1F : 0F, matrix == defaultMatrix ? 1F : 0F, value.x), 0F, 1F);

            matrix = applyAnchorTransform(matrix, value);
            lastMatrix = applyAnchorTransform(lastMatrix, value.previous);

            result.a = Matrices.lerp(lastMatrix, matrix, value.x);
        }

        return result;
    }

    /**
     * What the anchored form rides for its bone physics: the frame its anchor resolves to before the anchor's
     * own offset, in world space — the target as this anchor takes it. The offset is left out because moving
     * the form about inside the carrier is the form's own motion, which the physics should feel. {@code null}
     * when the physics feels the whole world: no target, or an anchor feeling all of its target's motion
     * ({@link Anchor#physics} at 1, the default). A target transition blends the two frames and the two
     * shares the way the form itself is blended between them.
     */
    public static PhysicsCarrier getCarrier(Map<String, IEntity> entities, Anchor value, Matrix4f defaultMatrix, float transition, FormFrameCache frame)
    {
        if (value == null) return null;
        value = FormPoseEvents.ANCHOR.invoker().resolve(value);

        boolean same = value.previous == null || Objects.equals(value, value.previous);
        boolean only = value.previous != null && (value.x == 0F || value.x == 1F);
        Anchor anchor = same || value.x == 1F ? value : value.previous;
        float feel = MathUtils.clamp(same || only ? anchor.physics : Lerps.lerp(value.previous.physics, value.physics, value.x), 0F, 1F);

        if (feel >= 1F)
        {
            return null;
        }

        Matrix4f matrix = getEntityMatrix(entities, 0D, 0D, 0D, anchor, defaultMatrix, transition, 0, false, frame);

        if (!same && !only)
        {
            Matrix4f lastMatrix = getEntityMatrix(entities, 0D, 0D, 0D, value.previous, defaultMatrix, transition, 0, false, frame);

            matrix = lastMatrix == defaultMatrix && matrix == defaultMatrix ? defaultMatrix : Matrices.lerp(lastMatrix, matrix, value.x);
        }

        return matrix == defaultMatrix ? null : PhysicsCarrier.of(matrix, feel);
    }

    private static Matrix4f applyAnchorTransform(Matrix4f matrix, Anchor anchor)
    {
        if (matrix == null || anchor == null || anchor.transform.isDefault())
        {
            return matrix;
        }

        /* Both endpoints may share defaultMatrix when their targets are absent. */
        return new Matrix4f(matrix).mul(anchor.transform.createMatrix());
    }

    public static Matrix4f getEntityMatrix(Map<String, IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i)
    {
        return getEntityMatrix(entities, cameraX, cameraY, cameraZ, anchor, defaultMatrix, transition, i, false);
    }

    public static Matrix4f getEntityMatrix(Map<String, IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i, boolean fullMatrix)
    {
        return getEntityMatrix(entities, cameraX, cameraY, cameraZ, anchor, defaultMatrix, transition, i, fullMatrix, null);
    }

    public static Matrix4f getEntityMatrix(Map<String, IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i, boolean fullMatrix, FormFrameCache frame)
    {
        if (anchor == null) return defaultMatrix;
        if (anchor.spline && cyclic(entities, anchor.replay, new java.util.HashSet<>())) return defaultMatrix;
        IEntity entity = entities.get(anchor.replay);

        if (entity != null)
        {
            Matrix4f basic = getMatrixForRenderWithRotation(entity, cameraX, cameraY, cameraZ, transition);

            Pair<Matrix4f, Float> totalMatrix = getTotalMatrix(entities, getAnchor(entity), basic, cameraX, cameraY, cameraZ, transition, i + 1, fullMatrix, frame);
            if (totalMatrix.a != null) basic = totalMatrix.a;

            Form form = entity.getForm();

            if (form != null)
            {
                /* The pose evaluation the attachment bone comes from — shared with the caller's pass when it
                 * established one (see FormFrameCache), evaluated fresh otherwise. Note it does NOT depend on
                 * the camera position, which is why resolving the same anchor for the camera-relative and the
                 * world matrix is the same evaluation twice. */
                MatrixCache map = FormFrameCache.collect(frame, form, entity, transition);
                Matrix4f matrix = map.get(anchor.attachment).matrix();

                if (anchor.spline)
                {
                    Form target = FormUtils.getForm(form, anchor.attachment);
                    if (!(target instanceof SplineForm spline) || matrix == null) return defaultMatrix;
                    Matrix4f world = new Matrix4f(basic).mul(matrix);
                    var path = SplinePath.create(spline, world);
                    Matrix4f sampled = path == null ? null : path.frame(anchor.progress, anchor.horizontal);
                    if (sampled == null) return defaultMatrix;
                    Matrix4f result = fullMatrix ? sampled : anchor.filterMatrix(sampled, defaultMatrix);
                    if (fullMatrix || anchor.inheritScale) result.scale(world.getScale(new Vector3f()));
                    return result.isFinite() ? result : defaultMatrix;
                }

                if (matrix != null)
                {
                    basic.mul(matrix);

                    /* The full matrix is the target's frame as it is — the gizmo's parent and the
                     * drag's world space are about where the target actually is, not about which
                     * components of it this anchor chose to ride. */
                    if (!fullMatrix)
                    {
                        basic = anchor.filterMatrix(basic, defaultMatrix);
                    }
                }

            }

            return basic;
        }

        return defaultMatrix;
    }

    private static boolean cyclic(Map<String, IEntity> entities, String id, java.util.Set<String> visited)
    {
        if (id.isEmpty()) return false;
        if (!visited.add(id) || visited.size() > 32) return true;
        IEntity entity = entities.get(id);
        Anchor next = getAnchor(entity);
        boolean result = next != null && (cyclic(entities, next.replay, visited)
            || next.previous != null && cyclic(entities, next.previous.replay, visited));
        visited.remove(id);
        return result;
    }

    /** Same path evaluator for camera and actor bindings, with camera offsets measured in blocks. */
    public static Matrix4f getPathMatrix(Map<String, IEntity> entities, String replay, String attachment,
        float progress, boolean horizontal, double cx, double cy, double cz, float transition)
    {
        Anchor anchor = new Anchor();
        anchor.replay = replay;
        anchor.attachment = attachment;
        anchor.spline = true;
        anchor.progress = progress;
        anchor.horizontal = horizontal;
        anchor.inheritScale = false;
        Matrix4f fallback = new Matrix4f();
        Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, anchor, fallback, transition, 0);
        return matrix == fallback ? null : matrix;
    }

    /**
     * The replay's own world orientation &mdash; the frame
     * {@link TransformSpace#GLOBAL}
     * aligns the gizmo to in the film viewport. It is exactly the rotation
     * {@link #getMatrixForRenderWithRotation} puts the whole actor under
     * ({@code bodyYaw} about the world Y), and nothing else: not the pose, not
     * the form's own transform, not an anchor parent's frame. So the frame turns
     * with the replay's facing while staying flat and axis-aligned like the world
     * one &mdash; drag X and the bone slides along the actor's own left/right
     * whatever direction the actor was placed in.
     *
     * <p>Returns the identity (the plain world axes, the pre-change behaviour)
     * for a missing entity, and naturally for any replay whose facing is zero.
     */
    public static Matrix3f getReplayWorldAxes(IEntity entity, float tickDelta)
    {
        Matrix3f axes = new Matrix3f();

        if (entity == null)
        {
            return axes;
        }

        float bodyYaw = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), tickDelta);

        return axes.rotateY(MathUtils.toRad(-bodyYaw));
    }

    public static Matrix4f getMatrixForRenderWithRotation(IEntity entity, double cameraX, double cameraY, double cameraZ, float tickDelta)
    {
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), tickDelta) - cameraX;
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), tickDelta) - cameraY;
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), tickDelta) - cameraZ;

        Matrix4f matrix = new Matrix4f();

        float bodyYaw = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), tickDelta);

        matrix.translate((float) x, (float) y, (float) z);
        matrix.rotateY(MathUtils.toRad(-bodyYaw));

        return matrix;
    }

    /**
     * Bone transform as composed for the film viewport: the same {@code target}
     * that {@link #renderEntity} multiplies onto the stack before the bone
     * matrix from {@link FormUtilsClient#getRenderer(Form)#collectMatrices},
     * i.e. {@code target.mul(bone)}. This includes replay position, whole-entity
     * {@code bodyYaw} from {@link #getMatrixForRenderWithRotation}, anchor
     * chains, etc. — everything that is <em>outside</em> the form's internal
     * {@code collectMatrices} tree but affects where the gizmo is drawn.
     *
     * @param cameraX camera position X (same convention as {@link #renderEntity})
     * @param cameraY camera position Y
     * @param cameraZ camera position Z
     * @param bonePath path key matching {@link #renderAxes} (see pose.bones. stripping)
     * @param useBoneMatrix if {@code true}, use the rotation-bearing bone matrix;
     *                      if {@code false}, use the origin-only matrix (matches
     *                      GLOBAL gizmo mode in {@link #renderAxes})
     */
    public static Matrix4f getGizmoBoneCompositeMatrix(
        Map<String, IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition,
        String bonePath,
        boolean useBoneMatrix
    )
    {
        Matrix4f matrix = getBoneCompositeMatrix(entities, entity, replay, cameraX, cameraY, cameraZ, transition, bonePath, useBoneMatrix, true);

        return matrix == null ? null : MatrixStackUtils.stripScale(matrix);
    }

    /**
     * The bone's EVALUATED channel rotation (ZYX euler radians, rest + actions +
     * pose — the additive total the renderer composes) from the same capture the
     * gizmo matrices come from, resolved by the same bone path. Feeds the
     * overlay-editing base of the gizmo drag; {@code null} when the bone isn't a
     * model bone or its rotation left the euler channels.
     */
    public static Vector3f getGizmoBoneEvaluatedRotation(IEntity entity, float transition, String bonePath)
    {
        if (entity == null || entity.getForm() == null || bonePath == null)
        {
            return null;
        }

        String mapKey = boneMapKey(bonePath);

        MatrixCache map = FormUtilsClient.getRenderer(FormUtils.getRoot(entity.getForm())).collectMatrices(entity, transition);

        return map.get(mapKey).evaluatedRotation();
    }

    /**
     * The same composite as {@link #getGizmoBoneCompositeMatrix} but with the bone's scale kept.
     * The gizmo drops scale on purpose (a gizmo must not inherit it); world-space transform capture
     * needs the full matrix, so it goes through this variant instead.
     *
     * <p>A form's path answers with the frame its body parts hang in, not the gizmo's spot on the
     * form's pivot (see {@link MatrixCacheEntry#placement}).</p>
     */
    public static Matrix4f getBoneCompositeMatrix(
        Map<String, IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition,
        String bonePath,
        boolean useBoneMatrix
    ) {
        return getBoneCompositeMatrix(entities, entity, replay, cameraX, cameraY, cameraZ, transition, bonePath, useBoneMatrix, false);
    }

    private static Matrix4f getBoneCompositeMatrix(
        Map<String, IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition,
        String bonePath,
        boolean useBoneMatrix,
        boolean placement
    ) {
        if (entity == null || entity.getForm() == null || bonePath == null)
        {
            return null;
        }

        Form form = entity.getForm();
        boolean relative = replay != null && replay.relative.get();
        Vector3d origin = replayOrigin(replay, relative, cameraX, cameraY, cameraZ);

        double cx = origin.x;
        double cy = origin.y;
        double cz = origin.z;

        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        Matrix4f target;

        /* Anchor resolution and the bone lookup below both evaluate a pose, and for a form anchored to
         * itself (or to a bone of its own tree) that is literally the same one. Nothing between the two
         * touches the pose, so they share a frame. */
        FormFrameCache frame = new FormFrameCache();

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, getAnchor(entity, replay), defaultMatrix, cx, cy, cz, transition, 0, false, frame);

            target = pair.a != null ? pair.a : defaultMatrix;
        }
        else
        {
            target = defaultMatrix;
        }

        String mapKey = boneMapKey(bonePath);

        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormFrameCache.collect(frame, root, entity, transition);
        MatrixCacheEntry entry = map.get(mapKey);
        Matrix4f bone = placement ? entry.placement(useBoneMatrix) : useBoneMatrix ? entry.matrix() : entry.origin();

        if (bone == null)
        {
            return null;
        }

        return new Matrix4f(target).mul(bone);
    }

    /**
     * The anchor's resolved world matrix as composed for the film viewport — the
     * same {@code target} {@link #renderEntity} renders the form with, i.e.
     * {@code getTotalMatrix(replay.anchor)}. Used by the gizmo drag to numerically
     * sample how {@code replay.anchor.transform} maps to world position/rotation
     * (the counterpart of {@link #getGizmoBoneCompositeMatrix} for the anchor,
     * with no bone multiply since the anchor moves the whole form).
     */
    public static Matrix4f getGizmoAnchorCompositeMatrix(
        Map<String, IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition
    ) {
        Matrix4f full = entity == null
            ? null
            : getAnchorMatrix(entities, entity, replay, cameraX, cameraY, cameraZ, transition, getAnchor(entity, replay));

        return full == null ? null : MatrixStackUtils.stripScale(full);
    }

    /**
     * Where a form ends up hanging off the given anchor — the same {@code target} the renderer
     * places it with, but for an anchor that is not necessarily the form's current one. That is
     * what lets a caller ask where the form <em>would</em> sit under another target and compare
     * the two (see {@code AnchorRebase}); {@link #getGizmoAnchorCompositeMatrix} is this with the
     * gizmo's scale stripped off.
     *
     * <p>A replay in relative mode renders without resolving anchors at all (see
     * {@link FilmEntityRenderer}), so it answers with the replay's own placement whatever the
     * anchor says — a caller for whom that distinction matters checks {@code replay.relative}
     * itself.</p>
     */
    public static Matrix4f getAnchorMatrix(
        Map<String, IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition,
        Anchor anchor
    ) {
        if (entity == null)
        {
            return null;
        }

        boolean relative = replay != null && replay.relative.get();
        Vector3d origin = replayOrigin(replay, relative, cameraX, cameraY, cameraZ);

        double cx = origin.x;
        double cy = origin.y;
        double cz = origin.z;

        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);

        if (relative)
        {
            return defaultMatrix;
        }

        Pair<Matrix4f, Float> pair = getTotalMatrix(entities, anchor, defaultMatrix, cx, cy, cz, transition, 0);

        return pair.a != null ? pair.a : defaultMatrix;
    }

    static String boneMapKey(String bonePath)
    {
        TrackId track = TrackId.parse(bonePath);

        return track != null && track.is(TrackKind.BONE) ? track.subjectPath() : bonePath;
    }

    /** Where the replay's own frame sits: the camera normally, its own origin in relative mode. */
    private static Vector3d replayOrigin(Replay replay, boolean relative, double cameraX, double cameraY, double cameraZ)
    {
        if (!relative || replay == null)
        {
            return new Vector3d(cameraX, cameraY, cameraZ);
        }

        return replay.getRelativeOrigin();
    }
}
