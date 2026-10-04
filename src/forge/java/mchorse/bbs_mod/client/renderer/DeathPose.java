package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

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
        if (deathTime <= 0)
        {
            return 0F;
        }

        float progress = (deathTime + tickDelta - 1F) / 20F * 1.6F;

        return Math.min(MathHelper.sqrt(progress), 1F) * 90F;
    }

    /**
     * Rolls the body onto its side. Goes on AFTER the body yaw, exactly where
     * vanilla puts it, so the body falls sideways relative to itself.
     */
    public static void apply(MatrixStack matrices, int deathTime, float tickDelta)
    {
        float angle = angle(deathTime, tickDelta);

        if (angle != 0F)
        {
            matrices.multiply(new Quaternionf().rotationZ((float) Math.toRadians(angle)));
        }
    }
}
