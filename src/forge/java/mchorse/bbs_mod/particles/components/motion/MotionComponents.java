package mchorse.bbs_mod.particles.components.motion;

import mchorse.bbs_mod.particles.ParticleScheme;

/**
 * Helpers for managing the two motion components as two independent axes (position and rotation),
 * each of which may be dynamic or parametric. A {@link ParticleComponentMotionDynamic} exists while
 * any axis is dynamic, a {@link ParticleComponentMotionParametric} while any axis is parametric, and
 * the components' {@code drivesPosition}/{@code drivesRotation} flags record which axis each owns.
 */
public class MotionComponents
{
    public static ParticleComponentMotionDynamic dynamic(ParticleScheme scheme)
    {
        return scheme == null ? null : scheme.get(ParticleComponentMotionDynamic.class);
    }

    public static ParticleComponentMotionParametric parametric(ParticleScheme scheme)
    {
        return scheme == null ? null : scheme.get(ParticleComponentMotionParametric.class);
    }

    public static boolean isPositionParametric(ParticleScheme scheme)
    {
        ParticleComponentMotionParametric parametric = parametric(scheme);

        return parametric != null && parametric.drivesPosition;
    }

    public static boolean isRotationParametric(ParticleScheme scheme)
    {
        ParticleComponentMotionParametric parametric = parametric(scheme);

        return parametric != null && parametric.drivesRotation;
    }

    /**
     * Reconcile the motion components to the requested per-axis modes: create/remove the dynamic and
     * parametric components as needed and tag which axis each one drives.
     */
    public static void setModes(ParticleScheme scheme, boolean positionParametric, boolean rotationParametric)
    {
        boolean needDynamic = !positionParametric || !rotationParametric;
        boolean needParametric = positionParametric || rotationParametric;

        ParticleComponentMotionDynamic dynamic = dynamic(scheme);
        ParticleComponentMotionParametric parametric = parametric(scheme);

        boolean changed = false;

        if (needDynamic && dynamic == null)
        {
            dynamic = scheme.add(ParticleComponentMotionDynamic.class);
            changed = true;
        }
        else if (!needDynamic && dynamic != null)
        {
            scheme.remove(ParticleComponentMotionDynamic.class);
            dynamic = null;
            changed = true;
        }

        if (needParametric && parametric == null)
        {
            parametric = scheme.add(ParticleComponentMotionParametric.class);
            changed = true;
        }
        else if (!needParametric && parametric != null)
        {
            scheme.remove(ParticleComponentMotionParametric.class);
            parametric = null;
            changed = true;
        }

        if (dynamic != null)
        {
            dynamic.drivesPosition = !positionParametric;
            dynamic.drivesRotation = !rotationParametric;
        }

        if (parametric != null)
        {
            parametric.drivesPosition = positionParametric;
            parametric.drivesRotation = rotationParametric;
        }

        /* Rebuild the cached component interface lists: scheme.remove() (unlike add()) doesn't, so a
         * removed component would otherwise keep being applied until the next full reload. */
        if (changed)
        {
            scheme.setup();
        }
    }
}
