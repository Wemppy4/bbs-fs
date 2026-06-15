package mchorse.bbs_mod.cubic.physics;

import java.util.Map;

public record ModelPhysicsConfig(Map<String, Bone> bones)
{
    public static final float DEFAULT_WEIGHT = 1F;
    public static final float DEFAULT_STIFFNESS = 0.2F;

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
