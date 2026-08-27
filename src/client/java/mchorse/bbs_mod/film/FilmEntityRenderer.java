package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
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
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.Map;

/**
 * Drawing one replay's actor into the world: its form, the gizmo axes when it is the one being
 * edited, and its name tag.
 *
 * <p>Split out of {@link BaseFilmController} for the same reason as {@link FilmMatrices} — it is
 * static, it is long, and it is what every host of a film (the editor, the world, the export)
 * calls to put an actor on screen, rather than part of running the film itself.
 */
public class FilmEntityRenderer
{
    public static void renderEntity(FilmControllerContext context)
    {
        Map<String, IEntity> entities = context.entities;
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

        boolean relative = context.replay != null && context.relative;
        Vector3d origin = relative
            ? context.replay.getRelativeOrigin()
            : new Vector3d(camera.getPos().x, camera.getPos().y, camera.getPos().z);

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
            Pair<Matrix4f, Float> pair = FilmMatrices.getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0, false, anchorFrame);

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
            Matrix4f defaultWorldMatrix = FilmMatrices.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);
            Pair<Matrix4f, Float> pairWorld = FilmMatrices.getTotalMatrix(entities, form.anchor.get(), defaultWorldMatrix, 0D, 0D, 0D, transition, 0, false, anchorFrame);

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
        String mapKey = FilmMatrices.boneMapKey(bone);
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

            RenderSystem.enableDepthTest();
            stack.pop();
        }
    }

    /**
     * The replay's "axes preview" (a secondary bone): plain, non-interactive
     * cool axes via {@link Draw#coolerAxes} — not the editing gizmo. Resolves the
     * bone matrix exactly like {@link #renderAxes} and applies the same
     * distance scaling the gizmo uses - the "fixed gizmo size" setting included, since
     * the whole point is that the preview matches the gizmo's axes.
     */
    private static void renderPreviewAxes(String bone, boolean local, Form form, IEntity entity, float transition, MatrixStack stack, FormFrameCache frame)
    {
        String mapKey = FilmMatrices.boneMapKey(bone);
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
        float distanceScale = BBSSettings.getGizmoDistanceScale(cameraRelative.length(), fov);

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
    private static void renderAnchorGizmo(Map<String, IEntity> entities, IEntity entity, Matrix4f full, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, boolean local, TransformSpace space, Matrix4f gizmoView, StencilMap stencilMap, MatrixStack stack, FormFrameCache frame)
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
            Matrix4f parent = FilmMatrices.getEntityMatrix(entities, cx, cy, cz, form.anchor.get(), defaultMatrix, transition, 0, true, frame);

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

        RenderSystem.enableDepthTest();
        stack.pop();
    }

    static void renderNameTag(IEntity entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
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
}
