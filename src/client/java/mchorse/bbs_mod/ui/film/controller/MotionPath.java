package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.forms.entities.ReplayEntity;

import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.ui.utils.ScreenOverlay;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeSet;

/**
 * The selected replay's (or bone's) world trajectory drawn into the film viewport —
 * Blender's motion paths: a sampled curve with a dot per tick, a marker per keyframe and a
 * highlight on the current frame, all configured through {@link ValueMotionPath}.
 *
 * <p>The root path comes straight from the position channels. The BONE path is the expensive
 * one: a bone's world position must be simulated tick by tick, so it runs on a scratch entity
 * (never the on-screen model) into a cached point list, recomputed only when the animation
 * changes — a cheap structural signature plus a divergence check against one live sample.
 * Procedural limb motion and physics are approximate there; keyframed pose and IK are exact.
 *
 * <p>Drawn on the film viewport's markup ({@link ScreenOverlay}): the curve a line one screen
 * pixel wide, a dot per tick, a key per keyframe as the timeline draws one, a ring on the current
 * frame, in the markup's sizes.
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
    private static ReplayEntity scratchEntity;

    /* The animator's action slots (mirrors Animator#setup) — emptied on the scratch form so the
     * always-applied actions don't perturb the sampled bone path. */
    private static final String[] ACTION_KEYS = {
        "idle", "running", "sprinting", "crouching", "crouching_idle", "dying", "falling",
        "swimming", "swimming_idle", "riding", "riding_idle", "flying", "flying_idle",
        "swipe", "jump", "jump_alt", "hurt", "land", "shoot", "consume", "base_pre", "base_post"
    };

    public static void render(ScreenOverlay overlay, mchorse.bbs_mod.camera.Camera camera, ValueMotionPath config, UIFilmController controller, Replay replay, FilmTarget target, float currentTick)
    {
        if (replay == null || replay.relative.get())
        {
            return;
        }

        /* The same target the gizmo is on, so the path is the trajectory of the thing being
         * dragged. Root is the fallback in both senses: the target's own, and what a bone or
         * anchor falls back to when its path cannot be sampled (no form, no world yet). */
        Trajectory trajectory = target == null || target.is(FilmTarget.Kind.ROOT) || target.isNone()
            ? null
            : sampledTrajectory(controller, replay, target);

        if (trajectory == null)
        {
            trajectory = rootTrajectory(replay);
        }

        if (trajectory == null)
        {
            return;
        }

        draw(overlay, camera, config, trajectory, currentTick);
    }

    /* Drawing */

    private static void draw(ScreenOverlay overlay, mchorse.bbs_mod.camera.Camera camera, ValueMotionPath config, Trajectory trajectory, float currentTick)
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

        Matrix4f mvp = new Matrix4f(camera.projection).mul(camera.view);
        Vector3d eye = camera.position;

        /* The interpolated curve: a line through every tick with a dot on each (so the
         * spacing shows speed), the exact endpoints kept. */
        trajectory.worldAt(first, POINT_A);

        Vector3f a = screen(overlay, mvp, eye, POINT_A);

        gradient(config, first, currentTick, first, last, COLOR_A);

        if (config.frames.get() && a != null)
        {
            overlay.dot(a.x, a.y, pack(brighten(COLOR_A, DOT_COLOR)));
        }

        for (float tick = first; tick < last; )
        {
            tick = Math.min(tick + 1F, last);

            trajectory.worldAt(tick, POINT_B);

            Vector3f b = screen(overlay, mvp, eye, POINT_B);

            gradient(config, tick, currentTick, first, last, COLOR_B);

            if (a != null && b != null)
            {
                SCRATCH[0] = (COLOR_A[0] + COLOR_B[0]) * 0.5F;
                SCRATCH[1] = (COLOR_A[1] + COLOR_B[1]) * 0.5F;
                SCRATCH[2] = (COLOR_A[2] + COLOR_B[2]) * 0.5F;

                overlay.line(a, b, pack(SCRATCH));
            }

            if (config.frames.get() && b != null)
            {
                overlay.dot(b.x, b.y, pack(brighten(COLOR_B, DOT_COLOR)));
            }

            a = b;
            System.arraycopy(COLOR_B, 0, COLOR_A, 0, 3);
        }

        /* A key, as the timeline draws one, on every keyframe inside the window. */
        if (config.keyframes.get())
        {
            for (float tick : trajectory.keyframeTicks())
            {
                if (tick >= first && tick <= last)
                {
                    trajectory.worldAt(tick, POINT_B);

                    Vector3f key = screen(overlay, mvp, eye, POINT_B);

                    if (key != null)
                    {
                        overlay.key(key.x, key.y, Colors.A100 | config.keyframeColor.get());
                    }
                }
            }
        }

        /* The current frame's place on the path, when it falls inside the window. */
        if (config.current.get() && currentTick >= first && currentTick <= last)
        {
            trajectory.worldAt(currentTick, POINT_B);

            Vector3f current = screen(overlay, mvp, eye, POINT_B);

            if (current != null)
            {
                overlay.ring(current.x, current.y, Colors.A100 | config.currentColor.get());
            }
        }
    }

    /** A world point on the viewport, through the camera (its view is camera-relative). */
    private static Vector3f screen(ScreenOverlay overlay, Matrix4f mvp, Vector3d eye, Vector3d point)
    {
        return overlay.project(mvp, new Vector3f((float) (point.x - eye.x), (float) (point.y - eye.y), (float) (point.z - eye.z)));
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

    /** Lighten a colour towards white, so the per-frame dots read against the line they sit on. */
    private static float[] brighten(float[] src, float[] out)
    {
        out[0] = src[0] + (1F - src[0]) * 0.45F;
        out[1] = src[1] + (1F - src[1]) * 0.45F;
        out[2] = src[2] + (1F - src[2]) * 0.45F;

        return out;
    }

    private static int pack(float[] color)
    {
        return Colors.A100 | ((int) (color[0] * 255) << 16) | ((int) (color[1] * 255) << 8) | (int) (color[2] * 255);
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

    /** The trajectory of a bone or of the form's anchor: both are sampled by posing a scratch
     *  entity tick by tick and reading a matrix off it, and differ only in which matrix. */
    private static Trajectory sampledTrajectory(UIFilmController controller, Replay replay, FilmTarget target)
    {
        World world = MinecraftClient.getInstance().world;
        Form form = replay.form.get();

        if (!ensureScratch(world, form, replay))
        {
            return null;
        }

        Map<String, IEntity> entities = controller.getEntities();

        /* Recompute only when the animation data actually changes (a content signature including
         * keyframe values). The previous per-frame "resample and compare" check was both the
         * source of the jitter — it re-posed the shared scratch entity, corrupting the IK warm-up
         * seed so each recompute drifted ~1cm — and self-triggering (a single off-sequence sample
         * never matches the in-sequence cached value). Without it the cached path is stable while
         * idle and only rebuilt on a real edit. */
        String signature = signature(replay, target);

        if (boneCache == null || !signature.equals(boneCacheSignature))
        {
            /* The sampling replay owns its runtime values; the visible replay is never changed. */
            scratchEntity.replay.copy(replay);
            boneCache = computeSampledTrajectory(entities, replay, target);
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
    private static boolean ensureScratch(World world, Form form, Replay replay)
    {
        if (world == null || form == null)
        {
            return false;
        }

        if (scratchEntity == null || scratchForm != form || !scratchEntity.replay.getId().equals(replay.getId()))
        {
            Form copy = FormUtils.copy(form);

            if (copy == null)
            {
                scratchEntity = null;
                scratchForm = null;

                return false;
            }

            disableActions(copy);

            scratchEntity = new ReplayEntity(world, new Replay(replay.getId()));
            scratchEntity.setForm(copy);
            scratchForm = form;
        }

        return true;
    }

    private static BoneTrajectory computeSampledTrajectory(Map<String, IEntity> entities, Replay replay, FilmTarget target)
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

        try
        {
            for (int i = 0; i < count; i++)
            {
                if (!sampleTargetWorld(entities, replay, target, base + i, LIVE))
                {
                    return null;
                }

                points[i * 3] = LIVE.x;
                points[i * 3 + 1] = LIVE.y;
                points[i * 3 + 2] = LIVE.z;
            }
        }
        finally
        {
            RenderFrame.invalidate();
        }

        TreeSet<Float> ticks = new TreeSet<>();
        String marker = trackMarker(target);
        if (target.is(FilmTarget.Kind.ANCHOR)) collectTicks(ticks, replay.keyframes.anchor);

        for (KeyframeChannel<?> channel : replay.properties.tracks.values())
        {
            if (channel.getId() != null && channel.getId().contains(marker))
            {
                collectTicks(ticks, channel);
            }
        }

        return new BoneTrajectory(base, count, points, range[0], range[1], ticks);
    }

    /** Which property tracks put a keyframe dot on the path: the ones naming this target. */
    private static String trackMarker(FilmTarget target)
    {
        if (target.is(FilmTarget.Kind.ANCHOR))
        {
            return "anchor";
        }

        String bonePath = target.bone();

        return bonePath.contains(".") ? bonePath.substring(bonePath.lastIndexOf('.') + 1) : bonePath;
    }

    /** Pose the scratch entity at {@code tick} and read the target's world position (camera at origin). */
    private static boolean sampleTargetWorld(Map<String, IEntity> entities, Replay replay, FilmTarget target, int tick, Vector3d out)
    {
        StubEntity entity = scratchEntity;

        replay.keyframes.apply(tick, entity);
        entity.update();
        entity.getForm().update(entity);
        replay.properties.applyProperties(entity.getForm(), tick);

        RenderFrame.invalidate();

        Matrix4f matrix = target.is(FilmTarget.Kind.ANCHOR)
            ? FilmMatrices.getGizmoAnchorCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F)
            : FilmMatrices.getGizmoBoneCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F, target.bone(), false);

        if (target.is(FilmTarget.Kind.SPLINE_POINT))
        {
            var point = mchorse.bbs_mod.ui.utils.SplineEditorUtils.resolve(entity.getForm(), target.bone());
            matrix = FilmMatrices.getSplineParentCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F, point);
            if (matrix != null) matrix.translate(point.point().position.get().translate);
        }

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

        for (KeyframeChannel<?> channel : replay.properties.tracks.values())
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
        return channel.isEmpty() ? -Float.MAX_VALUE : (float) channel.getLength();
    }

    private static String signature(Replay replay, FilmTarget target)
    {
        /* The kind is part of the key, not just the bone path: a null path would make the anchor
         * and "no bone" hash alike and hand one the other's cached path. */
        StringBuilder builder = new StringBuilder(replay.getId())
            .append('|').append(target.kind())
            .append('|').append(target.bone());

        signature(builder, replay.keyframes.x);
        signature(builder, replay.keyframes.y);
        signature(builder, replay.keyframes.z);
        signature(builder, replay.keyframes.anchor);
        builder.append('|').append(replay.anchor.getOriginalValue().toData());

        for (KeyframeChannel<?> channel : replay.properties.tracks.values())
        {
            builder.append('#').append(channel.getId());
            signature(builder, channel);
        }

        return builder.toString();
    }

    private static void signature(StringBuilder builder, KeyframeChannel<?> channel)
    {
        /* A content hash so the signature changes on value edits (re-posing a bone), not only on
         * add/remove/move — the path follows a gizmo drag without a per-frame resample of the
         * bone. Field mixing, not serialization: the old toData().toString() built the whole
         * data tree and a giant string for every channel every frame. */
        int hash = 1;

        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            hash = 31 * hash + keyframe.contentHash();
        }

        hash = 31 * hash + channel.getLoops().hashCode();
        builder.append(':').append(hash);
    }

    private static void collectTicks(TreeSet<Float> ticks, KeyframeChannel<?> channel)
    {
        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            ticks.add(keyframe.getTick());
        }
    }
}
