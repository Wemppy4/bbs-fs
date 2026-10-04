package mchorse.bbs_mod.items;

import mchorse.bbs_mod.actions.ActionActorContext;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.FormUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Original BBS gun behavior through Forge's native item-use boundary. */
public class GunItem extends Item {
    public GunItem() {
        this.setRegistryName("bbs","gun"); this.setTranslationKey("bbs.gun");
        this.setMaxStackSize(1); this.setCreativeTab(net.minecraft.creativetab.CreativeTabs.TOOLS);
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        Entity owner=ActionActorContext.getActor()==null?player:ActionActorContext.getActor();
        ItemStack stack=player.getHeldItem(hand);
        GunProperties properties=GunProperties.get(stack);
        if(properties.launch) {
            Vec3d velocity=owner.getLookVec().scale(properties.launchPower);
            if(properties.launchAdditive)owner.addVelocity(velocity.x,velocity.y,velocity.z);
            else { owner.motionX=velocity.x;owner.motionY=velocity.y;owner.motionZ=velocity.z; }
            owner.velocityChanged=true;
            return new ActionResult<>(EnumActionResult.SUCCESS,stack);
        }
        if(!world.isRemote) {
            for(int i=0;i<Math.max(properties.projectiles,1);i++) {
                GunProjectileEntity projectile=new GunProjectileEntity(world);
                projectile.setProperties(properties);
                projectile.setForm(FormUtils.copy(properties.projectileForm));
                projectile.setOwner(owner);
                projectile.setPosition(owner.posX,owner.posY+owner.getEyeHeight(),owner.posZ);
                float yaw=owner.getRotationYawHead()+(float)(properties.scatterY*(world.rand.nextDouble()-0.5D));
                float pitch=owner.rotationPitch+(float)(properties.scatterX*(world.rand.nextDouble()-0.5D));
                double radians=Math.PI/180D;
                projectile.shoot(-Math.sin(yaw*radians)*Math.cos(pitch*radians),-Math.sin(pitch*radians),Math.cos(yaw*radians)*Math.cos(pitch*radians),properties.speed,0F);
                projectile.motionX+=owner.motionX;projectile.motionZ+=owner.motionZ;
                if(!owner.onGround)projectile.motionY+=owner.motionY;
                world.spawnEntity(projectile);
            }
            if(!properties.cmdFiring.isEmpty()&&owner.getServer()!=null)
                owner.getServer().getCommandManager().executeCommand(owner,properties.cmdFiring);
        }
        return new ActionResult<>(EnumActionResult.PASS,stack);
    }
}