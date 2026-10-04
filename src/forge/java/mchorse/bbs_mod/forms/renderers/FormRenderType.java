package mchorse.bbs_mod.forms.renderers;

import net.minecraft.client.renderer.block.model.ItemCameraTransforms;

public enum FormRenderType
{
    MODEL_BLOCK, ENTITY, ITEM_FP, ITEM_TP, ITEM_INVENTORY, ITEM, PREVIEW;

    public static FormRenderType fromModelMode(ItemCameraTransforms.TransformType mode)
    {
        if ((mode == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND || mode == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND))
        {
            return ITEM_FP;
        }
        else if (mode == ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND || mode == ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND)
        {
            return ITEM_TP;
        }
        else if (mode == ItemCameraTransforms.TransformType.GROUND)
        {
            return ITEM;
        }
        else if (mode == ItemCameraTransforms.TransformType.GUI)
        {
            return ITEM_INVENTORY;
        }

        return ENTITY;
    }
}