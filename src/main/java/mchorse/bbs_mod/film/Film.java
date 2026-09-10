package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.migration.FilmStableIds;
import mchorse.bbs_mod.data.migration.SaveVersion;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.Replays;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.numeric.ValueLong;
import mchorse.bbs_mod.utils.categories.Categories;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class Film extends ValueGroup
{
    public final Clips camera = new Clips("camera", BBSMod.getFactoryCameraClips());
    public final Replays replays = new Replays("replays");
    /**
     * The replay list's folders: their order, whether they are open, and the empty ones that
     * would otherwise not exist at all. What folder a replay is in is on the replay
     * ({@link Replay#category}), so this list is free to know nothing about most of them.
     */
    public final Categories replayCategories = new Categories("replay_categories");

    /** Author's notes pinned to ticks, drawn on every timeline's ruler. */
    public final FilmMarkers markers = new FilmMarkers("markers");

    public final ValueFloat hp = new ValueFloat("hp", 20F);
    public final ValueFloat hunger = new ValueFloat("hunger", 20F);
    public final ValueInt xpLevel = new ValueInt("xp_level", 0);
    public final ValueFloat xpProgress = new ValueFloat("xp_progress", 0F);

    /**
     * Radius (in blocks) around the player within which nearby mobs are captured
     * into separate replays when recording a player replay (Right Alt). {@code 0} disables it.
     */
    public final ValueFloat mobRecordingRadius = new ValueFloat("mob_recording_radius", 0F);

    public final ValueString description = new ValueString("description", "");
    /** UTC instant as ISO-8601 ({@link Instant#toString()}), set when the film is first created. */
    public final ValueString createdAt = new ValueString("created_at", "");
    /** Time spent editing with recent input (excludes AFK idle in the film editor). */
    public final ValueLong timeSpentActive = new ValueLong("time_spent_active", 0L);

    public Film()
    {
        super("");

        /* The server drives the actors from the replays — their keyframes, properties, action
         * clips and flags — so any edit in that subtree has to reach its copy of the film. One
         * declaration on the subtree replaces the hand-written list of path endings that used to
         * decide this (and kept falling behind as channels were added). The camera stays
         * client-side: the server never plays it, and saving ships the whole film anyway. */
        this.replays.synced();

        this.add(this.camera);
        this.add(this.replays);
        this.add(this.replayCategories);
        this.add(this.markers);

        this.add(this.hp);
        this.add(this.hunger);
        this.add(this.xpLevel);
        this.add(this.xpProgress);
        this.add(this.mobRecordingRadius);

        this.add(this.description);
        this.add(this.createdAt);
        this.add(this.timeSpentActive);
    }

    @Override
    public void fromData(BaseType data)
    {
        /* Imports, clipboard data and network repositories can bypass BaseManager.load().
         * Resolve legacy references before ValueStableList assigns ids and properties are read. */
        if (data.isMap() && SaveVersion.read(data.asMap()) < 2)
        {
            new FilmStableIds().migrate(data.asMap());
        }

        super.fromData(data);

        FilmLegacy.migrateHotbar(this, data);
    }

    public void stampCreationTimeNow()
    {
        this.createdAt.set(Instant.now().toString());
    }

    /**
     * @return Localized date/time for UI, or {@code null} if missing/legacy films without {@link #createdAt}.
     */
    public static String formatCreatedAtForDisplay(String isoUtc)
    {
        if (isoUtc == null || isoUtc.isEmpty())
        {
            return null;
        }

        try
        {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .format(Instant.parse(isoUtc).atZone(ZoneId.systemDefault()));
        }
        catch (DateTimeException e)
        {
            return isoUtc;
        }
    }

    public Replay getFirstPersonReplay()
    {
        for (Replay replay : this.replays.getList())
        {
            if (replay.fp.get())
            {
                return replay;
            }
        }

        return null;
    }

    public boolean hasFirstPerson()
    {
        return this.getFirstPersonReplay() != null;
    }

    /**
     * How long this film actually runs: the camera's length, or the last keyframe of any enabled
     * replay when a take outlives the shot.
     *
     * <p>Playback used to end at the camera's duration alone, and ending a playback discards its
     * actors &mdash; a scene authored longer than its camera lost its bodies mid-take. The camera
     * still decides what is <em>shown</em>; this decides how long the world keeps acting.
     */
    public int calculateDuration()
    {
        int duration = this.camera.calculateDuration();

        for (Replay replay : this.replays.getList())
        {
            if (!replay.enabled.get())
            {
                continue;
            }

            for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
            {
                duration = Math.max(duration, (int) channel.getLength() + 1);
            }
        }

        return duration;
    }
}
