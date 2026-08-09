package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.Replays;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.numeric.ValueLong;
import mchorse.bbs_mod.utils.clips.Clips;

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
     * Names of replay categories that exist even with no replay assigned (empty groups).
     * Union with {@link Replay#category} on each replay defines all categories in the UI.
     */
    public final ValueStringKeys replayCategoryNames = new ValueStringKeys("replay_categories");

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

        this.add(this.camera);
        this.add(this.replays);
        this.add(this.replayCategoryNames);

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
}