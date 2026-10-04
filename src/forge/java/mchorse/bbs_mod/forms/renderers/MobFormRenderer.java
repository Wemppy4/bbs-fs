package mchorse.bbs_mod.forms.renderers;

import com.mojang.authlib.GameProfile;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.cubic.jem.VanillaRigs;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.mob.NativeMobRenderContext;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.utils.*;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;

/** Vanilla entities, animation and render layers, with the original BBS pose/bone contract. */
public final class MobFormRenderer extends NativeGeometryFormRenderer<MobForm> implements ITickable
{
    private static final Method PHASE=ReflectionHelper.findMethod(RenderLivingBase.class,"handleRotationFloat","func_77044_a",EntityLivingBase.class,float.class);
    private static final Method ROTATIONS=ReflectionHelper.findMethod(RenderLivingBase.class,"applyRotations","func_77043_a",EntityLivingBase.class,float.class,float.class,float.class);
    private static final Method POSITION=ReflectionHelper.findMethod(RenderLivingBase.class,"renderLivingAt","func_77039_a",EntityLivingBase.class,double.class,double.class,double.class);
    private static final Method PLAYER_MODEL=ReflectionHelper.findMethod(RenderPlayer.class,"setModelVisibilities","func_177137_d",AbstractClientPlayer.class);
    private static final Field ACTIVE_STACK=ReflectionHelper.findField(EntityLivingBase.class,"activeItemStack","field_184627_bm");
    private static final Field USE_COUNT=ReflectionHelper.findField(EntityLivingBase.class,"activeItemStackUseCount","field_184628_bn");
    private Entity entity;
    private String key;
    private Render renderer;
    private VanillaRigs.Rig rig;
    private IBoneHierarchy hierarchy;
    private boolean rightHanded=true;
    private final MatrixCache bones=new MatrixCache();
    public MobFormRenderer(MobForm form){super(form);}
    public Entity getEntity(){ensure();return entity;}
    private void ensure()
    {
        World world=Minecraft.getMinecraft().world;
        String current=form.mobID.get()+"\n"+form.mobNBT.get()+"\n"+form.slim.get();
        if(!Objects.equals(key,current)||entity!=null&&entity.world!=world)
        {key=current;entity=null;renderer=null;rig=null;hierarchy=null;}
        if(world==null)return;
        if(entity!=null){refreshRig();return;}
        try
        {
            if(form.isPlayer())
            {
                final boolean slim=form.slim.get();
                entity=new EntityOtherPlayerMP(world,new GameProfile(UUID.fromString(slim?"5477bd28-e672-4f87-a209-c03cf75f3606":"b99a2400-28a8-4288-92dc-924beafbf756"),slim?"osmiq":"McHorseYT"))
                {
                    @Override public String getSkinType(){return slim?"slim":"default";}
                    @Override public boolean isWearing(EnumPlayerModelParts part){return true;}
                    @Override public EnumHandSide getPrimaryHand(){return rightHanded?EnumHandSide.RIGHT:EnumHandSide.LEFT;}
                };
            }
            else entity=EntityList.createEntityByIDFromName(new ResourceLocation(form.mobID.get()),world);
            if(entity==null)return;
            if(!form.mobNBT.get().trim().isEmpty())
            {
                try{entity.readFromNBT(JsonToNBT.getTagFromJson(form.mobNBT.get()));}
                catch(Exception malformed){BBSMod.LOGGER.warn("Ignoring invalid mob form NBT for "+form.mobID.get(),malformed);}
            }
            entity.noClip=true;if(entity instanceof EntityLiving)((EntityLiving)entity).setNoAI(true);
            refreshRig();
        }
        catch(Exception error){BBSMod.LOGGER.warn("Cannot create mob form "+form.mobID.get(),error);entity=null;}
    }
    private void refreshRig()
    {
        Render next=Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(entity);
        ModelBase model=next instanceof RenderLivingBase?((RenderLivingBase)next).getMainModel():null;
        /* OptiFine/resource packs replace either the renderer or its model during reload. */
        if(renderer!=next||(model!=null&&(rig==null||rig.model()!=model)))
        {
            renderer=next;rig=null;hierarchy=null;bones.clear();
            if(model!=null)
            {
                String id=form.mobID.get().replace("minecraft:","");
                rig=VanillaRigs.inspect(id,model);
                hierarchy=new IBoneHierarchy()
                {
                    public Collection<String> getRootGroupKeys(){return rig.roots();}
                    public Collection<String> getDirectChildrenKeys(String key){return rig.children().getOrDefault(key,Collections.emptyList());}
                    public String getParentGroupKey(String key){for(Map.Entry<String,List<String>> e:rig.children().entrySet())if(e.getValue().contains(key))return e.getKey();return null;}
                };
            }
        }
    }
    private void sync(IEntity source)
    {
        ensure();if(entity==null||source==null)return;
        entity.setPosition(source.getX(),source.getY(),source.getZ());
        entity.prevPosX=source.getPrevX();entity.prevPosY=source.getPrevY();entity.prevPosZ=source.getPrevZ();
        entity.ticksExisted=source.getAge();entity.onGround=source.isOnGround();entity.setSneaking(source.isSneaking());entity.setSprinting(source.isSprinting());
        entity.motionX=source.getVelocity().x;entity.motionY=source.getVelocity().y;entity.motionZ=source.getVelocity().z;
        entity.fallDistance=source.getFallDistance();
        NativeLivingState.setGliding(entity,source.isFallFlying());NativeLivingState.setSwimming(entity,source.isSwimming());
        rightHanded=source.isRightHanded();
        if(entity instanceof net.minecraft.entity.player.EntityPlayer)((net.minecraft.entity.player.EntityPlayer)entity).capabilities.isFlying=source.isFlying();
        if(entity instanceof EntityLiving)((EntityLiving)entity).setLeftHanded(!rightHanded);
        entity.rotationYaw=entity.prevRotationYaw=0;entity.rotationPitch=source.getPitch();entity.prevRotationPitch=source.getPrevPitch();
        if(entity instanceof EntityLivingBase)
        {
            EntityLivingBase living=(EntityLivingBase)entity;
            living.renderYawOffset=living.prevRenderYawOffset=0;
            living.rotationYawHead=source.getHeadYaw()-source.getBodyYaw();living.prevRotationYawHead=source.getPrevHeadYaw()-source.getPrevBodyYaw();
            living.limbSwing=source.getLimbPos(1);living.limbSwingAmount=source.getLimbSpeed(1);living.prevLimbSwingAmount=source.getLimbSpeed(0);
            living.swingProgress=source.getHandSwingProgress(1);living.prevSwingProgress=source.getHandSwingProgress(0);
            living.hurtTime=source.getHurtTimer();living.deathTime=source.getDeathTime();
            NativeLivingState.setRoll(living,source.getRoll());
            for(EntityEquipmentSlot slot:EntityEquipmentSlot.values())
            {
                ItemStack stack=source.getEquipmentStack(slot);
                if(!ItemStack.areItemStacksEqual(living.getItemStackFromSlot(slot),stack))living.setItemStackToSlot(slot,stack.copy());
            }
            syncUse(living,source);
        }
    }
    private static void syncUse(EntityLivingBase living,IEntity source)
    {
        ItemUsePose.Use use=ItemUsePose.get(source,true);EnumHand hand=EnumHand.MAIN_HAND;
        if(use==null){use=ItemUsePose.get(source,false);hand=EnumHand.OFF_HAND;}
        ItemStack stack=use==null?ItemStack.EMPTY:living.getHeldItem(hand);
        int count=stack.isEmpty()?0:Math.max(1,Math.round(stack.getMaxItemUseDuration()-use.elapsed()));
        try
        {
            /* Random-access film time must not start ticking/consuming a real item. These are
             * the same fields vanilla's item predicates and RenderPlayer read. */
            ACTIVE_STACK.set(living,stack);USE_COUNT.setInt(living,count);
            byte flags=living.getDataManager().get(NativeLivingState.handStates());
            living.getDataManager().set(NativeLivingState.handStates(),(byte)((flags&~3)|(count>0?1:0)|(count>0&&hand==EnumHand.OFF_HAND?2:0)));
        }
        catch(IllegalAccessException error){throw new IllegalStateException("Cannot synchronize mob item use",error);}
    }
    @Override public void tick(IEntity source)
    {
        ensure();if(entity==null)return;
        /* The display entity may animate, but its copied bow/food must never execute a use. */
        if(entity instanceof EntityLivingBase)syncUse((EntityLivingBase)entity,null);
        entity.onUpdate();sync(source);
    }
    private Pose pose()
    {
        form.syncOverlayTracks();Pose result=form.pose.get().copy();
        mchorse.bbs_mod.forge.studio.NativeFormRenderer.overlay(result,form.poseOverlay.get());
        for(mchorse.bbs_mod.settings.values.core.ValuePose overlay:form.additionalOverlays)mchorse.bbs_mod.forge.studio.NativeFormRenderer.overlay(result,overlay.get());
        return result;
    }
    @Override public IBoneHierarchy getBoneHierarchy(){ensure();return hierarchy;}
    @Override public List<String> getBones(){ensure();return rig==null?Collections.emptyList():new ArrayList<>(rig.parts().keySet());}
    @Override public AABB getPreviewBounds(){ensure();return entity==null?null:new AABB(-entity.width/2F,0,-entity.width/2F,entity.width,entity.height,entity.width);}
    @Override protected void updateStencilMap(FormRenderingContext context)
    {context.stencilMap.addPicking(form,"");if(rig!=null)for(String name:rig.parts().keySet())context.stencilMap.addPicking(form,name);}
    @Override protected void render3D(FormRenderingContext context)
    {
        bones.clear();sync(context.entity);if(entity==null||renderer==null)return;
        RenderManager manager=Minecraft.getMinecraft().getRenderManager();Entity view=manager.renderViewEntity;
        int depth=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),list=GL11.glGetInteger(GL11.GL_LIST_INDEX);
        Color color=Color.white();color.mul(context.color);
        try(NativeFormDraw draw=new NativeFormDraw(context,color,form.overlayColor.get(),false);
            NativeMobRenderContext mob=new NativeMobRenderContext(form,rig,pose(),context,bones))
        {
            manager.renderViewEntity=entity;
            renderer.doRender(entity,0D,0D,0D,0F,context.transition);
        }
        finally
        {
            manager.renderViewEntity=view;
            if(list==0&&GL11.glGetInteger(GL11.GL_LIST_INDEX)!=0)GlStateManager.glEndList();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            while(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)>depth)GlStateManager.popMatrix();
        }
    }
    @Override public void renderBodyParts(FormRenderingContext context)
    {
        for(BodyPart part:form.parts.getAllTyped())
        {
            Matrix4f matrix=part.filterBoneMatrix(bones.get(part.bone.get()).matrix());
            context.stack.push();if(context.world!=null)context.world.push();
            try
            {
                if(matrix!=null){MatrixStackUtils.multiply(context.stack,matrix);if(context.world!=null)MatrixStackUtils.multiply(context.world,matrix);}
                renderBodyPart(part,context);
            }
            finally{context.stack.pop();if(context.world!=null)context.world.pop();}
        }
    }
    /** Replays only native model transforms, suppressing all geometry and layers. GL stacks and
     * shared model fields are restored; this runs on the same client thread as the editor. */
    private MatrixCache evaluate(IEntity source,float partial)
    {
        MatrixCache result=new MatrixCache();if(source==null||NativeMobRenderContext.isActive())return result;
        sync(source);if(!(entity instanceof EntityLivingBase)||rig==null)return result;
        EntityLivingBase living=(EntityLivingBase)entity;ModelBase model=rig.model();
        Map<ModelRenderer,float[]> saved=new IdentityHashMap<>();
        for(ModelRenderer p:model.boxList)saved.put(p,new float[]{p.rotationPointX,p.rotationPointY,p.rotationPointZ,p.rotateAngleX,p.rotateAngleY,p.rotateAngleZ,p.offsetX,p.offsetY,p.offsetZ,p.showModel?1:0,p.isHidden?1:0});
        boolean child=model.isChild,riding=model.isRiding;float swing=model.swingProgress;
        ModelBiped biped=model instanceof ModelBiped?(ModelBiped)model:null;
        boolean sneak=biped!=null&&biped.isSneak;
        ModelBiped.ArmPose left=biped==null?null:biped.leftArmPose,right=biped==null?null:biped.rightArmPose;
        FormRenderingContext context=new FormRenderingContext().set(FormRenderType.ENTITY,source,new MatrixStack(),0x00f000f0,10<<16,partial).inUI();
        try(NativeFormDraw.State state=new NativeFormDraw.State();NativeMobRenderContext capture=new NativeMobRenderContext(form,rig,pose(),context,result).captureOnly())
        {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
            try
            {
                if(renderer instanceof RenderPlayer)PLAYER_MODEL.invoke(renderer,(AbstractClientPlayer)living);
                model.isChild=living.isChild();model.isRiding=living.isRiding()&&living.getRidingEntity()!=null&&living.getRidingEntity().shouldRiderSit();model.swingProgress=source.getHandSwingProgress(partial);
                float limb=source.getLimbPos(partial)*(model.isChild?3F:1F),speed=Math.min(1F,source.getLimbSpeed(partial));
                if(model.isRiding||!living.isEntityAlive()){limb=0F;speed=0F;}
                float age=((Number)PHASE.invoke(renderer,living,partial)).floatValue();
                float head=living.prevRotationYawHead+MathHelper.wrapDegrees(living.rotationYawHead-living.prevRotationYawHead)*partial,pitch=living.prevRotationPitch+(living.rotationPitch-living.prevRotationPitch)*partial;
                POSITION.invoke(renderer,living,0D,renderer instanceof RenderPlayer&&living.isSneaking()?-.125D:0D,0D);
                ROTATIONS.invoke(renderer,living,age,0F,partial);
                float scale=((RenderLivingBase)renderer).prepareScale(living,partial);
                model.setLivingAnimations(living,limb,speed,partial);model.setRotationAngles(limb,speed,age,head,pitch,scale,living);
                model.render(living,limb,speed,age,head,pitch,scale);
            }
            finally{GlStateManager.popMatrix();}
        }
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot evaluate mob pose",e);}
        finally
        {
            model.isChild=child;model.isRiding=riding;model.swingProgress=swing;
            if(biped!=null){biped.isSneak=sneak;biped.leftArmPose=left;biped.rightArmPose=right;}
            for(Map.Entry<ModelRenderer,float[]> e:saved.entrySet())
            {ModelRenderer p=e.getKey();float[] v=e.getValue();p.rotationPointX=v[0];p.rotationPointY=v[1];p.rotationPointZ=v[2];p.rotateAngleX=v[3];p.rotateAngleY=v[4];p.rotateAngleZ=v[5];p.offsetX=v[6];p.offsetY=v[7];p.offsetZ=v[8];p.showModel=v[9]!=0;p.isHidden=v[10]!=0;}
        }
        return result;
    }
    @Override public void collectMatrices(IEntity entity,MatrixStack stack,MatrixCache matrices,String prefix,float transition)
    {
        mchorse.bbs_mod.api.client.events.FormPoseEvents.PARENT_FRAME.invoker().capture(form,entity,stack.peek().getPositionMatrix(),prefix,transition);
        stack.push();applyTransforms(stack,true,transition);Matrix4f origin=new Matrix4f(stack.peek().getPositionMatrix());stack.pop();
        stack.push();applyTransforms(stack,false,transition);Matrix4f parent=new Matrix4f(stack.peek().getPositionMatrix());matrices.put(prefix,parent,origin);
        MatrixCache local=evaluate(entity,transition);
        for(Map.Entry<String,MatrixCacheEntry> e:local.entrySet())
            matrices.put(StringUtils.combinePaths(prefix,e.getKey()),new Matrix4f(parent).mul(e.getValue().matrix()),new Matrix4f(parent).mul(e.getValue().origin()));
        for(BodyPart part:form.parts.getAllTyped())
        {
            if(part.getForm()==null)continue;stack.push();Matrix4f bone=part.filterBoneMatrix(local.get(part.bone.get()).matrix());
            if(bone!=null)MatrixStackUtils.multiply(stack,bone);MatrixStackUtils.applyTransform(stack,part.transform.get());
            FormUtilsClient.getRenderer(part.getForm()).collectMatrices(part.getRenderEntity(entity),stack,matrices,StringUtils.combinePaths(prefix,part.getId()),transition);stack.pop();
        }
        stack.pop();
    }
}
