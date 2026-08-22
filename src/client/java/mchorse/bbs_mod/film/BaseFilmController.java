package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.client.renderer.ItemUseEffects;
import mchorse.bbs_mod.client.renderer.LivePlayerItemUse;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.ThirdPersonItemUse;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.replays.tracks.AnchorResolver;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviours;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayItemUse;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.mixin.client.ClientPlayerEntityAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class BaseFilmController
{
    public final Film film;

    public final IntObjectMap<IEntity> entities = new IntObjectHashMap<>();

    public boolean paused;
    public int exception = -1;

    private final AnchorResolver anchors = this::resolveAnchor;

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Vector3f TEMP_VECTOR = new Vector3f();

    /* Rendering helpers */

    public static void renderEntity(FilmControllerContext context)
    {
        IntObjectMap<IEntity> entities = context.entities;
        IEntity entity = context.entity;
        Camera camera = context.camera;
        MatrixStack stack = context.stack;
        float transition = context.transition;

        Form form = entity.getForm();

        if (form == null)
        {
            return;
        }

        Vector3d position = Vectors.TEMP_3D.set(
            Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
            Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
            Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
        );

        double cx = camera.getPos().x;
        double cy = camera.getPos().y;
        double cz = camera.getPos().z;

        boolean relative = context.replay != null && context.relative;

        if (relative)
        {
            cx = context.replay.keyframes.x.interpolate(0F) + context.replay.relativeOffset.get().x;
            cy = context.replay.keyframes.y.interpolate(0F) + context.replay.relativeOffset.get().y;
            cz = context.replay.keyframes.z.interpolate(0F) + context.replay.relativeOffset.get().z;
        }

        Matrix4f target = null;
        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        float opacity = 1F;

        /* The anchor is resolved twice below — once against the camera and once against the world origin —
         * and the pose evaluation inside is identical for both (it is camera-independent). Only pure matrix
         * math separates the two calls, so one evaluation covers them; see FormFrameCache on why the scope
         * is this narrow and not the whole frame. Deliberately dropped before the form renders: rendering
         * applies the form's animation states, which move the pose. */
        FormFrameCache anchorFrame = relative ? null : new FormFrameCache();

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0, false, anchorFrame);

            target = pair.a;
            opacity = pair.b;
        }

        if (target != null)
        {
            Vector3f v = target.getTranslation(new Vector3f());
            Vector3f v2 = defaultMatrix.getTranslation(new Vector3f());

            position.x += v.x - v2.x;
            position.y += v.y - v2.y;
            position.z += v.z - v2.z;
        }
        else
        {
            target = defaultMatrix;
        }

        Matrix4f targetWorld;

        if (relative)
        {
            targetWorld = new Matrix4f(target);
        }
        else
        {
            Matrix4f defaultWorldMatrix = getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);
            Pair<Matrix4f, Float> pairWorld = getTotalMatrix(entities, form.anchor.get(), defaultWorldMatrix, 0D, 0D, 0D, transition, 0, false, anchorFrame);

            targetWorld = pairWorld.a != null ? pairWorld.a : defaultWorldMatrix;
        }

        BlockPos pos = BlockPos.ofFloored(position.x, position.y + 0.5D, position.z);
        int sky = entity.getWorld().getLightLevel(LightType.SKY, pos);
        int torch = entity.getWorld().getLightLevel(LightType.BLOCK, pos);
        int light = LightmapTextureManager.pack(torch, sky);
        int overlay = OverlayTexture.packUv(OverlayTexture.getU(0F), OverlayTexture.getV(entity.getHurtTimer() > 0));

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.ENTITY, entity, stack, light, overlay, transition)
            .camera(camera)
            .stencilMap(context.map)
            .color(context.color);

        stack.push();

        if (relative)
        {
            stack.peek().getPositionMatrix().identity();
            stack.peek().getNormalMatrix().identity();
        }

        formContext.world.peek().getPositionMatrix().identity();
        formContext.world.peek().getNormalMatrix().identity();
        MatrixStackUtils.multiply(formContext.world, targetWorld);

        MatrixStackUtils.multiply(stack, target);
        FormUtilsClient.render(form, formContext);

        /* A second, post-render span: the gizmo, the axes preview and the anchor gizmo are adjacent and all
         * read the pose the form just rendered with (states applied), so they share one evaluation — the
         * gizmo and the preview resolve the very same form and entity, which in the editor is every frame,
         * in both the visible and the stencil-picking pass. It must stay separate from `anchorFrame` above,
         * which was taken before the states moved the pose. */
        FormFrameCache gizmoFrame = UIBaseMenu.shouldRenderAxes() ? new FormFrameCache() : null;

        if (UIBaseMenu.shouldRenderAxes())
        {
            if (context.bone != null) renderAxes(context.bone, context.local, context.space, context.gizmoView, context.map, form, entity, transition, stack, gizmoFrame);
            if (context.bone2 != null && context.map == null) renderPreviewAxes(context.bone2, context.local2, form, entity, transition, stack, gizmoFrame);
        }

        stack.pop();

        if (UIBaseMenu.shouldRenderAxes() && context.anchorGizmo)
        {
            renderAnchorGizmo(entities, entity, target, defaultMatrix, cx, cy, cz, transition, context.anchorLocal, context.space, context.gizmoView, context.map, stack, gizmoFrame);
        }

        if (!relative && context.map == null && opacity > 0F && context.shadowRadius > 0F && form.visible.get())
        {
            /* Skip the shadow when the form is hidden (form.visible, animatable via keyframes): the form
             * itself renders nothing then - see FormRenderer.render - so its shadow must vanish too.
             * The animated value is live here, applied to form.visible in startRenderFrame this frame.
             *
             * Place the shadow under the replay's perceived position: shift the actual shadow position
             * by how far the model (form transform + anchor-bone root motion) has moved from rest,
             * mapped from form-local into world axes via the render target. Moving the position itself
             * (not just translating the quad) makes the shadow's ground projection and shading match. */
            double shadowX = position.x;
            double shadowY = position.y;
            double shadowZ = position.z;

            FormRenderer renderer = FormUtilsClient.getRenderer(FormUtils.getRoot(form));

            if (renderer != null && !BBSRendering.isIrisShadowPass() && context.replay != null && context.replay.shadowFollow.get())
            {
                Vector3f displacement = renderer.getShadowDisplacement(entity, transition);

                if (displacement != null)
                {
                    target.transformDirection(displacement);

                    shadowX += displacement.x;
                    shadowY += displacement.y;
                    shadowZ += displacement.z;
                }

                /* Extra world-space nudge to seat the shadow on the model's real floor (added after the
                 * form-local displacement is mapped to world, so it stays vertical regardless of facing). */
                Point offset = context.replay.shadowOffset.get();

                shadowX += offset.x;
                shadowY += offset.y;
                shadowZ += offset.z;
            }

            stack.push();
            stack.translate(shadowX - cx, shadowY - cy, shadowZ - cz);

            ModelBlockEntityRenderer.renderShadow(context.consumers, stack, transition, shadowX, shadowY, shadowZ, 0F, 0F, 0F, context.shadowRadius, opacity);

            stack.pop();
        }

        if (!relative && !context.nameTag.isEmpty() && context.map == null && form.visible.get())
        {
            /* Hide the name tag along with the form (form.visible, animatable via keyframes): when the
             * form renders nothing, its name tag must vanish too - same reasoning as the shadow above. */
            stack.push();
            stack.translate(position.x - cx, position.y - cy, position.z - cz);

            renderNameTag(entity, Text.literal(StringUtils.processColoredText(context.nameTag)), stack, context.consumers, light);

            stack.pop();
        }

        RenderSystem.enableDepthTest();
    }

    private static void renderAxes(String bone, boolean local, TransformSpace space, Matrix4f gizmoView, StencilMap stencilMap, Form form, IEntity entity, float transition, MatrixStack stack, FormFrameCache frame)
    {
        String mapKey = boneMapKey(bone);
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormFrameCache.collect(frame, root, entity, transition);
        Matrix4f matrix = local ? map.get(mapKey).matrix() : map.get(mapKey).origin();

        if (matrix != null)
        {
            stack.push();
            MatrixStackUtils.multiply(stack, matrix);

            /* Reorient into the active space (the replay's own world axes for
             * GLOBAL, screen axes for VIEW; LOCAL untouched) before the frame is
             * captured — so the visual and the pick stencil, both built from it,
             * stay in lockstep. */
            Gizmo.INSTANCE.reorientForSpace(stack, space, gizmoView, getReplayWorldAxes(entity, transition));

            if (stencilMap == null)
            {
                /* The visual is drawn later, in the panel's UI pass (see
                 * Gizmo#renderInterface) — here we only snapshot its placement. */
                Gizmo.INSTANCE.captureVisual(stack);
            }
            else
            {
                Gizmo.INSTANCE.renderStencil(stack, stencilMap);
            }

            RenderSystem.enableDepthTest();
            stack.pop();
        }
    }

    /**
     * The replay's "axes preview" (a secondary bone): plain, non-interactive
     * cool axes via {@link Draw#coolerAxes} — not the editing gizmo. Resolves the
     * bone matrix exactly like {@link #renderAxes} and applies the same
     * distance scaling the gizmo uses, so the preview keeps a constant on-screen
     * size and matches the gizmo's axes.
     */
    private static void renderPreviewAxes(String bone, boolean local, Form form, IEntity entity, float transition, MatrixStack stack, FormFrameCache frame)
    {
        String mapKey = boneMapKey(bone);
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormFrameCache.collect(frame, root, entity, transition);
        MatrixCacheEntry entry = map.get(mapKey);

        if (entry == null)
        {
            return;
        }

        Matrix4f matrix = local ? entry.matrix() : entry.origin();

        if (matrix == null)
        {
            return;
        }

        if (local) matrix = MatrixStackUtils.stripScale(matrix);

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);

        Vector3f cameraRelative = stack.peek().getPositionMatrix().getTranslation(new Vector3f());
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        float fov = proj.m33() == 0 ? (float) (2.0 * Math.atan(1.0 / proj.m11())) : BBSSettings.getFov();
        float distanceScale = BBSSettings.getAxesDistanceScale(cameraRelative.length(), fov);

        stack.scale(distanceScale, distanceScale, distanceScale);
        Draw.coolerAxes(stack, 0.25F, 0.008F);

        RenderSystem.enableDepthTest();
        stack.pop();
    }

    /**
     * Draw the editing gizmo for the form's anchor offset. The anchor is applied
     * as {@code parent.mul(transform)} in {@link #getTotalMatrix}, so the gizmo
     * sits at that resolved matrix {@code full} (already computed as the entity's
     * render target) and edits {@code form.anchor.transform}. The placement
     * mirrors {@link #renderAxes}: local keeps the anchor's own orientation,
     * otherwise the attachment's orientation at the anchor's position is used
     * (this path's origin flavour) — and the result is reoriented into the
     * active space just the same, so the drawn handles match the frame the drag
     * works in (GLOBAL/VIEW would otherwise stay on the attachment's axes while
     * the drag ran in world/screen axes).
     */
    private static void renderAnchorGizmo(IntObjectMap<IEntity> entities, IEntity entity, Matrix4f full, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, boolean local, TransformSpace space, Matrix4f gizmoView, StencilMap stencilMap, MatrixStack stack, FormFrameCache frame)
    {
        Form form = entity.getForm();

        if (form == null || full == null)
        {
            return;
        }

        Matrix4f matrix;

        if (local)
        {
            matrix = MatrixStackUtils.stripScale(full);
        }
        else
        {
            Matrix4f parent = getEntityMatrix(entities, cx, cy, cz, form.anchor.get(), defaultMatrix, transition, 0, true, frame);

            matrix = MatrixStackUtils.stripScale(parent);
            matrix.setTranslation(full.getTranslation(new Vector3f()));
        }

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);

        /* Same lockstep as renderAxes: reorient before the frame is captured, so
         * the visual and the pick stencil built from it agree with the drag. */
        Gizmo.INSTANCE.reorientForSpace(stack, space, gizmoView, getReplayWorldAxes(entity, transition));

        if (stencilMap == null)
        {
            /* The visual is drawn later, in the panel's UI pass (see
             * Gizmo#renderInterface) — here we only snapshot its placement. */
            Gizmo.INSTANCE.captureVisual(stack);
        }
        else
        {
            Gizmo.INSTANCE.renderStencil(stack, stencilMap);
        }

        RenderSystem.enableDepthTest();
        stack.pop();
    }

    public static Pair<Matrix4f, Float> getTotalMatrix(IntObjectMap<IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i)
    {
        return getTotalMatrix(entities, value, defaultMatrix, cx, cy, cz, transition, i, false);
    }

    public static Pair<Matrix4f, Float> getTotalMatrix(IntObjectMap<IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i, boolean fullMatrix)
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
    public static Pair<Matrix4f, Float> getTotalMatrix(IntObjectMap<IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i, boolean fullMatrix, FormFrameCache frame)
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

    public static Matrix4f getEntityMatrix(IntObjectMap<IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i)
    {
        return getEntityMatrix(entities, cameraX, cameraY, cameraZ, anchor, defaultMatrix, transition, i, false);
    }

    public static Matrix4f getEntityMatrix(IntObjectMap<IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i, boolean fullMatrix)
    {
        return getEntityMatrix(entities, cameraX, cameraY, cameraZ, anchor, defaultMatrix, transition, i, fullMatrix, null);
    }

    public static Matrix4f getEntityMatrix(IntObjectMap<IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i, boolean fullMatrix, FormFrameCache frame)
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
        IntObjectMap<IEntity> entities,
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
        IntObjectMap<IEntity> entities,
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

        double cx = cameraX;
        double cy = cameraY;
        double cz = cameraZ;

        if (relative && replay != null)
        {
            cx = replay.keyframes.x.interpolate(0F) + replay.relativeOffset.get().x;
            cy = replay.keyframes.y.interpolate(0F) + replay.relativeOffset.get().y;
            cz = replay.keyframes.z.interpolate(0F) + replay.relativeOffset.get().z;
        }

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
        IntObjectMap<IEntity> entities,
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

        double cx = cameraX;
        double cy = cameraY;
        double cz = cameraZ;

        if (relative && replay != null)
        {
            cx = replay.keyframes.x.interpolate(0F) + replay.relativeOffset.get().x;
            cy = replay.keyframes.y.interpolate(0F) + replay.relativeOffset.get().y;
            cz = replay.keyframes.z.interpolate(0F) + replay.relativeOffset.get().z;
        }

        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        Matrix4f full = defaultMatrix;

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0);

            full = pair.a != null ? pair.a : defaultMatrix;
        }

        return MatrixStackUtils.stripScale(full);
    }

    private static void renderNameTag(IEntity entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        boolean sneaking = !entity.isSneaking();
        float hitboxH = (float) entity.getPickingHitbox().h + 0.5F;

        matrices.push();
        matrices.translate(0F, hitboxH, 0F);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        float opacity = MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F);
        int background = (int) (opacity * 255F) << 24;
        float h = (float) (-textRenderer.getWidth(text) / 2);

        textRenderer.draw(text, h, 0, 0x20ffffff, false, matrix4f, vertexConsumers, sneaking ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL, background, light);

        if (sneaking)
        {
            textRenderer.draw(text, h, 0, -1, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }

        matrices.pop();
    }

    /* Film controller */

    public BaseFilmController(Film film)
    {
        this.film = film;
    }

    public IntObjectMap<IEntity> getEntities()
    {
        return this.entities;
    }

    public void togglePause()
    {
        this.paused = !this.paused;
    }

    public void createEntities()
    {
        this.entities.clear();

        if (this.film == null)
        {
            return;
        }

        int i = 0;

        for (Replay replay : this.film.replays.getList())
        {
            if (replay.enabled.get())
            {
                World world = MinecraftClient.getInstance().world;
                IEntity entity = new StubEntity(world);
                int ticks = replay.getTick(this.getTick());

                entity.setForm(FormUtils.copy(replay.form.get()));
                replay.keyframes.apply(ticks, entity);
                entity.setPrevX(entity.getX());
                entity.setPrevY(entity.getY());
                entity.setPrevZ(entity.getZ());

                entity.setPrevYaw(entity.getYaw());
                entity.setPrevHeadYaw(entity.getHeadYaw());
                entity.setPrevPitch(entity.getPitch());
                entity.setPrevBodyYaw(entity.getBodyYaw());

                this.entities.put(i, entity);
            }

            i += 1;
        }
    }

    public abstract Map<String, Integer> getActors();

    public abstract int getTick();

    public boolean hasFinished()
    {
        return false;
    }

    public void update()
    {
        this.updateEntities(this.getTick());
    }

    protected void updateEntities(int ticks)
    {
        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            List<Replay> replays = this.film.replays.getList();
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                /* Replay-local: a looping replay wraps the film tick to its own window, and that must not
                 * carry over to the next replay in the loop (which would then wrap an already wrapped tick). */
                int replayTicks = replay.getTick(ticks);

                this.updateEntityAndForm(entity, replayTicks);
                this.applyReplay(replay, replayTicks, entity);

                /* Vanilla's eating and drinking effects: the actor never ticks
                 * an item use, so the crumbs and chewing come from the clip.
                 * The take the real player acts is no exception - the film's use
                 * is only answered while drawing, so vanilla's own tick spits
                 * nothing for them either (see LivePlayerItemUse). */
                ItemUseEffects.tick(replay, entity, replayTicks);

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId != null)
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof ActorEntity actor)
                        {
                            /* Force synchronize entity angles */
                            actor.setYaw(replay.keyframes.yaw.interpolate(replayTicks).floatValue());
                            actor.setHeadYaw(replay.keyframes.headYaw.interpolate(replayTicks).floatValue());
                            actor.setBodyYaw(replay.keyframes.bodyYaw.interpolate(replayTicks).floatValue());
                            actor.setPitch(replay.keyframes.pitch.interpolate(replayTicks).floatValue());
                            replay.applyClientActions(replayTicks, new MCEntity(anEntity), this.film);
                        }
                        else if (anEntity instanceof PlayerEntity player)
                        {
                            double x = replay.keyframes.x.interpolate(replayTicks);
                            double y = replay.keyframes.y.interpolate(replayTicks);
                            double z = replay.keyframes.z.interpolate(replayTicks);
                            double prevX = replay.keyframes.x.interpolate(replayTicks - 1);
                            double prevY = replay.keyframes.y.interpolate(replayTicks - 1);
                            double prevZ = replay.keyframes.z.interpolate(replayTicks - 1);

                            player.setVelocity(x - prevX, y - prevY, z - prevZ);
                        }
                    }
                }
            }
        }
    }

    public void updateEndWorld()
    {
        int ticks = this.getTick();

        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            List<Replay> replays = this.film.replays.getList();
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                ticks = replay.getTick(ticks);

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId != null)
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof PlayerEntity player)
                        {
                            double x = replay.keyframes.x.interpolate(ticks);
                            double y = replay.keyframes.y.interpolate(ticks);
                            double z = replay.keyframes.z.interpolate(ticks);
                            boolean sneaking = replay.keyframes.sneaking.interpolate(ticks) > 0;
                            boolean grounded = replay.keyframes.grounded.interpolate(ticks) > 0;

                            Vec3d pos = player.getPos();

                            /* Probe downwards so vanilla's collision registers the floor - see
                             * ReplayKeyframes#GRAVITY_PROBE. */
                            double dY = y - pos.y - (grounded ? ReplayKeyframes.GRAVITY_PROBE : 0D);

                            player.move(MovementType.SELF, new Vec3d(x - pos.x, dY, z - pos.z));
                            player.setPosition(x, y, z);

                            player.setSneaking(sneaking);
                            player.setOnGround(grounded);

                            /* The player's own tick overwrites this from the input every tick, but
                             * baseTick (which spawns the sprinting particles) runs before it, so a
                             * value written at the end of the world tick is the one vanilla sees. */
                            player.setSprinting(replay.keyframes.sprinting.interpolate(ticks) > 0);

                            /* First person teleports the player from keyframes instead of walking it, so vanilla's
                             * stride distance (the view-bobbing amplitude) is computed from a zero velocity and stays
                             * flat. Re-derive it from the actual per-tick displacement (the same source as the limb
                             * animation) with vanilla's own easing. prevStrideDistance already holds last tick's value
                             * (snapshotted by the player tick), so only the current one is advanced — keeping the bob
                             * smooth between frames. */
                            float dx = (float) (player.getX() - player.prevX);
                            float dz = (float) (player.getZ() - player.prevZ);
                            float stride = grounded ? Math.min(0.1F, (float) Math.sqrt(dx * dx + dz * dz)) : 0F;

                            player.strideDistance = player.prevStrideDistance + (stride - player.prevStrideDistance) * 0.4F;

                            if (player instanceof ClientPlayerEntityAccessor accessor)
                            {
                                accessor.bbs$setIsSneakingPose(sneaking);
                            }

                            if (player instanceof ClientPlayerEntity playerEntity)
                            {
                                playerEntity.input.sneaking = sneaking;
                            }

                            player.fallDistance = replay.keyframes.fall.interpolate(ticks).floatValue();
                        }
                    }
                }
            }
        }
    }

    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        entity.update();

        if (entity.getForm() != null)
        {
            entity.getForm().update(entity);
        }
    }

    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        replay.keyframes.apply(ticks, entity);
        replay.applyClientActions(ticks, entity, this.film);
    }

    public void startRenderFrame(float transition)
    {
        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = this.film.replays.getList().get(i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.PROPERTIES))
            {
                continue;
            }

            float delta = this.getTransition(entity, transition);
            int tick = replay.getTick(this.getTick());

            /* Apply property */
            Form form1 = entity.getForm();

            this.applyTracks(replay, form1, tick + delta, delta);

            /* The item use of this take, published for everything that draws
             * its body: the procedural animator poses the arms with it, and the
             * model form renderer makes the vanilla item predicates fire on the
             * held items (a drawn bow bends and shows its arrow) */
            ItemUsePose.Use use = ReplayItemUse.compute(replay, tick + delta, true);
            ItemUsePose.Use offUse = ReplayItemUse.compute(replay, tick + delta, false);

            ThirdPersonItemUse.set(ThirdPersonItemUse.keyOf(entity), use, offUse);

            Map<String, Integer> actors = this.getActors();

            if (actors != null)
            {
                Integer entityId = actors.get(replay.getId());

                if (entityId != null)
                {
                    Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                    ThirdPersonItemUse.set(anEntity, use, offUse);

                    if (anEntity instanceof ActorEntity actor)
                    {
                        this.applyTracks(replay, actor.getForm(), tick + delta, delta);
                    }
                    else if (anEntity instanceof PlayerEntity player)
                    {
                        /* The first person hand is vanilla's, and it asks the
                         * live player what it is using - the film has to say */
                        LivePlayerItemUse.apply(player, use, offUse);

                        Morph morph = Morph.getMorph(player);

                        if (morph != null)
                        {
                            this.applyTracks(replay, morph.getForm(), tick + delta, delta);
                        }

                        float yawHead = replay.keyframes.headYaw.interpolate(tick + delta).floatValue();
                        float yawBody = replay.keyframes.bodyYaw.interpolate(tick + delta).floatValue();
                        float pitch = replay.keyframes.pitch.interpolate(tick + delta).floatValue();

                        player.setYaw(yawHead);
                        player.setHeadYaw(yawHead);
                        player.setPitch(pitch);
                        player.setBodyYaw(yawBody);
                        player.prevYaw = yawHead;
                        player.prevHeadYaw = yawHead;
                        player.prevPitch = pitch;
                        player.prevBodyYaw = yawBody;
                    }
                }
            }
        }
    }

    /**
     * Lay one record's tracks over a form for this frame: the per-frame overrides the track kinds
     * leave behind are dropped first, so a track that was deleted (or whose keyframes ran out) stops
     * driving the form, and then every track applies itself.
     *
     * <p>This used to be two passes written side by side — {@code FormProperties.applyProperties}
     * for properties, bones and materials, and a second dispatcher here for the IK, pole, physics
     * and wind tracks. Both walked the same map and matched the same ids; the kinds now say what
     * they do themselves (see {@link TrackBehaviour}).</p>
     */
    protected void applyTracks(Replay replay, Form root, float tick, float transition)
    {
        if (replay == null || root == null)
        {
            return;
        }

        TrackBehaviours.clearOverrides(root);

        replay.properties.apply(TrackContext.frame(root, transition, this.anchors), tick, 1F);
    }

    /**
     * Resolving an anchor is the one thing a track cannot do on its own: it means composing the bone
     * matrices of another record's live entity, which only the controller has.
     */
    private Vector3f resolveAnchor(Anchor anchor, float transition)
    {
        if (this.entities.get(anchor.replay) == null)
        {
            return null;
        }

        Pair<Matrix4f, Float> matrix = getTotalMatrix(this.entities, anchor, IDENTITY, 0D, 0D, 0D, transition, 0, true);

        return (matrix.a != null ? matrix.a : IDENTITY).getTranslation(TEMP_VECTOR);
    }

    /**
     * The matrix-cache key of a bone path: the {@code pose.bones.} namespace drops out, leaving the
     * owning form's path and the bone ({@code 0/1/pose.bones.head} &rarr; {@code 0/1/head}), which is
     * how {@link MatrixCache} keys its entries. A path that is not a bone track passes through.
     */
    private static String boneMapKey(String bonePath)
    {
        TrackId track = TrackId.parse(bonePath);

        return track != null && track.is(TrackKind.BONE) ? track.subjectPath() : bonePath;
    }

    protected float getTransition(IEntity entity, float transition)
    {
        return this.paused ? 0F : transition;
    }

    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        if (this.paused && (updateMode == UpdateMode.UPDATE))
        {
            return false;
        }

        return i != this.exception;
    }

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = this.film.replays.getList().get(i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.RENDER))
            {
                continue;
            }

            this.renderEntity(context, replay, entity);
        }
    }

    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity)
    {
        if (replay.actor.get())
        {
            this.renderActorNameTag(context, replay, entity);

            return;
        }

        FilmControllerContext filmContext = getFilmControllerContext(context, replay, entity);

        filmContext.transition = getTransition(entity, context.tickDelta());

        renderEntity(filmContext);
    }

    /**
     * An actor replay is drawn by vanilla as a real entity, so the film controller renders nothing for
     * it - and the name tag used to leave with the rest of the render. The tag belongs to the replay,
     * not to the entity, so nothing else puts it up: draw it here, over the body vanilla is actually
     * drawing (the networked actor, not the keyframed stub, so a moving actor keeps its tag on its
     * head). No actor entity in the world means no body to label - see FrozenFilmController, which is
     * deliberately blind to them.
     */
    protected void renderActorNameTag(WorldRenderContext context, Replay replay, IEntity entity)
    {
        Form form = entity.getForm();

        /* Same conditions as the name tag of a regularly rendered replay: hidden along with the form
         * (form.visible, animatable via keyframes) and absent in the relative mode. */
        if (replay.nameTag.get().isEmpty() || replay.relative.get() || form == null || !form.visible.get())
        {
            return;
        }

        Map<String, Integer> actors = this.getActors();
        Integer entityId = actors == null ? null : actors.get(replay.getId());
        World world = MinecraftClient.getInstance().world;
        Entity actor = entityId == null || world == null ? null : world.getEntityById(entityId);

        if (actor == null)
        {
            return;
        }

        float transition = context.tickDelta();

        /* Vanilla's own render position for this entity (see WorldRenderer#render), so the tag sits
         * exactly above the body instead of above the keyframe the server sent it to. */
        double x = Lerps.lerp(actor.lastRenderX, actor.getX(), transition);
        double y = Lerps.lerp(actor.lastRenderY, actor.getY(), transition);
        double z = Lerps.lerp(actor.lastRenderZ, actor.getZ(), transition);

        BlockPos pos = BlockPos.ofFloored(x, y + 0.5D, z);
        int sky = world.getLightLevel(LightType.SKY, pos);
        int torch = world.getLightLevel(LightType.BLOCK, pos);
        int light = LightmapTextureManager.pack(torch, sky);

        Camera camera = context.camera();
        MatrixStack stack = context.matrixStack();

        stack.push();
        stack.translate(x - camera.getPos().x, y - camera.getPos().y, z - camera.getPos().z);

        renderNameTag(entity, Text.literal(StringUtils.processColoredText(replay.nameTag.get())), stack, context.consumers(), light);

        stack.pop();
    }

    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        return FilmControllerContext.instance
            .setup(this.entities, entity, replay, context)
            .shadow(replay.shadow.get(), replay.shadowSize.get())
            .nameTag(replay.nameTag.get())
            .relative(replay.relative.get());
    }

    public void shutdown()
    {
        /* A live morphed player outlives the film - without this its bow would
         * stay drawn forever after the playback stops */
        ThirdPersonItemUse.clear();
        ItemUseEffects.clear();
        LivePlayerItemUse.clear();
    }

    public static enum UpdateMode
    {
        UPDATE, RENDER, PROPERTIES;
    }
}
