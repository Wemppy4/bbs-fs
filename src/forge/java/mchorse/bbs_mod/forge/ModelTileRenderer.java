package mchorse.bbs_mod.forge;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
public class ModelTileRenderer extends TileEntitySpecialRenderer<ModelTileEntity> {
    public void render(ModelTileEntity tile, double x, double y, double z, float partialTicks, int stage, float alpha) {
        if (stage >= 0) { ModelBlockBreakingRenderer.render(tile, x, y, z, partialTicks, stage); return; }
        mchorse.bbs_mod.client.BBSRendering.capturedModelBlocks.add(tile);
        StubEntity entity=tile.getRenderEntity();
        entity.setWorld(tile.getWorld());entity.setForm(tile.getProperties().getForm());
        if (!canRender(tile) || tile.getProperties().getForm() == null) return;
        ModelProperties properties = tile.getProperties();
        Transform transform = properties.getTransform();
        entity.setPosition(tile.getPos().getX()+0.5,tile.getPos().getY(),tile.getPos().getZ()+0.5);
        entity.setPrevX(entity.getX());entity.setPrevY(entity.getY());entity.setPrevZ(entity.getZ());
        mchorse.bbs_mod.graphics.WorldRenderContext frame = mchorse.bbs_mod.graphics.WorldRenderContext.capture(partialTicks);
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().set(mchorse.bbs_mod.forge.studio.NativeTextureRenderer.currentMatrix())
            .translate((float)x + 0.5F, (float)y, (float)z + 0.5F);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
        Transform applied = transform;
        if (properties.isLookAt()) applied = this.applyLookingAnimation(Minecraft.getMinecraft(), tile, properties, partialTicks);
        else {
            tile.resetLookYaw(); entity.setHeadYaw(0); entity.setPrevHeadYaw(0); entity.setPitch(0); entity.setPrevPitch(0);
        }
        MatrixStack base = new MatrixStack();
        base.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
        MatrixStackUtils.applyTransform(matrices, applied);
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        int mode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE);
        GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            FormRenderingContext context=new FormRenderingContext().set(FormRenderType.MODEL_BLOCK,entity,matrices,
                tile.getWorld().getCombinedLight(tile.getPos().add((int) transform.translate.x, (int) transform.translate.y, (int) transform.translate.z),0),10<<16,partialTicks).camera(frame.camera());
            GlStateManager.enableDepth();
            FormUtilsClient.render(properties.getForm(),context);
            if (properties.isShadow()) {
                base.translate(transform.translate.x, transform.translate.y, transform.translate.z);
                mchorse.bbs_mod.film.FilmOverlayRenderer.renderShadow(tile.getWorld(), base, frame.camera(),
                    tile.getPos().getX()+.5+transform.translate.x, tile.getPos().getY()+transform.translate.y,
                    tile.getPos().getZ()+.5+transform.translate.z, .5F, 1F);
            }
            if (this.canRenderAxes(tile) && UIBaseMenu.shouldRenderAxes() && !mchorse.bbs_mod.BBSModClient.getVideoRecorder().isRecording()) {
                matrices.push(); MatrixStackUtils.scaleBack(matrices);
                mchorse.bbs_mod.graphics.Draw.coolerAxes(matrices, .5F, .005F); matrices.pop();
            }
        } finally {
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(mode);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
    }
    @Override public boolean isGlobalRenderer(ModelTileEntity tile) { return tile.getProperties().isGlobal(); }
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

    private Transform applyLookingAnimation(Minecraft mc, ModelTileEntity entity, ModelProperties properties, float tickDelta)
    {
        Transform transform = properties.getTransform();
        mchorse.bbs_mod.camera.Camera camera = mchorse.bbs_mod.graphics.WorldRenderContext.capture(tickDelta).camera();
        Vec3d position = mc.gameSettings.thirdPersonView != 0 && mc.player != null
            ? mc.player.getPositionEyes(tickDelta)
            : new Vec3d(camera.position.x, camera.position.y, camera.position.z);

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

    private boolean canRenderAxes(ModelTileEntity entity)
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

    private boolean canRender(ModelTileEntity entity)
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
                return !modelBlockPanel.isEditing(entity) || modelBlockPanel.isRenderingToggled();
            }
        }

        return true;
    }
}
