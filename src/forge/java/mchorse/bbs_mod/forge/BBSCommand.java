package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.data.DataToString;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** A native server command; all world mutations happen on the server thread. */
public class BBSCommand extends CommandBase
{
    public String getName() { return "bbs"; }
    public String getUsage(ICommandSender sender) { return "/bbs model <id> [animation] | /bbs set <x> <y> <z> <form-json>"; }
    public int getRequiredPermissionLevel() { return 2; }
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 2 && args[0].equals("model")) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            BlockPos pos = player.getPosition().offset(player.getHorizontalFacing(), 3);
            if (!player.world.isAirBlock(pos)) throw new CommandException("BBS: placement position is occupied");
            player.world.setBlockState(pos, CommonProxy.MODEL_BLOCK.getDefaultState(), 3);
            ModelTileEntity tile = (ModelTileEntity) player.world.getTileEntity(pos);
            tile.form.model.set(args[1]);
            if (args.length > 2) tile.form.actions.get().actions.put("idle", new mchorse.bbs_mod.cubic.animation.ActionConfig(args[2]));
            tile.markDirty();
            player.world.notifyBlockUpdate(pos, tile.getBlockType().getDefaultState(), tile.getBlockType().getDefaultState(), 3);
            sender.sendMessage(new TextComponentString("BBS FS: model " + args[1] + " at " + pos.getX()+" "+pos.getY()+" "+pos.getZ()));
            return;
        }
        if (args.length >= 5 && args[0].equals("set")) {
            BlockPos pos = parseBlockPos(sender, args, 1, false);
            if (!(sender.getEntityWorld().getTileEntity(pos) instanceof ModelTileEntity)) throw new CommandException("BBS: no model block");
            ModelTileEntity tile = (ModelTileEntity) sender.getEntityWorld().getTileEntity(pos);
            try {
                ModelForm form = new ModelForm();
                form.fromData(DataToString.mapFromString(buildString(args, 4)));
                tile.form = form; tile.markDirty();
                sender.getEntityWorld().notifyBlockUpdate(pos, tile.getBlockType().getDefaultState(), tile.getBlockType().getDefaultState(), 3);
            } catch (Exception e) { throw new CommandException("BBS: invalid form: " + e.getMessage()); }
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }
}
