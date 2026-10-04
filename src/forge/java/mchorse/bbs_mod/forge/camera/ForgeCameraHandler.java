package mchorse.bbs_mod.forge.camera;

import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * Forge 1.12 rendering boundary for the original BBS camera controller stack.
 * The view entity is client-only: moving an editor camera never moves the player
 * or sends a player position packet to the server.
 */
public final class ForgeCameraHandler
{
    private static float orthoDistance = -1F;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final CameraController controller;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Matrix4f projection = new Matrix4f();
    private World lastWorld;
    private CameraEntity view;
    private Entity previousView;
    private int previousPerspective;
    private boolean previousCulling;
    private boolean changedCulling;

    public ForgeCameraHandler(CameraController controller)
    {
        this.controller = controller;
        this.lastWorld = this.mc.world;
    }

    /** Re-armed by OrbitViewportController.setup for each rendered frame. */
    public static void setOrthoDistance(float distance)
    {
        orthoDistance = distance;
    }

    public static boolean isOrthoActive()
    {
        return orthoDistance > 0F;
    }

    public boolean isActive()
    {
        return this.view != null && this.controller.getCurrent() != null
            && this.mc.getRenderViewEntity() == this.view;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        this.checkWorld();

        if (this.mc.world != null && !this.mc.isGamePaused())
        {
            this.controller.update();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START)
        {
            return;
        }

        this.checkWorld();
        this.restoreCulling();
        orthoDistance = -1F;

        if (this.mc.world == null || this.mc.player == null)
        {
            this.releaseView();
            return;
        }

        if (this.controller.getCurrent() == null)
        {
            this.releaseView();
            Entity entity = this.mc.getRenderViewEntity();

            if (entity != null)
            {
                this.controller.camera.set(entity, MathUtils.toRad(this.mc.gameSettings.fovSetting), event.renderTickTime);
            }

            return;
        }

        if (this.view == null)
        {
            this.previousView = this.mc.getRenderViewEntity();
            this.previousPerspective = this.mc.gameSettings.thirdPersonView;
            this.view = new CameraEntity(this.mc.world);
        }

        this.controller.setup(this.controller.camera, event.renderTickTime);
        this.view.apply(this.controller);
        this.mc.setRenderViewEntity(this.view);
        this.mc.gameSettings.thirdPersonView = 0;

        /* Point-camera occlusion is invalid for parallel orthographic rays. */
        if (isOrthoActive())
        {
            this.previousCulling = this.mc.renderChunksMany;
            this.changedCulling = true;
            this.mc.renderChunksMany = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void camera(EntityViewRenderEvent.CameraSetup event)
    {
        if (!this.isActive() || event.getEntity() != this.view)
        {
            return;
        }

        /* Vanilla's first-person 0.05 displacement precedes this event. BBS
         * positions are already eye positions, as in the modern Camera API. */
        GlStateManager.translate(0F, 0F, -0.05F);
        event.setYaw(this.controller.getYaw() + 180F);
        event.setPitch(this.controller.getPitch());
        event.setRoll(this.controller.getRoll());

        this.applyOrthographicProjection();
    }

    /**
     * 1.12 rebuilds the perspective after its sky pass and around clouds, after
     * CameraSetup has already run. Fog setup follows these rebuilds and precedes
     * the terrain/cloud draw, so it is the native boundary for restoring ortho.
     * Fog density and cancellation are deliberately left to the other handlers.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void fogProjection(EntityViewRenderEvent.FogDensity event)
    {
        if (this.isActive() && event.getEntity() == this.view)
        {
            this.applyOrthographicProjection();
        }
    }

    private void applyOrthographicProjection()
    {
        if (!isOrthoActive()) return;

        this.matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, this.matrixBuffer);
        this.projection.set(this.matrixBuffer);

        /* Several fog setups can share one projection. Never convert twice. */
        if (this.projection.m33() != 0F) return;

        float halfHeight = orthoDistance / this.projection.m11();
        float halfWidth = halfHeight * this.projection.m11() / this.projection.m00();
        float far = this.projection.m32() / (this.projection.m22() + 1F);

        this.projection.setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, 0F, far);
        this.matrixBuffer.clear();
        this.projection.get(this.matrixBuffer);

        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(this.matrixBuffer);
        GlStateManager.matrixMode(matrixMode);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fov(EntityViewRenderEvent.FOVModifier event)
    {
        if (this.isActive() && event.getEntity() == this.view)
        {
            event.setFOV((float) this.controller.getFOV());
        }
    }

    @SubscribeEvent
    public void hand(RenderHandEvent event)
    {
        if (this.isActive())
        {
            event.setCanceled(true);
        }
    }

    /** Keep screen-space picking and overlays on the actual rendered matrices. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void worldRendered(RenderWorldLastEvent event)
    {
        if (this.mc.world == null)
        {
            return;
        }

        /* The above-cloud pass restores perspective immediately before this event. */
        if (this.isActive()) this.applyOrthographicProjection();

        this.matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, this.matrixBuffer);
        this.controller.camera.projection.set(this.matrixBuffer);
        this.matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, this.matrixBuffer);
        this.controller.camera.view.set(this.matrixBuffer);

        if (!this.isActive())
        {
            /* The ordinary 1.12 model-view still contains -eyeHeight. BBS view
             * matrices are relative to the camera's eye, not the entity's feet. */
            Entity entity = this.mc.getRenderViewEntity();

            if (entity != null)
            {
                this.controller.camera.view.translate(0F, entity.getEyeHeight(), 0F);
            }
        }
    }

    /** Restore the game's camera and discard controllers which belong to a world. */
    public void reset()
    {
        this.releaseView();

        while (this.controller.getCurrent() != null)
        {
            this.controller.remove(this.controller.getCurrent());
        }

        this.controller.reset();
        orthoDistance = -1F;
    }

    private void checkWorld()
    {
        if (this.mc.world != this.lastWorld)
        {
            if (this.lastWorld != null)
            {
                this.reset();
            }

            this.lastWorld = this.mc.world;
        }
    }

    private void restoreCulling()
    {
        if (this.changedCulling)
        {
            this.mc.renderChunksMany = this.previousCulling;
            this.changedCulling = false;
        }
    }

    private void releaseView()
    {
        this.restoreCulling();

        if (this.view == null)
        {
            return;
        }

        if (this.mc.getRenderViewEntity() == this.view)
        {
            Entity restore = this.previousView;

            if (restore == null || restore.world != this.mc.world || restore.isDead)
            {
                restore = this.mc.player;
            }

            this.mc.setRenderViewEntity(restore);
        }

        /* UIDashboard restores its own perspective when it closes. Preserve
         * that explicit restore instead of replacing it with the captured 0. */
        if (this.mc.gameSettings.thirdPersonView == 0)
        {
            this.mc.gameSettings.thirdPersonView = this.previousPerspective;
        }

        this.view = null;
        this.previousView = null;
    }

    private static final class CameraEntity extends Entity
    {
        CameraEntity(World world)
        {
            super(world);
            this.noClip = true;
            this.setSize(0F, 0F);
        }

        void apply(CameraController controller)
        {
            this.setPosition(controller.getPosition().x, controller.getPosition().y, controller.getPosition().z);
            this.prevPosX = this.lastTickPosX = this.posX;
            this.prevPosY = this.lastTickPosY = this.posY;
            this.prevPosZ = this.lastTickPosZ = this.posZ;
            this.prevRotationYaw = this.rotationYaw = controller.getYaw();
            this.prevRotationPitch = this.rotationPitch = controller.getPitch();
        }

        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound tag) {}
        @Override protected void writeEntityToNBT(NBTTagCompound tag) {}
        @Override public float getEyeHeight() { return 0F; }
    }
}