package mchorse.bbs_mod.utils;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

public final class PermissionUtils
{
    public static final String EDITING_RULE="bbsEditing";
    public static void register(MinecraftServer server)
    {
        GameRules rules=server.getWorld(0).getGameRules();
        if(!rules.hasRule(EDITING_RULE))rules.addGameRule(EDITING_RULE,"true",GameRules.ValueType.BOOLEAN_VALUE);
    }
    public static boolean arePanelsAllowed(MinecraftServer server,EntityPlayerMP player)
    {
        register(server);
        return server.getWorld(0).getGameRules().getBoolean(EDITING_RULE)||server.getPlayerList().canSendCommands(player.getGameProfile());
    }
    private PermissionUtils(){}
}
