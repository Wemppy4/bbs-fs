package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.cubic.animation.ItemUsePose.Use;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

/**
 * Vanilla's arm poses (a drawn bow, a raised shield, a charged crossbow, a
 * lifted trident, a spyglass at the eye) for the procedural animator.
 *
 * <p>Every number here is {@code BipedEntityModel.positionRightArm} /
 * {@code positionLeftArm} / {@code CrossbowPosing} taken off the 1.20.4
 * bytecode, and the pose selection is {@code PlayerEntityRenderer.getArmPose}
 * plus its {@code setModelPose} two-handed rule. The model's bones speak their
 * own units and signs, so they come in through {@link Arm} adapters that talk
 * vanilla: pitch and yaw in radians, vanilla's own directions.</p>
 */
public class VanillaArmPoses
{
    public enum Pose
    {
        EMPTY, ITEM, BLOCK, BOW_AND_ARROW, THROW_SPEAR, CROSSBOW_CHARGE, CROSSBOW_HOLD, SPYGLASS, TOOT_HORN, BRUSH;

        public boolean isTwoHanded()
        {
            return this == BOW_AND_ARROW || this == CROSSBOW_CHARGE || this == CROSSBOW_HOLD;
        }
    }

    /** An arm bone in vanilla's terms: radians, vanilla's signs. */
    public interface Arm
    {
        public float pitch();

        public void pitch(float pitch);

        public float yaw();

        public void yaw(float yaw);
    }

    /**
     * Poses both arms the way vanilla does at the end of its base angles: the
     * hand that is using something wins alone, otherwise each arm takes the pose
     * of whatever it holds.
     *
     * <p>The actor is right handed (so is the rest of BBS): the right arm gets
     * the main hand, the left arm the off hand.</p>
     */
    public static void apply(Arm right, Arm left, float headPitch, float headYaw, ItemStack main, ItemStack off, Use mainUse, Use offUse, boolean sneaking, boolean swinging)
    {
        Pose rightPose = poseOf(main, mainUse, swinging);
        Pose leftPose = poseOf(off, offUse, swinging);

        /* setModelPose: a two-handed main pose leaves the off hand nothing to do. */
        if (rightPose.isTwoHanded())
        {
            leftPose = off == null || off.isEmpty() ? Pose.EMPTY : Pose.ITEM;
        }

        /* Vanilla clears both arms' yaw before posing them; BBS never did, and
         * base animations of existing models live in that yaw. So the yaw is
         * only touched once something is actually being held up - films where
         * nobody uses anything keep the arms they were authored with. */
        boolean strict = mainUse != null || offUse != null || rightPose.isTwoHanded() || leftPose.isTwoHanded();

        if (mainUse != null || offUse != null)
        {
            /* Vanilla positions only the active arm - the other one keeps its
             * plain angles unless the pose itself is two-handed and writes both. */
            boolean mainActive = mainUse != null;

            position(mainActive ? rightPose : leftPose, mainActive, right, left, headPitch, headYaw, sneaking, mainActive ? mainUse : offUse, strict);
        }
        else if (leftPose.isTwoHanded())
        {
            position(leftPose, false, right, left, headPitch, headYaw, sneaking, null, strict);
            position(rightPose, true, right, left, headPitch, headYaw, sneaking, null, strict);
        }
        else
        {
            position(rightPose, true, right, left, headPitch, headYaw, sneaking, null, strict);
            position(leftPose, false, right, left, headPitch, headYaw, sneaking, null, strict);
        }
    }

    /** {@code PlayerEntityRenderer.getArmPose} for one hand. */
    private static Pose poseOf(ItemStack stack, Use use, boolean swinging)
    {
        if (stack == null || stack.isEmpty())
        {
            return Pose.EMPTY;
        }

        if (use != null)
        {
            switch (use.action())
            {
                case BLOCK: return Pose.BLOCK;
                case BOW: return Pose.BOW_AND_ARROW;
                case SPEAR: return Pose.THROW_SPEAR;
                case CROSSBOW: return Pose.CROSSBOW_CHARGE;
                case SPYGLASS: return Pose.SPYGLASS;
                case TOOT_HORN: return Pose.TOOT_HORN;
                case BRUSH: return Pose.BRUSH;
                /* Eating and drinking have no pose of their own - vanilla falls through to ITEM. */
                default: break;
            }
        }
        else if (!swinging && stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack))
        {
            return Pose.CROSSBOW_HOLD;
        }

        return Pose.ITEM;
    }

    private static void position(Pose pose, boolean rightSide, Arm right, Arm left, float headPitch, float headYaw, boolean sneaking, Use use, boolean strict)
    {
        Arm arm = rightSide ? right : left;

        switch (pose)
        {
            case EMPTY:
                if (strict)
                {
                    arm.yaw(0F);
                }

                break;
            case ITEM:
                arm.pitch(arm.pitch() * 0.5F - 0.31415927F);

                if (strict)
                {
                    arm.yaw(0F);
                }

                break;
            case BLOCK:
                arm.pitch(arm.pitch() * 0.5F - 0.9424779F + MathHelper.clamp(headPitch, -1.3962634F, 0.43633232F));
                arm.yaw((rightSide ? -30F : 30F) * 0.017453292F + MathHelper.clamp(headYaw, -0.5235988F, 0.5235988F));

                break;
            case THROW_SPEAR:
                arm.pitch(arm.pitch() * 0.5F - 3.1415927F);
                arm.yaw(0F);

                break;
            case BOW_AND_ARROW:
                right.yaw(-0.1F + headYaw - (rightSide ? 0F : 0.4F));
                left.yaw(0.1F + headYaw + (rightSide ? 0.4F : 0F));
                right.pitch(-1.5707964F + headPitch);
                left.pitch(-1.5707964F + headPitch);

                break;
            case CROSSBOW_CHARGE:
                charge(right, left, rightSide, use);

                break;
            case CROSSBOW_HOLD:
                hold(right, left, rightSide, headPitch, headYaw);

                break;
            case BRUSH:
                arm.pitch(arm.pitch() * 0.5F - 0.62831855F);
                arm.yaw(0F);

                break;
            case SPYGLASS:
                arm.pitch(MathHelper.clamp(headPitch - 1.9198622F - (sneaking ? 0.2617994F : 0F), -2.4F, 3.3F));
                arm.yaw(headYaw + (rightSide ? -0.2617994F : 0.2617994F));

                break;
            case TOOT_HORN:
                arm.pitch(MathHelper.clamp(headPitch, -1.2F, 1.2F) - 1.4835298F);
                arm.yaw(headYaw + (rightSide ? -0.5235988F : 0.5235988F));

                break;
        }
    }

    /** {@code CrossbowPosing.hold}: the crossbow arm aims, the other one steadies it. */
    private static void hold(Arm right, Arm left, boolean rightSide, float headPitch, float headYaw)
    {
        Arm holding = rightSide ? right : left;
        Arm other = rightSide ? left : right;

        holding.yaw((rightSide ? -0.3F : 0.3F) + headYaw);
        other.yaw((rightSide ? 0.6F : -0.6F) + headYaw);
        holding.pitch(-1.5707964F + headPitch + 0.1F);
        other.pitch(-1.5F + headPitch);
    }

    /** {@code CrossbowPosing.charge}: the free arm pulls the string as the charge fills. */
    private static void charge(Arm right, Arm left, boolean rightSide, Use use)
    {
        Arm charging = rightSide ? right : left;
        Arm other = rightSide ? left : right;

        charging.yaw(rightSide ? -0.8F : 0.8F);
        charging.pitch(-0.97079635F);
        other.pitch(charging.pitch());

        float pullTime = use == null ? 25F : Math.max(1, ItemUsePose.pullTime(use.stack(), use.user()));
        float used = MathHelper.clamp(use == null ? 0F : use.elapsed(), 0F, pullTime);
        float progress = used / pullTime;

        other.yaw(MathHelper.lerp(progress, 0.4F, 0.85F) * (rightSide ? 1F : -1F));
        other.pitch(MathHelper.lerp(progress, other.pitch(), -1.5707964F));
    }
}
