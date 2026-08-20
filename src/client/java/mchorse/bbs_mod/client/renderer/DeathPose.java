package mchorse.bbs_mod.client.renderer;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Vanilla's body falling over, for everything that renders a body itself.
 *
 * <p>{@code LivingEntityRenderer.setupTransforms} tips a dead entity onto its
 * side over the 20 ticks of {@code deathTime} (javap 1.20.4). The renderers of
 * this mod replace that method wholesale - the actor entity's and the morphed
 * entity's - and the morph one dropped the fall on the floor. The curve lives
 * here once so both tip the same way.</p>
 */
public class DeathPose
{
    /**
     * Degrees the body has rolled by, 0 while it stands. The tick delta is
     * vanilla's: the fall is smooth between ticks, not a staircase.
     */
    public static float angle(int deathTime, float tickDelta)
    {
        return angle(deathTime <= 0 ? 0F : deathTime + tickDelta);
    }

    /**
     * The same curve for the 1.21.2+ render-state model, where the tick delta is
     * already summed into the state's own deathTime.
     */
    public static float angle(float deathTime)
    {
        if (deathTime <= 0F)
        {
            return 0F;
        }

        float progress = (deathTime - 1F) / 20F * 1.6F;

        return Math.min(MathHelper.sqrt(progress), 1F) * 90F;
    }

    /**
     * Rolls the body onto its side. Goes on AFTER the body yaw, exactly where
     * vanilla puts it, so the body falls sideways relative to itself.
     */
    public static void apply(MatrixStack matrices, int deathTime, float tickDelta)
    {
        apply(matrices, deathTime <= 0 ? 0F : deathTime + tickDelta);
    }

    public static void apply(MatrixStack matrices, float deathTime)
    {
        float angle = angle(deathTime);

        if (angle != 0F)
        {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        }
    }
}
