package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forge.CommonProxy;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.items.GunZoom;
import mchorse.bbs_mod.client.renderer.item.GunItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.IScoreCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real item use, server simulation and Forge spawn payload, isolated to the disposable test world. */
public final class OriginalGunsProbe {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static volatile JsonObject result=new JsonObject();
    private static volatile String error;
    private static volatile int completed;
    private static int requested;
    private static final List<Integer> projectiles=new ArrayList<>();
    public static JsonObject run(JsonObject request) {
        if(MC.getIntegratedServer()==null||MC.player==null||!new File(MC.gameDir,"saves/ai_test").getAbsoluteFile().equals(MC.getIntegratedServer().getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile()))
            throw new IllegalStateException("Gun probe requires the ai_test integrated world");
        String op=request.has("op")?request.get("op").getAsString():"status";
        if(op.equals("test")||op.equals("cleanup")) {
            final int task=++requested;error=null;
            MC.getIntegratedServer().addScheduledTask(()->{
                try { if(op.equals("test"))result=test();else cleanup(); }
                catch(Throwable failure) { error=failure.toString();failure.printStackTrace(); }
                finally { completed=task; }
            });
        }
        JsonObject out=new JsonObject();out.addProperty("ok",error==null);out.addProperty("requested",requested);out.addProperty("completed",completed);
        if(error!=null)out.addProperty("error",error);
        out.add("result",result);
        JsonArray visible=new JsonArray();
        synchronized(projectiles) { for(int id:projectiles)if(MC.world.getEntityByID(id) instanceof GunProjectileEntity)visible.add(id); }
        out.add("clientProjectiles",visible);
        ItemStack gun=new ItemStack(CommonProxy.GUN_ITEM);
        out.addProperty("itemRegistered",ItemStack.EMPTY.getItem()!=gun.getItem()&&"bbs:gun".equals(gun.getItem().getRegistryName().toString()));
        out.addProperty("nativeItemRenderer",MC.getRenderItem().getItemModelWithOverrides(gun,MC.world,MC.player).isBuiltInRenderer());
        out.addProperty("defaultGunForm",GunItemRenderer.INSTANCE.get(gun).properties.getForm()!=null);
        return out;
    }
    private static GunProperties properties() {
        GunProperties properties=GunProperties.get(new ItemStack(CommonProxy.GUN_ITEM));
        properties.lifeSpan=200;properties.gravity=0;properties.friction=1;properties.projectiles=3;properties.speed=0;
        properties.collideBlocks=false;properties.collideEntities=false;
        BlockForm form=new BlockForm();form.blockState.set(Blocks.GOLD_BLOCK.getDefaultState());properties.projectileForm=form;
        return properties;
    }
    private static GunProjectileEntity projectile(WorldServer world,GunProperties properties,double x,double y,double z) {
        GunProjectileEntity projectile=new GunProjectileEntity(world);projectile.setProperties(properties);projectile.setForm(mchorse.bbs_mod.forms.FormUtils.copy(properties.projectileForm));projectile.setPosition(x,y,z);return projectile;
    }
    private static ItemStack stack(GunProperties properties) {
        ItemStack stack=new ItemStack(CommonProxy.GUN_ITEM);NBTTagCompound nbt=new NBTTagCompound();nbt.setTag("GunData",DataStorageUtils.toNbt(properties.toData()));stack.setTagCompound(nbt);return stack;
    }
    private static JsonObject test() throws Exception {
        cleanup();
        WorldServer world=MC.getIntegratedServer().getWorld(0);
        EntityPlayerMP real=MC.getIntegratedServer().getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
        FakePlayer player=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"[BBS Gun QA]")) {
            @Override public boolean canUseCommand(int level,String command) { return level<=2; }
            @Override public boolean sendCommandFeedback() { return false; }
        };
        player.setPosition(real.posX+5,real.posY+5,real.posZ+5);
        player.rotationYaw=0;player.rotationYawHead=0;player.rotationPitch=0;
        String objectiveName="bg"+Long.toUnsignedString(System.nanoTime(),36);
        ScoreObjective objective=world.getScoreboard().addScoreObjective(objectiveName,IScoreCriteria.DUMMY);
        BlockPos block=new BlockPos(real.posX+8,250,real.posZ+8);
        net.minecraft.block.state.IBlockState old=world.getBlockState(block);
        net.minecraft.tileentity.TileEntity oldTile=world.getTileEntity(block);
        NBTTagCompound oldNbt=oldTile==null?null:oldTile.writeToNBT(new NBTTagCompound());
        EntityPig pig=null;
        try {
            JsonObject out=new JsonObject();GunProperties properties=properties();
            properties.cmdFiring="scoreboard players add fired "+objectiveName+" 1";
            player.setHeldItem(EnumHand.MAIN_HAND,stack(properties));
            List<Entity> before=new ArrayList<>(world.loadedEntityList);
            CommonProxy.GUN_ITEM.onItemRightClick(world,player,EnumHand.MAIN_HAND);
            List<GunProjectileEntity> spawned=new ArrayList<>();
            for(Entity entity:world.loadedEntityList)if(entity instanceof GunProjectileEntity&&!before.contains(entity)&&((GunProjectileEntity)entity).getOwner()==player)spawned.add((GunProjectileEntity)entity);
            synchronized(projectiles){for(GunProjectileEntity entity:spawned)projectiles.add(entity.getEntityId());}
            out.addProperty("projectileCount",spawned.size());
            out.addProperty("fireCommand",world.getScoreboard().getOrCreateScore("fired",objective).getScorePoints()==1);
            GunProjectileEntity source=spawned.get(0);
            io.netty.buffer.ByteBuf buffer=Unpooled.buffer();GunProjectileEntity clone=new GunProjectileEntity(world);
            try { source.writeSpawnData(buffer);clone.readSpawnData(buffer); } finally { buffer.release(); }
            out.addProperty("spawnPayload",clone.getForm() instanceof BlockForm&&clone.getProperties().lifeSpan==200&&clone.getProperties().projectiles==3);

            GunProperties launch=properties();launch.launch=true;launch.launchPower=2;
            player.setHeldItem(EnumHand.MAIN_HAND,stack(launch));CommonProxy.GUN_ITEM.onItemRightClick(world,player,EnumHand.MAIN_HAND);
            out.addProperty("launch",Math.abs(player.motionZ-2)<.001&&Math.abs(player.motionX)<.001);
            launch.launchAdditive=true;player.setHeldItem(EnumHand.MAIN_HAND,stack(launch));CommonProxy.GUN_ITEM.onItemRightClick(world,player,EnumHand.MAIN_HAND);
            out.addProperty("launchAdditive",Math.abs(player.motionZ-4)<.001);
            player.motionX=player.motionY=player.motionZ=0;

            world.setBlockState(block,Blocks.STONE.getDefaultState(),3);
            GunProperties bounce=properties();bounce.collideBlocks=true;bounce.bounces=1;bounce.bounceDamping=.5F;bounce.vanish=false;
            bounce.cmdImpact="scoreboard players add impact "+objectiveName+" 1";
            GunProjectileEntity bouncing=projectile(world,bounce,block.getX()+.5,block.getY()+.5,block.getZ()-.5);bouncing.motionZ=1;bouncing.onUpdate();
            out.addProperty("bounce",bouncing.getBouncesLeft()==0&&bouncing.motionZ<0&&!bouncing.isStuck());
            out.addProperty("impactCommand",world.getScoreboard().getOrCreateScore("impact",objective).getScorePoints()==1);
            GunProperties stick=properties();stick.collideBlocks=true;stick.vanish=false;
            GunProjectileEntity stuck=projectile(world,stick,block.getX()+.5,block.getY()+.5,block.getZ()-.5);stuck.motionZ=1;stuck.onUpdate();
            out.addProperty("stick",stuck.isStuck()&&!stuck.isDead);
            world.setBlockToAir(block);stuck.onUpdate();out.addProperty("fallAfterBlockRemoved",!stuck.isStuck());

            GunProperties life=properties();life.lifeSpan=3;life.ticking=1;
            life.cmdTicking="scoreboard players add tick "+objectiveName+" 1";life.cmdVanish="scoreboard players add vanish "+objectiveName+" 1";
            GunProjectileEntity timed=projectile(world,life,block.getX()+.5,252,block.getZ()+.5);
            for(int i=0;i<3;i++)timed.onUpdate();
            out.addProperty("lifetime",timed.isDead&&timed.getLifeLeft()==3);
            out.addProperty("tickAndVanishCommands",world.getScoreboard().getOrCreateScore("tick",objective).getScorePoints()==3&&world.getScoreboard().getOrCreateScore("vanish",objective).getScorePoints()==1);

            pig=new EntityPig(world);pig.setPosition(block.getX()+.5,252,block.getZ()+.5);world.spawnEntity(pig);
            GunProperties damage=properties();damage.collideEntities=true;damage.damage=2;damage.knockback=1;damage.vanish=true;
            GunProjectileEntity shot=projectile(world,damage,pig.posX,pig.posY+.5,pig.posZ-1);shot.motionZ=1;float health=pig.getHealth();shot.onUpdate();
            out.addProperty("damage",pig.getHealth()<health);out.addProperty("knockback",pig.motionZ>0);out.addProperty("vanishOnHit",shot.isDead);

            GunZoom zoom=new GunZoom(40,properties.fovInterp,10);zoom.update(true,6);float halfway=zoom.getFOV(70);zoom.update(true,20);
            out.addProperty("zoomInterpolation",halfway>40&&halfway<70&&Math.abs(zoom.getFOV(70)-40)<.001);
            zoom.update(false,20);out.addProperty("zoomRestore",zoom.canBeRemoved()&&Math.abs(zoom.getFOV(70)-70)<.001);
            GunZoom instant=new GunZoom(40,properties.fovInterp,0);instant.update(true,1);out.addProperty("instantZoom",instant.getFOV(70)==40);
            return out;
        } finally {
            if(pig!=null){pig.setDead();world.removeEntity(pig);}
            world.setBlockState(block,old,3);
            if(oldNbt!=null){net.minecraft.tileentity.TileEntity tile=net.minecraft.tileentity.TileEntity.create(world,oldNbt);if(tile!=null)world.setTileEntity(block,tile);}
            world.getScoreboard().removeObjective(objective);
        }
    }
    private static void cleanup() {
        WorldServer world=MC.getIntegratedServer().getWorld(0);
        synchronized(projectiles) {
            for(int id:projectiles){Entity entity=world.getEntityByID(id);if(entity!=null){entity.setDead();world.removeEntity(entity);}}
            projectiles.clear();
        }
    }
}