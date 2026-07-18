package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstanceListener;
import net.minecraft.client.sound.SoundListenerTransform;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Captures Minecraft sounds (footsteps, digging, mobs, weather, etc.) played while a
 * video export is recording, so they can be mixed into the exported audio track
 * afterwards by {@link MinecraftSoundMixer}.
 *
 * <p>Registered as a vanilla {@link SoundInstanceListener} for the duration of the
 * recording, so it sees every sound the player would hear. Vanilla notifies the
 * listeners after the category volume cull but before the master volume check and
 * before the OpenAL source is created, so capturing works even with muted speakers.
 * Background music and UI clicks are deliberately skipped - they aren't part of the
 * scene.
 *
 * <p>Timing comes from {@link #captureFrame()}, which must be called once per recorded
 * frame: sounds are stamped with the index of the upcoming frame (game ticks - and with
 * them all sound events - happen between recorded frames), and the sound listener's
 * transform is sampled per frame for the mixer's distance/pan calculations. Looping
 * sounds with dynamic state (minecarts, elytra) additionally get their volume, pitch
 * and position tracked per frame until they stop.
 *
 * <p>Known limitation: only sounds *started* during the recording exist in the
 * capture - a loop that began before the recording (and is still audible) can't be
 * seen through the listener API.
 */
public class MinecraftSoundCapture implements SoundInstanceListener
{
    private final List<CapturedSound> sounds = new ArrayList<>();
    private final List<ListenerFrame> frames = new ArrayList<>();
    private final List<CapturedSound> playingLoops = new ArrayList<>();

    private boolean active;

    public boolean isActive()
    {
        return this.active;
    }

    public List<CapturedSound> getSounds()
    {
        return this.sounds;
    }

    public List<ListenerFrame> getFrames()
    {
        return this.frames;
    }

    public void begin()
    {
        /* A stale capture (e.g. an export torn down without finishing, like a
         * disconnect mid-recording) must not survive into the next one */
        this.end();

        this.sounds.clear();
        this.frames.clear();
        this.active = true;

        MinecraftClient.getInstance().getSoundManager().registerListener(this);
    }

    /**
     * Stop capturing. The captured data remains available through {@link #getSounds()}
     * and {@link #getFrames()} until the next {@link #begin()}.
     */
    public void end()
    {
        if (!this.active)
        {
            return;
        }

        this.active = false;

        MinecraftClient.getInstance().getSoundManager().unregisterListener(this);

        /* Loops still playing keep endFrame == -1 (audible until the recording's end) */
        for (CapturedSound loop : this.playingLoops)
        {
            loop.instance = null;
        }

        this.playingLoops.clear();
    }

    /**
     * Must be called once per recorded frame (right before the frame is handed to the
     * recorder): samples the sound listener transform, tracks the dynamic state of
     * playing loops and detects the ones that stopped.
     */
    public void captureFrame()
    {
        if (!this.active)
        {
            return;
        }

        net.minecraft.client.sound.SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
        Iterator<CapturedSound> it = this.playingLoops.iterator();

        while (it.hasNext())
        {
            CapturedSound loop = it.next();

            try
            {
                if (!this.hasLoopEnded(soundManager, loop))
                {
                    loop.track.add(new LoopFrame(
                        MathUtils.clamp(loop.instance.getVolume(), 0F, 1F),
                        MathUtils.clamp(loop.instance.getPitch(), 0.5F, 2F),
                        loop.instance.getX(), loop.instance.getY(), loop.instance.getZ()
                    ));

                    continue;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            /* The loop stopped (or misbehaved) somewhere before this frame */
            loop.endFrame = this.frames.size();
            loop.instance = null;

            it.remove();
        }

        SoundListenerTransform transform = soundManager.getListenerTransform();
        Vec3d position = transform.position();
        Vec3d right = transform.right();

        this.frames.add(new ListenerFrame(position.x, position.y, position.z, right.x, right.y, right.z));
    }

    /**
     * Whether a tracked loop stopped. Tickable loops (minecarts, elytra, ambience)
     * report it themselves; for the rest the sound system is polled - but a missing
     * source only counts as a stop after the sound has been seen playing at least
     * once, because with the master volume at zero vanilla never creates sources,
     * and isPlaying() stays false for sounds that are logically still playing.
     */
    private boolean hasLoopEnded(net.minecraft.client.sound.SoundManager soundManager, CapturedSound loop)
    {
        if (loop.instance instanceof TickableSoundInstance tickable && tickable.isDone())
        {
            return true;
        }

        if (soundManager.isPlaying(loop.instance))
        {
            loop.seenPlaying = true;

            return false;
        }

        return loop.seenPlaying;
    }

    @Override
    public void onSoundPlayed(SoundInstance instance, WeightedSoundSet soundSet, float range)
    {
        if (!this.active)
        {
            return;
        }

        /* Broken third-party sound instances must never break the recording */
        try
        {
            this.capture(instance, range);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void capture(SoundInstance instance, float range)
    {
        SoundCategory category = instance.getCategory();

        /* Background music isn't part of the scene */
        if (category == SoundCategory.MUSIC)
        {
            return;
        }

        boolean relative = instance.isRelative();
        SoundInstance.AttenuationType attenuationType = instance.getAttenuationType();

        /* UI clicks (vanilla buttons, BBS's own clicks) are master category, relative
         * and unattenuated - they aren't part of the scene either */
        if (category == SoundCategory.MASTER && relative && attenuationType == SoundInstance.AttenuationType.NONE)
        {
            return;
        }

        Sound sound = instance.getSound();

        if (sound == null)
        {
            return;
        }

        /* Match vanilla's clamps. The player's category/master sliders are deliberately
         * not applied, so the exported mix doesn't depend on personal volume settings. */
        float volume = MathUtils.clamp(instance.getVolume(), 0F, 1F);
        float pitch = MathUtils.clamp(instance.getPitch(), 0.5F, 2F);
        boolean loop = instance.isRepeatable() && instance.getRepeatDelay() == 0;

        /* Loops are kept even at zero volume - dynamic ones (minecarts) start silent */
        if (volume <= 0F && !loop)
        {
            return;
        }

        CapturedSound captured = new CapturedSound(
            sound.getLocation(),
            this.frames.size(),
            instance.getX(), instance.getY(), instance.getZ(),
            relative,
            attenuationType == SoundInstance.AttenuationType.LINEAR,
            volume, pitch, range, loop
        );

        this.sounds.add(captured);

        if (loop)
        {
            captured.instance = instance;

            this.playingLoops.add(captured);
        }
    }

    /**
     * A single captured sound event, stamped with the recording frame it started at.
     */
    public static class CapturedSound
    {
        /** Resource location of the concrete .ogg file that was picked to play. */
        public final Identifier location;
        /** Index of the recording frame the sound started at. */
        public final int frame;

        public final double x;
        public final double y;
        public final double z;

        /** Whether the position is relative to the listener (such sounds play centered). */
        public final boolean relative;
        /** Whether the sound uses vanilla's linear distance attenuation. */
        public final boolean attenuate;
        /** Effective volume (including sounds.json volume), clamped like vanilla. */
        public final float volume;
        /** Effective pitch (playback speed factor), clamped like vanilla. */
        public final float pitch;
        /** Attenuation distance in blocks (vanilla: {@code max(volume, 1) * 16} by default). */
        public final float range;
        /** Whether this is a no-delay looping sound. */
        public final boolean loop;

        /** For loops: per-frame dynamic state, starting at {@link #frame}. */
        public final List<LoopFrame> track;

        /** For loops: index of the recording frame the loop stopped at, -1 = played to the end. */
        public int endFrame = -1;

        /** Live instance of a playing loop, polled by {@link #captureFrame()}; null once stopped. */
        private SoundInstance instance;
        /** Whether the loop's OpenAL source was ever observed playing (see hasLoopEnded). */
        private boolean seenPlaying;

        public CapturedSound(Identifier location, int frame, double x, double y, double z, boolean relative, boolean attenuate, float volume, float pitch, float range, boolean loop)
        {
            this.location = location;
            this.frame = frame;
            this.x = x;
            this.y = y;
            this.z = z;
            this.relative = relative;
            this.attenuate = attenuate;
            this.volume = volume;
            this.pitch = pitch;
            this.range = range;
            this.loop = loop;
            this.track = loop ? new ArrayList<>() : null;
        }

        /**
         * Dynamic state of a playing loop at the given recording frame (clamped to the
         * tracked span), or null when nothing was tracked.
         */
        public LoopFrame getTrackFrame(int frameIndex)
        {
            if (this.track == null || this.track.isEmpty())
            {
                return null;
            }

            return this.track.get(MathUtils.clamp(frameIndex - this.frame, 0, this.track.size() - 1));
        }
    }

    /**
     * Dynamic state of a looping sound at one recorded frame.
     */
    public static class LoopFrame
    {
        public final float volume;
        public final float pitch;
        public final double x;
        public final double y;
        public final double z;

        public LoopFrame(float volume, float pitch, double x, double y, double z)
        {
            this.volume = volume;
            this.pitch = pitch;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Sound listener transform at one recorded frame (position and right vector,
     * for the mixer's distance and pan calculations).
     */
    public static class ListenerFrame
    {
        public final double x;
        public final double y;
        public final double z;
        public final double rightX;
        public final double rightY;
        public final double rightZ;

        public ListenerFrame(double x, double y, double z, double rightX, double rightY, double rightZ)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rightX = rightX;
            this.rightY = rightY;
            this.rightZ = rightZ;
        }
    }
}
