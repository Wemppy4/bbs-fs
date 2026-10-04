package mchorse.bbs_mod.audio.wav;

import mchorse.bbs_mod.audio.BinaryChunk;
import mchorse.bbs_mod.audio.BinaryReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.utils.Pair;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** RIFF reader preserving BBS cues/LIST metadata, including short reads and odd chunk padding. */
public class WaveReader extends BinaryReader
{
    public Wave read(InputStream input) throws Exception
    {
        DataInputStream stream = new DataInputStream(input);
        BinaryChunk riff = this.readChunk(stream);
        if (!"RIFF".equals(riff.id) || !"WAVE".equals(this.readFourString(stream)))
            throw new IOException("Not a RIFF/WAVE stream");
        long remaining = Integer.toUnsignedLong(riff.size) - 4;
        int audioFormat = -1, channels = -1, rate = -1, byteRate = -1, align = -1, bits = -1;
        byte[] data = null;
        List<WaveList> lists = new ArrayList<>();
        List<WaveCue> cues = new ArrayList<>();
        while (remaining > 0)
        {
            if (remaining < 8) throw new EOFException("Incomplete WAVE chunk header");
            BinaryChunk chunk = this.readChunk(stream);
            remaining -= 8;
            long size = Integer.toUnsignedLong(chunk.size);
            if (size > remaining || size > Integer.MAX_VALUE) throw new EOFException("Invalid WAVE chunk size: " + chunk.id);
            byte[] bytes = new byte[(int) size];
            stream.readFully(bytes);
            remaining -= size;
            if ((size & 1) != 0 && remaining > 0)
            {
                stream.readByte();
                remaining--;
            }
            DataInputStream payload = new DataInputStream(new ByteArrayInputStream(bytes));
            if ("fmt ".equals(chunk.id))
            {
                if (size < 16) throw new IOException("Invalid WAVE format chunk");
                audioFormat = this.readShort(payload); channels = this.readShort(payload);
                rate = this.readInt(payload); byteRate = this.readInt(payload);
                align = this.readShort(payload); bits = this.readShort(payload);
            }
            else if ("data".equals(chunk.id)) data = bytes;
            else if ("LIST".equals(chunk.id))
            {
                WaveList list = new WaveList(this.readFourString(payload));
                while (payload.available() > 0)
                {
                    BinaryChunk entry = this.readChunk(payload);
                    if (entry.size < 0 || entry.size > payload.available()) throw new EOFException("Truncated WAVE LIST entry");
                    byte[] value = new byte[entry.size];
                    payload.readFully(value);
                    list.entries.add(new Pair<>(entry.id, new String(value, StandardCharsets.UTF_8)));
                    if ((entry.size & 1) != 0 && payload.available() > 0) payload.readByte();
                }
                lists.add(list);
            }
            else if ("cue ".equals(chunk.id))
            {
                int count = this.readInt(payload);
                if (count < 0 || count > payload.available() / 24) throw new EOFException("Truncated WAVE cue entries");
                for (int i = 0; i < count; i++)
                {
                    WaveCue cue = new WaveCue();
                    cue.id = this.readInt(payload); cue.position = this.readInt(payload);
                    cue.dataChunkID = this.readInt(payload); cue.chunkStart = this.readInt(payload);
                    cue.blockStart = this.readInt(payload); cue.sampleStart = this.readInt(payload);
                    cues.add(cue);
                }
            }
        }
        if (data == null || channels <= 0 || rate <= 0 || align <= 0 || bits <= 0)
            throw new IOException("WAVE requires valid format and data chunks");
        Wave wave = new Wave(audioFormat, channels, rate, byteRate, align, bits, data);
        wave.lists = lists; wave.cues = cues;
        return wave;
    }

    public BinaryChunk readChunk(InputStream stream) throws Exception
    {
        byte[] id = new byte[4];
        new DataInputStream(stream).readFully(id);
        return new BinaryChunk(new String(id, StandardCharsets.US_ASCII), this.readInt(stream));
    }

    @Override
    public String readFourString(InputStream stream) throws IOException
    {
        new DataInputStream(stream).readFully(this.buf);
        return new String(this.buf, StandardCharsets.US_ASCII);
    }

    @Override
    public int readInt(InputStream stream) throws IOException
    {
        new DataInputStream(stream).readFully(this.buf);
        return b2i(this.buf[0], this.buf[1], this.buf[2], this.buf[3]);
    }

    @Override
    public int readShort(InputStream stream) throws IOException
    {
        new DataInputStream(stream).readFully(this.buf, 0, 2);
        return b2i(this.buf[0], this.buf[1], (byte) 0, (byte) 0);
    }
}
