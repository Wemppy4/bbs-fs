package mchorse.bbs_mod.actions;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketBlockAction;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Sends vanilla lid-count updates to clients without changing real viewer counts.
 * Thus the server's periodic chest recount cannot find the fake player missing and
 * prematurely close the visual lid. The actual inventory is never opened. */
public final class ContainerLid
{
    public static boolean isLidded(World world, BlockPos pos)
    { TileEntity tile=world.getTileEntity(pos); return tile instanceof TileEntityChest || tile instanceof TileEntityEnderChest; }
    public static void setOpen(World world, BlockPos pos, boolean open)
    {
        TileEntity tile=world.getTileEntity(pos);
        if (!(world instanceof WorldServer) || !isLidded(world,pos)) return;
        signal((WorldServer)world,pos,open);
        double x=pos.getX()+0.5D, y=pos.getY()+0.5D, z=pos.getZ()+0.5D;
        SoundEvent sound;
        if(tile instanceof TileEntityChest)
        {
            IBlockState block=world.getBlockState(pos);
            for(EnumFacing side: EnumFacing.Plane.HORIZONTAL)
            {
                BlockPos other=pos.offset(side);
                if(world.getBlockState(other).getBlock()==block.getBlock() && world.getTileEntity(other) instanceof TileEntityChest)
                { signal((WorldServer)world,other,open);x+=side.getXOffset()*0.5D;z+=side.getZOffset()*0.5D;break; }
            }
            sound=open?SoundEvents.BLOCK_CHEST_OPEN:SoundEvents.BLOCK_CHEST_CLOSE;
        }
        else sound=open?SoundEvents.BLOCK_ENDERCHEST_OPEN:SoundEvents.BLOCK_ENDERCHEST_CLOSE;
        world.playSound((EntityPlayer)null,x,y,z,sound,SoundCategory.BLOCKS,0.5F,world.rand.nextFloat()*0.1F+0.9F);
    }
    private static void signal(WorldServer world,BlockPos pos,boolean open)
    {
        world.getMinecraftServer().getPlayerList().sendToAllNearExcept(null,pos.getX()+0.5D,pos.getY()+0.5D,pos.getZ()+0.5D,64D,world.provider.getDimension(),
            new SPacketBlockAction(pos,world.getBlockState(pos).getBlock(),1,open?1:0));
    }
    private ContainerLid() {}
}
