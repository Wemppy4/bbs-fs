package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * The selected replay's world-space trajectory drawn into the film viewport: a
 * curve sampled from the position channels ({@link Replay#keyframes}) with a dot
 * on every tick, a bigger marker on every keyed tick and a highlight on the
 * current frame — the same idea as Blender's motion paths, a read-only overlay
 * that shows the shape of the movement. Every part is configured through
 * {@link ValueMotionPath} (edited from the preview's motion path button).
 *
 * <p>It draws in the world / 3D pass like {@link UIFilmController}'s orbit
 * centre marker (camera-relative, depth disabled so the whole path stays
 * visible through the model). The curve is a camera-facing ribbon (one flat
 * quad per segment, widened perpendicular to the segment and to the view ray)
 * and the dots are small axis-aligned cubes — both go straight through the
 * {@code position_color} program. A ribbon is used instead of a GL line
 * (clamped to 1px in the core profile) or a rotated box (the box's orientation
 * maths mis-aimed it).
 */
public class MotionPath
{
    private static final float[] COLOR_A = new float[3];
    private static final float[] COLOR_B = new float[3];
    private static final float[] DOT_COLOR = new float[3];
    private static final float[] SCRATCH = new float[3];

    public static void render(WorldRenderContext context, ValueMotionPath config, Replay replay, float currentTick)
    {
        if (replay == null || replay.relative.get())
        {
            return;
        }

        KeyframeChannel<Double> x = replay.keyframes.x;
        KeyframeChannel<Double> y = replay.keyframes.y;
        KeyframeChannel<Double> z = replay.keyframes.z;

        if (x.isEmpty() && y.isEmpty() && z.isEmpty())
        {
            return;
        }

        float first = Float.MAX_VALUE;
        float last = -Float.MAX_VALUE;

        for (KeyframeChannel<Double> channel : Arrays.asList(x, y, z))
        {
            if (!channel.isEmpty())
            {
                first = Math.min(first, channel.get(0).getTick());
                last = Math.max(last, channel.get(channel.getKeyframes().size() - 1).getTick());
            }
        }

        /* Limit the path to a window around the current frame. */
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

        /* The world is rendered camera-relative, so the points are subtracted
         * from the camera here, in double precision: feeding raw world
         * coordinates into the float matrix collapses each short per-tick
         * segment into rounding noise (the same convention as the orbit centre
         * marker and the recording overlays). */
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
         * tick (so beziers read smooth and the spacing shows speed), the exact
         * endpoints kept regardless of a fractional range. */
        Vector3d prev = sample(replay, first, cx, cy, cz, new Vector3d());

        gradient(config, first, currentTick, first, last, COLOR_A);

        if (config.frames.get())
        {
            dot(builder, stack, prev, config.frameSize.get(), brighten(COLOR_A, DOT_COLOR));
        }

        for (float tick = first; tick < last; )
        {
            tick = Math.min(tick + 1F, last);

            Vector3d point = sample(replay, tick, cx, cy, cz, new Vector3d());

            gradient(config, tick, currentTick, first, last, COLOR_B);
            ribbon(builder, matrix, prev, point, COLOR_A, COLOR_B, halfWidth);

            if (config.frames.get())
            {
                dot(builder, stack, point, config.frameSize.get(), brighten(COLOR_B, DOT_COLOR));
            }

            prev = point;
            System.arraycopy(COLOR_B, 0, COLOR_A, 0, 3);
        }

        /* A bigger marker on every keyed tick across the three channels. */
        if (config.keyframes.get())
        {
            unpack(config.keyframeColor.get(), SCRATCH);

            TreeSet<Float> ticks = new TreeSet<>();

            collectTicks(ticks, x);
            collectTicks(ticks, y);
            collectTicks(ticks, z);

            for (float tick : ticks)
            {
                if (tick >= first && tick <= last)
                {
                    dot(builder, stack, sample(replay, tick, cx, cy, cz, prev), config.keyframeSize.get(), SCRATCH);
                }
            }
        }

        /* The current frame's place on the path, when it falls inside the range. */
        if (config.current.get() && currentTick >= first && currentTick <= last)
        {
            unpack(config.currentColor.get(), SCRATCH);
            dot(builder, stack, sample(replay, currentTick, cx, cy, cz, prev), config.currentSize.get(), SCRATCH);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * The colour for a point at {@code tick}: the line colour at the current
     * frame, fading to {@code pastColor} fully in the past and {@code futureColor}
     * fully in the future, so the direction in time is visible. With the gradient
     * off it is just the line colour.
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
     * camera. Its width is distance-scaled to stay a constant on-screen size; the
     * two ends carry {@code colorA} / {@code colorB} for the time gradient.
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

    private static void collectTicks(TreeSet<Float> ticks, KeyframeChannel<Double> channel)
    {
        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            ticks.add(keyframe.getTick());
        }
    }

    private static Vector3d sample(Replay replay, float tick, double cx, double cy, double cz, Vector3d out)
    {
        return out.set(
            replay.keyframes.x.interpolate(tick) - cx,
            replay.keyframes.y.interpolate(tick) - cy,
            replay.keyframes.z.interpolate(tick) - cz
        );
    }
}
