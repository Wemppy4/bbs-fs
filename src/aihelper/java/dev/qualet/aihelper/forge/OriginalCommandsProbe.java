package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionManager;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forge.BBSCommand;
import mchorse.bbs_mod.forge.CommonProxy;
import mchorse.bbs_mod.forge.ModelTileEntity;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.states.StatePlayer;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSenderWrapper;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.ByteBuffer;

/** Real command dispatcher and client packet checks, restricted to the disposable ai_test world. */
public final class OriginalCommandsProbe
{
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static volatile int requested, completed;
    private static volatile String error, id;
    private static volatile boolean active, serverPlaying, serverActors;
    private static volatile JsonObject checks = new JsonObject();
    private static BlockPos position;
    private static IBlockState previousBlock;
    private static NBTTagCompound previousTile;
    private static Form previousMorph, refreshBefore;
    private static boolean refreshSeen;
    private static File overlayImage;
    private static boolean previousHideGui;
    private static net.minecraft.client.gui.GuiScreen previousScreen;
    private static final HudListener HUD = new HudListener();
    private static boolean watchingHud;
    private static ByteBuffer framePixels;

    public static JsonObject run(JsonObject request) throws Exception
    {
        if (MC.world == null || MC.player == null || MC.getIntegratedServer() == null || !"ai_test".equals(MC.getIntegratedServer().getFolderName()))
            throw new IllegalStateException("Command fixture requires ai_test");
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        if (!op.equals("status"))
        {
            if (requested != completed) throw new IllegalStateException("Wait for the preceding server command");
            if (op.equals("prepare"))
            {
                previousHideGui = MC.gameSettings.hideGUI; MC.gameSettings.hideGUI = false;
                previousScreen = MC.currentScreen; MC.displayGuiScreen(null);
                if (!watchingHud) { MinecraftForge.EVENT_BUS.register(HUD); watchingHud = true; }
                HUD.cameraHidden = HUD.worldVisible = 0;
            }
            if (op.equals("refresh")) { refreshBefore = clientForm(); refreshSeen = false; }
            final int task = ++requested;
            error = null;
            MC.getIntegratedServer().addScheduledTask(() ->
            {
                try { server(op); }
                catch (Throwable failure) { error = failure.toString(); failure.printStackTrace(); }
                finally { completed = task; }
            });
        }
        JsonObject out = new JsonObject();
        out.addProperty("ok", error == null); out.addProperty("requested", requested); out.addProperty("completed", completed);
        if (error != null) out.addProperty("error", error);
        out.add("checks", checks); out.addProperty("active", active); out.addProperty("film", id);
        out.addProperty("serverPlaying", serverPlaying); out.addProperty("serverActors", serverActors);
        out.addProperty("clientPlaying", id != null && BBSModClient.getFilms().has(id));
        Form client = clientForm();
        out.addProperty("clientModel", client != null && "command block".equals(client.name.get()));
        boolean playingState = false;
        if (client != null)
        {
            Field field = Form.class.getDeclaredField("statePlayers"); field.setAccessible(true);
            for (StatePlayer player : (List<StatePlayer>) field.get(client))
                if ("command state".equals(player.getState().customId.get())) playingState = true;
        }
        if (refreshBefore != null && client != null && client != refreshBefore) refreshSeen = true;
        out.addProperty("clientState", playingState); out.addProperty("clientRefreshed", refreshSeen);
        Morph morph = Morph.getMorph(MC.player);
        out.addProperty("clientMorph", morph != null && morph.getForm() != null && "command morph".equals(morph.getForm().name.get()));
        out.addProperty("clientDemorph", morph != null && morph.getForm() == null);
        out.addProperty("capturedModel", position != null && BBSRendering.capturedModelBlocks.stream().anyMatch(tile -> position.equals(tile.getPos())));
        boolean camera = BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController;
        out.addProperty("camera", camera); out.addProperty("cameraHudHidden", HUD.cameraHidden); out.addProperty("worldHudVisible", HUD.worldVisible);
        if (camera) pixels(out);
        if (op.equals("status") && requested == completed && !active && watchingHud)
        {
            MinecraftForge.EVENT_BUS.unregister(HUD); watchingHud = false; MC.gameSettings.hideGUI = previousHideGui;
            MC.displayGuiScreen(previousScreen);
        }
        return out;
    }

    private static Form clientForm()
    {
        TileEntity tile = position == null || MC.world == null ? null : MC.world.getTileEntity(position);
        return tile instanceof ModelTileEntity ? ((ModelTileEntity) tile).getProperties().getForm() : null;
    }

    private static ICommandSender sender(MinecraftServer server, EntityPlayerMP player, int permission)
    {
        return CommandSenderWrapper.create(server).withEntity(player, player.getPositionVector()).withPermissionLevel(permission).withSendCommandFeedback(false);
    }

    private static void execute(MinecraftServer server, ICommandSender sender, String command)
    {
        if (server.getCommandManager().executeCommand(sender, command) <= 0)
            throw new IllegalStateException("Native command failed: " + command);
    }

    private static void server(String op) throws Exception
    {
        MinecraftServer server = MC.getIntegratedServer();
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
        WorldServer world = player.getServerWorld();
        ICommandSender source = sender(server, player, 4);
        if (op.equals("prepare")) { prepare(server, player, source); return; }
        if (op.equals("cleanup") && !active) return;
        if (!active) throw new IllegalStateException("Prepare the command fixture first");
        if (op.equals("state")) execute(server, source, "bbs model_block play_state " + coords(position) + " \"command state\"");
        else if (op.equals("refresh")) execute(server, source, "bbs model_block refresh 0");
        else if (op.equals("play") || op.equals("camera"))
        {
            execute(server, source, "bbs films @a play \"" + id + "\" " + op.equals("camera"));
            serverPlaying = BBSMod.getActions().getPlayer(id) != null;
            Field actors = mchorse.bbs_mod.actions.ActionPlayer.class.getDeclaredField("actors"); actors.setAccessible(true);
            serverActors = serverPlaying && !((Map<?, ?>) actors.get(BBSMod.getActions().getPlayer(id))).isEmpty();
        }
        else if (op.equals("stop"))
        {
            execute(server, source, "bbs films @a stop \"" + id + "\"");
            serverPlaying = BBSMod.getActions().getPlayer(id) != null;
        }
        else if (op.equals("demorph")) execute(server, source, "bbs morph @a");
        else if (op.equals("cleanup"))
        {
            BBSMod.getActions().stop(id); ServerNetwork.sendStopFilm(player, id); serverPlaying = false;
            restore(world, position, previousBlock, previousTile);
            ServerNetwork.sendMorphToTracked(player, previousMorph);
            BBSMod.getFilms().delete(id);
            if (overlayImage != null) Files.deleteIfExists(overlayImage.toPath());
            active = false;
        }
        else throw new IllegalArgumentException("Unknown command fixture operation");
    }

    private static void prepare(MinecraftServer server, EntityPlayerMP player, ICommandSender source) throws Exception
    {
        if (active) throw new IllegalStateException("Cleanup the preceding command fixture");
        WorldServer world = player.getServerWorld();
        Field dc = ActionManager.class.getDeclaredField("dc"); dc.setAccessible(true);
        if (((Map<?, ?>) dc.get(BBSMod.getActions())).containsKey(world))
            throw new IllegalStateException("Do not replace active damage-control ownership");
        id = "command qa " + UUID.randomUUID().toString();
        position = new BlockPos(player.getPositionEyes(1F).add(player.getLookVec().scale(3D)));
        previousBlock = world.getBlockState(position);
        TileEntity old = world.getTileEntity(position);
        previousTile = old == null ? null : old.writeToNBT(new NBTTagCompound());
        previousMorph = FormUtils.copy(Morph.getMorph(player).getForm());
        active = true; checks = new JsonObject(); refreshBefore = null; refreshSeen = false;

        world.setBlockState(position, CommonProxy.MODEL_BLOCK.getDefaultState(), 3);
        BlockForm block = new BlockForm(); block.blockState.set(Blocks.GOLD_BLOCK.getDefaultState()); block.name.set("command block");
        block.states.addState().customId.set("command state");
        execute(server, source, "bbs set " + coords(position) + " " + DataToString.toString(FormUtils.toData(block), true));
        checks.addProperty("setForm", "command block".equals(((ModelTileEntity) world.getTileEntity(position)).getProperties().getForm().name.get()));
        BBSCommand command = (BBSCommand) server.getCommandManager().getCommands().get("bbs");
        checks.addProperty("stateCompletion", command.getTabCompletions(server, source, new String[]{"model_block", "play_state", "" + position.getX(), "" + position.getY(), "" + position.getZ(), "com"}, position).contains("command state"));
        checks.addProperty("permissionRejected", server.getCommandManager().executeCommand(sender(server, player, 0), "bbs morph @a") == 0);

        ItemStack held = player.getHeldItemMainhand().copy(), helmet = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).copy();
        float yaw = player.rotationYaw, headYaw = player.rotationYawHead, pitch = player.rotationPitch;
        EntityPig pig = null;
        try
        {
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            execute(server, source, "bbs on_head");
            checks.addProperty("onHead", player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() == Items.DIAMOND && player.getHeldItemMainhand().getItem() == Items.DIAMOND);
            /* Put the target in the player's immediate eye ray, above terrain and before any world block. */
            player.rotationYaw = player.rotationYawHead = 0F; player.rotationPitch = -30F;
            Vec3d ray = player.getPositionEyes(1F).add(player.getLook(1F).scale(1.5D));
            pig = new EntityPig(world); pig.setPosition(ray.x, ray.y - .4D, ray.z); pig.setNoAI(true); world.spawnEntity(pig);
            execute(server, source, "bbs morph_entity");
            Form captured = Morph.getMorph(player).getForm();
            checks.addProperty("capturedMob", captured instanceof MobForm ? ((MobForm) captured).mobID.get() : "none");
            checks.addProperty("morphEntity", captured instanceof MobForm && "minecraft:pig".equals(((MobForm) captured).mobID.get()));
        }
        finally
        {
            if (pig != null) world.removeEntity(pig);
            player.rotationYaw = yaw; player.rotationYawHead = headYaw; player.rotationPitch = pitch;
            player.setHeldItem(EnumHand.MAIN_HAND, held); player.setItemStackToSlot(EntityEquipmentSlot.HEAD, helmet); player.inventoryContainer.detectAndSendChanges();
        }
        checks.addProperty("equipmentRestored", ItemStack.areItemStacksEqual(held, player.getHeldItemMainhand()) && ItemStack.areItemStacksEqual(helmet, player.getItemStackFromSlot(EntityEquipmentSlot.HEAD)));
        block.name.set("command morph");
        execute(server, source, "bbs morph @a " + DataToString.toString(FormUtils.toData(block), true));
        checks.addProperty("morphServer", "command morph".equals(Morph.getMorph(player).getForm().name.get()));

        boolean damageSetting = BBSSettings.damageControl.get();
        try
        {
            execute(server, source, "bbs config set misc.damage_control " + !damageSetting);
            checks.addProperty("config", BBSSettings.damageControl.get() != damageSetting);
        }
        finally { BBSSettings.damageControl.set(damageSetting); BBSMod.getSettings().modules.get("bbs").saveLater(); }
        boolean cheats = world.getWorldInfo().areCommandsAllowed();
        try
        {
            execute(server, sender(server, player, 0), "bbs cheats false");
            checks.addProperty("cheatsOff", !world.getWorldInfo().areCommandsAllowed());
            execute(server, sender(server, player, 0), "bbs cheats true");
            checks.addProperty("cheatsOn", world.getWorldInfo().areCommandsAllowed());
        }
        finally { execute(server, source, "bbs cheats " + cheats); }

        BlockPos scratch = new BlockPos(player.posX + 7, 250, player.posZ + 7);
        IBlockState previous = world.getBlockState(scratch);
        TileEntity previousEntity = world.getTileEntity(scratch);
        NBTTagCompound previousData = previousEntity == null ? null : previousEntity.writeToNBT(new NBTTagCompound());
        File structure = BBSMod.getAssetsPath("structures/" + id + ".nbt");
        boolean holdingDamage = false;
        try
        {
            BBSSettings.damageControl.set(true);
            world.setBlockState(scratch, Blocks.STONE.getDefaultState(), 3);
            TileEntityCommandBlock commandBlock = new TileEntityCommandBlock(); commandBlock.setWorld(world); commandBlock.setPos(scratch);
            execute(server, commandBlock.getCommandBlockLogic(), "bbs dc start"); holdingDamage = true;
            world.setBlockState(scratch, Blocks.GOLD_BLOCK.getDefaultState(), 3);
            execute(server, source, "bbs dc stop"); holdingDamage = false;
            checks.addProperty("damageControlRestore", world.getBlockState(scratch).getBlock() == Blocks.STONE);
            execute(server, source, "bbs dc start"); holdingDamage = true;
            world.setBlockState(scratch, Blocks.DIAMOND_BLOCK.getDefaultState(), 3);
            execute(server, source, "bbs dc shutdown"); holdingDamage = false;
            checks.addProperty("damageControlShutdown", world.getBlockState(scratch).getBlock() == Blocks.STONE);
            /* Structure names are words in the original grammar. */
            String name = id.replace(' ', '_'); structure = BBSMod.getAssetsPath("structures/" + name + ".nbt");
            execute(server, source, "execute " + player.getName() + " ~ ~ ~ bbs structures save " + name + " " + coords(scratch) + " " + coords(scratch));
            try (FileInputStream input = new FileInputStream(structure))
            {
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(input);
                checks.addProperty("structureCommand", nbt.getTagList("blocks", 10).tagCount() == 1 && nbt.getTagList("palette", 10).getCompoundTagAt(0).getString("Name").equals("minecraft:stone"));
            }
            ExplosionListener observer = new ExplosionListener(); MinecraftForge.EVENT_BUS.register(observer);
            try { execute(server, source, "bbs boom " + scratch.getX() + " 245 " + scratch.getZ() + " 1 false"); checks.addProperty("explosionNative", observer.count == 1); }
            finally { MinecraftForge.EVENT_BUS.unregister(observer); }
        }
        finally
        {
            if (holdingDamage) BBSMod.getActions().resetDamage(world);
            BBSSettings.damageControl.set(damageSetting);
            restore(world, scratch, previous, previousData);
            Files.deleteIfExists(structure.toPath());
        }
        Film film = new Film(); film.setId(id); IdleClip idle = new IdleClip(); idle.duration.set(1200);
        idle.position.get().point.set(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        idle.position.get().angle.yaw = player.rotationYaw; idle.position.get().angle.pitch = player.rotationPitch;
        film.camera.addClip(idle);
        SubtitleClip subtitle = new SubtitleClip(); subtitle.title.set("BBS command subtitles"); subtitle.duration.set(1200); subtitle.layer.set(1);
        subtitle.color.set(0xffff00ff); subtitle.placement.get().scaleX = subtitle.placement.get().scaleY = 4;
        subtitle.placement.get().windowY = .15F; film.camera.addClip(subtitle);
        overlayImage = BBSMod.getAssetsPath("textures/" + id.replace(' ', '_') + ".png"); overlayImage.getParentFile().mkdirs();
        BufferedImage cyan = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) cyan.setRGB(x, y, 0xff00ffff);
        ImageIO.write(cyan, "png", overlayImage);
        ImageClip image = new ImageClip(); image.duration.set(1200); image.layer.set(2); image.texture.set(Link.assets("textures/" + overlayImage.getName()));
        image.placement.get().scaleX = image.placement.get().scaleY = 10; image.placement.get().windowY = .4F; film.camera.addClip(image);
        Replay replay = film.replays.addReplay(); replay.form.set(FormUtils.copy(block)); replay.actor.set(true);
        replay.keyframes.x.insert(0, player.posX + 3); replay.keyframes.y.insert(0, player.posY + 2); replay.keyframes.z.insert(0, player.posZ + 3);
        if (!BBSMod.getFilms().save(id, film.toData().asMap())) throw new IllegalStateException("Cannot persist command fixture film");
        checks.addProperty("filmCompletion", command.getTabCompletions(server, source, new String[]{"films", "@a", "play", "command"}, position).contains(id));
    }

    private static String coords(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }
    private static void restore(WorldServer world, BlockPos pos, IBlockState state, NBTTagCompound data)
    {
        world.setBlockState(pos, state, 3);
        if (data != null)
        {
            TileEntity tile = TileEntity.create(world, data);
            if (tile != null) world.setTileEntity(pos, tile);
        }
        world.notifyBlockUpdate(pos, state, state, 3);
    }
    public static final class ExplosionListener
    {
        int count;
        @SubscribeEvent public void explode(ExplosionEvent.Start event) { if (!event.getWorld().isRemote) count++; }
    }
    public static final class HudListener
    {
        int cameraHidden, worldVisible;
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void hud(RenderGameOverlayEvent.Pre event)
        {
            if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
            if (BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController)
            { if (event.isCanceled()) cameraHidden++; }
            else if (!event.isCanceled()) worldVisible++;
        }
    }
    private static void pixels(JsonObject out)
    {
        net.minecraft.client.shader.Framebuffer frame = MC.getFramebuffer();
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), magenta = 0, cyan = 0;
        int bytes = frame.framebufferTextureWidth * frame.framebufferTextureHeight * 4;
        if (framePixels == null || framePixels.capacity() != bytes) framePixels = BufferUtils.createByteBuffer(bytes);
        ByteBuffer pixels = framePixels; pixels.clear();
        GlStateManager.bindTexture(frame.framebufferTexture);
        try
        {
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            for (int i = 0; i < pixels.limit(); i += 4)
            {
                int r = pixels.get(i) & 255, g = pixels.get(i + 1) & 255, b = pixels.get(i + 2) & 255;
                if (r > 180 && g < 70 && b > 180) magenta++;
                if (r < 30 && g > 200 && b > 200) cyan++;
            }
        }
        finally { GlStateManager.bindTexture(previous); }
        out.addProperty("subtitlePixels", magenta); out.addProperty("imagePixels", cyan); out.addProperty("glError", GL11.glGetError());
    }
}
