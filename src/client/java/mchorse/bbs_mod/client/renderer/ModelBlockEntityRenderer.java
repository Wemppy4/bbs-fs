package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.entity.ActorEntity;
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
import mchorse.bbs_mod.mixin.client.EntityRendererDispatcherInvoker;
import mchorse.bbs_mod.mixin.client.WorldRendererAccessor;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.SortedSet;

public class ModelBlockEntityRenderer implements BlockEntityRenderer<ModelBlockEntity>
{
    private static ActorEntity entity;

    public static void renderShadow(VertexConsumerProvider provider, MatrixStack matrices, float tickDelta, double x, double y, double z, float tx, float ty, float tz)
    {
        renderShadow(provider, matrices, tickDelta, x, y, z, tx, ty, tz, 0.5F, 1F);
    }

    public static void renderShadow(VertexConsumerProvider provider, MatrixStack matrices, float tickDelta, double x, double y, double z, float tx, float ty, float tz, float radius, float opacity)
    {
        ClientWorld world = MinecraftClient.getInstance().world;

        if (entity == null || entity.getWorld() != world)
        {
            entity = new ActorEntity(BBSMod.ACTOR_ENTITY, world);
        }

        entity.setPos(x, y, z);
        entity.lastRenderX = x;
        entity.lastRenderY = y;
        entity.lastRenderZ = z;
        entity.prevX = x;
        entity.prevY = y;
        entity.prevZ = z;

        double distance = MinecraftClient.getInstance().getEntityRenderDispatcher().getSquaredDistanceToCamera(x, y, z);

        opacity = (float) ((1D - distance / 256D) * opacity);

        matrices.push();
        matrices.translate(tx, ty, tz);

        EntityRendererDispatcherInvoker.bbs$renderShadow(matrices, provider, entity, opacity, tickDelta, entity.getWorld(), radius);

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
    public boolean rendersOutsideBoundingBox(ModelBlockEntity blockEntity)
    {
        return blockEntity.getProperties().isGlobal();
    }

    @Override
    public void render(ModelBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModelProperties properties = entity.getProperties();
        Transform transform = properties.getTransform();
        BlockPos pos = entity.getPos();

        /* While the matrices still sit at the cell's corner. */
        this.renderBreakingOverlay(mc, entity, matrices);

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

            RenderSystem.enableDepthTest();
            FormUtilsClient.render(properties.getForm(), new FormRenderingContext()
                .set(FormRenderType.MODEL_BLOCK, entity.getEntity(), matrices, lightAbove, overlay, tickDelta)
                .camera(camera));
            RenderSystem.disableDepthTest();

            if (this.canRenderAxes(entity) && UIBaseMenu.shouldRenderAxes())
            {
                matrices.push();
                MatrixStackUtils.scaleBack(matrices);
                Draw.coolerAxes(matrices, 0.5F, 0.005F);
                matrices.pop();
            }

            matrices.pop();
        }

        RenderSystem.disableDepthTest();

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

            renderShadow(vertexConsumers, matrices, tickDelta, x, y, z, tx, ty, tz);
        }
    }

    private Transform applyLookingAnimation(MinecraftClient mc, ModelBlockEntity entity, ModelProperties properties, float tickDelta)
    {
        Transform transform = properties.getTransform();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d position = !mc.options.getPerspective().isFirstPerson() && mc.player != null
            ? mc.player.getCameraPosVec(tickDelta)
            : camera.getPos();

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

    /**
     * The vanilla mining cracks, painted over the block's hitbox box. The
     * block renders INVISIBLE, so vanilla's own crumbling pass (which redraws
     * the block model) has nothing to draw on — instead the cracks go onto the
     * body's shape here, through the same decal machinery vanilla uses: the
     * per-stage block-breaking layers on the effect buffers, UVs projected
     * from positions by {@link OverlayVertexConsumer}.
     */
    private void renderBreakingOverlay(MinecraftClient mc, ModelBlockEntity entity, MatrixStack matrices)
    {
        SortedSet<BlockBreakingInfo> infos = ((WorldRendererAccessor) mc.worldRenderer).bbs$getBlockBreakingProgressions().get(entity.getPos().asLong());

        if (infos == null || infos.isEmpty())
        {
            return;
        }

        int stage = infos.last().getStage();

        if (stage < 0 || stage >= ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.size())
        {
            return;
        }

        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer consumer = new OverlayVertexConsumer(
            mc.getBufferBuilders().getEffectVertexConsumers().getBuffer(ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)),
            entry.getPositionMatrix(), entry.getNormalMatrix(), 1F
        );

        Box box = entity.getShape().getBoundingBox();
        int light = WorldRenderer.getLightmapCoordinates(entity.getWorld(), entity.getPos());
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        /* Vertices wind counter-clockwise seen from outside each face. */
        quad(consumer, entry, light, 0F, -1F, 0F, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(consumer, entry, light, 0F, 1F, 0F, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1);
        quad(consumer, entry, light, 0F, 0F, -1F, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(consumer, entry, light, 0F, 0F, 1F, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
        quad(consumer, entry, light, -1F, 0F, 0F, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);
        quad(consumer, entry, light, 1F, 0F, 0F, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    private static void quad(VertexConsumer consumer, MatrixStack.Entry entry, int light, float nx, float ny, float nz, float... xyz)
    {
        for (int i = 0; i < 4; i++)
        {
            consumer.vertex(entry.getPositionMatrix(), xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                .color(255, 255, 255, 255)
                .texture(0F, 0F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry.getNormalMatrix(), nx, ny, nz)
                .next();
        }
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
}