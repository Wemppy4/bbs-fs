package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.MotionPathValue;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.ReplayEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.ScreenOverlay;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The film's motion paths drawn into the viewport — Blender's motion paths: each replay keeps a list
 * of the points the animator asked to follow ({@link Replay#motionPaths}: its root, its anchor, a
 * bone, a spline point), and every one of them is on screen in its own colour whatever is selected,
 * a sampled curve with a key per keyframe and a ring on the current frame.
 *
 * <p>The root path comes straight from the position channels. Bone, anchor and spline paths must be
 * simulated tick by tick, so they run on a scratch entity per replay (never the on-screen model): one
 * pass over the replay poses it once per tick and reads every listed point off that pose, into a cache
 * rebuilt only when the animation or the list changes and, as in Blender, once a transform ends rather
 * than during it. Procedural limb motion and physics are approximate there; keyframed pose and IK are
 * exact. A point that can't be sampled is not drawn (and reads red in the list) — never swapped for
 * the root's path.
 *
 * <p>Drawn on the film viewport's markup ({@link ScreenOverlay}) in the markup's sizes.
 */
public class MotionPath
{
    /** The colours paths are handed in turn; a new path takes the first one no path of the film has. */
    public static final int[] PALETTE = {0x45c8ff, 0xff6fb1, 0x7ae37a, 0xffd34d, 0xb48cff, 0xff8a4d, 0x4dffd2, 0xff4d4d};

    private static final int KEY_COLOR = Colors.WHITE;
    private static final int CURRENT_COLOR = 0xff9e3d;

    public static final String ROOT = "root";
    public static final String ANCHOR = "anchor";
    private static final String BONE = "bone:";
    private static final String SPLINE = "spline:";

    private static final float[] COLOR = new float[3];
    private static final float[] PAST = new float[3];
    private static final float[] FUTURE = new float[3];
    private static final float[] COLOR_A = new float[3];
    private static final float[] COLOR_B = new float[3];
    private static final float[] SCRATCH = new float[3];

    private static final Vector3d POINT_A = new Vector3d();
    private static final Vector3d POINT_B = new Vector3d();
    private static final Vector3f TEMP = new Vector3f();

    /* Sampled paths per replay id; dropped for replays that no longer have any. */
    private static final Map<String, ReplayPaths> CACHE = new HashMap<>();

    /* The animator's action slots (mirrors Animator#setup) — emptied on the scratch form so the
     * always-applied actions don't perturb the sampled bone path. */
    private static final String[] ACTION_KEYS = {
        "idle", "running", "sprinting", "crouching", "crouching_idle", "dying", "falling",
        "swimming", "swimming_idle", "riding", "riding_idle", "flying", "flying_idle",
        "swipe", "jump", "jump_alt", "hurt", "land", "shoot", "consume", "base_pre", "base_post"
    };

    /* The list */

    /** How a target is written down in {@link MotionPathValue#target}; null for nothing to follow. */
    public static String key(FilmTarget target)
    {
        if (target == null || target.isNone())
        {
            return null;
        }

        return switch (target.kind())
        {
            case ROOT -> ROOT;
            case ANCHOR -> ANCHOR;
            case BONE -> BONE + target.bone();
            case SPLINE_POINT -> SPLINE + target.bone();
            default -> null;
        };
    }

    public static FilmTarget target(String key)
    {
        if (key.equals(ANCHOR)) return FilmTarget.anchor(null);
        if (key.startsWith(BONE)) return FilmTarget.bone(key.substring(BONE.length()), null);
        if (key.startsWith(SPLINE)) return FilmTarget.splinePoint(key.substring(SPLINE.length()), null);

        return FilmTarget.root(null);
    }

    /** The bone, spline point or anchor a path follows, short; empty for the root. */
    public static String shortName(String key)
    {
        if (key.equals(ROOT))
        {
            return "";
        }

        String name = key.startsWith(BONE) ? key.substring(BONE.length()) : key.startsWith(SPLINE) ? key.substring(SPLINE.length()) : key;
        int dot = name.lastIndexOf('.');

        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /** M: put the path of what's selected on screen, or take it off if it's there already. */
    public static void toggle(Film film, Replay replay, FilmTarget target)
    {
        String key = key(target);

        if (film == null || replay == null || key == null)
        {
            return;
        }

        MotionPathValue existing = replay.motionPaths.find(key);

        if (existing != null)
        {
            remove(replay, existing);
        }
        else
        {
            add(film, replay, key);
        }
    }

    public static void add(Film film, Replay replay, String key)
    {
        if (replay.motionPaths.find(key) != null)
        {
            return;
        }

        MotionPathValue path = new MotionPathValue("");

        path.target.set(key);
        path.color.set(nextColor(film));

        BaseValue.edit(replay.motionPaths, (v) ->
        {
            replay.motionPaths.add(path);
            replay.motionPaths.sync();
        });
    }

    public static void remove(Replay replay, MotionPathValue path)
    {
        BaseValue.edit(replay.motionPaths, (v) ->
        {
            replay.motionPaths.getAllTyped().remove(path);
            replay.motionPaths.sync();
        });
    }

    public static void clear(Film film)
    {
        for (Replay replay : film.replays.getList())
        {
            if (!replay.motionPaths.getAllTyped().isEmpty())
            {
                BaseValue.edit(replay.motionPaths, (v) ->
                {
                    replay.motionPaths.getAllTyped().clear();
                    replay.motionPaths.sync();
                });
            }
        }
    }

    private static int nextColor(Film film)
    {
        Set<Integer> used = new HashSet<>();
        int count = 0;

        for (Replay replay : film.replays.getList())
        {
            for (MotionPathValue path : replay.motionPaths.getAllTyped())
            {
                used.add(path.color.get());
                count += 1;
            }
        }

        for (int color : PALETTE)
        {
            if (!used.contains(color))
            {
                return color;
            }
        }

        return PALETTE[count % PALETTE.length];
    }

    /** Whether the path was asked for and couldn't be sampled (its bone is gone, the form has none). */
    public static boolean isBroken(Replay replay, MotionPathValue path)
    {
        ReplayPaths paths = CACHE.get(replay.getId());

        return paths != null && paths.failed.contains(path.target.get());
    }

    /* Rendering */

    public static void render(ScreenOverlay overlay, mchorse.bbs_mod.camera.Camera camera, ValueMotionPath config, UIFilmController controller, Film film, int tick)
    {
        Set<String> sampled = new HashSet<>();

        for (Replay replay : film.replays.getList())
        {
            List<MotionPathValue> paths = replay.motionPaths.getAllTyped();

            if (paths.isEmpty() || replay.relative.get())
            {
                continue;
            }

            float currentTick = replay.getTick(tick);
            ReplayPaths cache = null;

            for (MotionPathValue path : paths)
            {
                String key = path.target.get();
                Trajectory trajectory;

                if (key.equals(ROOT))
                {
                    trajectory = rootTrajectory(replay);
                }
                else
                {
                    if (cache == null)
                    {
                        cache = sampledPaths(controller, replay, paths);
                        sampled.add(replay.getId());
                    }

                    trajectory = cache == null ? null : cache.paths.get(key);
                }

                if (trajectory != null)
                {
                    draw(overlay, camera, config, trajectory, currentTick, path.color.get());
                }
            }
        }

        CACHE.keySet().retainAll(sampled);
    }

    private static void draw(ScreenOverlay overlay, mchorse.bbs_mod.camera.Camera camera, ValueMotionPath config, Trajectory trajectory, float currentTick, int color)
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

        /* The path's own colour at the current frame, darker into the past, lighter into the future. */
        unpack(color, COLOR);
        mix(COLOR, 0F, 0.6F, PAST);
        mix(COLOR, 1F, 0.75F, FUTURE);

        Matrix4f mvp = new Matrix4f(camera.projection).mul(camera.view);
        Vector3d eye = camera.position;

        /* The interpolated curve: a line through every tick, the exact endpoints kept. */
        trajectory.worldAt(first, POINT_A);

        Vector3f a = screen(overlay, mvp, eye, POINT_A);

        gradient(first, currentTick, first, last, COLOR_A);

        for (float tick = first; tick < last; )
        {
            tick = Math.min(tick + 1F, last);

            trajectory.worldAt(tick, POINT_B);

            Vector3f b = screen(overlay, mvp, eye, POINT_B);

            gradient(tick, currentTick, first, last, COLOR_B);

            if (a != null && b != null)
            {
                SCRATCH[0] = (COLOR_A[0] + COLOR_B[0]) * 0.5F;
                SCRATCH[1] = (COLOR_A[1] + COLOR_B[1]) * 0.5F;
                SCRATCH[2] = (COLOR_A[2] + COLOR_B[2]) * 0.5F;

                overlay.line(a, b, pack(SCRATCH));
            }

            a = b;
            System.arraycopy(COLOR_B, 0, COLOR_A, 0, 3);
        }

        /* A key, as the timeline draws one, on every keyframe inside the window. */
        for (float tick : trajectory.keyframeTicks())
        {
            if (tick >= first && tick <= last)
            {
                trajectory.worldAt(tick, POINT_B);

                Vector3f key = screen(overlay, mvp, eye, POINT_B);

                if (key != null)
                {
                    overlay.key(key.x, key.y, Colors.A100 | KEY_COLOR);
                }
            }
        }

        /* The current frame's place on the path, when it falls inside the window. */
        if (currentTick >= first && currentTick <= last)
        {
            trajectory.worldAt(currentTick, POINT_B);

            Vector3f current = screen(overlay, mvp, eye, POINT_B);

            if (current != null)
            {
                overlay.ring(current.x, current.y, Colors.A100 | CURRENT_COLOR);
            }
        }
    }

    /** A world point on the viewport, through the camera (its view is camera-relative). */
    private static Vector3f screen(ScreenOverlay overlay, Matrix4f mvp, Vector3d eye, Vector3d point)
    {
        return overlay.project(mvp, new Vector3f((float) (point.x - eye.x), (float) (point.y - eye.y), (float) (point.z - eye.z)));
    }

    /**
     * The colour for a point at {@code tick}: the path's colour at the current frame, fading to
     * its past shade fully in the past and its future shade fully in the future.
     */
    private static void gradient(float tick, float current, float first, float last, float[] out)
    {
        float factor;

        if (tick <= current)
        {
            factor = current <= first ? 0F : -(current - tick) / (current - first);
        }
        else
        {
            factor = current >= last ? 0F : (tick - current) / (last - current);
        }

        float[] target = factor < 0F ? PAST : FUTURE;
        float weight = Math.abs(factor);

        out[0] = COLOR[0] + (target[0] - COLOR[0]) * weight;
        out[1] = COLOR[1] + (target[1] - COLOR[1]) * weight;
        out[2] = COLOR[2] + (target[2] - COLOR[2]) * weight;
    }

    /** {@code src} moved {@code weight} of the way towards a grey level (0 black, 1 white). */
    private static void mix(float[] src, float level, float weight, float[] out)
    {
        out[0] = src[0] + (level - src[0]) * weight;
        out[1] = src[1] + (level - src[1]) * weight;
        out[2] = src[2] + (level - src[2]) * weight;
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

    /* Sampled trajectories: every listed point of a replay, simulated in one pass on a scratch entity. */

    /** A replay's sampled paths and the scratch entity they're simulated on. */
    private static final class ReplayPaths
    {
        /* Reused across recomputes (re-posed per tick), so a recompute — one per edit — does not pay
         * a deep form copy each time; it is only rebuilt when the form itself changes. */
        private Form form;
        private ReplayEntity scratch;

        private String signature;
        private final Map<String, SampledTrajectory> paths = new HashMap<>();
        private final Set<String> failed = new HashSet<>();
    }

    private static ReplayPaths sampledPaths(UIFilmController controller, Replay replay, List<MotionPathValue> paths)
    {
        World world = MinecraftClient.getInstance().world;
        Form form = replay.form.get();
        ReplayPaths cache = CACHE.computeIfAbsent(replay.getId(), (id) -> new ReplayPaths());

        /* As in Blender, the paths hold still while a transform runs (gizmo or G/R/S) and are rebuilt
         * once it ends: a drag changes the keys every frame, and following it meant a simulation of
         * the whole replay per frame. */
        if (cache.signature != null && Gizmo.INSTANCE.getTrackedGesture() != null)
        {
            return cache;
        }

        List<String> keys = new ArrayList<>();

        for (MotionPathValue path : paths)
        {
            if (!path.target.get().equals(ROOT))
            {
                keys.add(path.target.get());
            }
        }

        /* Recompute only when the animation data or the list actually changes (a content signature
         * including keyframe values). A per-frame "resample and compare" check used to re-pose the
         * shared scratch entity, corrupting the IK warm-up seed so each recompute drifted ~1cm, and
         * triggered itself; without it the cached paths are stable while idle. A failed sampling is
         * cached as well, so a missing bone isn't searched for every frame. */
        String signature = signature(replay) + '|' + String.join(",", keys) + '|' + System.identityHashCode(form);

        if (signature.equals(cache.signature))
        {
            return cache;
        }

        cache.signature = signature;
        cache.paths.clear();
        cache.failed.clear();

        if (!ensureScratch(cache, world, form, replay))
        {
            cache.failed.addAll(keys);

            return cache;
        }

        /* The sampling replay owns its runtime values; the visible replay is never changed. */
        cache.scratch.replay.copy(replay);
        sample(cache, controller.getEntities(), replay, keys);

        return cache;
    }

    /**
     * Rebuild the scratch entity when the form changes. Its actions (the always-applied
     * idle/walk animations) are disabled: the renderer's animator switches and rewinds them per
     * tick from the entity's movement, which made the sampled bone path jitter. The path is meant
     * to show the authored motion (keyframes + IK), so the procedural overlay is dropped.
     */
    private static boolean ensureScratch(ReplayPaths cache, World world, Form form, Replay replay)
    {
        if (world == null || form == null)
        {
            return false;
        }

        if (cache.scratch == null || cache.form != form)
        {
            Form copy = FormUtils.copy(form);

            if (copy == null)
            {
                cache.scratch = null;
                cache.form = null;

                return false;
            }

            disableActions(copy);

            cache.scratch = new ReplayEntity(world, new Replay(replay.getId()));
            cache.scratch.setForm(copy);
            cache.form = form;
        }

        return true;
    }

    /** Pose the scratch entity once per tick and read every listed point off that pose. */
    private static void sample(ReplayPaths cache, Map<String, IEntity> entities, Replay replay, List<String> keys)
    {
        float[] range = range(replay);

        if (range == null)
        {
            cache.failed.addAll(keys);

            return;
        }

        int base = (int) Math.floor(range[0]);
        int end = (int) Math.ceil(range[1]);
        int count = end - base + 1;
        List<FilmTarget> targets = new ArrayList<>();
        Map<String, double[]> points = new HashMap<>();

        for (String key : keys)
        {
            targets.add(target(key));
            points.put(key, new double[count * 3]);
        }

        try
        {
            for (int i = 0; i < count; i++)
            {
                pose(replay, cache.scratch, base + i);

                for (int t = 0; t < keys.size(); t++)
                {
                    String key = keys.get(t);
                    double[] list = points.get(key);

                    if (list == null)
                    {
                        continue;
                    }

                    Matrix4f matrix = targetMatrix(entities, cache.scratch, replay, targets.get(t));

                    if (matrix == null)
                    {
                        points.remove(key);
                        cache.failed.add(key);

                        continue;
                    }

                    matrix.getTranslation(TEMP);
                    list[i * 3] = TEMP.x;
                    list[i * 3 + 1] = TEMP.y;
                    list[i * 3 + 2] = TEMP.z;
                }
            }
        }
        finally
        {
            RenderFrame.invalidate();
        }

        for (int t = 0; t < keys.size(); t++)
        {
            String key = keys.get(t);
            double[] list = points.get(key);

            if (list != null)
            {
                cache.paths.put(key, new SampledTrajectory(base, count, list, range[0], range[1], keyframeTicks(replay, targets.get(t))));
            }
        }
    }

    /** The scratch entity as the replay has it at {@code tick}. */
    private static void pose(Replay replay, ReplayEntity entity, int tick)
    {
        replay.keyframes.apply(tick, entity);
        entity.update();
        entity.getForm().update(entity);
        replay.properties.applyProperties(entity.getForm(), tick);

        RenderFrame.invalidate();
    }

    /** Where the target sits on the posed scratch entity (camera at origin); null when it can't be found. */
    private static Matrix4f targetMatrix(Map<String, IEntity> entities, ReplayEntity entity, Replay replay, FilmTarget target)
    {
        if (target.is(FilmTarget.Kind.ANCHOR))
        {
            return FilmMatrices.getGizmoAnchorCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F);
        }

        if (target.is(FilmTarget.Kind.SPLINE_POINT))
        {
            var point = SplineEditorUtils.resolve(entity.getForm(), target.bone());

            if (point == null)
            {
                return null;
            }

            Matrix4f matrix = FilmMatrices.getSplineParentCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F, point);

            if (matrix != null)
            {
                matrix.translate(point.point().position.get().translate);
            }

            return matrix;
        }

        return FilmMatrices.getGizmoBoneCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F, target.bone(), false);
    }

    /** The ticks that put a key on the path: the keyframes of the property tracks naming this target. */
    private static TreeSet<Float> keyframeTicks(Replay replay, FilmTarget target)
    {
        TreeSet<Float> ticks = new TreeSet<>();
        String marker;

        if (target.is(FilmTarget.Kind.ANCHOR))
        {
            collectTicks(ticks, replay.keyframes.anchor);
            marker = "anchor";
        }
        else
        {
            String bonePath = target.bone();

            marker = bonePath.contains(".") ? bonePath.substring(bonePath.lastIndexOf('.') + 1) : bonePath;
        }

        for (KeyframeChannel<?> channel : replay.properties.tracks.values())
        {
            if (channel.getId() != null && channel.getId().contains(marker))
            {
                collectTicks(ticks, channel);
            }
        }

        return ticks;
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

    private record SampledTrajectory(int base, int count, double[] points, float first, float last, TreeSet<Float> keyframeTicks) implements Trajectory
    {
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

    private static String signature(Replay replay)
    {
        StringBuilder builder = new StringBuilder(replay.getId());

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
         * add/remove/move — the path follows an edit without a per-frame resample of the bone.
         * Field mixing, not serialization: the old toData().toString() built the whole data tree
         * and a giant string for every channel every frame. */
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
