package mchorse.bbs_mod.api;

import mchorse.bbs_mod.cubic.physics.WindControl;
import org.joml.Vector3f;

/**
 * A form's wind, felt at one point and moment: the same field BBS's own chains are pushed by, so
 * anything else that moves in the wind moves in the same gusts.
 *
 * <p>The strength is in gravity's terms: a strength of 1 pushes as hard as a gravity of 1 pulls.
 * Turbulence modulates it by a noise field sampled at the point and scrolled downwind over time — a
 * pure function of position and film time, so playback and export reproduce, and two things at the
 * same place on the same tick feel the same gust.</p>
 */
public final class WindSampler
{
    private static final float EPS = 1.0e-6F;

    /** Converts film ticks × the gust speed into noise-space drift, so a gust speed of 1 evolves the
     * pattern over roughly a second of playback (20 ticks ≈ one noise cell). */
    private static final float GUST_TIME_SCALE = 0.04F;

    private WindSampler()
    {}

    /**
     * Whether {@code wind} blows at all, filling {@code out} with its unit direction. A local wind's
     * direction is still in the form's own frame here; rotate it by the form's orientation.
     */
    public static boolean direction(WindControl wind, Vector3f out)
    {
        if (wind == null || wind.strength <= 0F)
        {
            return false;
        }

        out.set(wind.x, wind.y, wind.z);

        if (out.lengthSquared() < EPS * EPS)
        {
            return false;
        }

        out.normalize();

        return true;
    }

    /**
     * How strongly {@code wind} blows at {@code position} on film tick {@code time}, as a multiple of
     * its steady strength: 1 without turbulence, around 1 with it, never below 0.
     *
     * @param direction the wind's unit direction as it blows (see {@link #direction})
     */
    public static float gust(WindControl wind, Vector3f direction, float time, Vector3f position)
    {
        return gust(wind.turbulence, wind.turbulenceSpeed, wind.turbulenceScale, direction, time, position.x, position.y, position.z);
    }

    /** {@link #gust(WindControl, Vector3f, float, Vector3f)} with the wind's turbulence settings spelled out. */
    public static float gust(float turbulence, float turbulenceSpeed, float turbulenceScale, Vector3f direction, float time, float x, float y, float z)
    {
        if (turbulence <= 0F)
        {
            return 1F;
        }

        float drift = time * turbulenceSpeed * GUST_TIME_SCALE;
        float n = fbm(x * turbulenceScale - direction.x * drift, y * turbulenceScale - direction.y * drift, z * turbulenceScale - direction.z * drift);

        return Math.max(0F, 1F + turbulence * n);
    }

    /** Two-octave fractal value noise: a slow swell (the gust) plus a faster, weaker ripple (the flutter). */
    private static float fbm(float x, float y, float z)
    {
        return valueNoise(x, y, z) * 0.65F
            + valueNoise(x * 2.3F + 11.1F, y * 2.3F + 7.7F, z * 2.3F + 3.3F) * 0.35F;
    }

    /** Smooth 3D value noise in [-1, 1]: hashed lattice corners, trilinearly interpolated with a smootherstep fade. */
    private static float valueNoise(float x, float y, float z)
    {
        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);

        float xf = fade(x - xi);
        float yf = fade(y - yi);
        float zf = fade(z - zi);

        float x00 = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), xf);
        float x10 = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), xf);
        float x01 = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), xf);
        float x11 = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), xf);

        float y0 = lerp(x00, x10, yf);
        float y1 = lerp(x01, x11, yf);

        return lerp(y0, y1, zf);
    }

    /** Integer lattice hash to [-1, 1]; deterministic (int overflow wraps), no shared state. */
    private static float hash(int x, int y, int z)
    {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);

        return (h & 0xFFFF) / 32767.5F - 1F;
    }

    private static float fade(float t)
    {
        return t * t * t * (t * (t * 6F - 15F) + 10F);
    }

    private static float lerp(float a, float b, float t)
    {
        return a + (b - a) * t;
    }

    private static int floor(float v)
    {
        int i = (int) v;

        return v < i ? i - 1 : i;
    }
}
