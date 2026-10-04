package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.resources.Link;
import org.lwjgl.openal.AL10;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public class SoundBuffer
{
    private final Link id;
    private int buffer;
    private float duration;
    private Waveform waveform;

    public SoundBuffer(Link id, Wave wave, Waveform waveform)
    {
        this.id = id;

        this.buffer = AL10.alGenBuffers();
        ByteBuffer buffer = BufferUtils.createByteBuffer(wave.data.length);

        buffer.put(wave.data);
        buffer.flip();

        AL10.alBufferData(this.buffer, wave.getALFormat(), buffer, wave.sampleRate);

        this.duration = wave.getDuration();
        this.waveform = waveform;
    }

    public Link getId()
    {
        return this.id;
    }

    public int getBuffer()
    {
        return this.buffer;
    }

    public float getDuration()
    {
        return this.duration;
    }

    public Waveform getWaveform()
    {
        return this.waveform;
    }

    public void delete()
    {
        AL10.alDeleteBuffers(this.buffer);

        this.buffer = -1;

        if (this.waveform != null)
        {
            this.waveform.delete();
        }
    }
}