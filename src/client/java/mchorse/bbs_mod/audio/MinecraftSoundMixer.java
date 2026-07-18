package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.audio.MinecraftSoundCapture.CapturedSound;
import mchorse.bbs_mod.audio.MinecraftSoundCapture.ListenerFrame;
import mchorse.bbs_mod.audio.MinecraftSoundCapture.LoopFrame;
import mchorse.bbs_mod.audio.ogg.VorbisReader;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mixes sounds captured by {@link MinecraftSoundCapture} (plus the film's audio track,
 * when present) into a 16-bit stereo WAV, approximating what OpenAL did during the
 * recording: vanilla's linear distance attenuation, constant-power panning from the
 * per-frame listener transform, pitch as playback speed. Stereo source files play
 * unattenuated and unpanned, exactly like in vanilla.
 *
 * <p>The mix is streamed to the file in fixed windows, so memory stays flat no matter
 * how long the recording is. Like the film clip mixing in {@link Wave#add}, overload
 * is handled per sample (hard limit) rather than by normalizing the whole track.
 */
public class MinecraftSoundMixer
{
    /** Per-channel gain of a centered sound (constant-power pan at the middle). */
    private static final float CENTER_GAIN = (float) Math.sqrt(0.5D);
    /** One-pole gain smoothing time constant (seconds) - kills zipper noise from per-frame gain updates. */
    private static final double GAIN_SMOOTHING = 0.005D;
    /** Samples mixed per streaming window (10 seconds at 48 kHz). */
    private static final int WINDOW_SAMPLES = 48000 * 10;
    /** The WAV data chunk size is a 32 bit field - stay well below it (~2.9 hours of 48 kHz stereo). */
    private static final long MAX_SAMPLES = 500_000_000L;

    /**
     * Mix everything into {@code file} as a stereo WAV of exactly
     * {@code totalFrames / frameRate} seconds. Returns false when there is nothing
     * to mix or the mix failed (the file is not written then).
     *
     * @param filmAudio the film's own audio track (usually mono, from
     *        {@link AudioRenderer#renderAudio}), mixed in at its original level; may be null
     */
    public static boolean mixToFile(File file, List<CapturedSound> sounds, List<ListenerFrame> frames, Wave filmAudio, int sampleRate, double frameRate, int totalFrames)
    {
        if (totalFrames <= 0 || frameRate <= 0D)
        {
            return false;
        }

        long totalSamples = (long) Math.ceil(totalFrames / frameRate * sampleRate);

        if (totalSamples <= 0)
        {
            return false;
        }

        if (totalSamples > MAX_SAMPLES)
        {
            System.err.println("Minecraft sounds mix skipped: the recording is longer than the WAV format allows (" + (totalSamples / sampleRate / 60L) + " minutes)");

            return false;
        }

        if (filmAudio != null && filmAudio.bitsPerSample != 16)
        {
            filmAudio = filmAudio.convertTo16();
        }

        if (filmAudio != null && (filmAudio.numChannels < 1 || filmAudio.data.length == 0))
        {
            filmAudio = null;
        }

        List<SoundCursor> cursors = createCursors(sounds, frames, sampleRate, frameRate, totalSamples);

        if (cursors.isEmpty() && filmAudio == null)
        {
            return false;
        }

        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file)))
        {
            WaveWriter.writeHeader(stream, 2, sampleRate, 16, (int) (totalSamples * 4L));

            float[] left = new float[WINDOW_SAMPLES];
            float[] right = new float[WINDOW_SAMPLES];
            byte[] packed = new byte[WINDOW_SAMPLES * 4];

            for (long windowStart = 0; windowStart < totalSamples; windowStart += WINDOW_SAMPLES)
            {
                int length = (int) Math.min(WINDOW_SAMPLES, totalSamples - windowStart);

                java.util.Arrays.fill(left, 0, length, 0F);
                java.util.Arrays.fill(right, 0, length, 0F);

                for (SoundCursor cursor : cursors)
                {
                    try
                    {
                        cursor.mix(left, right, windowStart, length);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                if (filmAudio != null)
                {
                    mixFilmAudio(left, right, filmAudio, windowStart, length, sampleRate);
                }

                pack(left, right, packed, length);
                stream.write(packed, 0, length * 4);
            }

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            file.delete();

            return false;
        }
    }

    /**
     * Decode every distinct sound file once and pair each audible captured sound
     * with its wave. Loops whose whole tracked volume stayed at zero contribute
     * nothing and are dropped upfront.
     */
    private static List<SoundCursor> createCursors(List<CapturedSound> sounds, List<ListenerFrame> frames, int sampleRate, double frameRate, long totalSamples)
    {
        List<SoundCursor> cursors = new ArrayList<>();
        Map<Identifier, Wave> waves = new HashMap<>();

        for (CapturedSound sound : sounds)
        {
            try
            {
                if (isSilent(sound))
                {
                    continue;
                }

                Wave wave = read(waves, sound.location);

                if (wave != null)
                {
                    cursors.add(new SoundCursor(sound, wave, frames, sampleRate, frameRate, totalSamples));
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return cursors;
    }

    private static boolean isSilent(CapturedSound sound)
    {
        if (sound.volume > 0F)
        {
            return false;
        }

        if (sound.track == null)
        {
            return true;
        }

        for (LoopFrame frame : sound.track)
        {
            if (frame.volume > 0F)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Decode the sound's .ogg from the game's resources (resource packs included),
     * with caching - footsteps reuse the same handful of files hundreds of times.
     */
    private static Wave read(Map<Identifier, Wave> cache, Identifier location)
    {
        if (cache.containsKey(location))
        {
            return cache.get(location);
        }

        Wave wave = null;

        try
        {
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(location);

            if (resource.isPresent())
            {
                try (InputStream stream = resource.get().getInputStream())
                {
                    wave = VorbisReader.read(new Link(location.getNamespace(), location.getPath()), stream);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (wave != null && (wave.bitsPerSample != 16 || wave.data.length == 0 || wave.numChannels < 1))
        {
            wave = null;
        }

        cache.put(location, wave);

        return wave;
    }

    private static void mixFilmAudio(float[] left, float[] right, Wave film, long windowStart, int length, int sampleRate)
    {
        int channels = film.numChannels;
        int srcSamples = film.data.length / (2 * channels);
        double step = film.sampleRate / (double) sampleRate;

        for (int i = 0; i < length; i++)
        {
            double srcPos = (windowStart + i) * step;
            int s0 = (int) srcPos;

            if (s0 >= srcSamples)
            {
                break;
            }

            float frac = (float) (srcPos - s0);
            int s1 = Math.min(s0 + 1, srcSamples - 1);

            if (channels == 1)
            {
                float value = lerpSample(film.data, s0 * 2, s1 * 2, frac);

                /* The film track used to be exported as the only track - keep its level */
                left[i] += value;
                right[i] += value;
            }
            else
            {
                int b0 = s0 * channels * 2;
                int b1 = s1 * channels * 2;

                left[i] += lerpSample(film.data, b0, b1, frac);
                right[i] += lerpSample(film.data, b0 + 2, b1 + 2, frac);
            }
        }
    }

    /** Linearly interpolated little-endian 16-bit sample, normalized to [-1, 1]. */
    private static float lerpSample(byte[] data, int offset0, int offset1, float frac)
    {
        float a = (short) ((data[offset0] & 0xFF) | (data[offset0 + 1] << 8));
        float b = (short) ((data[offset1] & 0xFF) | (data[offset1 + 1] << 8));

        return (a + (b - a) * frac) / 32768F;
    }

    /** Pack a window into interleaved little-endian 16-bit stereo, hard-limited per sample. */
    private static void pack(float[] left, float[] right, byte[] packed, int length)
    {
        for (int i = 0; i < length; i++)
        {
            short l = (short) (MathUtils.clamp(left[i], -0.999F, 0.999F) * Short.MAX_VALUE);
            short r = (short) (MathUtils.clamp(right[i], -0.999F, 0.999F) * Short.MAX_VALUE);
            int offset = i * 4;

            packed[offset] = (byte) (l & 0xFF);
            packed[offset + 1] = (byte) ((l >> 8) & 0xFF);
            packed[offset + 2] = (byte) (r & 0xFF);
            packed[offset + 3] = (byte) ((r >> 8) & 0xFF);
        }
    }

    /**
     * One captured sound being mixed: carries its playback phase, smoothed gains and
     * per-frame state across streaming windows.
     */
    private static class SoundCursor
    {
        private final CapturedSound sound;
        private final Wave wave;
        private final List<ListenerFrame> frames;

        private final int channels;
        private final int srcSamples;
        /** Whether the sound is positioned in the world (mono and not listener-relative). */
        private final boolean positioned;
        /** Stereo files keep their own image and level (OpenAL doesn't position them). */
        private final boolean stereo;

        private final long start;
        private final long end;
        private final int sampleRate;
        private final double framesPerSample;
        private final float smoothing;

        /** Next absolute output sample this cursor will mix. */
        private long position;
        /** Playback position inside the source data, in source samples. */
        private double srcPos;
        private double step;

        private final float[] gains = new float[2];
        private float gainL;
        private float gainR;
        private int lastFrame = -1;
        private boolean done;

        public SoundCursor(CapturedSound sound, Wave wave, List<ListenerFrame> frames, int sampleRate, double frameRate, long totalSamples)
        {
            this.sound = sound;
            this.wave = wave;
            this.frames = frames;

            this.channels = wave.numChannels;
            this.srcSamples = wave.data.length / (2 * this.channels);
            this.positioned = this.channels == 1 && !sound.relative;
            this.stereo = this.channels >= 2;

            this.sampleRate = sampleRate;
            this.framesPerSample = frameRate / sampleRate;
            this.smoothing = (float) (1D - Math.exp(-1D / (GAIN_SMOOTHING * sampleRate)));
            this.step = sound.pitch * wave.sampleRate / (double) sampleRate;

            this.start = Math.round(sound.frame / frameRate * sampleRate);
            this.position = this.start;

            long end = totalSamples;

            if (sound.loop)
            {
                if (sound.endFrame >= 0)
                {
                    end = Math.min(end, Math.round(sound.endFrame / frameRate * sampleRate));
                }
            }
            else
            {
                /* One-shots have a constant step, their length is known upfront */
                end = Math.min(end, this.start + (long) Math.floor(this.srcSamples / this.step));
            }

            this.end = end;
            this.done = this.srcSamples <= 0 || this.start >= end;
        }

        /** Mix this sound's samples that fall into the window [windowStart, windowStart + length). */
        public void mix(float[] left, float[] right, long windowStart, int length)
        {
            if (this.done || this.position >= windowStart + length)
            {
                return;
            }

            for (long i = this.position; i < windowStart + length; i++)
            {
                if (i >= this.end || (!this.sound.loop && this.srcPos >= this.srcSamples))
                {
                    this.done = true;

                    break;
                }

                this.updateGains(i);

                this.gainL += (this.gains[0] - this.gainL) * this.smoothing;
                this.gainR += (this.gains[1] - this.gainR) * this.smoothing;

                int s0 = (int) this.srcPos;
                float frac = (float) (this.srcPos - s0);
                int s1 = s0 + 1;

                if (s1 >= this.srcSamples)
                {
                    s1 = this.sound.loop ? 0 : s0;
                }

                int index = (int) (i - windowStart);

                if (this.stereo)
                {
                    int b0 = s0 * this.channels * 2;
                    int b1 = s1 * this.channels * 2;

                    left[index] += lerpSample(this.wave.data, b0, b1, frac) * this.gainL;
                    right[index] += lerpSample(this.wave.data, b0 + 2, b1 + 2, frac) * this.gainR;
                }
                else
                {
                    float value = lerpSample(this.wave.data, s0 * 2, s1 * 2, frac);

                    left[index] += value * this.gainL;
                    right[index] += value * this.gainR;
                }

                this.srcPos += this.step;

                while (this.sound.loop && this.srcPos >= this.srcSamples)
                {
                    this.srcPos -= this.srcSamples;
                }

                this.position = i + 1;
            }
        }

        /** Refresh the target gains (and a loop's pitch) once per recording frame. */
        private void updateGains(long sample)
        {
            int frameIndex = (int) (sample * this.framesPerSample);

            if (frameIndex == this.lastFrame)
            {
                return;
            }

            LoopFrame state = this.sound.loop ? this.sound.getTrackFrame(frameIndex) : null;

            if (state != null)
            {
                this.step = state.pitch * this.wave.sampleRate / (double) this.sampleRate;
            }

            this.computeGains(state, frameIndex);

            if (this.lastFrame == -1)
            {
                /* First frame: start at the target instead of ramping up from silence */
                this.gainL = this.gains[0];
                this.gainR = this.gains[1];
            }

            this.lastFrame = frameIndex;
        }

        /**
         * Target per-channel gains at the given recording frame: volume times vanilla's
         * linear distance attenuation, panned with a constant-power law from the
         * listener-space direction.
         */
        private void computeGains(LoopFrame state, int frameIndex)
        {
            float volume = state != null ? state.volume : this.sound.volume;

            if (this.stereo)
            {
                this.gains[0] = this.gains[1] = volume;

                return;
            }

            if (!this.positioned || this.frames.isEmpty())
            {
                this.gains[0] = this.gains[1] = volume * CENTER_GAIN;

                return;
            }

            ListenerFrame listener = this.frames.get(MathUtils.clamp(frameIndex, 0, this.frames.size() - 1));
            double x = state != null ? state.x : this.sound.x;
            double y = state != null ? state.y : this.sound.y;
            double z = state != null ? state.z : this.sound.z;

            double dx = x - listener.x;
            double dy = y - listener.y;
            double dz = z - listener.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            float gain = volume;

            if (this.sound.attenuate && this.sound.range > 0F)
            {
                /* Vanilla's AL_LINEAR_DISTANCE_CLAMPED: rolloff 1, reference distance 0 */
                gain *= MathUtils.clamp(1F - (float) (distance / this.sound.range), 0F, 1F);
            }

            if (gain <= 0F)
            {
                this.gains[0] = this.gains[1] = 0F;

                return;
            }

            if (distance < 0.001D)
            {
                this.gains[0] = this.gains[1] = gain * CENTER_GAIN;

                return;
            }

            double pan = MathUtils.clamp((dx * listener.rightX + dy * listener.rightY + dz * listener.rightZ) / distance, -1D, 1D);
            double angle = (pan + 1D) * Math.PI / 4D;

            this.gains[0] = gain * (float) Math.cos(angle);
            this.gains[1] = gain * (float) Math.sin(angle);
        }
    }
}
