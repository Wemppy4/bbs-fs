package mchorse.bbs_mod.cubic.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Force fields acting on the chains: the per-chain gravity (with its relative-gravity rotation) and the
 * global wind. Both produce a per-step acceleration the solver adds during Verlet integration.
 */
final class PhysicsForces
{
    private static final float EPS = 1.0e-6f;

    /** Converts film ticks × the user's gust speed into noise-space drift, so a gust speed of 1 evolves the
     * pattern over roughly a second of playback (20 ticks ≈ one noise cell). */
    private static final float GUST_TIME_SCALE = 0.04F;

    private PhysicsForces()
    {
    }

    static void computeGravityDirection(ModelPhysicsCache.CompiledChain chain, Quaternionf parentRotation, float gravity, Vector3f out)
    {
        out.set(0F, -1F, 0F);

        if (chain.relativeGravity() && parentRotation != null)
        {
            /* Model bone forward axis is -Y in this rig convention. */
            parentRotation.transform(out);
        }

        if (chain.hasGravityRotation())
        {
            if (parentRotation != null)
            {
                /* Apply user rotation in chain local space, then convert back to world space. */
                Quaternionf inverseParent = new Quaternionf(parentRotation).invert();
                inverseParent.transform(out);
                chain.applyGravityRotation(out);
                parentRotation.transform(out);
            }
            else
            {
                chain.applyGravityRotation(out);
            }
        }

        if (out.lengthSquared() < EPS * EPS)
        {
            out.set(0F, -1F, 0F);
        }

        out.normalize().mul(gravity);
    }

    /**
     * Resolves the wind's unit direction into {@code dirOut} and returns the base acceleration magnitude
     * ({@code base} × strength), or 0 (leaving {@code dirOut} untouched) when there is no wind. The solver
     * calls this once per step, then asks for the per-point force via {@link #windForceAt}.
     */
    static float prepareWind(ModelPhysicsConfig.Wind wind, float base, Vector3f dirOut)
    {
        if (wind == null || !wind.active())
        {
            return 0F;
        }

        dirOut.set(wind.x(), wind.y(), wind.z());

        if (dirOut.lengthSquared() < EPS * EPS)
        {
            return 0F;
        }

        dirOut.normalize();

        return base * wind.strength();
    }

    /**
     * The wind acceleration felt at one point. The steady force is {@code dir × baseMagnitude}; turbulence
     * then modulates the magnitude by a noise field sampled at the point's position and scrolled along the
     * wind direction over time. Nearby points (along a chain) sample nearby — but not identical — values, so
     * the chain ripples instead of moving rigidly, and the gust pattern drifts downwind as time advances.
     * Deterministic: the noise is a pure function of position and film time, so playback and export reproduce.
     */
    static void windForceAt(Vector3f dir, float baseMagnitude, ModelPhysicsConfig.Wind wind, float time, Vector3f pos, Vector3f out)
    {
        float turbulence = wind.turbulence();
        float factor = 1F;

        if (turbulence > 0F)
        {
            float scale = wind.turbulenceScale();
            float drift = time * wind.turbulenceSpeed() * GUST_TIME_SCALE;

            float n = fbm(pos.x * scale - dir.x * drift, pos.y * scale - dir.y * drift, pos.z * scale - dir.z * drift);

            factor = 1F + turbulence * n;

            if (factor < 0F)
            {
                factor = 0F;
            }
        }

        out.set(dir).mul(baseMagnitude * factor);
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
