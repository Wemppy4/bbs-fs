package mchorse.bbs_mod.settings.values.mc;

import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

public class ValueBlockState extends BaseKeyframeFactoryValue<IBlockState>
{
    public ValueBlockState(String id)
    {
        super(id, KeyframeFactories.BLOCK_STATE, Blocks.AIR.getDefaultState());
    }
}