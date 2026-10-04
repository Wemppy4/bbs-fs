package mchorse.bbs_mod.entity;

import io.netty.buffer.ByteBuf;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.forge.GunNetwork;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/** BBS projectile simulation over Blockbuster's 1.12 swept block/entity collision boundary. */
public class GunProjectileEntity extends Entity implements IProjectile, IEntityFormProvider, IEntityAdditionalSpawnData {
    private GunProperties properties=new GunProperties();
    private Form form;
    private final StubEntity stub=new StubEntity();
    private final MCEntity target=new MCEntity(this);
    private Entity owner;
    private boolean stuck,impacted,despawn;
    private int lifeLeft,bounces;
    private IBlockState stuckBlockState;

    public GunProjectileEntity(World world) { super(world);this.setSize(0.25F,0.25F); }
    @Override protected void entityInit() {}
    public GunProperties getProperties() { return this.properties; }
    public void setProperties(GunProperties properties) { this.properties=properties;this.bounces=properties.bounces; }
    public void setOwner(Entity owner) { this.owner=owner; }
    public Entity getOwner() { return this.owner; }
    public boolean isStuck() { return this.stuck; }
    public int getBouncesLeft() { return this.bounces; }
    public int getLifeLeft() { return this.lifeLeft; }
    @Override public Form getForm() { return this.form; }
    @Override public void setForm(Form form) { this.form=form;if(form!=null)form.playMain(); }
    public IEntity getEntity() { this.stub.setWorld(this.world);return this.properties.useTarget?this.target:this.stub; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBeAttackedWithItem() { return false; }
    @Override public boolean isInRangeToRenderDist(double distance) { return true; }
    @Override public boolean canUseCommand(int permission,String command) { return permission<=2; }
    @Override public boolean sendCommandFeedback() { return false; }
    private Vec3d velocity() { return new Vec3d(this.motionX,this.motionY,this.motionZ); }
    private void velocity(Vec3d value) { this.motionX=value.x;this.motionY=value.y;this.motionZ=value.z; }
    @Override public void shoot(double x,double y,double z,float speed,float inaccuracy) {
        Vec3d direction=new Vec3d(x,y,z).normalize().add(this.rand.nextGaussian()*0.0075D*inaccuracy,this.rand.nextGaussian()*0.0075D*inaccuracy,this.rand.nextGaussian()*0.0075D*inaccuracy).scale(speed);
        this.velocity(direction);
        this.rotationYaw=(float)Math.toDegrees(MathHelper.atan2(direction.x,direction.z));
        this.rotationPitch=(float)Math.toDegrees(MathHelper.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z)));
        this.prevRotationYaw=this.rotationYaw;this.prevRotationPitch=this.rotationPitch;
    }
    private void execute(String command) {
        if(!this.world.isRemote&&!command.isEmpty()&&this.getServer()!=null)this.getServer().getCommandManager().executeCommand(this,command);
    }
    private void vanish() { if(!this.isDead){this.setDead();this.execute(this.properties.cmdVanish);} }
    private void impact() {
        if(this.world.isRemote)return;
        if(!this.impacted) { this.setForm(FormUtils.copy(this.properties.impactForm));this.impacted=true; }
        this.execute(this.properties.cmdImpact);
        if(!this.isDead)GunNetwork.sendState(this);
    }
    @Override public void onUpdate() {
        if(this.despawn) { this.setDead();return; }
        super.onUpdate();
        this.getEntity().update();
        if(this.form!=null)this.form.update(this.getEntity());
        if(!this.world.isRemote) {
            this.lifeLeft++;
            if(this.properties.ticking>0&&this.lifeLeft%this.properties.ticking==0)this.execute(this.properties.cmdTicking);
            if(this.lifeLeft>=this.properties.lifeSpan) { this.vanish();return; }
        }
        if(this.isWet())this.extinguish();
        if(this.stuck&&this.properties.collideBlocks) {
            if(this.stuckBlockState!=this.world.getBlockState(this.getPosition())&&this.world.getCollisionBoxes(this,new AxisAlignedBB(this.posX-.06,this.posY-.06,this.posZ-.06,this.posX+.06,this.posY+.06,this.posZ+.06)).isEmpty()) {
                this.stuck=false;
                this.motionX*=this.rand.nextFloat()*.2;this.motionY*=this.rand.nextFloat()*.2;this.motionZ*=this.rand.nextFloat()*.2;
                if(!this.world.isRemote)GunNetwork.sendState(this);
            }
            return;
        }
        Vec3d start=this.getPositionVector(),end=start.add(this.velocity());
        RayTraceResult hit=this.properties.collideBlocks?this.world.rayTraceBlocks(start,end,false,true,false):null;
        if(hit!=null)end=hit.hitVec;
        if(this.properties.collideEntities) {
            double nearest=start.squareDistanceTo(end);
            for(Entity candidate:this.world.getEntitiesWithinAABBExcludingEntity(this,this.getEntityBoundingBox().expand(this.motionX,this.motionY,this.motionZ).grow(1))) {
                if(candidate==this.owner||candidate instanceof GunProjectileEntity||(candidate instanceof net.minecraft.entity.player.EntityPlayer&&((net.minecraft.entity.player.EntityPlayer)candidate).isSpectator())||!candidate.canBeCollidedWith())continue;
                RayTraceResult intercept=candidate.getEntityBoundingBox().grow(.3).calculateIntercept(start,end);
                if(intercept!=null&&start.squareDistanceTo(intercept.hitVec)<=nearest) {
                    nearest=start.squareDistanceTo(intercept.hitVec);hit=new RayTraceResult(candidate,intercept.hitVec);
                }
            }
        }
        if(hit!=null&&!net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this,hit)) {
            if(hit.typeOfHit==RayTraceResult.Type.ENTITY)this.hitEntity(hit.entityHit);
            else if(hit.typeOfHit==RayTraceResult.Type.BLOCK)this.hitBlock(hit);
            this.velocityChanged=true;
        }
        if(this.isDead)return;
        double x=this.posX+this.motionX,y=this.posY+this.motionY,z=this.posZ+this.motionZ;
        float yaw=(float)Math.toDegrees(MathHelper.atan2(this.motionX,this.motionZ));
        float pitch=(float)Math.toDegrees(MathHelper.atan2(this.motionY,Math.sqrt(this.motionX*this.motionX+this.motionZ*this.motionZ)));
        this.rotationYaw=this.prevRotationYaw+MathHelper.wrapDegrees(yaw-this.prevRotationYaw)*.2F;
        this.rotationPitch=this.prevRotationPitch+MathHelper.wrapDegrees(pitch-this.prevRotationPitch)*.2F;
        float friction=this.properties.friction;
        if(this.isInWater()) {
            for(int i=0;i<4;i++)this.world.spawnParticle(EnumParticleTypes.WATER_BUBBLE,x-this.motionX*.25,y-this.motionY*.25,z-this.motionZ*.25,this.motionX,this.motionY,this.motionZ);
            friction=.6F;
        }
        this.motionX*=friction;this.motionY=this.motionY*friction-this.properties.gravity;this.motionZ*=friction;
        this.setPosition(x,y,z);this.doBlockCollisions();
    }
    private void hitBlock(RayTraceResult hit) {
        Vec3d motion=hit.hitVec.subtract(this.getPositionVector());
        if(this.bounces>0) {
            this.bounces--;
            motion=this.velocity().scale(this.properties.bounceDamping);
            if(hit.sideHit.getAxis()==EnumFacing.Axis.X)motion=new Vec3d(-motion.x,motion.y,motion.z);
            if(hit.sideHit.getAxis()==EnumFacing.Axis.Y)motion=new Vec3d(motion.x,-motion.y,motion.z);
            if(hit.sideHit.getAxis()==EnumFacing.Axis.Z)motion=new Vec3d(motion.x,motion.y,-motion.z);
        } else {
            this.stuckBlockState=this.world.getBlockState(hit.getBlockPos());this.stuck=true;
            if(this.properties.vanish)this.vanish();
        }
        this.velocity(motion);
        Vec3d offset=motion.normalize().scale(.05);
        this.setPosition(this.posX-offset.x,this.posY-offset.y,this.posZ-offset.z);
        this.impact();
    }
    private void hitEntity(Entity entity) {
        if(this.world.isRemote||this.properties.damage<=0)return;
        int damage=MathHelper.ceil(MathHelper.clamp(this.velocity().length()*this.properties.damage,0,Integer.MAX_VALUE));
        boolean burning=entity.isBurning();
        if(this.isBurning())entity.setFire(5);
        if(entity.attackEntityFrom(DamageSource.MAGIC,damage)) {
            if(entity instanceof EntityLivingBase) {
                EntityLivingBase living=(EntityLivingBase)entity;
                if(this.properties.knockback>0) {
                    double resistance=Math.max(0,1-living.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue());
                    Vec3d punch=this.velocity().normalize().scale(this.properties.knockback*.6*resistance);
                    if(punch.lengthSquared()>0)living.addVelocity(punch.x,.1,punch.z);
                }
                if(this.owner instanceof EntityLivingBase) {
                    EnchantmentHelper.applyThornEnchantments(living,this.owner);
                    EnchantmentHelper.applyArthropodEnchantments((EntityLivingBase)this.owner,living);
                }
                if(this.bounces<=0&&this.properties.vanish)this.vanish();else this.impact();
            }
        } else {
            if(!burning)entity.extinguish();
            this.velocity(this.velocity().scale(-.1));this.rotationYaw+=180;this.prevRotationYaw+=180;
        }
    }
    public NBTTagCompound snapshot() {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setTag("Properties",DataStorageUtils.toNbt(this.properties.toData()));
        if(this.form!=null)tag.setTag("Form",DataStorageUtils.toNbt(FormUtils.toData(this.form)));
        tag.setBoolean("Stuck",this.stuck);tag.setBoolean("Impacted",this.impacted);
        tag.setInteger("Bounces",this.bounces);tag.setInteger("Life",this.lifeLeft);tag.setInteger("Age",this.ticksExisted);
        tag.setDouble("MotionX",this.motionX);tag.setDouble("MotionY",this.motionY);tag.setDouble("MotionZ",this.motionZ);
        tag.setInteger("Owner",this.owner==null?-1:this.owner.getEntityId());
        return tag;
    }
    public void applySnapshot(NBTTagCompound tag) {
        GunProperties properties=new GunProperties();properties.fromData(DataStorageUtils.fromNbt(tag.getCompoundTag("Properties")).asMap());this.setProperties(properties);
        this.setForm(tag.hasKey("Form",10)?FormUtils.fromData(DataStorageUtils.fromNbt(tag.getCompoundTag("Form"))):null);
        this.stuck=tag.getBoolean("Stuck");this.impacted=tag.getBoolean("Impacted");
        this.bounces=tag.getInteger("Bounces");this.lifeLeft=tag.getInteger("Life");this.ticksExisted=tag.getInteger("Age");
        this.motionX=tag.getDouble("MotionX");this.motionY=tag.getDouble("MotionY");this.motionZ=tag.getDouble("MotionZ");
        this.owner=this.world.getEntityByID(tag.getInteger("Owner"));
        if(this.stuck)this.stuckBlockState=this.world.getBlockState(this.getPosition());
    }
    @Override public void writeSpawnData(ByteBuf buffer) { ByteBufUtils.writeTag(buffer,this.snapshot()); }
    @Override public void readSpawnData(ByteBuf buffer) { NBTTagCompound tag=ByteBufUtils.readTag(buffer);if(tag!=null)this.applySnapshot(tag); }
    @Override protected void writeEntityToNBT(NBTTagCompound tag) { tag.setBoolean("despawn",true); }
    @Override protected void readEntityFromNBT(NBTTagCompound tag) { this.despawn=tag.getBoolean("despawn"); }
}