package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public class PermissionUtils
{
    public static boolean arePanelsAllowed(MinecraftServer server, ServerPlayerEntity player)
    {
        boolean rule = server.getOverworld().getGameRules().getValue(BBSMod.BBS_EDITING_RULE);
        boolean allowed = rule || server.getPlayerManager().isOperator(new PlayerConfigEntry(player.getGameProfile()));

        return allowed;
    }
}