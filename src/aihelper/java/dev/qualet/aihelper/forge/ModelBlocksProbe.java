package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.*;
import mchorse.bbs_mod.blocks.ModelBlockSound;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forge.*;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.film.replays.ReplayFactory;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;

/** Real packet, tile, item and body checks in the disposable world, with fixture restoration. */
public final class ModelBlocksProbe
{
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static BlockPos pos;
    private static IBlockState previous;
    private static NBTTagCompound previousTile;
    private static ItemStack previousHand;
    private static volatile String error;
    private static volatile JsonObject serverState = new JsonObject();
    private static int requested;
    private static volatile int completed;
    private static ModelTileEntity tile() { return (ModelTileEntity) MC.world.getTileEntity(pos); }
    public static JsonObject handle(JsonObject input)
    {
        IntegratedServer server = MC.getIntegratedServer();
        if (server == null || MC.world == null || !new File(MC.gameDir,"saves/ai_test").getAbsoluteFile().equals(server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile()))
            throw new IllegalStateException("Model block probe requires ai_test integrated world");
        String op = input.has("op") ? input.get("op").getAsString() : "status";
        if (op.equals("prepare"))
        {
            if (pos != null) throw new IllegalStateException("Restore previous fixture first");
            pos = new BlockPos(MC.player.posX + 3, MC.player.posY, MC.player.posZ + 3);
            schedule(server, () -> {
                WorldServer world=server.getWorld(0);
                previous=world.getBlockState(pos);
                TileEntity old=world.getTileEntity(pos);
                previousTile=old==null?null:old.writeToNBT(new NBTTagCompound());
                EntityPlayerMP player=server.getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
                previousHand=player.getHeldItemMainhand().copy();
                world.setBlockState(pos, CommonProxy.MODEL_BLOCK.getDefaultState(), 3);
            });
        }
        else if (op.equals("edit"))
        {
            ModelProperties p=tile().getProperties();
            BlockForm block=new BlockForm();block.blockState.set(Blocks.GOLD_BLOCK.getDefaultState());
            block.name.set("QA model body");p.setForm(block);
            LabelForm label=new LabelForm();label.text.set("BBS item Ёж");p.setFormInventory(label);
            ItemForm item=new ItemForm();item.stack.set(new ItemStack(Items.DIAMOND_SWORD));p.setFormFirstPerson(item);
            BlockForm third=new BlockForm();third.blockState.set(Blocks.REDSTONE_BLOCK.getDefaultState());p.setFormThirdPerson(third);
            p.getTransform().translate.set(.25F,.125F,-.125F);p.getTransform().rotate.set(.3F,.7F,.1F);p.getTransform().scale.set(.8F);
            p.getBody().setHitboxMode(ModelBody.HitboxMode.MANUAL);p.getBody().getHitboxMin().set(.1F,0,.1F);p.getBody().getHitboxMax().set(.9F,1.5F,.9F);
            p.getBody().setSolid(true);p.getBody().setCameraCollision(true);p.getBody().setLightLevel(12);p.getBody().setHardness(2);p.getBody().setSound(ModelBlockSound.WOOD);
            p.getEquipment().set(EntityEquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));p.setShadow(true);p.setGlobal(true);
            ClientNetwork.sendModelBlockForm(pos,tile());
        }
        else if (op.equals("snapshot")) schedule(server, () -> inspect(server));
        else if (op.equals("give")) schedule(server, () -> {
            WorldServer world=server.getWorld(0);EntityPlayerMP player=server.getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
            ItemStack stack=CommonProxy.MODEL_BLOCK.getPickBlock(world.getBlockState(pos),null,world,pos,player);
            player.setHeldItem(EnumHand.MAIN_HAND,stack);player.inventoryContainer.detectAndSendChanges();
        });
        else if (op.equals("itemEdit"))
        {
            ModelProperties properties=BBSModClient.getItemStackProperties(MC.player.getHeldItemMainhand());
            properties.getTransformInventory().scale.set(.4F);properties.getTransformFirstPerson().translate.set(.2F,.3F,.4F);
            ClientNetwork.sendModelBlockTransforms(properties.toData());
        }
        else if (op.equals("open")) BBSMod.proxy.openModel(tile());
        else if (op.equals("cleanup"))
        {
            MC.displayGuiScreen(null);
            schedule(server, () -> {
                WorldServer world=server.getWorld(0);world.setBlockState(pos,previous,3);
                if(previousTile!=null){TileEntity old=TileEntity.create(world,previousTile);if(old!=null)world.setTileEntity(pos,old);}
                EntityPlayerMP player=server.getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
                player.setHeldItem(EnumHand.MAIN_HAND,previousHand);player.inventoryContainer.detectAndSendChanges();
                pos=null;
            });
        }
        JsonObject out=new JsonObject();out.addProperty("ok",true);out.addProperty("requested",requested);out.addProperty("completed",completed);out.addProperty("error",error);out.add("server",serverState);
        if(pos!=null && MC.world.getTileEntity(pos) instanceof ModelTileEntity)
        {
            ModelTileEntity tile=tile();ModelProperties props=tile.getProperties();
            out.addProperty("clientTile",true);out.addProperty("clientLight",props.getBody().getLightLevel());
            out.addProperty("clientForms",props.getForm()!=null&&props.getFormInventory()!=null&&props.getFormFirstPerson()!=null&&props.getFormThirdPerson()!=null);
            if(props.getForm()!=null)
            {
                Film film=new Film();Replay replay=ReplayFactory.fromModelBlock(film,tile);
                out.addProperty("replayAnchor",replay.form.get() instanceof AnchorForm);
                out.addProperty("replayShadow",replay.shadow.get());
                out.addProperty("replayX",replay.keyframes.x.interpolate(0));
                out.addProperty("expectedX",pos.getX()+.5+props.getTransform().translate.x);
            }
            UIModelBlockPanel panel=BBSModClient.getDashboard().getPanel(UIModelBlockPanel.class);
            out.addProperty("panelSelected",panel!=null && panel.getModelBlock()==tile);
            ItemStack hand=MC.player.getHeldItemMainhand();
            out.addProperty("heldModel",hand.getItem()==net.minecraft.item.Item.getItemFromBlock(CommonProxy.MODEL_BLOCK));
            if(out.get("heldModel").getAsBoolean())
            {
                ModelProperties item=BBSModClient.getItemStackProperties(hand);
                out.addProperty("itemInventoryScale",item.getTransformInventory().scale.x);
                out.addProperty("itemFirstPersonY",item.getTransformFirstPerson().translate.y);
                out.addProperty("itemFourForms",item.getForm()!=null&&item.getFormInventory()!=null&&item.getFormFirstPerson()!=null&&item.getFormThirdPerson()!=null);
            }
        }
        return out;
    }
    private static void schedule(IntegratedServer server,Runnable work)
    {
        int id=++requested;error=null;
        server.addScheduledTask(() -> {try{work.run();}catch(Throwable e){error=e.toString();e.printStackTrace();}finally{completed=id;}});
    }
    private static void inspect(IntegratedServer server)
    {
        WorldServer world=server.getWorld(0);ModelTileEntity tile=(ModelTileEntity)world.getTileEntity(pos);
        ModelProperties props=tile.getProperties();IBlockState state=world.getBlockState(pos);JsonObject out=new JsonObject();
        NBTTagCompound nbt=tile.writeToNBT(new NBTTagCompound());ModelTileEntity restored=new ModelTileEntity();restored.readFromNBT(nbt);
        out.addProperty("nbtRoundtrip",props.toData().equals(restored.getProperties().toData()));
        out.addProperty("light",state.getLightValue(world,pos));out.addProperty("hardness",state.getBlockHardness(world,pos));
        out.addProperty("woodSound",CommonProxy.MODEL_BLOCK.getSoundType(state,world,pos,null)==net.minecraft.block.SoundType.WOOD);
        AxisAlignedBB box=state.getCollisionBoundingBox(world,pos);out.addProperty("solid",box!=null);out.addProperty("height",box==null?0:box.maxY-box.minY);
        out.addProperty("equipment",props.getEquipment().get(EntityEquipmentSlot.HEAD).getItem()==Items.DIAMOND_HELMET);
        Vec3d start=new Vec3d(pos).add(.5,.5,-.1),end=new Vec3d(pos).add(.5,.5,.95);
        props.getBody().setCameraCollision(false);out.addProperty("cameraIgnores",ModelCameraHooks.trace(world,start,end)==null);
        props.getBody().setCameraCollision(true);RayTraceResult trace=ModelCameraHooks.trace(world,start,end);out.addProperty("cameraCollides",trace!=null&&pos.equals(trace.getBlockPos()));
        props.getBody().setSolid(false);out.addProperty("nonSolid",state.getCollisionBoundingBox(world,pos)==null);
        out.addProperty("cameraIndependent",ModelCameraHooks.trace(world,start,end)!=null);props.getBody().setSolid(true);
        out.addProperty("ordinaryPick",world.rayTraceBlocks(start,end)!=null);out.addProperty("cameraScopeRestored",!ModelCameraHooks.isCameraTrace());
        EntityPlayerMP player=server.getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
        ItemStack held=player.getHeldItemMainhand();
        if(held.getItem()==net.minecraft.item.Item.getItemFromBlock(CommonProxy.MODEL_BLOCK))
        {
            ModelTileEntity item=new ModelTileEntity();item.readFromNBT(held.getTagCompound().getCompoundTag("BlockEntityTag"));
            out.addProperty("itemInventoryScale",item.getProperties().getTransformInventory().scale.x);
            out.addProperty("itemFirstPersonY",item.getProperties().getTransformFirstPerson().translate.y);
        }
        serverState=out;
    }
}
