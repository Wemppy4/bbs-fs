package mchorse.bbs_mod.particles.components.motion;

import mchorse.bbs_mod.particles.components.ParticleComponentBase;

/**
 * Base for the two motion components ({@link ParticleComponentMotionDynamic} and
 * {@link ParticleComponentMotionParametric}). In the Bedrock format these are mutually exclusive and
 * each bundles both position and rotation, but here they may coexist so that position and rotation can
 * use different modes (e.g. dynamic position with parametric rotation). The {@code drivesPosition} and
 * {@code drivesRotation} flags mark which axes this component owns; they are runtime state, not
 * serialized — on load they are inferred from which fields are present.
 */
public abstract class ParticleComponentMotion extends ParticleComponentBase
{
    public boolean drivesPosition = true;
    public boolean drivesRotation = true;
}
