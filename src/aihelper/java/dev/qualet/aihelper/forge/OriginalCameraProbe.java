package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.forge.camera.ForgeCameraHandler;
import mchorse.bbs_mod.graphics.window.InputCodes;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.camera.OrbitViewportController;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.joml.Vector3f;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Isolated fixture for the original flight/orbit UI and the real Forge world camera. */
public final class OriginalCameraProbe extends UIBaseMenu
{
    private static OriginalCameraProbe last;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final UIOrbitCamera flight = new UIOrbitCamera();
    private final Camera initial = new Camera();
    private final Vector3f subject = new Vector3f();
    private final float previousSmoothness;
    private Entity previousView;
    private int previousPerspective;
    private boolean previousCulling;
    private boolean open;
    private final java.nio.FloatBuffer fogMatrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private float fogProjectionM23;
    private float fogProjectionM33;
    private int fogPasses;

    private final ICameraController flying = new ICameraController()
    {
        @Override public void setup(Camera camera, float transition)
        {
            camera.position.set(flight.orbit.getFinalPosition());
            camera.rotation.set(flight.orbit.rotation);
            camera.fov = flight.orbit.fov;
        }
    };

    private final OrbitViewportController orbit = new OrbitViewportController()
    {
        @Override protected UIContext getContext() { return context; }
        @Override protected Area getViewport() { return viewport; }
        @Override protected Camera getViewportCamera() { return BBSModClient.getCameraController().camera; }
        @Override protected float getSpeed() { return flight.orbit.getSpeed(); }
        @Override protected Vector3f getSubjectPivot(float transition) { return new Vector3f(subject); }
        @Override protected boolean hasSubject() { return true; }
    };

    private OriginalCameraProbe()
    {
        this.previousSmoothness = BBSSettings.editorCameraSmoothness.get();
        BBSSettings.editorCameraSmoothness.set(0F);
        this.getRoot().prepend(this.flight);
        this.getRoot().add(new IUIElement()
        {
            @Override public boolean isEnabled() { return true; }
            @Override public boolean canBeRendered(Area area) { return true; }
            @Override public IUIElement keyPressed(UIContext context)
            {
                boolean handled = orbit.enabled ? orbit.keyPressed(context, viewport)
                    : flight.getControl() && flight.orbit.keyPressed(context);
                return handled ? this : null;
            }
            @Override public IUIElement mouseClicked(UIContext context)
            {
                if (!orbit.enabled || !viewport.isInside(context)) return null;
                orbit.start(context);
                return this;
            }
            @Override public IUIElement mouseReleased(UIContext context)
            {
                if (!orbit.enabled) return null;
                orbit.stop();
                return this;
            }
            @Override public IUIElement mouseScrolled(UIContext context)
            {
                return orbit.enabled && orbit.zoom(context.mouseWheel) ? this : null;
            }
            @Override public void render(UIContext context)
            {
                if (orbit.enabled)
                {
                    orbit.handleOrbiting(context);
                    orbit.update(context);
                }
            }
        });
    }

    @Override public boolean canPause() { return false; }
    @Override public IUIElement getPointerOwner() { return this.flight.isFreeLook() ? this.flight : null; }

    @Override public void onOpen(UIBaseMenu oldMenu)
    {
        this.previousView = this.mc.getRenderViewEntity();
        this.previousPerspective = this.mc.gameSettings.thirdPersonView;
        this.previousCulling = this.mc.renderChunksMany;
        this.initial.set(this.previousView, MathUtils.toRad(this.mc.gameSettings.fovSetting));
        this.flight.orbit.setup(this.initial);
        this.subject.set(this.initial.position).add(this.initial.getLookDirection().mul(6F));
        this.flight.setControl(true);
        BBSModClient.getCameraController().add(this.flying);
        this.open = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @Override public void onClose(UIBaseMenu nextMenu)
    {
        this.flight.setControl(false);
        this.orbit.enabled = false;
        BBSModClient.getCameraController().remove(this.flying);
        BBSModClient.getCameraController().remove(this.orbit);
        BBSSettings.editorCameraSmoothness.set(this.previousSmoothness);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
        this.open = false;
    }

    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(8, 8, 320, 55, BBSSettings.baseSurface());
        render.batcher.text("BBS FS: original flight / orbit camera", 16, 16, -1);
        render.batcher.text(this.orbit.enabled ? "Orbit: turn, pan, zoom, orthographic" : "Flight: WASD, mouse, roll, FOV", 16, 34, -1);
    }

    private void setOrbit(boolean enabled)
    {
        BBSModClient.getCameraController().remove(this.flying);
        BBSModClient.getCameraController().remove(this.orbit);
        this.flight.setControl(!enabled);
        this.flight.setEnabled(!enabled);
        this.orbit.enabled = enabled;

        if (enabled)
        {
            this.orbit.reset();
            BBSModClient.getCameraController().add(this.orbit);
        }
        else
        {
            this.flight.orbit.setup(BBSModClient.getCameraController().camera);
            BBSModClient.getCameraController().add(this.flying);
        }
    }

    public static JsonObject handle(JsonObject request)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (request.has("terrain"))
        {
            if (request.has("invalidate")) mc.renderGlobal.setDisplayListEntitiesDirty();
            if (request.has("reload")) mc.renderGlobal.loadRenderers();
            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.addProperty("renders", mc.renderGlobal.getDebugInfoRenders());
            result.addProperty("loaded", mc.world.getChunkProvider().makeString());
            Entity view = mc.getRenderViewEntity();
            result.add("position", vector(view.posX, view.posY, view.posZ));
            result.add("chunk", vector(view.chunkCoordX, view.chunkCoordY, view.chunkCoordZ));
            result.addProperty("viewIsPlayer", view == mc.player);
            return result;
        }
        if (request.has("perspective")) mc.gameSettings.thirdPersonView = request.get("perspective").getAsInt();
        if (request.has("open") && request.get("open").getAsBoolean())
        {
            if (mc.world == null || mc.getRenderViewEntity() == null) throw new IllegalStateException("Enter the test world first");
            if (UIScreen.getCurrentMenu() instanceof OriginalCameraProbe) mc.displayGuiScreen(null);
            last = new OriginalCameraProbe();
            UIScreen.open(last);
        }
        if (last == null) throw new IllegalStateException("Open the camera probe first");
        if (request.has("close") && request.get("close").getAsBoolean()) mc.displayGuiScreen(null);
        if (request.has("freeLook")) last.flight.setFreeLook(request.get("freeLook").getAsBoolean());
        if (request.has("orbit")) last.setOrbit(request.get("orbit").getAsBoolean());
        if (request.has("toggleOrtho") && request.get("toggleOrtho").getAsBoolean()) last.orbit.toggleOrtho();
        if (request.has("fov"))
        {
            Camera pose = new Camera();
            pose.position.set(last.flight.orbit.getFinalPosition());
            pose.rotation.set(last.flight.orbit.rotation);
            pose.fov = MathUtils.toRad(request.get("fov").getAsFloat());
            last.flight.orbit.setup(pose);
        }
        if (request.has("key"))
        {
            int nativeKey = Keyboard.getKeyIndex(request.get("key").getAsString().toUpperCase(java.util.Locale.ROOT));
            if (nativeKey == Keyboard.KEY_NONE) throw new IllegalArgumentException("Unknown native key");
            boolean pressed = !request.has("pressed") || request.get("pressed").getAsBoolean();
            last.handleKey(InputCodes.fromNative(nativeKey), nativeKey, pressed ? InputCodes.PRESS : InputCodes.RELEASE, 0);
        }
        return last.snapshot();
    }

    /** Read the projection while the actual terrain/cloud pass is being prepared. */
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST, receiveCanceled = true)
    public void fog(net.minecraftforge.client.event.EntityViewRenderEvent.FogDensity event)
    {
        this.fogMatrix.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, this.fogMatrix);
        this.fogProjectionM23 = this.fogMatrix.get(11);
        this.fogProjectionM33 = this.fogMatrix.get(15);
        this.fogPasses++;
    }
    private JsonObject snapshot()
    {
        Camera camera = BBSModClient.getCameraController().camera;
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("open", this.open);
        result.addProperty("width", this.width);
        result.addProperty("height", this.height);
        result.addProperty("active", BBSModClient.getCameraController().getCurrent() == this.flying || BBSModClient.getCameraController().getCurrent() == this.orbit);
        result.addProperty("viewIsPlayer", this.mc.getRenderViewEntity() == this.mc.player);
        result.addProperty("viewRestored", this.mc.getRenderViewEntity() == this.previousView);
        result.addProperty("perspective", this.mc.gameSettings.thirdPersonView);
        result.addProperty("previousPerspective", this.previousPerspective);
        result.addProperty("culling", this.mc.renderChunksMany);
        result.addProperty("previousCulling", this.previousCulling);
        result.addProperty("freeLook", this.flight.isFreeLook());
        result.addProperty("grabbed", Mouse.isGrabbed());
        result.addProperty("clipMouse", Mouse.isClipMouseCoordinatesToWindow());
        result.addProperty("mouseX", this.context.mouseX);
        result.addProperty("mouseY", this.context.mouseY);
        result.addProperty("orbit", this.orbit.enabled);
        result.addProperty("ortho", ForgeCameraHandler.isOrthoActive());
        result.addProperty("fogProjectionM23", this.fogProjectionM23);
        result.addProperty("fogProjectionM33", this.fogProjectionM33);
        result.addProperty("fogPasses", this.fogPasses);
        result.addProperty("projectionM23", camera.projection.m23());
        result.addProperty("projectionM33", camera.projection.m33());
        result.addProperty("yaw", MathUtils.toDeg(camera.rotation.y));
        result.addProperty("pitch", MathUtils.toDeg(camera.rotation.x));
        result.addProperty("roll", MathUtils.toDeg(camera.rotation.z));
        result.addProperty("fov", MathUtils.toDeg(camera.fov));
        result.addProperty("speed", this.flight.orbit.speed.getX());
        result.add("position", vector(camera.position.x, camera.position.y, camera.position.z));
        if (this.mc.player != null)
        {
            result.add("player", vector(this.mc.player.posX, this.mc.player.posY, this.mc.player.posZ));
            result.addProperty("playerYaw", this.mc.player.rotationYaw);
            result.addProperty("playerPitch", this.mc.player.rotationPitch);
        }
        result.addProperty("glError", GL11.glGetError());
        return result;
    }

    private static JsonArray vector(double x, double y, double z)
    {
        JsonArray values = new JsonArray();
        values.add(x); values.add(y); values.add(z);
        return values;
    }
}
