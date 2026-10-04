package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.graphics.render.BufferBuilder;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Vector3f;

public interface ICubicRenderer
{
    /**
     * The bone's transient constraint-stack shift (today: the IK stretch),
     * applied in its PARENT's frame ahead of everything the bone itself does,
     * so it carries the bone and its whole subtree without disturbing the pose.
     */
    public static void offsetGroup(MatrixStack stack, ModelGroup group)
    {
        Vector3f offset = group.offset;

        if (offset != null)
        {
            stack.translate(offset.x, offset.y, offset.z);
        }
    }

    public static void translateGroup(MatrixStack stack, ModelGroup group)
    {
        Vector3f translate = group.current.translate;
        Vector3f pivot = group.initial.translate;

        stack.translate(-(translate.x - pivot.x) / 16F, (translate.y - pivot.y) / 16F, (translate.z - pivot.z) / 16F);
    }

    public static void moveToGroupPivot(MatrixStack stack, ModelGroup group)
    {
        Vector3f pivot = group.initial.translate;

        stack.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
    }

    public static void rotateGroup(MatrixStack stack, ModelGroup group)
    {
        if (group.orient != null)
        {
            stack.multiply(group.orient);

            return;
        }

        if (group.current.rotationMode == Transform.RotationMode.QUATERNION)
        {
            stack.multiply(group.current.quat);

            return;
        }

        Vector3f rotate = group.current.rotate;

        /* Rest bones (all angles zero — the common case in a big model) skip
         * the trig entirely; cubic model channels are degrees. */
        if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
        {
            stack.multiply(Matrices.toLocalRotationZYXDegrees(rotate));
        }
    }

    public static void scaleGroup(MatrixStack stack, ModelGroup group)
    {
        Vector3f scale = group.current.scale;

        MatrixStackUtils.scaleStack(stack, scale.x, scale.y, scale.z);
    }

    public static void moveBackFromGroupPivot(MatrixStack stack, ModelGroup group)
    {
        Vector3f pivot = group.initial.translate;

        stack.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    public default void applyGroupTransformations(MatrixStack stack, ModelGroup group)
    {
        offsetGroup(stack, group);
        translateGroup(stack, group);
        moveToGroupPivot(stack, group);
        rotateGroup(stack, group);
        scaleGroup(stack, group);
        moveBackFromGroupPivot(stack, group);
    }

    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model);
}