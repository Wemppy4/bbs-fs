package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.structure.StructureOperations;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The original BBS command tree on Forge's native server command dispatcher. */
public class BBSCommand extends CommandBase
{
    @Override public String getName() { return "bbs"; }
    @Override public String getUsage(ICommandSender sender)
    {
        return "/bbs films|morph|morph_entity|model_block|structures|dc|on_head|config|cheats|boom|model|set";
    }

    /* The original root is visible without cheats; permissions belong to its branches. */
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length == 0) throw new WrongUsageException(this.getUsage(sender));
        String command = args[0];

        if (command.equals("cheats"))
        {
            if (server.isDedicatedServer() || args.length != 2) throw new WrongUsageException("/bbs cheats <true|false>");
            boolean enabled = parseBoolean(args[1]);
            server.getWorld(0).getWorldInfo().setAllowCommands(enabled);
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) ServerNetwork.sendCheatsPermission(player, enabled);
            server.saveAllWorlds(false);
            return;
        }
        if (command.equals("structures"))
        {
            if (args.length != 9 || !args[1].equals("save"))
                throw new WrongUsageException("/bbs structures save <name> <x1> <y1> <z1> <x2> <y2> <z2>");
            if (!StructureOperations.save(world(sender), args[2], parseBlockPos(sender, args, 3, false), parseBlockPos(sender, args, 6, false)))
                throw new CommandException("BBS: could not save structure %s", args[2]);
            return;
        }
        if (!sender.canUseCommand(2, this.getName())) throw new CommandException("commands.generic.permission");

        switch (command)
        {
            case "morph":
                if (args.length < 2) throw new WrongUsageException("/bbs morph <targets> [form]");
                Form form = args.length == 2 ? null : parseForm(buildString(args, 2));
                for (EntityPlayerMP player : getPlayers(server, sender, args[1])) ServerNetwork.sendMorphToTracked(player, form);
                return;
            case "morph_entity":
                if (args.length != 1) throw new WrongUsageException("/bbs morph_entity");
                EntityPlayerMP target = getCommandSenderAsPlayer(sender);
                Form mob = Morph.getMobForm(target);
                if (mob != null) ServerNetwork.sendMorphToTracked(target, mob);
                return;
            case "films": this.films(server, sender, args); return;
            case "model_block": this.modelBlock(server, sender, args); return;
            case "dc":
                if (args.length != 2) throw new WrongUsageException("/bbs dc <start|stop|shutdown>");
                if (args[1].equals("start")) BBSMod.getActions().trackDamage(world(sender));
                else if (args[1].equals("stop")) BBSMod.getActions().stopDamage(world(sender));
                else if (args[1].equals("shutdown")) BBSMod.getActions().resetDamage(world(sender));
                else throw new WrongUsageException("/bbs dc <start|stop|shutdown>");
                return;
            case "on_head":
                if (args.length != 1) throw new WrongUsageException("/bbs on_head");
                if (sender.getCommandSenderEntity() instanceof EntityLivingBase)
                {
                    EntityLivingBase living = (EntityLivingBase) sender.getCommandSenderEntity();
                    ItemStack stack = living.getHeldItemMainhand();
                    if (!stack.isEmpty()) living.setItemStackToSlot(EntityEquipmentSlot.HEAD, stack.copy());
                }
                return;
            case "config": this.config(args); return;
            case "boom":
                if (args.length != 6) throw new WrongUsageException("/bbs boom <x> <y> <z> <radius> <fire>");
                Vec3d at = sender.getPositionVector();
                world(sender).newExplosion(null,
                    parseCoordinate(at.x, args[1], true).getResult(),
                    parseCoordinate(at.y, args[2], false).getResult(),
                    parseCoordinate(at.z, args[3], true).getResult(),
                    (float) parseDouble(args[4], 1D), parseBoolean(args[5]), true);
                return;
            case "model": this.placeModel(sender, args); return;
            case "set":
                if (args.length < 5) throw new WrongUsageException("/bbs set <x> <y> <z> <form>");
                BlockPos pos = parseBlockPos(sender, args, 1, false);
                ModelTileEntity tile = modelBlock(sender, pos);
                tile.getProperties().setForm(parseForm(buildString(args, 4)));
                tile.updateForm(tile.getProperties().toData(), sender.getEntityWorld());
                return;
            default: throw new WrongUsageException(this.getUsage(sender));
        }
    }

    private void films(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 4 || !(args[2].equals("play") || args[2].equals("stop")))
            throw new WrongUsageException("/bbs films <targets> <play|stop> <film> [camera]");
        StringArgument film = string(args, 3);
        boolean play = args[2].equals("play");
        if (args.length > film.next + (play ? 1 : 0))
            throw new WrongUsageException("/bbs films <targets> <play|stop> <film> [camera]");
        boolean camera = film.next == args.length || parseBoolean(args[film.next]);
        List<EntityPlayerMP> targets = getPlayers(server, sender, args[1]);
        if (play)
        {
            if (!BBSMod.getFilms().exists(film.value)) throw new CommandException("BBS: no film %s", film.value);
            for (EntityPlayerMP target : targets) ServerNetwork.sendPlayFilm(target, film.value, camera);
        }
        else
        {
            BBSMod.getActions().stop(film.value);
            for (EntityPlayerMP target : targets) ServerNetwork.sendStopFilm(target, film.value);
        }
    }

    private void modelBlock(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length == 3 && args[1].equals("refresh"))
        {
            int range = parseInt(args[2]);
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) ServerNetwork.sendReloadModelBlocks(player, range);
            return;
        }
        if (args.length >= 6 && args[1].equals("play_state"))
        {
            BlockPos pos = parseBlockPos(sender, args, 2, false);
            modelBlock(sender, pos);
            StringArgument state = string(args, 5);
            if (state.next != args.length) throw new WrongUsageException("/bbs model_block play_state <x> <y> <z> <state>");
            for (EntityPlayer player : sender.getEntityWorld().playerEntities)
            {
                /* Original command uses squared block-position distance <= 64. */
                if (player instanceof EntityPlayerMP && player.getPosition().distanceSq(pos) <= 64D)
                    ServerNetwork.sendModelBlockState((EntityPlayerMP) player, pos, state.value);
            }
            return;
        }
        throw new WrongUsageException("/bbs model_block play_state <x> <y> <z> <state> | /bbs model_block refresh <random_range>");
    }

    private void config(String[] args) throws CommandException
    {
        if (args.length < 4 || !args[1].equals("set")) throw new WrongUsageException("/bbs config set <category.option> <value>");
        Settings settings = BBSMod.getSettings().modules.get("bbs");
        String[] path = args[2].split("\\.");
        BaseValue value = settings == null || path.length < 2 ? null : settings.get(path[0], path[1]);
        if (value == null) throw new CommandException("BBS: unknown setting %s", args[2]);
        try
        {
            BaseType data = DataToString.fromString(buildString(args, 3));
            if (data == null) throw new IllegalArgumentException("Empty value");
            value.fromData(data);
            settings.saveLater();
        }
        catch (RuntimeException error) { throw new CommandException("BBS: invalid value: %s", error.getMessage()); }
    }

    private void placeModel(ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 2 || args.length > 3) throw new WrongUsageException("/bbs model <id> [animation]");
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        BlockPos pos = player.getPosition().offset(player.getHorizontalFacing(), 3);
        if (!player.world.isAirBlock(pos)) throw new CommandException("BBS: placement position is occupied");
        player.world.setBlockState(pos, CommonProxy.MODEL_BLOCK.getDefaultState(), 3);
        ModelTileEntity tile = modelBlock(sender, pos);
        ModelForm form = new ModelForm();
        form.model.set(args[1]);
        if (args.length > 2) form.actions.get().actions.put("idle", new mchorse.bbs_mod.cubic.animation.ActionConfig(args[2]));
        tile.getProperties().setForm(form);
        tile.updateForm(tile.getProperties().toData(), player.world);
        sender.sendMessage(new TextComponentString("BBS FS: model " + args[1] + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
    }

    private static ModelTileEntity modelBlock(ICommandSender sender, BlockPos pos) throws CommandException
    {
        TileEntity tile = sender.getEntityWorld().getTileEntity(pos);
        if (!(tile instanceof ModelTileEntity)) throw new CommandException("BBS: no model block at %s", pos);
        return (ModelTileEntity) tile;
    }

    private static WorldServer world(ICommandSender sender) throws CommandException
    {
        if (!(sender.getEntityWorld() instanceof WorldServer)) throw new CommandException("BBS: server world required");
        return (WorldServer) sender.getEntityWorld();
    }

    private static Form parseForm(String data) throws CommandException
    {
        try
        {
            Form form = FormUtils.fromData(DataToString.mapFromString(data));
            if (form == null) throw new IllegalArgumentException("Unknown form type");
            return form;
        }
        catch (RuntimeException error) { throw new CommandException("BBS: invalid form: %s", error.getMessage()); }
    }

    /** Native commands split on spaces, so preserve Brigadier's quoted string arguments. */
    private static StringArgument string(String[] args, int start) throws CommandException
    {
        if (!args[start].startsWith("\"")) return new StringArgument(args[start], start + 1);
        StringBuilder quoted = new StringBuilder();
        for (int i = start; i < args.length; i++)
        {
            if (i > start) quoted.append(' ');
            quoted.append(args[i]);
            int last = quoted.length() - 1;
            if (last > 0 && quoted.charAt(last) == '"')
            {
                int slashes = 0;
                for (int j = last - 1; j >= 0 && quoted.charAt(j) == '\\'; j--) slashes++;
                if ((slashes & 1) == 0)
                {
                    try { return new StringArgument(new com.google.gson.JsonParser().parse(quoted.toString()).getAsString(), i + 1); }
                    catch (RuntimeException error) { throw new CommandException("BBS: invalid quoted string"); }
                }
            }
        }
        throw new CommandException("BBS: unclosed quoted string");
    }

    private static final class StringArgument
    {
        final String value;
        final int next;
        StringArgument(String value, int next) { this.value = value; this.next = next; }
    }

    @Override public boolean isUsernameIndex(String[] args, int index)
    {
        return index == 1 && args.length > 0 && (args[0].equals("morph") || args[0].equals("films"));
    }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos)
    {
        if (args.length == 1)
        {
            List<String> commands = new ArrayList<>();
            commands.add("structures");
            if (!server.isDedicatedServer()) commands.add("cheats");
            if (sender.canUseCommand(2, this.getName()))
                Collections.addAll(commands, "films", "morph", "morph_entity", "model_block", "dc", "on_head", "config", "boom", "model", "set");
            return getListOfStringsMatchingLastWord(args, commands);
        }
        if (args.length == 2 && (args[0].equals("morph") || args[0].equals("films")))
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        if (args[0].equals("films"))
        {
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, "play", "stop");
            if (args.length == 4) return getListOfStringsMatchingLastWord(args, BBSMod.getFilms().getKeys());
            if (args.length == 5 && args[2].equals("play")) return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if (args[0].equals("dc") && args.length == 2) return getListOfStringsMatchingLastWord(args, "start", "stop", "shutdown");
        if (args[0].equals("cheats") && args.length == 2 || args[0].equals("boom") && args.length == 6)
            return getListOfStringsMatchingLastWord(args, "true", "false");
        if (args[0].equals("structures"))
        {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, "save");
            if (args.length >= 4 && args.length <= 6) return getTabCompletionCoordinate(args, 3, targetPos);
            if (args.length >= 7 && args.length <= 9) return getTabCompletionCoordinate(args, 6, targetPos);
        }
        if (args[0].equals("set") && args.length >= 2 && args.length <= 4) return getTabCompletionCoordinate(args, 1, targetPos);
        if (args[0].equals("boom") && args.length >= 2 && args.length <= 4) return getTabCompletionCoordinate(args, 1, targetPos);
        if (args[0].equals("model_block"))
        {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, "play_state", "refresh");
            if (args[1].equals("play_state"))
            {
                if (args.length >= 3 && args.length <= 5) return getTabCompletionCoordinate(args, 2, targetPos);
                if (args.length == 6)
                {
                    try
                    {
                        Form form = modelBlock(sender, parseBlockPos(sender, args, 2, false)).getProperties().getForm();
                        List<String> names = new ArrayList<>();
                        if (form != null) for (AnimationState state : form.states.getAllTyped())
                            names.add(state.customId.get().trim().isEmpty() ? state.id.get() : state.customId.get());
                        return getListOfStringsMatchingLastWord(args, names);
                    }
                    catch (CommandException ignored) { return Collections.emptyList(); }
                }
            }
        }
        if (args[0].equals("config"))
        {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, "set");
            if (args.length == 3 && args[1].equals("set"))
            {
                List<String> names = new ArrayList<>();
                Settings settings = BBSMod.getSettings().modules.get("bbs");
                if (settings != null) for (ValueGroup group : settings.categories.values())
                    for (BaseValue value : group.getAll()) names.add(group.getId() + "." + value.getId());
                return getListOfStringsMatchingLastWord(args, names);
            }
        }
        return Collections.emptyList();
    }
}
