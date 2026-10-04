package mchorse.bbs_mod.audio.ogg;

import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import paulscode.sound.SoundBuffer;
import paulscode.sound.codecs.CodecJOrbis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import javax.sound.sampled.AudioFormat;

/** Decode through the JOrbis codec shipped with Minecraft 1.12.2. */
public class VorbisReader
{
    public static Wave read(Link link, InputStream stream) throws IOException
    {
        final byte[] encoded = IOUtils.readBytes(stream);
        URL url = new URL(null, "bbs-audio:asset.ogg", new URLStreamHandler()
        {
            @Override
            protected URLConnection openConnection(URL url)
            {
                return new URLConnection(url)
                {
                    public void connect() {}
                    public InputStream getInputStream() { return new ByteArrayInputStream(encoded); }
                };
            }
        });
        CodecJOrbis decoder = new CodecJOrbis();
        try
        {
            if (!decoder.initialize(url)) throw new IOException("Cannot decode Vorbis asset " + link);
            AudioFormat format = decoder.getAudioFormat();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (!decoder.endOfStream())
            {
                SoundBuffer part = decoder.read();
                if (part == null)
                {
                    if (!decoder.endOfStream()) throw new IOException("Truncated Vorbis asset " + link);
                    break;
                }
                output.write(part.audioData);
                part.cleanup();
            }
            byte[] pcm = output.toByteArray();
            if (format.isBigEndian())
            {
                for (int i = 0; i + 1 < pcm.length; i += 2)
                {
                    byte first = pcm[i]; pcm[i] = pcm[i + 1]; pcm[i + 1] = first;
                }
            }
            return new Wave(1, format.getChannels(), (int) format.getSampleRate(), format.getSampleSizeInBits(), pcm);
        }
        finally { decoder.cleanup(); }
    }
}
