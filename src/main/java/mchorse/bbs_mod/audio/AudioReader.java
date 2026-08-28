package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.audio.ogg.VorbisReader;
import mchorse.bbs_mod.audio.wav.WaveReader;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.File;
import java.io.InputStream;

public class AudioReader
{
    public static boolean isVideo(String pathLower)
    {
        return pathLower.endsWith(".mp4") || pathLower.endsWith(".mov") || pathLower.endsWith(".mkv")
            || pathLower.endsWith(".webm") || pathLower.endsWith(".avi") || pathLower.endsWith(".m4v");
    }

    public static Wave read(AssetProvider provider, Link link) throws Exception
    {
        String pathLower = link.path.toLowerCase();

        if (isVideo(pathLower))
        {
            return readVideoAudio(provider, link);
        }

        if (!pathLower.endsWith(".wav") && !pathLower.endsWith(".ogg"))
        {
            return null;
        }

        /* System.out.println("Reading: " + link); */

        try (InputStream asset = provider.getAsset(link))
        {
            if (pathLower.endsWith(".wav"))
            {
                return new WaveReader().read(asset);
            }
            else if (pathLower.endsWith(".ogg"))
            {
                return VorbisReader.read(link, asset);
            }
        }

        throw new IllegalStateException("Given link " + link + " isn't a Wave or a Vorbis file!");
    }

    /**
     * Extract a video file's audio track as 16-bit stereo PCM by piping it through ffmpeg.
     * Returns null when the file is unreachable, ffmpeg is not set up, or the video has no audio.
     */
    private static Wave readVideoAudio(AssetProvider provider, Link link) throws Exception
    {
        File file = provider.getFile(link);

        if (file == null || !file.isFile())
        {
            return null;
        }

        ProcessBuilder builder = new ProcessBuilder(
            FFMpegUtils.getFFMPEG(),
            "-i", file.getAbsolutePath(),
            "-vn", "-sn", "-dn",
            "-ac", "2", "-ar", "44100",
            "-acodec", "pcm_s16le", "-f", "s16le",
            "pipe:1"
        );

        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        byte[] data;

        try (InputStream stream = process.getInputStream())
        {
            data = stream.readAllBytes();
        }
        finally
        {
            process.destroy();
        }

        if (data.length == 0)
        {
            return null;
        }

        return new Wave(1, 2, 44100, 16, data);
    }
}
