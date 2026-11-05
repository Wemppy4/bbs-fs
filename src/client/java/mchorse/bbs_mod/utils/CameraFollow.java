package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.network.ClientNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * "Player follows the camera": the player is kept where the film's (or sequence's) camera is,
 * so the world around the shot stays loaded. The player's EYES go to the camera, not the feet:
 * with the feet there, a camera going through a doorway put the head in the wall above it.
 *
 * <p>With {@link BBSSettings#editorPlayerFollowsSpectator} on, the player is a spectator for as
 * long as it follows, so it never gets stuck in blocks at all. The server switches the mode by
 * itself (no command, so nothing is said in the chat) and gives the old mode back when the
 * following stops or the player leaves.</p>
 */
public class CameraFollow
{
    private static boolean spectating;

    /**
     * Put the player's eyes where the camera is, looking where it looks.
     */
    public static void teleportToCamera(Camera camera)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return;
        }

        double y = camera.position.y - player.getStandingEyeHeight();

        PlayerUtils.teleport(camera.position.x, y, camera.position.z, MathUtils.toDeg(camera.rotation.y) - 180F, MathUtils.toDeg(camera.rotation.x));
    }

    /**
     * One tick of following: to the camera, as a spectator if that is asked for.
     */
    public static void follow(Camera camera)
    {
        setSpectating(BBSSettings.editorPlayerFollowsSpectator.get());
        teleportToCamera(camera);
    }

    /**
     * Not following (anymore): the player gets its own game mode back.
     */
    public static void stop()
    {
        setSpectating(false);
    }

    /** The game was left: the server gives the mode back by itself. */
    public static void reset()
    {
        spectating = false;
    }

    private static void setSpectating(boolean value)
    {
        /* Without BBS on the server only a command could do it, and a command talks in the chat */
        if (spectating == value || !ClientNetwork.isIsBBSModOnServer())
        {
            return;
        }

        spectating = value;
        ClientNetwork.sendFollowSpectator(value);
    }
}
