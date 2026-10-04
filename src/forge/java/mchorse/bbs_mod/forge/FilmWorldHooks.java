package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Called directly by native World before replacing a block; never loads client classes. */
public final class FilmWorldHooks
{
    public static void beforeSetBlock(World world,BlockPos pos)
    {
        if(!(world instanceof WorldServer))return;
        ActionManager actions=BBSMod.getActions();
        if(actions!=null&&actions.isTracking())
            actions.changedBlock((WorldServer)world,pos,world.getBlockState(pos),world.getTileEntity(pos));
    }
    private FilmWorldHooks(){}
}
