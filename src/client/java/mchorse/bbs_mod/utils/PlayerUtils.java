package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.network.ClientNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Vector3d;

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

    /**
     * Put the player exactly where a replay stands at the given tick &mdash; position,
     * yaw, head and body yaw, pitch. This is what the film editor's teleport key does,
     * and what recording in the world does on its own when
     * {@link mchorse.bbs_mod.BBSSettings#recordingTeleport} is on, so the two can never
     * drift apart.
     *
     * <p>A replay that has no transform keyframes at all is left alone: its channels
     * read {@code 0} everywhere, and teleporting would drop the player at the world
     * origin instead of doing nothing.</p>
     *
     * @return the spot the player was sent to, or {@code null} if nothing was done.
     *         The teleport itself is a round trip through the server, so the player
     *         is <em>not</em> there yet when this returns
     */
    public static Vector3d teleportToReplay(Replay replay, int tick)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ReplayKeyframes keyframes = replay == null ? null : replay.keyframes;

        if (player == null || keyframes == null || (keyframes.x.isEmpty() && keyframes.y.isEmpty() && keyframes.z.isEmpty()))
        {
            return null;
        }

        /* Through the replay's own clock, so a looping replay hands over the
         * spot that's actually on screen at that tick */
        int replayTick = replay.getTick(tick);
        double x = keyframes.x.interpolate(replayTick);
        double y = keyframes.y.interpolate(replayTick);
        double z = keyframes.z.interpolate(replayTick);
        float yaw = keyframes.yaw.interpolate(replayTick).floatValue();
        float headYaw = keyframes.headYaw.interpolate(replayTick).floatValue();
        float bodyYaw = keyframes.bodyYaw.interpolate(replayTick).floatValue();
        float pitch = keyframes.pitch.interpolate(replayTick).floatValue();

        teleport(x, y, z, headYaw, pitch);

        player.setYaw(yaw);
        player.setHeadYaw(headYaw);
        player.setBodyYaw(bodyYaw);
        player.setPitch(pitch);

        return new Vector3d(x, y, z);
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