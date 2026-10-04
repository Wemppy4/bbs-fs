package mchorse.bbs_mod.film;

import mchorse.bbs_mod.api.client.events.FilmGizmoEvents;
import mchorse.bbs_mod.api.client.events.FormPoseEvents;

import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.client.renderer.DeathPose;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoSize;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.Map;

/**
 * Drawing one replay's actor into the world: its form, the gizmo axes when it is the one
 * being edited, and its name tag. Split out of {@link BaseFilmController} like
 * {@link FilmMatrices}: every host of a film calls it to put an actor on screen.
 */
public class FilmEntityRenderer
{
    public static void renderEntity(FilmControllerContext context)
    {
        FormPoseEvents.ACTOR_BEFORE.invoker().prepare(context);

        Map<String, IEntity> entities = context.entities;
        IEntity entity = context.entity;
        Camera camera = context.camera;
        MatrixStack stack = context.stack;
        float transition = context.transition;

        Form form = entity.getForm();

        Vector3d position = Vectors.TEMP_3D.set(
            Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
            Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
            Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
        );

        boolean relative = context.replay != null && context.relative;
        Vector3d origin = relative
            ? context.replay.getRelativeOrigin()
            : new Vector3d(camera.position.x, camera.position.y, camera.position.z);

        double cx = origin.x;
        double cy = origin.y;
        double cz = origin.z;

        Matrix4f target = null;
        Matrix4f defaultMatrix = FilmMatrices.getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        float opacity = 1F;

        /* The anchor is resolved twice below — once against the camera and once against the world origin —
         * and the pose evaluation inside is identical for both (it is camera-independent). Only pure matrix
         * math separates the two calls, so one evaluation covers them; see FormFrameCache on why the scope
         * is this narrow and not the whole frame. Deliberately dropped before the form renders: rendering
         * applies the form's animation states, which move the pose. */
        FormFrameCache anchorFrame = relative ? null : new FormFrameCache();

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = FilmMatrices.getTotalMatrix(entities, FilmMatrices.getAnchor(entity, context.replay), defaultMatrix, cx, cy, cz, transition, 0, false, anchorFrame);

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

        if (form == null)
        {
            FilmTarget gizmo = context.gizmoTarget;
            if (UIBaseMenu.shouldRenderAxes())
            {
                if (gizmo.is(FilmTarget.Kind.ANCHOR)) renderAnchorGizmo(entities, entity, target, defaultMatrix, cx, cy, cz, transition, gizmo.space(), context.gizmoView, context.map, stack, anchorFrame);
                else if (gizmo.is(FilmTarget.Kind.ROOT)) renderReplayGizmo(entity, cx, cy, cz, transition, gizmo.space(), context.gizmoView, context.map, stack);
            }
            return;
        }

        Matrix4f targetWorld;

        if (relative)
        {
            targetWorld = new Matrix4f(target);
        }
        else
        {
            Matrix4f defaultWorldMatrix = FilmMatrices.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);
            Pair<Matrix4f, Float> pairWorld = FilmMatrices.getTotalMatrix(entities, FilmMatrices.getAnchor(entity, context.replay), defaultWorldMatrix, 0D, 0D, 0D, transition, 0, false, anchorFrame);

            targetWorld = pairWorld.a != null ? pairWorld.a : defaultWorldMatrix;
        }

        BlockPos pos = new BlockPos(position.x, position.y + 0.5D, position.z);
        int sky = entity.getWorld().getLightFor(EnumSkyBlock.SKY, pos);
        int torch = entity.getWorld().getLightFor(EnumSkyBlock.BLOCK, pos);
        int light = (torch << 4) | (sky << 20);
        int overlay = entity.getHurtTimer() > 0 ? (3 << 16) : (10 << 16);

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

        /* Vanilla's fall, on a body vanilla is not drawing. An actor's shell tips over across the
         * 20 ticks of its death, and the film draws the replay in its place, so the roll has to be
         * put on here or a killed actor stands to attention until it vanishes. Exactly where the
         * entity renderer puts it - after the body yaw, so the body falls sideways relative to
         * itself - and zero, hence nothing, for everyone still alive. */
        DeathPose.apply(stack, entity.getDeathTime(), transition);

        FormUtilsClient.render(form, formContext);

        /* A second, post-render span: the gizmo, the axes preview and the anchor gizmo are adjacent and all
         * read the pose the form just rendered with (states applied), so they share one evaluation — the
         * gizmo and the preview resolve the very same form and entity, which in the editor is every frame,
         * in both the visible and the stencil-picking pass. It must stay separate from `anchorFrame` above,
         * which was taken before the states moved the pose. */
        FormFrameCache gizmoFrame = UIBaseMenu.shouldRenderAxes() ? new FormFrameCache() : null;

        FilmTarget gizmoTarget = context.gizmoTarget;

        if (UIBaseMenu.shouldRenderAxes())
        {
            /* The bone case is the only one drawn INSIDE the form's frame; the other two are
             * placed on matrices of their own and so live past the pop below. Both halves read
             * the one target, so they cannot disagree about which of the three it is. */
            if (gizmoTarget.boneOrNull() != null) renderAxes(gizmoTarget.bone(), gizmoTarget.space(), context.gizmoView, context.map, form, entity, transition, stack, gizmoFrame);
            if (gizmoTarget.is(FilmTarget.Kind.SPLINE_POINT)) renderSplineGizmo(gizmoTarget, context.gizmoView, context.map, form, entity, transition, stack);
            if (context.bone2 != null && context.map == null) renderPreviewAxes(context.bone2, context.space2, form, entity, transition, stack, gizmoFrame, context.gizmoViewportHeight);
        }

        stack.pop();

        if (UIBaseMenu.shouldRenderAxes())
        {
            if (gizmoTarget.is(FilmTarget.Kind.ANCHOR))
            {
                renderAnchorGizmo(entities, entity, target, defaultMatrix, cx, cy, cz, transition, gizmoTarget.space(), context.gizmoView, context.map, stack, gizmoFrame);
            }
            else if (gizmoTarget.is(FilmTarget.Kind.ROOT))
            {
                renderReplayGizmo(entity, cx, cy, cz, transition, gizmoTarget.space(), context.gizmoView, context.map, stack);
            }
        }

        if (!relative && context.map == null && opacity > 0F && context.shadowRadius > 0F && form.visible.get())
        {
            /* No shadow while the form is hidden (form.visible, keyframable) — the form renders
             * nothing then, so its shadow must vanish too.
             *
             * The shadow goes under the replay's PERCEIVED position: shift it by how far the model
             * (form transform + anchor-bone root motion) has moved from rest, mapped into world
             * axes. Moving the position itself, not just the quad, keeps the ground projection and
             * the shading in step. */
            double shadowX = position.x;
            double shadowY = position.y;
            double shadowZ = position.z;

            FormRenderer renderer = FormUtilsClient.getRenderer(FormUtils.getRoot(form));

            if (renderer != null && context.replay != null && context.replay.shadowFollow.get())
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

            FilmOverlayRenderer.renderShadow(entity.getWorld(), stack, camera, shadowX, shadowY, shadowZ, context.shadowRadius, opacity);

            stack.pop();
        }

        if (!relative && !context.nameTag.isEmpty() && context.map == null && form.visible.get())
        {
            /* Hide the name tag along with the form (form.visible, animatable via keyframes): when the
             * form renders nothing, its name tag must vanish too - same reasoning as the shadow above. */
            stack.push();
            stack.translate(position.x - cx, position.y - cy, position.z - cz);

            FilmOverlayRenderer.renderNameTag(entity, StringUtils.processColoredText(context.nameTag), stack, camera);

            stack.pop();
        }

        GlStateManager.enableDepth();
    }

    private static void renderSplineGizmo(FilmTarget target, Matrix4f view, StencilMap map, Form form, IEntity entity, float transition, MatrixStack stack)
    {
        Form root = FormUtils.getRoot(form);
        var point = mchorse.bbs_mod.ui.utils.SplineEditorUtils.resolve(root, target.bone());
        Matrix4f matrix = mchorse.bbs_mod.ui.utils.SplineEditorUtils.pointMatrix(root, entity, transition, point);
        if (matrix == null) return;
        stack.push();
        MatrixStackUtils.multiply(stack, matrix);
        Gizmo.INSTANCE.reorientForSpace(stack, target.space(), view, FilmMatrices.getReplayWorldAxes(entity, transition));
        if (map == null) Gizmo.INSTANCE.captureVisual(stack, mchorse.bbs_mod.ui.utils.SplineEditorUtils.HANDLES);
        else Gizmo.INSTANCE.renderStencil(stack, mchorse.bbs_mod.ui.utils.SplineEditorUtils.HANDLES);
        stack.pop();
    }

    private static void renderAxes(String bone, TransformSpace space, Matrix4f gizmoView, StencilMap stencilMap, Form form, IEntity entity, float transition, MatrixStack stack, FormFrameCache frame)
    {
        String mapKey = FilmMatrices.boneMapKey(bone);
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormFrameCache.collect(frame, root, entity, transition);
        /* Placement flavour straight off the frame (TransformSpace#placesOnOwnFrame). */
        Matrix4f matrix = space.placesOnOwnFrame() ? map.get(mapKey).matrix() : map.get(mapKey).origin();

        if (matrix != null)
        {
            stack.push();
            MatrixStackUtils.multiply(stack, matrix);

            /* Reorient into the active space (the replay's own world axes for
             * GLOBAL, screen axes for VIEW; LOCAL untouched) before the frame is
             * captured — so the visual and the pick stencil, both built from it,
             * stay in lockstep. */
            Gizmo.INSTANCE.reorientForSpace(stack, space, gizmoView, FilmMatrices.getReplayWorldAxes(entity, transition));

            if (stencilMap == null)
            {
                /* The visual is drawn later, in the panel's UI pass (see
                 * Gizmo#renderInterface) — here we only snapshot its placement. */
                Gizmo.INSTANCE.captureVisual(stack);
            }
            else
            {
                Gizmo.INSTANCE.renderStencil(stack);
            }

            GlStateManager.enableDepth();
            stack.pop();
        }
    }

    /** The replay's "axes preview" (a secondary bone): plain non-interactive axes, not the
     *  editing gizmo. Resolved and distance-scaled exactly like {@link #renderAxes}, since
     *  the whole point is that it matches the gizmo's axes. */
    private static void renderPreviewAxes(String bone, TransformSpace space, Form form, IEntity entity, float transition, MatrixStack stack, FormFrameCache frame, float viewportHeight)
    {
        String mapKey = FilmMatrices.boneMapKey(bone);
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormFrameCache.collect(frame, root, entity, transition);
        MatrixCacheEntry entry = map.get(mapKey);

        if (entry == null)
        {
            return;
        }

        boolean ownFrame = space.placesOnOwnFrame();
        Matrix4f matrix = ownFrame ? entry.matrix() : entry.origin();

        if (matrix == null)
        {
            return;
        }

        if (ownFrame) matrix = MatrixStackUtils.stripScale(matrix);

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);

        float distanceScale = GizmoSize.getScale(stack.peek().getPositionMatrix(), mchorse.bbs_mod.graphics.NativeColorProgram.projection(), viewportHeight);

        stack.scale(distanceScale, distanceScale, distanceScale);
        Draw.coolerAxes(stack, 0.25F, 0.008F);

        GlStateManager.enableDepth();
        stack.pop();
    }

    /**
     * The editing gizmo for the form's anchor offset. The anchor is applied as
     * {@code parent.mul(transform)}, so the gizmo sits at the resolved matrix {@code full}
     * and edits {@code replay.anchor.transform}. Placement mirrors {@link #renderAxes}: the
     * anchor's own orientation for LOCAL, the attachment's (this path's origin flavour)
     * otherwise, reoriented into the active frame just the same.
     */
    private static void renderAnchorGizmo(Map<String, IEntity> entities, IEntity entity, Matrix4f full, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, TransformSpace space, Matrix4f gizmoView, StencilMap stencilMap, MatrixStack stack, FormFrameCache frame)
    {
        if (full == null)
        {
            return;
        }

        Matrix4f matrix;

        if (space.placesOnOwnFrame())
        {
            matrix = MatrixStackUtils.stripScale(full);
        }
        else
        {
            Matrix4f parent = FilmMatrices.getEntityMatrix(entities, cx, cy, cz, FilmMatrices.getAnchor(entity), defaultMatrix, transition, 0, true, frame);

            matrix = MatrixStackUtils.stripScale(parent);
            matrix.setTranslation(full.getTranslation(new Vector3f()));
        }

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);

        /* Same lockstep as renderAxes: reorient before the frame is captured, so
         * the visual and the pick stencil built from it agree with the drag. */
        Gizmo.INSTANCE.reorientForSpace(stack, space, gizmoView, FilmMatrices.getReplayWorldAxes(entity, transition));

        if (stencilMap == null)
        {
            /* The visual is drawn later, in the panel's UI pass (see
             * Gizmo#renderInterface) — here we only snapshot its placement. */
            Gizmo.INSTANCE.captureVisual(stack);
        }
        else
        {
            Gizmo.INSTANCE.renderStencil(stack);
        }

        GlStateManager.enableDepth();
        stack.pop();
    }

    /**
     * Draw the gizmo on the replay's own placement: the frame the whole actor is rendered
     * under, {@code translate(x, y, z) · rotateY(-bodyYaw)}, and nothing from inside the
     * form. It is deliberately the very matrix {@link BaseFilmController#renderEntity}
     * places the actor with, so the gizmo sits exactly where the actor does &mdash; and
     * deliberately flat: Y stays vertical, so the Y ring turns about the world's up (the
     * yaw channels) and the move bars run along the actor's own left/right/up.
     *
     * <p>The mask keeps scale, roll, the trackball and the view ring out of both passes:
     * a record has three position channels and two angles, and no third rotational
     * degree of freedom for the rest to write into.
     */
    private static void renderReplayGizmo(IEntity entity, double cx, double cy, double cz, float transition, TransformSpace space, Matrix4f gizmoView, StencilMap stencilMap, MatrixStack stack)
    {
        if (FilmGizmoEvents.DRAW.invoker().draw(FilmControllerContext.instance, stencilMap, stack)) return;

        stack.push();
        MatrixStackUtils.multiply(stack, FilmMatrices.getMatrixForRenderWithRotation(entity, cx, cy, cz, transition));

        /* Same lockstep as renderAxes: reorient before the frame is captured, so the
         * visual and the pick stencil built from it agree with the drag. */
        Gizmo.INSTANCE.reorientForSpace(stack, space, gizmoView, FilmMatrices.getReplayWorldAxes(entity, transition));

        if (stencilMap == null)
        {
            Gizmo.INSTANCE.captureVisual(stack, FilmGizmoMasks.REPLAY_TRANSFORM);
        }
        else
        {
            Gizmo.INSTANCE.renderStencil(stack, FilmGizmoMasks.REPLAY_TRANSFORM);
        }

        GlStateManager.enableDepth();
        stack.pop();
    }

}
