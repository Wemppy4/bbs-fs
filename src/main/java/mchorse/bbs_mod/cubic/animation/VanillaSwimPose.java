package mchorse.bbs_mod.cubic.animation;

import net.minecraft.util.math.MathHelper;

/**
 * Vanilla's swimming stroke: a front crawl in the arms and a flutter kick in the legs.
 *
 * <p>Every number here is the trailing {@code leaningPitch} block of
 * {@code BipedEntityModel.setAngles} taken off the 1.20.4 bytecode, the same way the arm poses
 * were. The procedural animator already tipped the body over and turned the head when the lean
 * came in - the limbs kept walking, which is what made a swim read as someone strolling along
 * on their side.</p>
 *
 * <p>The stroke runs on a 26 tick cycle of the walk phase, in three parts: the arms sweep out
 * and round (under 14), reach forward (14 to 22), then close (22 to 26). Every write is a lerp
 * out of whatever the bone already holds, weighted by the lean, so the stroke fades in as the
 * body goes flat rather than snapping on.</p>
 */
public class VanillaSwimPose
{
    private static final float PI = (float) Math.PI;
    private static final float TAU = PI * 2F;
    private static final float QUARTER_TURN = 1.5707964F;

    /** How far round the stroke carries an arm, and the two angles it passes through. */
    private static final float STROKE = 1.8707964F;
    private static final float LEFT_REACH = 5.012389F;
    private static final float RIGHT_REACH = 1.2707963F;

    private static final float CYCLE = 26F;
    private static final float SWEEP_END = 14F;
    private static final float REACH_END = 22F;

    /** The kick: a slow beat, shallow, and the legs half a cycle apart. */
    private static final float KICK_DEPTH = 0.3F;
    private static final float KICK_RATE = 0.33333334F;

    /**
     * @param leaningPitch how far the body has gone flat, 0 to 1 - the weight of the whole pose
     * @param limbPhase the walk phase the stroke is clocked off, same as the legs use
     * @param handSwingProgress a swing takes the striking arm out of the stroke
     * @param usingItem holding something up stops the arms stroking at all
     */
    public static void apply(VanillaBone rightArm, VanillaBone leftArm, VanillaBone rightLeg, VanillaBone leftLeg,
        float leaningPitch, float limbPhase, float handSwingProgress, boolean usingItem)
    {
        float phase = limbPhase % CYCLE;

        /* Vanilla asks which arm the entity leads with and spares that one while it swings.
         * BBS is right handed throughout - so is the rest of the arm posing - so it's the right. */
        float rightWeight = handSwingProgress > 0F ? 0F : leaningPitch;
        float leftWeight = leaningPitch;

        if (!usingItem && rightArm != null && leftArm != null)
        {
            if (phase < SWEEP_END)
            {
                float sweep = STROKE * curve(phase) / curve(SWEEP_END);

                leftArm.pitch(lerpAngle(leftWeight, leftArm.pitch(), 0F));
                rightArm.pitch(MathHelper.lerp(rightWeight, rightArm.pitch(), 0F));
                leftArm.yaw(lerpAngle(leftWeight, leftArm.yaw(), PI));
                rightArm.yaw(MathHelper.lerp(rightWeight, rightArm.yaw(), PI));
                leftArm.roll(lerpAngle(leftWeight, leftArm.roll(), PI + sweep));
                rightArm.roll(MathHelper.lerp(rightWeight, rightArm.roll(), PI - sweep));
            }
            else if (phase < REACH_END)
            {
                float reach = (phase - SWEEP_END) / 8F;

                leftArm.pitch(lerpAngle(leftWeight, leftArm.pitch(), QUARTER_TURN * reach));
                rightArm.pitch(MathHelper.lerp(rightWeight, rightArm.pitch(), QUARTER_TURN * reach));
                leftArm.yaw(lerpAngle(leftWeight, leftArm.yaw(), PI));
                rightArm.yaw(MathHelper.lerp(rightWeight, rightArm.yaw(), PI));
                leftArm.roll(lerpAngle(leftWeight, leftArm.roll(), LEFT_REACH - STROKE * reach));
                rightArm.roll(MathHelper.lerp(rightWeight, rightArm.roll(), RIGHT_REACH + STROKE * reach));
            }
            else
            {
                float close = (phase - REACH_END) / 4F;

                leftArm.pitch(lerpAngle(leftWeight, leftArm.pitch(), QUARTER_TURN - QUARTER_TURN * close));
                rightArm.pitch(MathHelper.lerp(rightWeight, rightArm.pitch(), QUARTER_TURN - QUARTER_TURN * close));
                leftArm.yaw(lerpAngle(leftWeight, leftArm.yaw(), PI));
                rightArm.yaw(MathHelper.lerp(rightWeight, rightArm.yaw(), PI));
                leftArm.roll(lerpAngle(leftWeight, leftArm.roll(), PI));
                rightArm.roll(MathHelper.lerp(rightWeight, rightArm.roll(), PI));
            }
        }

        if (leftLeg != null)
        {
            leftLeg.pitch(MathHelper.lerp(leaningPitch, leftLeg.pitch(), KICK_DEPTH * MathHelper.cos(limbPhase * KICK_RATE + PI)));
        }

        if (rightLeg != null)
        {
            rightLeg.pitch(MathHelper.lerp(leaningPitch, rightLeg.pitch(), KICK_DEPTH * MathHelper.cos(limbPhase * KICK_RATE)));
        }
    }

    /** {@code BipedEntityModel}'s own easing for the sweep: fast out of the water, slow back in. */
    private static float curve(float phase)
    {
        return -65F * phase + phase * phase;
    }

    /** {@code BipedEntityModel.lerpAngle}: a lerp that takes the short way round, in radians. */
    private static float lerpAngle(float delta, float from, float to)
    {
        float factor = (to - from) % TAU;

        if (factor < -PI) factor += TAU;
        if (factor >= PI) factor -= TAU;

        return from + delta * factor;
    }
}
