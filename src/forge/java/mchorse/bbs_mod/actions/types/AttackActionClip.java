package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

public class AttackActionClip extends ActionClip
{
    public final ValueFloat damage=new ValueFloat("damage",0F);
    public AttackActionClip(){this.add(damage);}
    @Override public void applyAction(EntityLivingBase actor,SuperFakePlayer player,Film film,Replay replay,int tick)
    {
        if(damage.get()<=0F)return;
        this.applyPositionRotation(player,replay,tick);
        Vec3d origin=player.getPositionEyes(1F),delta=player.getLook(1F).scale(6D),end=origin.add(delta);
        RayTraceResult block=player.world.rayTraceBlocks(origin,end,false,false,true);
        double nearest=block==null?36D:origin.squareDistanceTo(block.hitVec);
        Entity hit=null,source=actor==null?player:actor;
        AxisAlignedBB volume=player.getEntityBoundingBox().expand(delta.x,delta.y,delta.z).grow(1D);
        for(Entity entity:player.world.getEntitiesWithinAABBExcludingEntity(source,volume))
        {
            if((entity instanceof net.minecraft.entity.player.EntityPlayer && ((net.minecraft.entity.player.EntityPlayer) entity).isSpectator()) || !entity.canBeCollidedWith())continue;
            AxisAlignedBB box=entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult intercept=box.calculateIntercept(origin,end);
            if(box.contains(origin)){nearest=0D;hit=entity;}
            else if(intercept!=null)
            {
                double distance=origin.squareDistanceTo(intercept.hitVec);
                if(distance<nearest){nearest=distance;hit=entity;}
            }
        }
        if(hit!=null)hit.attackEntityFrom(DamageSource.causeMobDamage(player),damage.get());
    }
    @Override protected Clip create(){return new AttackActionClip();}
}
