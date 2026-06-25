package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * The selected replay's (or selected bone's) world-space trajectory drawn into
 * the film viewport: a curve sampled over time with a dot on every tick, a
 * marker on every keyframe and a highlight on the current frame — the same idea
 * as Blender's motion paths. Every part is configured through
 * {@link ValueMotionPath} (edited from the preview's motion path button).
 *
 * <p>The root path comes straight from the replay's position channels. The bone
 * path is the expensive one: a bone's world position has to be simulated tick by
 * tick (pose + IK run inside {@code collectMatrices}), so it is computed on a
 * scratch entity (never touching the on-screen model) into a cached world-point
 * list, recomputed only when the animation changes — detected by a cheap
 * structural signature plus a per-frame divergence check against one live sample
 * at the current frame (which also serves as the current-frame marker).
 * Procedural limb motion and physics are approximate (a discrete snapshot has no
 * history); keyframed pose and IK are exact.
 *
 * <p>It draws in the world / 3D pass like {@link UIFilmController}'s orbit centre
 * marker (camera-relative, depth disabled). The curve is a camera-facing ribbon
 * (one flat quad per segment) and the dots are small axis-aligned cubes.
 */
public class MotionPath
{
    private static final float[] COLOR_A = new float[3];
    private static final float[] COLOR_B = new float[3];
    private static final float[] DOT_COLOR = new float[3];
    private static final float[] SCRATCH = new float[3];

    private static final Vector3d POINT_A = new Vector3d();
    private static final Vector3d POINT_B = new Vector3d();

    /* Bone path cache (one editor at a time). */
    private static String boneCacheSignature;
    private static BoneTrajectory boneCache;
    private static final Vector3d LIVE = new Vector3d();
    private static final Vector3f TEMP = new Vector3f();

    /* A scratch entity reused across recomputes (re-posed per tick), so a recompute — which
     * happens every frame while a bone is being dragged — does not pay a deep form copy each
     * time; it is only rebuilt when the form itself changes. */
    private static Form scratchForm;
    private static StubEntity scratchEntity;

    /* The animator's action slots (mirrors Animator#setup) — emptied on the scratch form so the
     * always-applied actions don't perturb the sampled bone path. */
    private static final String[] ACTION_KEYS = {
        "idle", "running", "sprinting", "crouching", "crouching_idle", "dying", "falling",
        "swipe", "jump", "jump_alt", "hurt", "land", "shoot", "consume", "base_pre", "base_post"
    };

    public static void render(WorldRenderContext context, ValueMotionPath config, UIFilmController controller, Replay replay, float currentTick)
    {
        if (replay == null || replay.relative.get())
        {
            return;
        }

        Pair<String, Boolean> bone = controller.getBone();
        String bonePath = bone == null ? null : bone.a;

        Trajectory trajectory = bonePath == null ? null : boneTrajectory(controller, replay, bonePath);

        if (trajectory == null)
        {
            trajectory = rootTrajectory(replay);
        }

        if (trajectory == null)
        {
            return;
        }

        draw(context, config, trajectory, currentTick);
    }

    /* Drawing */

    private static void draw(WorldRenderContext context, ValueMotionPath config, Trajectory trajectory, float currentTick)
    {
        float first = trajectory.first();
        float last = trajectory.last();

        /* Limit the drawn part to a window around the current frame. */
        if (config.aroundCurrent.get())
        {
            first = Math.max(first, currentTick - config.before.get());
            last = Math.min(last, currentTick + config.after.get());

            if (first > last)
            {
                return;
            }
        }

        Camera camera = context.camera();
        MatrixStack stack = context.matrixStack();

        double cx = camera.getPos().x;
        double cy = camera.getPos().y;
        double cz = camera.getPos().z;

        Matrix4f matrix = stack.peek().getPositionMatrix();
        float halfWidth = config.width.get() * 0.5F;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* The interpolated curve: a camera-facing ribbon with a dot on every
         * tick (so the spacing shows speed), the exact endpoints kept. */
        trajectory.worldAt(first, POINT_A);
        POINT_A.sub(cx, cy, cz);

        gradient(config, first, currentTick, first, last, COLOR_A);

        if (config.frames.get())
        {
            dot(builder, stack, POINT_A, config.frameSize.get(), brighten(COLOR_A, DOT_COLOR));
        }

        for (float tick = first; tick < last; )
        {
            tick = Math.min(tick + 1F, last);

            trajectory.worldAt(tick, POINT_B);
            POINT_B.sub(cx, cy, cz);

            gradient(config, tick, currentTick, first, last, COLOR_B);
            ribbon(builder, matrix, POINT_A, POINT_B, COLOR_A, COLOR_B, halfWidth);

            if (config.frames.get())
            {
                dot(builder, stack, POINT_B, config.frameSize.get(), brighten(COLOR_B, DOT_COLOR));
            }

            POINT_A.set(POINT_B);
            System.arraycopy(COLOR_B, 0, COLOR_A, 0, 3);
        }

        /* A bigger marker on every keyframe inside the window. */
        if (config.keyframes.get())
        {
            unpack(config.keyframeColor.get(), SCRATCH);

            for (float tick : trajectory.keyframeTicks())
            {
                if (tick >= first && tick <= last)
                {
                    trajectory.worldAt(tick, POINT_B);
                    POINT_B.sub(cx, cy, cz);
                    dot(builder, stack, POINT_B, config.keyframeSize.get(), SCRATCH);
                }
            }
        }

        /* The current frame's place on the path, when it falls inside the window. */
        if (config.current.get() && currentTick >= first && currentTick <= last)
        {
            unpack(config.currentColor.get(), SCRATCH);
            trajectory.worldAt(currentTick, POINT_B);
            POINT_B.sub(cx, cy, cz);
            dot(builder, stack, POINT_B, config.currentSize.get(), SCRATCH);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * The colour for a point at {@code tick}: the line colour at the current
     * frame, fading to {@code pastColor} fully in the past and {@code futureColor}
     * fully in the future. With the gradient off it is just the line colour.
     */
    private static void gradient(ValueMotionPath config, float tick, float current, float first, float last, float[] out)
    {
        unpack(config.color.get(), out);

        if (!config.gradient.get())
        {
            return;
        }

        float factor;

        if (tick <= current)
        {
            factor = current <= first ? 0F : -(current - tick) / (current - first);
        }
        else
        {
            factor = current >= last ? 0F : (tick - current) / (last - current);
        }

        unpack(factor < 0F ? config.pastColor.get() : config.futureColor.get(), SCRATCH);

        float weight = Math.abs(factor);

        out[0] += (SCRATCH[0] - out[0]) * weight;
        out[1] += (SCRATCH[1] - out[1]) * weight;
        out[2] += (SCRATCH[2] - out[2]) * weight;
    }

    /** Lighten a colour towards white, so the per-frame dots read against the ribbon they sit on. */
    private static float[] brighten(float[] src, float[] out)
    {
        out[0] = src[0] + (1F - src[0]) * 0.45F;
        out[1] = src[1] + (1F - src[1]) * 0.45F;
        out[2] = src[2] + (1F - src[2]) * 0.45F;

        return out;
    }

    private static void dot(BufferBuilder builder, MatrixStack stack, Vector3d point, float radius, float[] color)
    {
        float half = radius * BBSSettings.getAxesDistanceScale((float) point.length());

        Draw.fillBox(builder, stack, (float) point.x - half, (float) point.y - half, (float) point.z - half, (float) point.x + half, (float) point.y + half, (float) point.z + half, color[0], color[1], color[2], 1F);
    }

    /**
     * One flat quad from {@code a} to {@code b}, widened along the vector
     * perpendicular to both the segment and the view ray (the segment midpoint,
     * since the coordinates are camera-relative), so the ribbon always faces the
     * camera. Its width is distance-scaled to stay a constant on-screen size.
     */
    private static void ribbon(BufferBuilder builder, Matrix4f matrix, Vector3d a, Vector3d b, float[] colorA, float[] colorB, float halfWidth)
    {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;

        double mx = (a.x + b.x) * 0.5;
        double my = (a.y + b.y) * 0.5;
        double mz = (a.z + b.z) * 0.5;

        /* side = segment x view ray */
        double sx = dy * mz - dz * my;
        double sy = dz * mx - dx * mz;
        double sz = dx * my - dy * mx;
        double length = Math.sqrt(sx * sx + sy * sy + sz * sz);

        if (length < 1.0E-6)
        {
            return;
        }

        double half = halfWidth * BBSSettings.getAxesDistanceScale((float) Math.sqrt(mx * mx + my * my + mz * mz)) / length;

        sx *= half;
        sy *= half;
        sz *= half;

        float ax1 = (float) (a.x + sx), ay1 = (float) (a.y + sy), az1 = (float) (a.z + sz);
        float ax2 = (float) (a.x - sx), ay2 = (float) (a.y - sy), az2 = (float) (a.z - sz);
        float bx1 = (float) (b.x + sx), by1 = (float) (b.y + sy), bz1 = (float) (b.z + sz);
        float bx2 = (float) (b.x - sx), by2 = (float) (b.y - sy), bz2 = (float) (b.z - sz);

        builder.vertex(matrix, ax1, ay1, az1).color(colorA[0], colorA[1], colorA[2], 1F).next();
        builder.vertex(matrix, ax2, ay2, az2).color(colorA[0], colorA[1], colorA[2], 1F).next();
        builder.vertex(matrix, bx2, by2, bz2).color(colorB[0], colorB[1], colorB[2], 1F).next();

        builder.vertex(matrix, ax1, ay1, az1).color(colorA[0], colorA[1], colorA[2], 1F).next();
        builder.vertex(matrix, bx2, by2, bz2).color(colorB[0], colorB[1], colorB[2], 1F).next();
        builder.vertex(matrix, bx1, by1, bz1).color(colorB[0], colorB[1], colorB[2], 1F).next();
    }

    private static void unpack(int color, float[] out)
    {
        out[0] = Colors.getR(color);
        out[1] = Colors.getG(color);
        out[2] = Colors.getB(color);
    }

    /* Root trajectory: straight from the position channels (cheap). */

    private static Trajectory rootTrajectory(Replay replay)
    {
        KeyframeChannel<Double> x = replay.keyframes.x;
        KeyframeChannel<Double> y = replay.keyframes.y;
        KeyframeChannel<Double> z = replay.keyframes.z;

        float[] range = range(x, y, z);

        if (range == null)
        {
            return null;
        }

        TreeSet<Float> ticks = new TreeSet<>();

        collectTicks(ticks, x);
        collectTicks(ticks, y);
        collectTicks(ticks, z);

        return new RootTrajectory(replay, range[0], range[1], ticks);
    }

    private record RootTrajectory(Replay replay, float first, float last, TreeSet<Float> keyframeTicks) implements Trajectory
    {
        @Override
        public void worldAt(float tick, Vector3d out)
        {
            out.set(
                this.replay.keyframes.x.interpolate(tick),
                this.replay.keyframes.y.interpolate(tick),
                this.replay.keyframes.z.interpolate(tick)
            );
        }
    }

    /* Bone trajectory: simulated per tick on a scratch entity, cached. */

    private static Trajectory boneTrajectory(UIFilmController controller, Replay replay, String bonePath)
    {
        World world = MinecraftClient.getInstance().world;
        Form form = replay.form.get();

        if (!ensureScratch(world, form))
        {
            return null;
        }

        IntObjectMap<IEntity> entities = controller.getEntities();

        /* Recompute only when the animation data actually changes (a content signature including
         * keyframe values). The previous per-frame "resample and compare" check was both the
         * source of the jitter — it re-posed the shared scratch entity, corrupting the IK warm-up
         * seed so each recompute drifted ~1cm — and self-triggering (a single off-sequence sample
         * never matches the in-sequence cached value). Without it the cached path is stable while
         * idle and only rebuilt on a real edit. */
        String signature = signature(replay, bonePath);

        if (boneCache == null || !signature.equals(boneCacheSignature))
        {
            boneCache = computeBoneTrajectory(entities, replay, bonePath);
            boneCacheSignature = signature;
        }

        return boneCache;
    }

    /**
     * Rebuild the scratch entity when the form changes. Its actions (the always-applied
     * idle/walk animations) are disabled: the renderer's animator switches and rewinds them per
     * tick from the entity's movement, which made the sampled bone path jitter. The path is meant
     * to show the authored motion (keyframes + IK), so the procedural overlay is dropped.
     */
    private static boolean ensureScratch(World world, Form form)
    {
        if (world == null || form == null)
        {
            return false;
        }

        if (scratchEntity == null || scratchForm != form)
        {
            Form copy = FormUtils.copy(form);

            if (copy == null)
            {
                scratchEntity = null;
                scratchForm = null;

                return false;
            }

            disableActions(copy);

            scratchEntity = new StubEntity(world);
            scratchEntity.setForm(copy);
            scratchForm = form;
        }

        return true;
    }

    private static BoneTrajectory computeBoneTrajectory(IntObjectMap<IEntity> entities, Replay replay, String bonePath)
    {
        float[] range = range(replay);

        if (range == null)
        {
            return null;
        }

        int base = (int) Math.floor(range[0]);
        int end = (int) Math.ceil(range[1]);
        int count = end - base + 1;

        double[] points = new double[count * 3];

        for (int i = 0; i < count; i++)
        {
            if (!sampleBoneWorld(entities, replay, bonePath, base + i, LIVE))
            {
                return null;
            }

            points[i * 3] = LIVE.x;
            points[i * 3 + 1] = LIVE.y;
            points[i * 3 + 2] = LIVE.z;
        }

        TreeSet<Float> ticks = new TreeSet<>();
        String boneName = bonePath.contains(".") ? bonePath.substring(bonePath.lastIndexOf('.') + 1) : bonePath;

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            if (channel.getId() != null && channel.getId().contains(boneName))
            {
                collectTicks(ticks, channel);
            }
        }

        return new BoneTrajectory(base, count, points, range[0], range[1], ticks);
    }

    /** Pose the scratch entity at {@code tick} and read the bone's world position (camera at origin). */
    private static boolean sampleBoneWorld(IntObjectMap<IEntity> entities, Replay replay, String bonePath, int tick, Vector3d out)
    {
        StubEntity entity = scratchEntity;

        replay.keyframes.apply(tick, entity);
        entity.update();
        entity.getForm().update(entity);
        replay.properties.applyProperties(entity.getForm(), tick);

        Matrix4f matrix = BaseFilmController.getBoneCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F, bonePath, false);

        if (matrix == null)
        {
            return false;
        }

        matrix.getTranslation(TEMP);
        out.set(TEMP.x, TEMP.y, TEMP.z);

        return true;
    }

    /** Empty every action slot on the form tree (names that match no animation) so the animator applies none. */
    private static void disableActions(Form form)
    {
        if (form instanceof ModelForm model)
        {
            ActionsConfig actions = model.actions.get();

            for (String key : ACTION_KEYS)
            {
                actions.actions.put(key, new ActionConfig(""));
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            Form child = part.getForm();

            if (child != null)
            {
                disableActions(child);
            }
        }
    }

    private static final class BoneTrajectory implements Trajectory
    {
        private final int base;
        private final int count;
        private final double[] points;
        private final float first;
        private final float last;
        private final TreeSet<Float> keyframeTicks;

        private BoneTrajectory(int base, int count, double[] points, float first, float last, TreeSet<Float> keyframeTicks)
        {
            this.base = base;
            this.count = count;
            this.points = points;
            this.first = first;
            this.last = last;
            this.keyframeTicks = keyframeTicks;
        }

        @Override
        public float first()
        {
            return this.first;
        }

        @Override
        public float last()
        {
            return this.last;
        }

        @Override
        public TreeSet<Float> keyframeTicks()
        {
            return this.keyframeTicks;
        }

        @Override
        public void worldAt(float tick, Vector3d out)
        {
            float local = tick - this.base;
            int i0 = (int) Math.floor(local);

            if (i0 < 0) i0 = 0;
            if (i0 > this.count - 1) i0 = this.count - 1;

            int i1 = Math.min(i0 + 1, this.count - 1);
            float frac = local - i0;

            if (frac < 0F) frac = 0F;
            if (frac > 1F) frac = 1F;

            int a = i0 * 3;
            int b = i1 * 3;

            out.set(
                this.points[a] + (this.points[b] - this.points[a]) * frac,
                this.points[a + 1] + (this.points[b + 1] - this.points[a + 1]) * frac,
                this.points[a + 2] + (this.points[b + 2] - this.points[a + 2]) * frac
            );
        }
    }

    private interface Trajectory
    {
        float first();

        float last();

        TreeSet<Float> keyframeTicks();

        /** Fill {@code out} with the WORLD position at {@code tick}. */
        void worldAt(float tick, Vector3d out);
    }

    /* Shared helpers */

    /** The full animation range over the replay's position channels and every property channel. */
    private static float[] range(Replay replay)
    {
        float first = Float.MAX_VALUE;
        float last = -Float.MAX_VALUE;

        for (KeyframeChannel<?> channel : Arrays.asList(replay.keyframes.x, replay.keyframes.y, replay.keyframes.z))
        {
            first = Math.min(first, firstTick(channel));
            last = Math.max(last, lastTick(channel));
        }

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            first = Math.min(first, firstTick(channel));
            last = Math.max(last, lastTick(channel));
        }

        return last < first ? null : new float[] {first, last};
    }

    private static float[] range(KeyframeChannel<?>... channels)
    {
        float first = Float.MAX_VALUE;
        float last = -Float.MAX_VALUE;

        for (KeyframeChannel<?> channel : channels)
        {
            first = Math.min(first, firstTick(channel));
            last = Math.max(last, lastTick(channel));
        }

        return last < first ? null : new float[] {first, last};
    }

    private static float firstTick(KeyframeChannel<?> channel)
    {
        return channel.isEmpty() ? Float.MAX_VALUE : channel.get(0).getTick();
    }

    private static float lastTick(KeyframeChannel<?> channel)
    {
        return channel.isEmpty() ? -Float.MAX_VALUE : channel.get(channel.getKeyframes().size() - 1).getTick();
    }

    private static String signature(Replay replay, String bonePath)
    {
        StringBuilder builder = new StringBuilder(replay.getId()).append('|').append(bonePath);

        signature(builder, replay.keyframes.x);
        signature(builder, replay.keyframes.y);
        signature(builder, replay.keyframes.z);

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            builder.append('#').append(channel.getId());
            signature(builder, channel);
        }

        return builder.toString();
    }

    private static void signature(StringBuilder builder, KeyframeChannel<?> channel)
    {
        /* Hash the channel's serialized data so the signature changes on value edits (re-posing a
         * bone), not only on add/remove/move — the path follows a gizmo drag without a per-frame
         * resample of the bone. */
        builder.append(':').append(channel.toData().toString().hashCode());
    }

    private static void collectTicks(TreeSet<Float> ticks, KeyframeChannel<?> channel)
    {
        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            ticks.add(keyframe.getTick());
        }
    }
}
