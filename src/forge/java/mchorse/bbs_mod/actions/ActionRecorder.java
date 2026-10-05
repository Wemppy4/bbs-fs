package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.SwipeActionClip;
import mchorse.bbs_mod.actions.types.blocks.InteractBlockActionClip;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clips;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;

public class ActionRecorder
{
    private Film film;
    private EntityPlayerMP entity;
    private WorldServer world;
    private Clips clips = new Clips("...", BBSMod.getFactoryActionClips());
    private int tick;
    private int countdown;
    private int initialTick;

    /** The use clip still being held down, so its duration can keep growing. */
    private UseItemActionClip useClip;

    /** The last block interaction, until it's known whether it opened a container. */
    private InteractBlockActionClip interactClip;

    public ActionRecorder(Film film, EntityPlayerMP entity, int tick, int countdown)
    {
        this.film = film;
        this.entity = entity;
        /* Remembered rather than asked for later: the take is held against the world it started
         * in, and a player who walks through a portal mid-recording must not leave that hold
         * behind in a world nobody will release. */
        this.world = entity.getServerWorld();
        this.tick = tick;
        this.countdown = countdown;
        this.initialTick = tick;
    }

    public Film getFilm()
    {
        return this.film;
    }

    public WorldServer getWorld()
    {
        return this.world;
    }

    public Clips getClips()
    {
        return this.clips;
    }

    public int getInitialTick()
    {
        return this.initialTick;
    }

    public Clips composeClips()
    {
        Clips clips = this.clips;

        clips.sortLayers();

        return clips;
    }

    public void add(ActionClip clip)
    {
        if (this.countdown > 0)
        {
            return;
        }

        clip.tick.set(this.tick);
        clip.duration.set(1);

        this.clips.addClip(clip);

        if (clip instanceof UseItemActionClip useClip)
        {
            this.useClip = useClip;
        }
        else if (clip instanceof InteractBlockActionClip interactClip)
        {
            this.interactClip = interactClip;
        }
    }

    public void tick(EntityPlayerMP player)
    {
        if (this.countdown > 0)
        {
            this.countdown -= 1;

            return;
        }

        this.trackItemUse(player);
        this.trackContainer(player);

        /* ServerTick.END runs after vanilla advances a newly received swing from
         * -1 to 0. Match the 1.12 animation start, excluding idle and sleeping. */
        if (player.isSwingInProgress && player.swingProgress == 0F && !player.isPlayerSleeping())
        {
            this.add(new SwipeActionClip());

            if (BBSSettings.recordingSwipeDamage.get())
            {
                AttackActionClip clip = new AttackActionClip();

                clip.damage.set(2F);
                this.add(clip);
            }
        }

        this.tick += 1;
    }

    /**
     * Grows the last "use item" clip for as long as the player keeps holding the
     * item up: the clip is born when {@code ItemStack.use} fires and would
     * otherwise stay one tick long, so a shield held for three seconds or a bow
     * drawn and held would play back as a blink. Everything that reads the use
     * out of a take reads exactly this duration.
     */
    private void trackItemUse(EntityPlayerMP player)
    {
        if (this.useClip == null)
        {
            return;
        }

        boolean sameHand = player.getActiveHand() == (this.useClip.hand.get() ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND);

        if (player.isHandActive() && sameHand)
        {
            this.useClip.duration.set(Math.max(1, this.tick - this.useClip.tick.get() + 1));
        }
        else
        {
            this.useClip = null;
        }
    }

    /**
     * Grows the last block interaction for as long as the container it opened
     * stays open (LUCKYWAY). The clip is born inside the interaction itself,
     * before the screen it leads to is up, so the first tick after it is the
     * one that knows whether there was a container at all - and from then on
     * the clip is exactly as long as the player kept the chest open. Playback
     * holds the lid up for precisely this stretch.
     */
    private void trackContainer(EntityPlayerMP player)
    {
        if (this.interactClip == null)
        {
            return;
        }

        if (player.openContainer != player.inventoryContainer)
        {
            this.interactClip.duration.set(Math.max(1, this.tick - this.interactClip.tick.get() + 1));
        }
        else
        {
            this.interactClip = null;
        }
    }
}
