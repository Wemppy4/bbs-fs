package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Recorder extends WorldFilmController
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");

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

    public Form lastForm;
    public Vector3d lastPosition;
    public Vector4f lastRotation;

    public int countdown;
    public final int initialTick;

    /**
     * Where the take is meant to begin, when starting the recording put the player on
     * the replay's own mark &mdash; see {@link #awaitMark(Vector3d)}. Dropped the moment
     * the player is standing on it, and the take is free to begin.
     */
    private Vector3d mark;
    private int markWait;

    /** How close to the mark counts as standing on it, in blocks. */
    private static final double MARK_REACHED = 1D;

    /** How many ticks the take may be held waiting for the teleport to land. */
    private static final int MARK_TIMEOUT = 20;

    /** How far the camera preview's frustum lines reach, in blocks. */
    public static final float FRUSTUM_LENGTH = 100F;

    /**
     * The camera's frustum edges and middle as rays from its position, {@link #FRUSTUM_LENGTH}
     * long, in world axes: the corners (+x+y, -x+y, +x-y, -x-y) then the middle, five in all.
     */
    public static Vector3f[] frustumRays(Position position)
    {
        Vector4f vector = new Vector4f();
        Matrix4f matrix = new Matrix4f();
        float fov = MathUtils.toRad(position.angle.fov);
        float aspect = BBSRendering.getVideoWidth() / (float) BBSRendering.getVideoHeight();
        float[][] corners = {{1F, 1F}, {-1F, 1F}, {1F, -1F}, {-1F, -1F}, {0F, 0F}};
        Vector3f[] rays = new Vector3f[corners.length];

        perspective.identity().perspective(fov, aspect, 0.001F, FRUSTUM_LENGTH).invert();

        matrix.identity()
            .rotateY(MathUtils.toRad(position.angle.yaw + 180))
            .rotateX(MathUtils.toRad(-position.angle.pitch));

        for (int i = 0; i < corners.length; i++)
        {
            transformFrustum(vector, matrix, corners[i][0], corners[i][1]);
            rays[i] = new Vector3f(vector.x, vector.y, vector.z);
        }

        return rays;
    }

    public static void renderCameraPreview(Position position, Camera camera, MatrixStack stack)
    {
        if (!BBSSettings.recordingOverlays.get())
        {
            return;
        }

        float x = (float) (position.point.x - camera.getPos().x);
        float y = (float) (position.point.y - camera.getPos().y);
        float z = (float) (position.point.z - camera.getPos().z);
        float thickness = 0.025F;
        Vector3f[] rays = frustumRays(position);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < rays.length; i++)
        {
            Vector3f ray = rays[i];
            boolean middle = i == rays.length - 1;

            Draw.fillBoxTo(builder, stack, x, y, z, x + ray.x, y + ray.y, z + ray.z, thickness, middle ? 0F : 1F, middle ? 0.5F : 1F, 1F, 1F);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.disableDepthTest();
    }

    private static void transformFrustum(Vector4f vector, Matrix4f matrix, float x, float y)
    {
        vector.set(x, y, 0F, 1F);
        vector.mul(perspective);
        vector.w = 1F;
        vector.normalize().mul(FRUSTUM_LENGTH);
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
     * Hold the take until the teleport that puts the player on {@code mark} has landed. It
     * goes through the server and comes back a couple of ticks later, while the countdown
     * ticks down on its own clock - with a short (or zero) countdown the first recorded ticks
     * would catch the player still at the old spot, and the take would open with a visible jump.
     *
     * <p>Only until it lands, once: where the player goes from there is their own business,
     * and the take begins wherever they are when the countdown runs out.</p>
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
        /* Watched through the countdown too, not only after it: the teleport lands while the
         * countdown is still running, and letting go of the mark right there is what lets the
         * player walk off it before the take begins. Waiting for them to come back would hold
         * this clock alone - the server's action recorder counts the same countdown and knows
         * nothing else - and every action of the take would land that much further down the
         * timeline than the pose recorded alongside it. */
        this.landMark();

        if (this.countdown > 0)
        {
            this.countdown -= 1;

            return;
        }

        if (this.mark != null && !this.giveUpOnMark())
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

    /** Let go of the mark once the player is standing on it: the teleport has landed. */
    private void landMark()
    {
        if (this.mark == null)
        {
            return;
        }

        if (this.distanceToMark() <= MARK_REACHED)
        {
            this.mark = null;
        }
    }

    /**
     * Whether to begin without the teleport ever having landed. Given up on after
     * {@link #MARK_TIMEOUT} rather than awaited forever: a mark inside a wall or up in the
     * air is one the player can never quite stand on, and a take that never starts is worse
     * than one that starts slightly off.
     */
    private boolean giveUpOnMark()
    {
        this.markWait += 1;

        if (this.markWait < MARK_TIMEOUT)
        {
            return false;
        }

        LOGGER.warn("[BBS film] Recording is starting {} blocks off the replay's mark - the teleport never landed.", String.format("%.2f", this.distanceToMark()));

        this.mark = null;

        return true;
    }

    private double distanceToMark()
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        return player == null ? Double.MAX_VALUE : this.mark.distance(player.getX(), player.getY(), player.getZ());
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

        for (LivingEntity entity : player.getWorld().getEntitiesByClass(LivingEntity.class, box, (e) -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq))
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

        renderCameraPreview(this.position, context.camera(), context.matrixStack());
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
