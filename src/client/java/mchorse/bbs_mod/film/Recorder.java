package mchorse.bbs_mod.film;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Recorder extends WorldFilmController
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
    public FormProperties properties = new FormProperties("properties");

    /**
     * Mobs captured within {@link Film#mobRecordingRadius} when recording started.
     * Their base attributes are recorded each tick and turned into replays on stop.
     */
    public final List<RecordedMob> mobs = new ArrayList<>();

    public float hp;
    public float hunger;
    public int xpLevel;
    public float xpProgress;

    private static Matrix4f perspective = new Matrix4f();

    /* BBS-owned 3D POSITION_COLOR triangles pipeline/layer for the camera-frustum preview.
     * 1.21.5 removed RenderSystem.setShader(GameRenderer::getPositionColorProgram) +
     * BufferRenderer.drawWithGlobalProgram(); finished BufferBuilders go through a RenderLayer. */
    private static final RenderPipeline POSITION_COLOR_TRIS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(net.minecraft.util.Identifier.of(BBSMod.MOD_ID, "pipeline/recorder_camera_preview"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build()
    );

    private static RenderLayer cameraPreviewLayer;

    private static RenderLayer getCameraPreviewLayer()
    {
        if (cameraPreviewLayer == null)
        {
            cameraPreviewLayer = RenderLayer.of(BBSMod.MOD_ID + "_recorder_camera_preview", RenderSetup.builder(POSITION_COLOR_TRIS).translucent().build());
        }

        return cameraPreviewLayer;
    }

    public Form lastForm;
    public Vector3d lastPosition;
    public Vector4f lastRotation;

    public int countdown;
    public final int initialTick;

    /**
     * Where the take is meant to begin, when starting the recording put the player on
     * the replay's own mark &mdash; see {@link #awaitMark(Vector3d)}.
     */
    private Vector3d mark;
    private int markWait;

    /** How close to the mark counts as standing on it, in blocks. */
    private static final double MARK_REACHED = 1D;

    /** How many ticks the take may be held waiting for the teleport to land. */
    private static final int MARK_TIMEOUT = 20;

    public static void renderCameraPreview(Position position, Camera camera, MatrixStack stack)
    {
        if (!BBSSettings.recordingOverlays.get())
        {
            return;
        }

        Vector4f vector = Vectors.TEMP_4F;
        Matrix4f matrix = Matrices.TEMP_4F;
        float x = (float) (position.point.x - camera.getCameraPos().x);
        float y = (float) (position.point.y - camera.getCameraPos().y);
        float z = (float) (position.point.z - camera.getCameraPos().z);
        float fov = MathUtils.toRad(position.angle.fov);
        float aspect = BBSRendering.getVideoWidth() / (float) BBSRendering.getVideoHeight();
        float thickness = 0.025F;

        perspective.identity().perspective(fov, aspect, 0.001F, 100F).invert();

        matrix.identity()
            .rotateY(MathUtils.toRad(position.angle.yaw + 180))
            .rotateX(MathUtils.toRad(-position.angle.pitch));


        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        transformFrustum(vector, matrix, 1F, 1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, -1F, 1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, 1F, -1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, -1F, -1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, 0F, 0F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 0F, 0.5F, 1F, 1F);

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            /* TODO(1.21.11 render): verify at runtime. RenderLayer.draw uploads + draws with the layer
             * pipeline; previously this was RenderSystem.setShader + BufferRenderer.drawWithGlobalProgram. */
            getCameraPreviewLayer().draw(built);
        }

        /* TODO(1.21.11 render): depth-test state now lives in the RenderPipeline/RenderLayer; removed RenderSystem.disableDepthTest() */
    }

    private static void transformFrustum(Vector4f vector, Matrix4f matrix, float x, float y)
    {
        vector.set(x, y, 0F, 1F);
        vector.mul(perspective);
        vector.w = 1F;
        vector.normalize().mul(100F);
        vector.w = 1F;
        vector.mul(matrix);
    }

    public Recorder(Film film, Form form, int replayId, int tick)
    {
        super(film);

        this.lastForm = FormUtils.copy(form);
        this.exception = replayId;
        this.tick = tick;
        this.countdown = TimeUtils.toTick(BBSSettings.recordingCountdown.get());
        this.initialTick = tick;
    }

    /**
     * Hold the take until the player is actually standing on {@code mark}. The teleport
     * that puts them there goes through the server and comes back a couple of ticks
     * later, while the countdown ticks down on its own clock - with a short (or zero)
     * countdown the first recorded ticks would catch the player still at the old spot,
     * and the take would open with a visible jump.
     */
    public void awaitMark(Vector3d mark)
    {
        this.mark = mark;
    }

    public boolean hasNotStarted()
    {
        return this.countdown > 0 || this.mark != null;
    }

    public void update()
    {
        if (this.countdown > 0)
        {
            this.countdown -= 1;

            return;
        }

        if (this.mark != null && !this.reachedMark())
        {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (this.lastPosition == null)
        {
            this.lastPosition = new Vector3d(player.getX(), player.getY(), player.getZ());
            this.lastRotation = new Vector4f(player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw());

            this.hp = player.getHealth();
            this.hunger = player.getHungerManager().getFoodLevel();
            this.xpLevel = player.experienceLevel;
            this.xpProgress = player.experienceProgress;

            this.captureMobs(player);
        }

        if (this.tick >= 0)
        {
            Morph morph = Morph.getMorph(player);

            this.keyframes.record(this.tick, morph.entity, null);
            this.recordMobs();
        }

        super.update();
    }

    /**
     * Whether the teleport has landed. Given up on after {@link #MARK_TIMEOUT} rather
     * than awaited forever: a mark inside a wall or up in the air is one the player can
     * never quite stand on, and a take that never starts is worse than one that starts
     * slightly off.
     */
    private boolean reachedMark()
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        double distance = player == null ? Double.MAX_VALUE : this.mark.distance(player.getX(), player.getY(), player.getZ());
        boolean reached = distance <= MARK_REACHED;

        this.markWait += 1;

        if (!reached && this.markWait < MARK_TIMEOUT)
        {
            return false;
        }

        if (!reached)
        {
            LOGGER.warn("[BBS film] Recording is starting {} blocks off the replay's mark - the teleport never landed.", String.format("%.2f", distance));
        }

        this.mark = null;

        return true;
    }

    /**
     * Snapshot every living entity (except the recording player) within
     * {@link Film#mobRecordingRadius} into {@link #mobs}. A radius of {@code 0} disables it.
     */
    private void captureMobs(ClientPlayerEntity player)
    {
        float radius = this.film.mobRecordingRadius.get();

        if (radius <= 0)
        {
            return;
        }

        Box box = player.getBoundingBox().expand(radius);
        double radiusSq = radius * radius;

        for (LivingEntity entity : player.getEntityWorld().getEntitiesByClass(LivingEntity.class, box, (e) -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq))
        {
            MobForm form = Morph.createMobForm(entity);

            if (form != null)
            {
                this.mobs.add(new RecordedMob(form, entity));
            }
        }
    }

    private void recordMobs()
    {
        for (RecordedMob mob : this.mobs)
        {
            if (mob.entity.getMcEntity().isAlive())
            {
                mob.keyframes.record(this.tick, mob.entity, null);
            }
        }
    }

    /**
     * A mob captured at recording start: its snapshotted {@link MobForm} plus the
     * keyframes recorded each tick from the live entity it wraps.
     */
    public static class RecordedMob
    {
        public final MobForm form;
        public final MCEntity entity;
        public final ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");

        public RecordedMob(MobForm form, Entity mcEntity)
        {
            this.form = form;
            this.entity = new MCEntity(mcEntity);
        }
    }

    public void render(WorldRenderContext context)
    {
        super.render(context);

        renderCameraPreview(this.position, MinecraftClient.getInstance().gameRenderer.getCamera(), context.matrices());
    }

    @Override
    public void shutdown()
    {
        Vector3d pos = this.lastPosition;

        if (pos != null)
        {
            Vector4f rot = this.lastRotation;

            PlayerUtils.teleport(pos.x, pos.y, pos.z, rot.z, rot.y);
            ClientNetwork.sendPlayerForm(this.lastForm);
        }

        super.shutdown();
    }
}
