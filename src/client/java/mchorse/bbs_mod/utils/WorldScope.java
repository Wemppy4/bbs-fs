package mchorse.bbs_mod.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.WorldSavePath;

/**
 * Which world the client is in, as a name that tells worlds apart: the save folder's name in
 * a single player world, the server's address otherwise, empty out of any world.
 *
 * <p>Documents kept in a world (films) are only unique inside it, so whatever points at one
 * from outside the world says which world it meant by this name.
 */
public class WorldScope
{
    public static String current()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getServer() != null)
        {
            return String.valueOf(mc.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName());
        }

        ServerInfo server = mc.getCurrentServerEntry();

        return server == null ? "" : server.address;
    }

    /**
     * Whether the given world is the one the client is in. An empty name (a clip made before
     * worlds were known) counts as this world.
     */
    public static boolean isCurrent(String world)
    {
        return world == null || world.isEmpty() || world.equals(current());
    }
}
