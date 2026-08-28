package mchorse.bbs_mod.video;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keeps one {@link VideoPlayer} per video file, mirroring how the sound manager
 * keeps one unique player per audio link.
 */
public class VideoManager
{
    /** Idle form decoders get their ffmpeg process shut down (the texture stays) */
    private static final long FORM_STOP_MS = 5_000;

    /** Long-abandoned form players (deleted forms) get disposed of entirely */
    private static final long FORM_DELETE_MS = 60_000;

    /** How often a missing/broken video file is given another chance */
    private static final long FORM_RETRY_MS = 3_000;

    private final Map<Link, VideoPlayer> players = new HashMap<>();

    /**
     * Per-owner players for video FORMS: unlike clips, two forms with the same
     * file can sit on different timestamps, so they cannot share a decoder.
     * Keyed by the owner's identity; idle entries are cleaned up in {@link #update()}
     * because nothing tells us when a form dies.
     */
    private final Map<Object, FormPlayerEntry> formPlayers = new IdentityHashMap<>();

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

    /**
     * A form's own player (see {@link #formPlayers}). Refreshes the entry's
     * last-use stamp; recreates the player when the form's file changed.
     */
    public VideoPlayer getFormPlayer(Object owner, Link link)
    {
        FormPlayerEntry entry = this.formPlayers.get(owner);
        long now = System.currentTimeMillis();

        /* A missing or broken file is retried once in a while - the file may
         * have appeared or been fixed since; without this the entry would sit
         * dead forever, because its lastUsed keeps refreshing. */
        boolean broken = entry != null && (entry.player == null || entry.player.isInvalid());
        boolean retry = broken && now - entry.createdAt > FORM_RETRY_MS;

        if (entry == null || !entry.link.equals(link) || retry)
        {
            if (entry != null && entry.player != null)
            {
                entry.player.delete();
            }

            File file = BBSMod.getProvider().getFile(link);
            VideoPlayer player = file != null && file.isFile() ? new VideoPlayer(file) : null;

            entry = new FormPlayerEntry(link, player);
            this.formPlayers.put(owner, entry);
        }

        entry.lastUsed = now;

        return entry.player;
    }

    /**
     * Once a tick: wind down decoders of forms that are no longer on screen.
     */
    public void update()
    {
        if (this.formPlayers.isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<FormPlayerEntry> it = this.formPlayers.values().iterator();

        while (it.hasNext())
        {
            FormPlayerEntry entry = it.next();

            if (now - entry.lastUsed > FORM_DELETE_MS)
            {
                if (entry.player != null)
                {
                    entry.player.delete();
                }

                it.remove();
            }
            else if (entry.player != null && now - entry.lastUsed > FORM_STOP_MS)
            {
                entry.player.stop();
            }
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

        for (FormPlayerEntry entry : this.formPlayers.values())
        {
            if (entry.player != null)
            {
                entry.player.delete();
            }
        }

        this.formPlayers.clear();
    }

    private static class FormPlayerEntry
    {
        public final Link link;
        public final VideoPlayer player;
        public final long createdAt = System.currentTimeMillis();
        public long lastUsed;

        public FormPlayerEntry(Link link, VideoPlayer player)
        {
            this.link = link;
            this.player = player;
        }
    }
}
