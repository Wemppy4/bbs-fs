package mchorse.bbs_mod.film;

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
        /* Stupid recursion stop, I don't think anyone would need more than that */
        if (i > 5)
        {
            return new Pair<>(defaultMatrix, 1F);
        }

        boolean same = value.previous == null || Objects.equals(value, value.previous);
        boolean only = value.x <= 0F && value.previous != null;
        Pair<Matrix4f, Float> result = new Pair<>(null, 1F);

        if (same || only)
        {
            Anchor anchor = same ? value : value.previous;
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, anchor, defaultMatrix, transition, i, fullMatrix, frame);

            matrix = applyAnchorTransform(matrix, anchor);

            if (matrix != defaultMatrix)
            {
                result.a = matrix;
                result.b = 0F;
            }
        }
        else
        {
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, value, defaultMatrix, transition, i, fullMatrix, frame);
            Matrix4f lastMatrix = getEntityMatrix(entities, cx, cy, cz, value.previous, defaultMatrix, transition, i, fullMatrix, frame);

            matrix = applyAnchorTransform(matrix, value);
            lastMatrix = applyAnchorTransform(lastMatrix, value.previous);

            result.a = value.x >= 1F ? matrix : Matrices.lerp(lastMatrix, matrix, value.x);

            if (value.isFadeOut()) result.b = value.x;
            else if (value.isFadeIn()) result.b = 1F - value.x;
            else result.b = 0F;
        }

        return result;
    }

    private static Matrix4f applyAnchorTransform(Matrix4f matrix, Anchor anchor)
    {
        if (matrix == null || anchor == null || anchor.transform.isDefault())
        {
            return matrix;
        }

        return matrix.mul(anchor.transform.createMatrix());
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
        IEntity entity = entities.get(anchor.replay);

        if (entity != null)
        {
            Matrix4f basic = getMatrixForRenderWithRotation(entity, cameraX, cameraY, cameraZ, transition);

            Form form = entity.getForm();

            if (form != null)
            {
                Pair<Matrix4f, Float> totalMatrix = getTotalMatrix(entities, form.anchor.get(), basic, cameraX, cameraY, cameraZ, transition, i + 1, fullMatrix, frame);

                if (totalMatrix.a != null)
                {
                    basic = totalMatrix.a;
                }

                /* The pose evaluation the attachment bone comes from — shared with the caller's pass when it
                 * established one (see FormFrameCache), evaluated fresh otherwise. Note it does NOT depend on
                 * the camera position, which is why resolving the same anchor for the camera-relative and the
                 * world matrix is the same evaluation twice. */
                MatrixCache map = FormFrameCache.collect(frame, form, entity, transition);
                Matrix4f matrix = map.get(anchor.attachment).matrix();

                if (matrix != null)
                {
                    basic.mul(matrix);

                    if (!fullMatrix && anchor.scale)
                    {
                        Matrix3f mat = new Matrix3f();
                        Vector3f v = new Vector3f();
                        basic.get3x3(mat);

                        mat.getColumn(0, v); v.normalize(); mat.setColumn(0, v);
                        mat.getColumn(1, v); v.normalize(); mat.setColumn(1, v);
                        mat.getColumn(2, v); v.normalize(); mat.setColumn(2, v);

                        basic.set3x3(mat);
                    }

                    if (!fullMatrix && anchor.translate)
                    {
                        Vector3f t = new Vector3f();
                        basic.getTranslation(t);
                        basic.set(defaultMatrix);
                        basic.setTranslation(t);
                    }
                }

            }

            return basic;
        }

        return defaultMatrix;
    }

    /**
     * The replay's own world orientation &mdash; the frame
     * {@link mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace#GLOBAL}
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
        Matrix4f matrix = getBoneCompositeMatrix(entities, entity, replay, cameraX, cameraY, cameraZ, transition, bonePath, useBoneMatrix);

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
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0, false, frame);

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
        Matrix4f bone = useBoneMatrix ? entry.matrix() : entry.origin();

        if (bone == null)
        {
            return null;
        }

        return new Matrix4f(target).mul(bone);
    }

    /**
     * The anchor's resolved world matrix as composed for the film viewport — the
     * same {@code target} {@link #renderEntity} renders the form with, i.e.
     * {@code getTotalMatrix(form.anchor)}. Used by the gizmo drag to numerically
     * sample how {@code form.anchor.transform} maps to world position/rotation
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
        if (entity == null || entity.getForm() == null)
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
        Matrix4f full = defaultMatrix;

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0);

            full = pair.a != null ? pair.a : defaultMatrix;
        }

        return MatrixStackUtils.stripScale(full);
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
