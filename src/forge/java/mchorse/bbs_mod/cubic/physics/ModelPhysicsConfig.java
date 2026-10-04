package mchorse.bbs_mod.cubic.physics;

import java.util.Map;

@com.github.bsideup.jabel.Desugar

public record ModelPhysicsConfig(Map<String, Bone> bones, Wind wind)
{
    public static final float DEFAULT_WEIGHT = 1F;
    public static final float DEFAULT_STIFFNESS = 0F;
    public static final float DEFAULT_TURBULENCE = 0.5F;
    public static final float DEFAULT_TURBULENCE_SPEED = 1F;
    public static final float DEFAULT_TURBULENCE_SCALE = 1F;

    public ModelPhysicsConfig
    {
        if (wind == null)
        {
            wind = Wind.NONE;
        }
    }

    /**
     * Global wind for the whole model's physics: a single world-space directional force ({@code x}/{@code y}/
     * {@code z} direction scaled by {@code strength}) added to every chain on top of its gravity. It is one
     * field for the whole model, not bound to any bone. Strength 0 — or a zero direction — means no wind.
     *
     * <p>{@code turbulence} (0..1) makes the wind gust instead of blowing steadily: the magnitude is
     * modulated by a noise field that drifts downwind over time ({@code turbulenceSpeed}) and varies across
     * space ({@code turbulenceScale}), so points along a chain ripple rather than move rigidly. Turbulence 0
     * leaves a steady force.
     *
     * <p>{@code local} makes the direction relative to the model instead of the world: when set, the
     * {@code x}/{@code y}/{@code z} direction is rotated by the model's world orientation, so the wind
     * follows the model as it turns. When clear, the direction is a fixed world vector.
     */
    @com.github.bsideup.jabel.Desugar
    public record Wind(float strength, float x, float y, float z, float turbulence, float turbulenceSpeed, float turbulenceScale, boolean local)
    {
        public static final Wind NONE = new Wind(0F, 1F, 0F, 0F, DEFAULT_TURBULENCE, DEFAULT_TURBULENCE_SPEED, DEFAULT_TURBULENCE_SCALE, false);

        public Wind
        {
            strength = Math.max(0F, strength);
            turbulence = turbulence < 0F ? 0F : Math.min(turbulence, 1F);
            turbulenceSpeed = Math.max(0F, turbulenceSpeed);
            turbulenceScale = Math.max(0F, turbulenceScale);
        }

        public boolean active()
        {
            return this.strength > 0F && (this.x != 0F || this.y != 0F || this.z != 0F);
        }

        public boolean isDefault()
        {
            return this.equals(NONE);
        }
    }

    @com.github.bsideup.jabel.Desugar

    public record Bone(String end, String targetBone, float gravity, float damping, float stiffness, int iterations, boolean relativeGravity, float relativeGravityRotateX, float relativeGravityRotateY, float relativeGravityRotateZ, boolean collisions, float radius, float weight)
    {
        public Bone
        {
            targetBone = targetBone == null ? "" : targetBone;
            stiffness = clamp01(stiffness);
            iterations = Math.max(1, iterations);
            radius = Math.max(0F, radius);
            weight = clamp01(weight);
        }

        public Bone(String end, String targetBone, float gravity, float damping, int iterations, boolean collisions, float radius)
        {
            this(end, targetBone, gravity, damping, DEFAULT_STIFFNESS, iterations, false, 0F, 0F, 0F, collisions, radius, DEFAULT_WEIGHT);
        }

        public Bone(String end, String targetBone, float gravity, float damping, int iterations, boolean relativeGravity, float relativeGravityRotateX, float relativeGravityRotateY, float relativeGravityRotateZ, boolean collisions, float radius)
        {
            this(end, targetBone, gravity, damping, DEFAULT_STIFFNESS, iterations, relativeGravity, relativeGravityRotateX, relativeGravityRotateY, relativeGravityRotateZ, collisions, radius, DEFAULT_WEIGHT);
        }

        public boolean hasRelativeGravityRotation()
        {
            return this.relativeGravityRotateX != 0F || this.relativeGravityRotateY != 0F || this.relativeGravityRotateZ != 0F;
        }

        private static float clamp01(float value)
        {
            if (value < 0F)
            {
                return 0F;
            }

            return Math.min(value, 1F);
        }
    }
}
