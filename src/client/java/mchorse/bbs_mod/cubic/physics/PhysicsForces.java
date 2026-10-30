package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.api.WindSampler;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Force fields acting on the chains: the per-chain gravity (with its relative-gravity rotation) and the
 * global wind. Both produce a per-step acceleration the solver adds during Verlet integration.
 */
final class PhysicsForces
{
    private static final float EPS = 1.0e-6f;

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
        float factor = WindSampler.gust(wind.turbulence(), wind.turbulenceSpeed(), wind.turbulenceScale(), dir, time, pos.x, pos.y, pos.z);

        out.set(dir).mul(baseMagnitude * factor);
    }
}
