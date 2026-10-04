package mchorse.bbs_mod;

import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.ogg.VorbisReader;
import mchorse.bbs_mod.audio.wav.WaveReader;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.resources.Link;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import static org.junit.Assert.*;

public class ForgeAudioTest
{
    @Test public void wavPreservesSignedPcmAndDuration() throws Exception
    {
        Wave original = new Wave(1, 1, 8000, 16, new byte[] {0, -128, -1, 127, 0, 0, 1, 0});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        WaveWriter.write(bytes, original);
        Wave decoded = new WaveReader().read(new ByteArrayInputStream(bytes.toByteArray()));
        assertArrayEquals(original.data, decoded.data);
        assertEquals(16, decoded.bitsPerSample);
        assertEquals(4 / 8000F, decoded.getDuration(), 1E-6F);
    }

    @Test public void nativeVorbisDecodesBundledCompletionSound() throws Exception
    {
        try (InputStream stream = new FileInputStream("src/client/resources/assets/bbs/assets/sounds/render_complete.ogg"))
        {
            Wave wave = VorbisReader.read(Link.assets("sounds/render_complete.ogg"), stream);
            assertEquals(16, wave.bitsPerSample);
            assertTrue(wave.numChannels == 1 || wave.numChannels == 2);
            assertTrue(wave.sampleRate >= 22050);
            assertTrue(wave.getDuration() > 0.1F);
            boolean audible = false;
            for (byte sample : wave.data) audible |= sample != 0;
            assertTrue(audible);
        }
    }

    @Test public void wavHandlesShortReadsAndRejectsTruncatedData() throws Exception
    {
        Wave wave = new Wave(1, 1, 8000, 16, new byte[] {1, 2, 3, 4, 5, 6});
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        WaveWriter.write(encoded, wave);
        byte[] bytes = encoded.toByteArray();
        InputStream fragmented = new ByteArrayInputStream(bytes)
        {
            @Override public synchronized int read(byte[] target, int offset, int length)
            {
                return super.read(target, offset, Math.min(1, length));
            }
        };
        assertArrayEquals(wave.data, new WaveReader().read(fragmented).data);
        try
        {
            new WaveReader().read(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
            fail("Truncated PCM must not be padded with silence");
        }
        catch (java.io.EOFException expected) {}
    }
}
