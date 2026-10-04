package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ISoundEventListener;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Captures Minecraft sounds (footsteps, digging, mobs, weather, etc.) played while a
 * video export is recording, so they can be mixed into the exported audio track
 * afterwards by {@link MinecraftSoundMixer}.
 *
 * <p>Registered as a vanilla {@link ISoundEventListener} for the duration of the
 * recording, so it sees every sound the player would hear. Vanilla notifies the
 * listeners before the category and master volume checks and
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
public class MinecraftSoundCapture implements ISoundEventListener
{
    private final List<CapturedSound> sounds = new ArrayList<>();
    private final List<ListenerFrame> frames = new ArrayList<>();
    private final List<CapturedSound> playingLoops = new ArrayList<>();
    private final java.nio.FloatBuffer listenerPosition = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private final java.nio.FloatBuffer listenerOrientation = org.lwjgl.BufferUtils.createFloatBuffer(16);

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

        Minecraft.getMinecraft().getSoundHandler().addListener(this);
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

        Minecraft.getMinecraft().getSoundHandler().removeListener(this);

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

        net.minecraft.client.audio.SoundHandler soundManager = Minecraft.getMinecraft().getSoundHandler();
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
                        loop.instance.getXPosF(), loop.instance.getYPosF(), loop.instance.getZPosF()
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

        /* Sample the actual OpenAL listener, which native SoundManager updates from
         * the render-view entity. This also follows camera mods and third-party listeners. */
        if (org.lwjgl.openal.AL.isCreated())
        {
            java.nio.FloatBuffer position = this.listenerPosition;
            java.nio.FloatBuffer orientation = this.listenerOrientation;
            position.clear();
            orientation.clear();
            org.lwjgl.openal.AL10.alGetListener(org.lwjgl.openal.AL10.AL_POSITION, position);
            org.lwjgl.openal.AL10.alGetListener(org.lwjgl.openal.AL10.AL_ORIENTATION, orientation);
            org.joml.Vector3f right = new org.joml.Vector3f(orientation.get(0), orientation.get(1), orientation.get(2))
                .cross(orientation.get(3), orientation.get(4), orientation.get(5)).normalize();
            this.frames.add(new ListenerFrame(position.get(0), position.get(1), position.get(2), right.x, right.y, right.z));
        }
        else
        {
            /* Recording may run without an audio device. Use the same entity transform
             * as native SoundManager.setListener instead of requiring audible playback. */
            Minecraft mc = Minecraft.getMinecraft();
            net.minecraft.entity.Entity view = mc.getRenderViewEntity();
            float delta = mc.getRenderPartialTicks();
            if (view == null) this.frames.add(new ListenerFrame(0, 0, 0, 1, 0, 0));
            else
            {
                double yaw = Math.toRadians(view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * delta);
                this.frames.add(new ListenerFrame(
                    view.prevPosX + (view.posX - view.prevPosX) * delta,
                    view.prevPosY + (view.posY - view.prevPosY) * delta + view.getEyeHeight(),
                    view.prevPosZ + (view.posZ - view.prevPosZ) * delta,
                    -Math.cos(yaw), 0, -Math.sin(yaw)));
            }
        }
    }

    /**
     * Whether a tracked loop stopped. Tickable loops (minecarts, elytra, ambience)
     * report it themselves; for the rest the sound system is polled - but a missing
     * source only counts as a stop after the sound has been seen playing at least
     * once, because with the master volume at zero vanilla never creates sources,
     * and isPlaying() stays false for sounds that are logically still playing.
     */
    private boolean hasLoopEnded(net.minecraft.client.audio.SoundHandler soundManager, CapturedSound loop)
    {
        if (loop.instance instanceof ITickableSound && ((ITickableSound) loop.instance).isDonePlaying())
        {
            return true;
        }

        if (soundManager.isSoundPlaying(loop.instance))
        {
            loop.seenPlaying = true;

            return false;
        }

        return loop.seenPlaying;
    }

    @Override
    public void soundPlay(ISound instance, SoundEventAccessor soundSet)
    {
        if (!this.active)
        {
            return;
        }

        /* Broken third-party sound instances must never break the recording */
        try
        {
            this.capture(instance, Math.max(1F, instance.getVolume()) * 16F);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void capture(ISound instance, float range)
    {
        SoundCategory category = instance.getCategory();

        /* Background music isn't part of the scene */
        if (category == SoundCategory.MUSIC)
        {
            return;
        }

        /* Native ISound has no separate relative flag; its unattenuated UI sounds
         * correspond to the original listener-relative sounds. */
        boolean relative = instance.getAttenuationType() == ISound.AttenuationType.NONE;
        ISound.AttenuationType attenuationType = instance.getAttenuationType();

        /* UI clicks (vanilla buttons, BBS's own clicks) are master category, relative
         * and unattenuated - they aren't part of the scene either */
        if (category == SoundCategory.MASTER && relative && attenuationType == ISound.AttenuationType.NONE)
        {
            return;
        }

        Sound sound = instance.getSound();

        if (sound == null || sound == net.minecraft.client.audio.SoundHandler.MISSING_SOUND)
        {
            return;
        }

        /* Match vanilla's clamps. The player's category/master sliders are deliberately
         * not applied, so the exported mix doesn't depend on personal volume settings. */
        float volume = MathUtils.clamp(instance.getVolume(), 0F, 1F);
        float pitch = MathUtils.clamp(instance.getPitch(), 0.5F, 2F);
        boolean loop = instance.canRepeat() && instance.getRepeatDelay() == 0;

        /* Loops are kept even at zero volume - dynamic ones (minecarts) start silent */
        if (volume <= 0F && !loop)
        {
            return;
        }

        CapturedSound captured = new CapturedSound(
            sound.getSoundAsOggLocation(),
            this.frames.size(),
            instance.getXPosF(), instance.getYPosF(), instance.getZPosF(),
            relative,
            attenuationType == ISound.AttenuationType.LINEAR,
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
        public final ResourceLocation location;
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
        private ISound instance;
        /** Whether the loop's OpenAL source was ever observed playing (see hasLoopEnded). */
        private boolean seenPlaying;

        public CapturedSound(ResourceLocation location, int frame, double x, double y, double z, boolean relative, boolean attenuate, float volume, float pitch, float range, boolean loop)
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
