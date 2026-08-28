package mchorse.bbs_mod.video;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hands out {@link VideoPlayer}s: one per PLAYING OWNER (a form renderer, a clip's
 * overlay, a UI preview), plus a link-keyed cache used only for metadata.
 */
public class VideoManager
{
    /** Idle decoders get their ffmpeg process shut down (the texture stays) */
    private static final long STOP_MS = 5_000;

    /** Long-abandoned players (deleted forms, removed clips) get disposed of entirely */
    private static final long DELETE_MS = 60_000;

    /** How often a missing/broken video file is given another chance */
    private static final long RETRY_MS = 3_000;

    /**
     * Metadata-only cache (duration, size, frame rate): nothing decodes through it,
     * so two owners of the same file never meet here.
     */
    private final Map<Link, VideoPlayer> players = new HashMap<>();

    /**
     * The players that actually decode. Two owners of the same file sit on different
     * timestamps, and a shared decoder serves neither: the alternating targets never
     * settle, so the frame never arrives at all. Keyed by the owner's identity; idle
     * entries are cleaned up in {@link #update()} because nothing tells us when an
     * owner dies.
     */
    private final Map<Object, PlayerEntry> ownedPlayers = new IdentityHashMap<>();

    /**
     * The link's metadata player - for code that only needs the duration or the size.
     * Never ask it for frames: use {@link #getPlayer(Object, Link)} for that.
     */
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
     * An owner's own player (see {@link #ownedPlayers}). Refreshes the entry's
     * last-use stamp; recreates the player when the owner's file changed.
     */
    public VideoPlayer getPlayer(Object owner, Link link)
    {
        PlayerEntry entry = this.ownedPlayers.get(owner);
        long now = System.currentTimeMillis();

        /* A missing or broken file is retried once in a while - the file may
         * have appeared or been fixed since; without this the entry would sit
         * dead forever, because its lastUsed keeps refreshing. */
        boolean broken = entry != null && (entry.player == null || entry.player.isInvalid());
        boolean retry = broken && now - entry.createdAt > RETRY_MS;

        if (entry == null || !entry.link.equals(link) || retry)
        {
            if (entry != null && entry.player != null)
            {
                entry.player.delete();
            }

            File file = BBSMod.getProvider().getFile(link);
            VideoPlayer player = file != null && file.isFile() ? new VideoPlayer(file) : null;

            entry = new PlayerEntry(link, player);
            this.ownedPlayers.put(owner, entry);
        }

        entry.lastUsed = now;

        return entry.player;
    }

    /**
     * Dispose of an owner's player right away, for the owners whose death IS
     * observable (a clip's context shutting down).
     */
    public void release(Object owner)
    {
        PlayerEntry entry = this.ownedPlayers.remove(owner);

        if (entry != null && entry.player != null)
        {
            entry.player.delete();
        }
    }

    /**
     * Once a tick: wind down decoders of owners that are no longer on screen.
     */
    public void update()
    {
        if (this.ownedPlayers.isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<PlayerEntry> it = this.ownedPlayers.values().iterator();

        while (it.hasNext())
        {
            PlayerEntry entry = it.next();

            if (now - entry.lastUsed > DELETE_MS)
            {
                if (entry.player != null)
                {
                    entry.player.delete();
                }

                it.remove();
            }
            else if (entry.player != null && now - entry.lastUsed > STOP_MS)
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

        for (PlayerEntry entry : this.ownedPlayers.values())
        {
            if (entry.player != null)
            {
                entry.player.delete();
            }
        }

        this.ownedPlayers.clear();
    }

    private static class PlayerEntry
    {
        public final Link link;
        public final VideoPlayer player;
        public final long createdAt = System.currentTimeMillis();
        public long lastUsed;

        public PlayerEntry(Link link, VideoPlayer player)
        {
            this.link = link;
            this.player = player;
        }
    }
}
