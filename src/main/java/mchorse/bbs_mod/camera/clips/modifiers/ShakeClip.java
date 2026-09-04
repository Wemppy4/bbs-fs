package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;

/**
 * Shake modifier
 *
 * This modifier shakes the camera depending on the given component
 * flags.
 *
 * <p>It shakes in one of two characters. A sine, which is perfectly periodic and moves
 * every enabled component off the same two waves — read it as a sway rather than a shake.
 * Or noise, where each component wanders on a channel of its own, which is what reads as a
 * camera someone is holding.</p>
 */
public class ShakeClip extends ComponentClip
{
    public final ValueFloat shake = new ValueFloat("shake", 0F);
    public final ValueFloat shakeAmount = new ValueFloat("shakeAmount", 0F);
    public final ValueBoolean noise = new ValueBoolean("noise", false);

    public ShakeClip()
    {
        super();

        this.add(this.shake);
        this.add(this.shakeAmount);
        this.add(this.noise);

        /* Yaw and pitch should be enabled by default */
        this.active.set(0b0011000);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        float shake = this.shake.get();
        float amount = this.shakeAmount.get();
        float time = context.ticks + context.transition;
        float period = shake == 0 ? 1 : shake;
        float x = time / period;

        boolean isX = this.isActive(0);
        boolean isY = this.isActive(1);
        boolean isZ = this.isActive(2);
        boolean isYaw = this.isActive(3);
        boolean isPitch = this.isActive(4);
        boolean isRoll = this.isActive(5);
        boolean isFov = this.isActive(6);

        if (this.noise.get())
        {
            /* The sine below turns around every PI * shake ticks, while noise turns around
             * at roughly every second cell — so cells of PI * shake / 2 ticks put the two at
             * the same speed, and the toggle is left changing the character and nothing else.
             * Measured: 160 vs 159 direction changes per 1000 ticks at shake = 2. */
            float n = time / (period * (float) (Math.PI / 2));

            if (isX) position.point.x += noise(n, 0) * amount;
            if (isY) position.point.y += noise(n, 1) * amount;
            if (isZ) position.point.z += noise(n, 2) * amount;
            if (isYaw) position.angle.yaw += noise(n, 3) * amount;
            if (isPitch) position.angle.pitch += noise(n, 4) * amount;
            if (isRoll) position.angle.roll += noise(n, 5) * amount;
            if (isFov) position.angle.fov += noise(n, 6) * amount;

            return;
        }

        double sin = Math.sin(x);
        double cos = Math.cos(x);

        if (isYaw && isPitch && !isX && !isY && !isZ && !isRoll && !isFov)
        {
            float swingX = (float) (sin * sin * cos * Math.cos(x / 2));
            float swingY = (float) (cos * sin * sin);

            position.angle.yaw += swingX * amount;
            position.angle.pitch += swingY * amount;
        }
        else
        {
            if (isX)
            {
                position.point.x += sin * amount;
            }

            if (isY)
            {
                position.point.y -= sin * amount;
            }

            if (isZ)
            {
                position.point.z += cos * amount;
            }

            if (isYaw)
            {
                position.angle.yaw += sin * amount;
            }

            if (isPitch)
            {
                position.angle.pitch += cos * amount;
            }

            if (isRoll)
            {
                position.angle.roll += sin * amount;
            }

            if (isFov)
            {
                position.angle.fov += cos * amount;
            }
        }
    }

    /**
     * Value noise: a smooth signal in [-1, 1] that wanders instead of repeating.
     *
     * <p>It is a pure function of x and the channel — the same tick always yields the same
     * value, so scrubbing the timeline, playback and the final render all agree. That is why
     * this cannot reach for {@link Math#random()}.</p>
     *
     * <p>Each component asks for a channel of its own, which is what keeps the axes
     * independent; the sine path moves them all off the same two waves.</p>
     */
    private static float noise(float x, int channel)
    {
        int cell = (int) Math.floor(x);
        float t = x - cell;

        /* Smoothstep, so the lattice points don't show up as kinks */
        t = t * t * (3F - 2F * t);

        return Lerps.lerp(hash(cell, channel), hash(cell + 1, channel), t);
    }

    /**
     * Hash a lattice point into [-1, 1). An integer avalanche, so that neighbouring cells —
     * and neighbouring channels — land nowhere near each other.
     */
    private static float hash(int cell, int channel)
    {
        int h = cell * 374761393 + channel * 668265263;

        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);

        return (h >>> 8) / (float) (1 << 24) * 2F - 1F;
    }

    @Override
    public Clip create()
    {
        return new ShakeClip();
    }
}