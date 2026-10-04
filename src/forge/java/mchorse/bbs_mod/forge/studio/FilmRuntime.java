package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.ReplayEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import java.util.*;

/** Client scene evaluated from the actual BBS Film, Replay and property tracks. */
public final class FilmRuntime
{
    public final Film film;
    public final Map<String, ReplayEntity> actors = new LinkedHashMap<>();
    public final Position cameraPosition = new Position();
    private final Position cameraBase = new Position();
    public boolean playing, loop, cameraEnabled;
    public int tick;
    private Entity previousView;
    private CameraEntity camera;
    private int previousThirdPerson;
    private final CameraClipContext cameraContext = new CameraClipContext();

    public FilmRuntime(Film film) { this.film = film; cameraContext.clips = film.camera; rebuild(); }
    public int duration() { return Math.max(100, film.calculateDuration()); }
    public void rebuild()
    {
        actors.clear();
        for (Replay replay : film.replays.getList())
        {
            if (!replay.enabled.get() || replay.form.get() == null) continue;
            ReplayEntity actor = new ReplayEntity(Minecraft.getMinecraft().world, replay);
            actor.setForm(FormUtils.copy(replay.form.get()));
            actor.setOnGround(true);
            NativeFormRenderer.prepare(actor.getForm());
            replay.keyframes.apply(0, actor); replay.keyframes.applyEquipment(0, actor);
            actor.setPrevX(actor.getX()); actor.setPrevY(actor.getY()); actor.setPrevZ(actor.getZ());
            actor.setPrevBodyYaw(actor.getBodyYaw()); actor.setPrevHeadYaw(actor.getHeadYaw()); actor.setPrevPitch(actor.getPitch());
            actor.getForm().playMain();
            actors.put(replay.getId(), actor);
        }
        tick = 0;
        evaluateProperties(0);
    }
    /** Replaying animation updates makes arbitrary backward/forward seeks deterministic. */
    public void seek(int target)
    {
        target = Math.max(0, target);
        rebuild();
        while (tick < target) advance();
        evaluateProperties(0);
        updateCamera(0);
    }
    public void update()
    {
        if (!playing) return;
        if (tick + 1 >= duration())
        {
            if (loop) seek(0);
            else { playing = false; return; }
        }
        else advance();
    }
    private void advance()
    {
        tick++;
        for (ReplayEntity actor : actors.values())
        {
            Replay replay = actor.replay;
            int time = replay.getTick(tick);
            actor.update();
            replay.keyframes.apply(time, actor);
            replay.keyframes.applyEquipment(time, actor);
            replay.properties.apply(TrackContext.frame(actor.getForm(), 0, null), time, 1);
            replay.applyClientActions(tick, actor, film);
            actor.getForm().update(actor);
        }
    }
    private void evaluateProperties(float partial)
    {
        for (ReplayEntity actor : actors.values())
            actor.replay.properties.apply(TrackContext.frame(actor.getForm(), partial, null), actor.replay.getTick(tick) + partial, 1);
    }
    public void render(float partial)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;
        partial = playing ? partial : 0;
        evaluateProperties(partial);
        Entity view = mc.getRenderViewEntity();
        float cameraPartial = mc.getRenderPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * cameraPartial;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * cameraPartial;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * cameraPartial;
        GlStateManager.pushMatrix();
        try
        {
            GlStateManager.translate(-vx, -vy, -vz);
            GlStateManager.enableDepth();
            NativeTextureRenderer.beginPass();
            try
            {
            for (ReplayEntity actor : actors.values())
            {
                Replay replay = actor.replay;
                replay.keyframes.apply(replay.getTick(tick) + partial, actor);
                GlStateManager.pushMatrix();
                try
                {
                    GlStateManager.translate(actor.getX(), actor.getY(), actor.getZ());
                    GlStateManager.rotate(-actor.getBodyYaw(), 0, 1, 0);
                    NativeFormRenderer.of(actor.getForm()).render(actor, partial);
                }
                finally { GlStateManager.popMatrix(); replay.keyframes.apply(replay.getTick(tick), actor); }
            }
            }
            finally { NativeTextureRenderer.endPass(); }
        }
        finally { GlStateManager.popMatrix(); GlStateManager.color(1,1,1,1); }
    }
    public void camera(boolean enabled)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (enabled == cameraEnabled) return;
        cameraEnabled = enabled && mc.world != null;
        if (cameraEnabled)
        {
            previousView = mc.getRenderViewEntity(); previousThirdPerson = mc.gameSettings.thirdPersonView;
            camera = new CameraEntity(mc.world);
            cameraPosition.point.set(previousView.posX, previousView.posY + previousView.getEyeHeight(), previousView.posZ);
            cameraPosition.angle.set(previousView.rotationYaw, previousView.rotationPitch, 0, mc.gameSettings.fovSetting);
            cameraBase.copy(cameraPosition);
            updateCamera(0);
            mc.setRenderViewEntity(camera); mc.gameSettings.thirdPersonView = 0;
        }
        else
        {
            if (mc.getRenderViewEntity() == camera) mc.setRenderViewEntity(previousView == null ? mc.player : previousView);
            mc.gameSettings.thirdPersonView = previousThirdPerson;
            camera = null; previousView = null;
        }
    }
    public void updateCamera(float partial)
    {
        if (!cameraEnabled || camera == null) return;
        partial = playing ? partial : 0;
        cameraPosition.copy(cameraBase);
        cameraContext.setup(tick, partial);
        for (Clip clip : film.camera.getClips(tick)) cameraContext.apply(clip, cameraPosition);
        camera.setPosition(cameraPosition.point.x, cameraPosition.point.y, cameraPosition.point.z);
        camera.prevPosX = camera.lastTickPosX = camera.posX;
        camera.prevPosY = camera.lastTickPosY = camera.posY;
        camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
        camera.prevRotationYaw = camera.rotationYaw = cameraPosition.angle.yaw;
        camera.prevRotationPitch = camera.rotationPitch = cameraPosition.angle.pitch;
    }
    public void close() { playing = false; camera(false); actors.clear(); }
    private static final class CameraEntity extends Entity
    {
        CameraEntity(World world) { super(world); noClip = true; setSize(0, 0); }
        protected void entityInit() {}
        protected void readEntityFromNBT(NBTTagCompound tag) {}
        protected void writeEntityToNBT(NBTTagCompound tag) {}
        public float getEyeHeight() { return 0; }
        public boolean isSpectator() { return true; }
    }
}
