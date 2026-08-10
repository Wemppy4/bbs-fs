package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class ModelBlockEntityRenderer implements BlockEntityRenderer<ModelBlockEntity, ModelBlockEntityRenderer.ModelBlockRenderState>
{
    /**
     * Immediate-context shadow (the film's AFTER_ENTITIES rendering): build the vanilla-style
     * shadow pieces under (x, y, z) and draw them through the entity-shadow layer right away —
     * see {@link FormShadows}. The provider parameter is a 1.21.1 leftover, kept for the call
     * sites; the draw is immediate either way.
     */
    public static void renderShadow(VertexConsumerProvider provider, MatrixStack matrices, float tickDelta, double x, double y, double z, float tx, float ty, float tz)
    {
        renderShadow(provider, matrices, tickDelta, x, y, z, tx, ty, tz, 0.5F, 1F);
    }

    public static void renderShadow(VertexConsumerProvider provider, MatrixStack matrices, float tickDelta, double x, double y, double z, float tx, float ty, float tz, float radius, float opacity)
    {
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return;
        }

        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        List<EntityRenderState.ShadowPiece> pieces = FormShadows.buildPieces(world, x, y, z, radius, opacity, cameraPos.squaredDistanceTo(x, y, z));

        if (pieces.isEmpty())
        {
            return;
        }

        matrices.push();
        matrices.translate(tx, ty, tz);

        FormShadows.drawImmediate(matrices, pieces, Math.min(radius, 32F));

        matrices.pop();
    }

    private static float getHeadYaw(float constraint, float yawDelta, float travel)
    {
        float headLimit = (float) Math.toRadians(constraint);
        float headYawBase = MathUtils.clamp(yawDelta, -headLimit, headLimit);

        float syncStart = (float) Math.toRadians(315D);
        float syncRange = (float) Math.toRadians(45D);
        float t = 0F;

        if (travel >= syncStart)
        {
            t = Math.min(1F, (travel - syncStart) / syncRange);
        }

        return headYawBase * (1F - t);
    }

    public ModelBlockEntityRenderer(BlockEntityRendererFactory.Context ctx)
    {}

    @Override
    public ModelBlockRenderState createRenderState()
    {
        return new ModelBlockRenderState();
    }

    @Override
    public void updateRenderState(ModelBlockEntity blockEntity, ModelBlockRenderState state, float tickDelta, Vec3d cameraPos, net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay)
    {
        BlockEntityRenderer.super.updateRenderState(blockEntity, state, tickDelta, cameraPos, crumblingOverlay);

        state.entity = blockEntity;
        state.tickDelta = tickDelta;
    }

    @Override
    public boolean rendersOutsideBoundingBox()
    {
        /* TODO(1.21.11 render): rendersOutsideBoundingBox no longer receives the block entity; global
         * model blocks must always be allowed to render outside their bounding box, so report true. */
        return true;
    }

    @Override
    public void render(ModelBlockRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState)
    {
        ModelBlockEntity entity = state.entity;

        if (entity == null)
        {
            return;
        }

        /* The form below draws into the world's frame, so under a shaderpack it must carry the pack's
         * programs — the span makes BBSShaders hand it the world pipeline variants (see
         * BBSRendering#beginWorldForms). Block entities render in their own vanilla pass, so this
         * needs its own span; the one around the film's AFTER_ENTITIES drawing has already closed. */
        boolean prevWorldForms = BBSRendering.beginWorldForms();

        try
        {
            this.renderBlock(state, entity, matrices, queue, cameraState);
        }
        finally
        {
            BBSRendering.endWorldForms(prevWorldForms);
        }
    }

    private void renderBlock(ModelBlockRenderState state, ModelBlockEntity entity, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState)
    {

        float tickDelta = state.tickDelta;
        int overlay = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;
        MinecraftClient mc = MinecraftClient.getInstance();
        ModelProperties properties = entity.getProperties();
        Transform transform = properties.getTransform();
        BlockPos pos = entity.getPos();

        matrices.push();
        matrices.translate(0.5F, 0F, 0.5F);

        if (properties.getForm() != null && this.canRender(entity))
        {
            matrices.push();

            Transform applied = transform;

            if (properties.isLookAt())
            {
                applied = this.applyLookingAnimation(mc, entity, properties, tickDelta);
            }
            else
            {
                IEntity iEntity = entity.getEntity();

                entity.resetLookYaw();
                iEntity.setHeadYaw(0F);
                iEntity.setPrevHeadYaw(0F);
                iEntity.setPitch(0F);
                iEntity.setPrevPitch(0F);
            }

            MatrixStackUtils.applyTransform(matrices, applied);

            int lightAbove = WorldRenderer.getLightmapCoordinates(entity.getWorld(), pos.add((int) transform.translate.x, (int) transform.translate.y, (int) transform.translate.z));
            Camera camera = mc.gameRenderer.getCamera();

            /* TODO(1.21.11 render): depth state is now pipeline-encoded; RenderSystem.enableDepthTest was removed. */
            FormUtilsClient.render(properties.getForm(), new FormRenderingContext()
                .set(FormRenderType.MODEL_BLOCK, entity.getEntity(), matrices, lightAbove, overlay, tickDelta)
                .camera(camera));

            if (this.canRenderAxes(entity) && UIBaseMenu.shouldRenderAxes())
            {
                matrices.push();
                MatrixStackUtils.scaleBack(matrices);
                Draw.coolerAxes(matrices, 0.5F, 0.005F);
                matrices.pop();
            }

            matrices.pop();
        }

        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard
            && dashboard.getPanels().panel instanceof UIModelBlockPanel modelBlockPanel)
        {
            modelBlockPanel.renderWorldGizmo(matrices, entity);
        }

        if (mc.getDebugHud().shouldShowDebugHud())
        {
            Draw.renderBox(matrices, -0.5D, 0, -0.5D, 1, 1, 1, 0, 0.5F, 1F, 0.5F);
        }

        matrices.pop();

        if (properties.isShadow())
        {
            float tx = 0.5F + transform.translate.x;
            float ty = transform.translate.y;
            float tz = 0.5F + transform.translate.z;
            double x = pos.getX() + tx;
            double y = pos.getY() + ty;
            double z = pos.getZ() + tz;

            /* Queue-context shadow: record the pieces for the entity-shadow pass. Same pieces
             * the immediate overload draws (FormShadows), submitted the vanilla 1.21.5+ way. */
            ClientWorld world = mc.world;

            if (world != null)
            {
                Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
                List<EntityRenderState.ShadowPiece> pieces = FormShadows.buildPieces(world, x, y, z, 0.5F, 1F, cameraPos.squaredDistanceTo(x, y, z));

                if (!pieces.isEmpty())
                {
                    matrices.push();
                    matrices.translate(tx, ty, tz);

                    queue.submitShadowPieces(matrices, 0.5F, pieces);

                    matrices.pop();
                }
            }
        }
    }

    private Transform applyLookingAnimation(MinecraftClient mc, ModelBlockEntity entity, ModelProperties properties, float tickDelta)
    {
        Transform transform = properties.getTransform();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d position = !mc.options.getPerspective().isFirstPerson() && mc.player != null
            ? mc.player.getCameraPosVec(tickDelta)
            : camera.getCameraPos();

        BlockPos pos = entity.getPos();
        double x = pos.getX() + 0.5D + transform.translate.x;
        double y = pos.getY() + transform.translate.y;
        double z = pos.getZ() + 0.5D + transform.translate.z;

        double dx = position.x - x;
        double dz = position.z - z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        float initialYaw = lookYaw(transform);
        float yaw = (float) Math.atan2(dx, dz);
        float yawContinuous = entity.updateLookYawContinuous(yaw);
        float yawDelta = yawContinuous - initialYaw;
        float travel = Math.abs(yawDelta) % (MathUtils.PI * 2F);

        Transform finalTransform = transform.copy();
        Form form = properties.getForm();
        boolean lookAt = form instanceof MobForm;
        float headHeight = form.hitboxHeight.get() * form.hitboxEyeHeight.get() * finalTransform.scale.y;
        float constraint = 45F;
        boolean isPitching = true;

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            View view = model == null ? null : model.getView();

            if (view != null)
            {
                String headKey = view.headBone;

                lookAt = true;
                constraint = view.constraint;
                isPitching = view.pitch;

                if (FormUtilsClient.getBones(modelForm).contains(headKey))
                {
                    MatrixCache matrices = new MatrixCache();

                    model.captureMatrices(matrices);

                    Matrix4f matrix = matrices.get(headKey).matrix();

                    if (matrix != null)
                    {
                        headHeight = matrix.getTranslation(new Vector3f()).y * finalTransform.scale.y;
                    }
                }
            }
        }

        setLookYaw(finalTransform, yawContinuous);

        if (lookAt)
        {
            IEntity iEntity = entity.getEntity();
            double deltaHead = position.y - (y + headHeight);
            float pitch = MathUtils.clamp((float) Math.atan2(deltaHead, distance), -MathUtils.PI / 2F, MathUtils.PI / 2F);
            float headYaw = getHeadYaw(constraint, yawDelta, travel);
            float anchorYaw = yawDelta - headYaw;

            if (travel >= (float) Math.toRadians(359D))
            {
                headYaw = 0F;
                anchorYaw = 0F;

                entity.snapLookYawToBase(yaw, initialYaw);
            }

            setLookYaw(finalTransform, initialYaw + anchorYaw);
            headYaw = -MathUtils.toDeg(headYaw);
            pitch = -MathUtils.toDeg(isPitching ? pitch : 0F);

            iEntity.setHeadYaw(headYaw);
            iEntity.setPrevHeadYaw(headYaw);
            iEntity.setPitch(pitch);
            iEntity.setPrevPitch(pitch);
        }

        return finalTransform;
    }

    /**
     * The block transform's ZYX yaw channel, mode-aware: the euler channel directly, or — on a
     * quaternion transform, where the channels are stale — the quat decomposed on the branch
     * nearest those stale channels, so the yaw reads the same value the euler mode would hold
     * (a naive principal decomposition flips branches past ±90° and would read a wrong yaw).
     */
    private static float lookYaw(Transform transform)
    {
        if (transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            return Matrices.toCompatibleEulerZYXRadians(transform.quat, transform.rotate, new Vector3f()).y;
        }

        return transform.rotate.y;
    }

    /**
     * Writes the ZYX yaw channel mode-aware: the euler channel directly, or the quaternion
     * re-composed about the same compatible decomposition's X/Z tilt with the new yaw — the exact
     * quaternion equivalent of {@code rotate.y = yaw}, so look-at turns a quaternion-mode block
     * identically to a euler one.
     */
    private static void setLookYaw(Transform transform, float yaw)
    {
        if (transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            Vector3f euler = Matrices.toCompatibleEulerZYXRadians(transform.quat, transform.rotate, new Vector3f());

            transform.quat.rotationZYX(euler.z, yaw, euler.x);

            return;
        }

        transform.rotate.y = yaw;
    }

    @Override
    public int getRenderDistance()
    {
        return 512;
    }

    private boolean canRenderAxes(ModelBlockEntity entity)
    {
        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIModelBlockPanel modelBlockPanel)
            {
                /* The selected block shows the interactive gizmo instead of the plain axes. */
                return !modelBlockPanel.isShowingGizmo(entity);
            }
        }

        return false;
    }

    private boolean canRender(ModelBlockEntity entity)
    {
        if (!entity.getProperties().isEnabled())
        {
            return false;
        }

        if (!BBSSettings.renderAllModelBlocks.get())
        {
            return false;
        }

        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIModelBlockPanel modelBlockPanel)
            {
                return !modelBlockPanel.isEditing(entity) || UIModelBlockPanel.toggleRendering;
            }
        }

        return true;
    }

    /**
     * Carries the live block entity + tick delta through the render-state model.
     *
     * TODO(1.21.11 render): carrying the live entity is a build-only bridge until the BBS form
     * pipeline is adapted to read off the render state.
     */
    public static class ModelBlockRenderState extends BlockEntityRenderState
    {
        public ModelBlockEntity entity;
        public float tickDelta;
    }
}
