package mchorse.bbs_mod.cubic.jem;

import com.mojang.authlib.GameProfile;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Method;
import java.util.UUID;

/** CEM starts from the actual 1.12 ModelBase animation, on a private native model and entity. */
public final class CemVanillaStage implements ICemVanillaStage
{
    private static final Method PHASE=ReflectionHelper.findMethod(RenderLivingBase.class,"handleRotationFloat","func_77044_a",EntityLivingBase.class,float.class);
    private final String entity;
    private final boolean baby;
    private final boolean player;
    private final boolean slim;
    private final CemPartNames names;
    private final CemVanillaSeed seed=new CemVanillaSeed();
    private EntityLivingBase standIn;
    private MCEntity adapter;
    private VanillaRigs.Rig rig;
    private RenderLivingBase<?> renderer;

    public CemVanillaStage(String jem)
    {
        this.entity=CemNames.entity(jem);this.baby=CemNames.baby(jem);
        this.player=this.entity.equals("player")||this.entity.equals("player_slim");
        this.slim=this.entity.equals("player_slim");this.names=CemPartNames.of(this.entity);
    }
    private boolean ensure(IEntity source)
    {
        World world=source.getWorld()==null?Minecraft.getMinecraft().world:source.getWorld();
        if(world==null)return false;
        if(this.standIn!=null&&this.standIn.world==world)return this.rig!=null&&this.renderer!=null;
        Entity entity=this.player?new EntityOtherPlayerMP(world,new GameProfile(new UUID(0L,1L),"BBS_CEM"))
            :EntityList.createEntityByIDFromName(new ResourceLocation(VanillaRigs.entityId(this.entity)),world);
        if(!(entity instanceof EntityLivingBase))return false;
        this.standIn=(EntityLivingBase)entity;this.standIn.noClip=true;
        if(entity instanceof EntityLiving)((EntityLiving)entity).setNoAI(true);
        if(this.baby&&entity instanceof EntityAgeable)((EntityAgeable)entity).setGrowingAge(-24000);
        if(this.baby&&entity instanceof EntityZombie)((EntityZombie)entity).setChild(true);
        this.adapter=new MCEntity(entity);
        this.rig=VanillaRigs.create(this.entity);
        Render<?> render=this.player?Minecraft.getMinecraft().getRenderManager().getSkinMap().get(this.slim?"slim":"default")
            :Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(entity);
        this.renderer=render instanceof RenderLivingBase?(RenderLivingBase<?>)render:null;
        return this.rig!=null&&this.renderer!=null;
    }
    private void synchronize(IEntity source)
    {
        this.adapter.copy(source);this.adapter.setAge(source.getAge());
        this.standIn.hurtTime=source.getHurtTimer();this.standIn.deathTime=source.getDeathTime();
        this.standIn.prevSwingProgress=source.getHandSwingProgress(0F);
        this.standIn.swingProgress=source.getHandSwingProgress(1F);
        this.standIn.limbSwing=source.getLimbPos(1F);this.standIn.prevLimbSwingAmount=source.getLimbSpeed(0F);this.standIn.limbSwingAmount=source.getLimbSpeed(1F);
        for(EntityEquipmentSlot slot:EntityEquipmentSlot.values())this.standIn.setItemStackToSlot(slot,source.getEquipmentStack(slot));
        if(this.standIn instanceof EntityTameable){((EntityTameable)this.standIn).setSitting(source.isSitting());((EntityTameable)this.standIn).setTamed(source.isTamed());}
        if(this.standIn instanceof EntityWolf)((EntityWolf)this.standIn).setAngry(source.isAggressive());
        if(this.standIn instanceof EntityZombie)((EntityZombie)this.standIn).setArmsRaised(source.isAggressive());
        if(this.standIn instanceof EntityLiving)((EntityLiving)this.standIn).setLeftHanded(!source.isRightHanded());
    }
    @Override public void tick(IEntity source)
    {
        if(!this.ensure(source))return;
        /* This is a private client-world entity: tick animators, never run server AI or register it in the world. */
        this.standIn.onUpdate();this.synchronize(source);
    }
    @Override public CemVanillaSeed seed(IEntity source,float transition)
    {
        if(!this.ensure(source))return null;
        this.synchronize(source);
        this.rig.restore();
        ModelBase model=this.rig.model;
        model.isChild=this.baby||source.isChild();model.isRiding=source.isRiding();model.swingProgress=source.getHandSwingProgress(transition);
        if(model instanceof ModelBiped)
        {
            ModelBiped biped=(ModelBiped)model;
            biped.isSneak=source.isSneaking();
            ModelBiped.ArmPose main=armPose(source.getEquipmentStack(EntityEquipmentSlot.MAINHAND),source.isUsingItem());
            ModelBiped.ArmPose off=armPose(source.getEquipmentStack(EntityEquipmentSlot.OFFHAND),source.isUsingItem());
            biped.rightArmPose=source.isRightHanded()?main:off;
            biped.leftArmPose=source.isRightHanded()?off:main;
        }
        float limb=source.getLimbPos(transition),speed=Math.min(1F,source.getLimbSpeed(transition));
        if(model.isChild)limb*=3F;
        if(model.isRiding||source.getDeathTime()>0){limb=0F;speed=0F;}
        float body=source.getPrevBodyYaw()+MathHelper.wrapDegrees(source.getBodyYaw()-source.getPrevBodyYaw())*transition;
        float head=source.getPrevHeadYaw()+MathHelper.wrapDegrees(source.getHeadYaw()-source.getPrevHeadYaw())*transition;
        float pitch=source.getPrevPitch()+(source.getPitch()-source.getPrevPitch())*transition;
        try
        {
            float age=((Number)PHASE.invoke(this.renderer,this.standIn,transition)).floatValue();
            model.setLivingAnimations(this.standIn,limb,speed,transition);
            model.setRotationAngles(limb,speed,age,MathHelper.wrapDegrees(head-body),pitch,.0625F,this.standIn);
        }
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot evaluate vanilla CEM pose for "+this.entity,e);}
        for(String root:this.rig.roots)this.read(root,0F,0F,0F);
        return this.seed;
    }
    private static ModelBiped.ArmPose armPose(ItemStack stack,boolean using)
    {
        if(stack.isEmpty())return ModelBiped.ArmPose.EMPTY;
        if(using&&stack.getItemUseAction()==EnumAction.BLOCK)return ModelBiped.ArmPose.BLOCK;
        if(using&&stack.getItemUseAction()==EnumAction.BOW)return ModelBiped.ArmPose.BOW_AND_ARROW;
        return ModelBiped.ArmPose.ITEM;
    }
    private void read(String name,float ox,float oy,float oz)
    {
        ModelRenderer part=this.rig.parts.get(name);
        float ax=ox+part.rotationPointX,ay=oy+part.rotationPointY,az=oz+part.rotationPointZ;
        String external=this.names.optifine(name);
        fill(this.seed.part(external),part,ax,ay,az);
        if(!external.equals(name))fill(this.seed.part(name),part,ax,ay,az);
        for(String child:this.rig.children.getOrDefault(name,java.util.Collections.emptyList()))this.read(child,ax,ay,az);
    }
    private static void fill(CemVanillaSeed.Part slot,ModelRenderer part,float ax,float ay,float az)
    {
        slot.tx=part.rotationPointX;slot.ty=part.rotationPointY;slot.tz=part.rotationPointZ;
        slot.ax=ax;slot.ay=ay;slot.az=az;slot.rx=part.rotateAngleX;slot.ry=part.rotateAngleY;slot.rz=part.rotateAngleZ;
        /* ModelRenderer has no per-part scale in 1.12; its actual local scale is one. */
        slot.sx=slot.sy=slot.sz=1F;slot.visible=part.showModel&&!part.isHidden;
    }
}