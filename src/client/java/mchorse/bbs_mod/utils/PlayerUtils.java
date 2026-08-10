package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.network.ClientNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class PlayerUtils
{
    public static void teleport(double x, double y, double z, float yaw, float pitch)
    {
        teleport(x, y, z, yaw, yaw, pitch);
    }

    public static void teleport(double x, double y, double z, float yaw, float bodyYaw, float pitch)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (!ClientNetwork.isIsBBSModOnServer())
        {
            String command = "tp " + player.getGameProfile().name() + " " + x + " " + y + " " + z + " " + yaw + " " + pitch;

            player.networkHandler.sendChatCommand(command);
        }
        else
        {
            ClientNetwork.sendTeleport(x, y, z, yaw, bodyYaw, pitch);
            player.setYaw(yaw);
            player.setHeadYaw(yaw);
            player.setBodyYaw(bodyYaw);
            player.setPitch(pitch);
        }
    }

    public static void teleport(double x, double y, double z)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (!ClientNetwork.isIsBBSModOnServer())
        {
            player.networkHandler.sendChatCommand("tp " + player.getGameProfile().name() + " " + x + " " + y + " " + z);
        }
        else
        {
            ClientNetwork.sendTeleport(player, x, y, z);
        }
    }

}