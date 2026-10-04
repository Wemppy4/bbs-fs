package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.item.UseBlockItemActionClip;
import mchorse.bbs_mod.actions.types.blocks.BreakBlockActionClip;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Original ItemStack/ServerWorld capture boundaries; Forge interaction events alone miss these. */
public final class FilmActionHooks
{
    public static void useBlock(ItemStack stack,EntityPlayer user,World world,BlockPos pos,EnumHand hand,EnumFacing face,float x,float y,float z)
    {
        if(world.isRemote || !(user instanceof EntityPlayerMP) || user instanceof SuperFakePlayer)return;
        EntityPlayerMP player=(EntityPlayerMP)user;
        BBSMod.getActions().addAction(player,() -> {
            UseBlockItemActionClip clip=new UseBlockItemActionClip();
            clip.hit.setHitResult(new RayTraceResult(new Vec3d(pos).add(x,y,z),face,pos));
            clip.itemStack.set(stack.copy());clip.hand.set(hand==EnumHand.MAIN_HAND);return clip;
        });
    }
    public static void breaking(World world,int entityId,BlockPos pos,int progress)
    {
        if(world.isRemote || !(world instanceof WorldServer))return;
        Entity entity=world.getEntityByID(entityId);
        if(!(entity instanceof EntityPlayerMP) || entity instanceof SuperFakePlayer)return;
        BBSMod.getActions().addAction((EntityPlayerMP)entity,() -> {
            BreakBlockActionClip clip=new BreakBlockActionClip();
            clip.x.set(pos.getX());clip.y.set(pos.getY());clip.z.set(pos.getZ());clip.progress.set(progress);return clip;
        });
    }
}
