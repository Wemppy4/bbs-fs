package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.audio.Wave;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class OpenALRecorder implements Runnable
{
    private static final int SAMPLE_RATE = 44100;
    private static final int FORMAT = AL10.AL_FORMAT_MONO16;
    private static final int BUFFER_SAMPLES = 1024;

    /** Rolling history of peak amplitudes (0..1) for the live waveform display. */
    private static final int WAVEFORM_RESOLUTION = 256;
    /** Peaks pushed per poll; sets how fast the waveform scrolls regardless of chunk size. */
    private static final int WAVEFORM_POINTS_PER_POLL = 8;

    private long captureDevice;
    private ByteBuffer buffer;
    private boolean running = true;
    private boolean cancelled;
    private Consumer<Wave> consumer;
    private long startTime;
    private float volume;

    private final float[] waveformPeak = new float[WAVEFORM_RESOLUTION];
    private final float[] waveformAverage = new float[WAVEFORM_RESOLUTION];
    private int waveformHead;

    public OpenALRecorder(Consumer<Wave> consumer)
    {
        this.consumer = consumer;
    }

    public void stop()
    {
        this.running = false;
    }

    /** Stop recording and discard the take — the wave is never delivered to the consumer. */
    public void cancel()
    {
        this.cancelled = true;
        this.running = false;
    }

    public long getTime()
    {
        return System.currentTimeMillis() - this.startTime;
    }

    public float getVolume()
    {
        return this.volume;
    }

    /**
     * Snapshot the rolling waveform history in chronological order (oldest first), reusing
     * {@code out} when sized right. Returns {@code [peak, average]} amplitude arrays — the
     * average is the darker inner envelope, like {@link mchorse.bbs_mod.audio.Waveform}.
     * The recorder thread keeps appending, so this is synchronized against it.
     */
    public synchronized float[][] getWaveform(float[][] out)
    {
        int n = this.waveformPeak.length;

        if (out == null || out.length != 2 || out[0].length != n)
        {
            out = new float[][] { new float[n], new float[n] };
        }

        for (int i = 0; i < n; i++)
        {
            int idx = (this.waveformHead + i) % n;

            out[0][i] = this.waveformPeak[idx];
            out[1][i] = this.waveformAverage[idx];
        }

        return out;
    }

    /**
     * Downsample the freshly captured chunk into {@link #WAVEFORM_POINTS_PER_POLL} buckets,
     * recording the peak and mean absolute amplitude of each, and append them to the history.
     */
    private synchronized void captureWaveform(ByteBuffer chunk, int available)
    {
        int per = Math.max(1, available / WAVEFORM_POINTS_PER_POLL);

        for (int p = 0; p < WAVEFORM_POINTS_PER_POLL; p++)
        {
            int start = p * per;
            int end = p == WAVEFORM_POINTS_PER_POLL - 1 ? available : Math.min(available, start + per);
            float peak = 0F;
            float sum = 0F;
            int count = 0;

            for (int i = start; i < end; i++)
            {
                float sample = Math.abs(chunk.getShort(i * 2) / 32768F);

                peak = Math.max(peak, sample);
                sum += sample;
                count++;
            }

            this.waveformPeak[this.waveformHead] = peak;
            this.waveformAverage[this.waveformHead] = count > 0 ? sum / count : 0F;
            this.waveformHead = (this.waveformHead + 1) % this.waveformPeak.length;
        }
    }

    public void init()
    {
        String defaultDeviceName = ALC11.alcGetString(0, ALC11.ALC_CAPTURE_DEFAULT_DEVICE_SPECIFIER);

        if (defaultDeviceName == null)
        {
            throw new IllegalStateException("No capture devices available.");
        }

        this.captureDevice = ALC11.alcCaptureOpenDevice(defaultDeviceName, SAMPLE_RATE, FORMAT, BUFFER_SAMPLES);

        if (this.captureDevice == 0)
        {
            throw new RuntimeException("Failed to open capture device.");
        }

        ALC11.alcCaptureStart(this.captureDevice);

        this.buffer = MemoryUtil.memAlloc(SAMPLE_RATE * 2);
        this.startTime = System.currentTimeMillis();
    }

    public void pollAndProcess()
    {
        int available = ALC10.alcGetInteger(this.captureDevice, ALC11.ALC_CAPTURE_SAMPLES);

        if (available > 0)
        {
            if (this.buffer.position() + available * 2 > this.buffer.capacity())
            {
                ByteBuffer newBuffer = MemoryUtil.memAlloc(this.buffer.capacity() * 2);

                this.buffer.flip();
                newBuffer.put(this.buffer);
                MemoryUtil.memFree(this.buffer);

                this.buffer = newBuffer;
            }

            ByteBuffer buffer = BufferUtils.createByteBuffer(available * 2);

            ALC11.alcCaptureSamples(this.captureDevice, buffer, available);
            this.buffer.put(buffer);

            this.volume = 0F;

            for (int i = 0; i < available; i++)
            {
                this.volume = Math.max(Math.abs(buffer.getShort(0) / 65535F), this.volume);
            }

            this.captureWaveform(buffer, available);
        }
    }

    public void cleanup()
    {
        ALC11.alcCaptureStop(this.captureDevice);
        ALC11.alcCaptureCloseDevice(this.captureDevice);

        this.buffer.flip();

        if (!this.cancelled && this.consumer != null)
        {
            byte[] pcm = new byte[this.buffer.limit()];

            this.buffer.get(pcm);
            this.consumer.accept(new Wave(1, 1, SAMPLE_RATE, 16, pcm));
        }

        MemoryUtil.memFree(this.buffer);
        this.buffer = null;
    }

    @Override
    public void run()
    {
        this.init();

        while (this.running)
        {
            this.pollAndProcess();

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        this.cleanup();
    }
}
