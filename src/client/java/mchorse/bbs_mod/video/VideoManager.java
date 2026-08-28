package mchorse.bbs_mod.video;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps one {@link VideoPlayer} per video file, mirroring how the sound manager
 * keeps one unique player per audio link.
 */
public class VideoManager
{
    private final Map<Link, VideoPlayer> players = new HashMap<>();

    public VideoPlayer get(Link link)
    {
        if (!this.players.containsKey(link))
        {
            File file = BBSMod.getProvider().getFile(link);
            VideoPlayer player = null;

            if (file != null && file.isFile())
            {
                player = new VideoPlayer(file);
            }

            this.players.put(link, player);

            return player;
        }

        return this.players.get(link);
    }

    /**
     * Frame at given time, or null when the video is missing or undecodable.
     */
    public Texture getFrame(Link link, float seconds)
    {
        VideoPlayer player = this.get(link);

        return player == null ? null : player.getFrame(seconds);
    }

    /**
     * Kill the link's decoding process (keeps the player and its last frame).
     */
    public void stop(Link link)
    {
        VideoPlayer player = this.players.get(link);

        if (player != null)
        {
            player.stop();
        }
    }

    public void delete()
    {
        for (VideoPlayer player : this.players.values())
        {
            if (player != null)
            {
                player.delete();
            }
        }

        this.players.clear();
    }
}
