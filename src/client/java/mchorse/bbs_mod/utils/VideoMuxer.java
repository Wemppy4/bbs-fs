package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Second ffmpeg pass that merges an audio track into an already recorded video.
 * Used by the Minecraft sounds export: the captured sounds are only known once
 * the recording ends, so the audio can't be handed to the recording ffmpeg
 * process upfront like the plain film audio track is.
 */
public class VideoMuxer
{
    /**
     * Merge {@code audio} into {@code video}, replacing it: the merged file takes the
     * video's base name with the extension produced by the mux arguments, the original
     * video is deleted. Returns the merged file, or null when the merge failed (the
     * recorded video is left untouched then).
     */
    public static File mux(File video, File audio, String movieName)
    {
        File folder = video.getParentFile();
        String tempName = movieName + ".tmp";

        try
        {
            List<String> args = new ArrayList<>();

            args.add(FFMpegUtils.getFFMPEG());

            /* Tokens are substituted after splitting, so file names with spaces stay
             * single arguments. ProcessBuilder passes quote characters literally, so
             * they must not be added around paths. Both files sit in the working
             * directory, plain names keep the arguments path-free. */
            for (String arg : BBSSettings.videoArgumentsMux.get().split(" "))
            {
                if (arg.isEmpty())
                {
                    continue;
                }

                arg = arg.replace("%VIDEO%", video.getName());
                arg = arg.replace("%AUDIO_TRACK%", audio.getName());
                arg = arg.replace("%NAME%", tempName);

                args.add(arg);
            }

            System.out.println("Muxing audio with following arguments: " + args);

            ProcessBuilder builder = new ProcessBuilder(args);
            File log = new File(folder, movieName + ".mux.log");

            if (!BBSSettings.videoEncoderLog.get())
            {
                log = BBSMod.getSettingsPath("video.log");
            }

            builder.directory(folder);
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);

            Process process = builder.start();

            /* ffmpeg must never wait on stdin (e.g. an overwrite prompt when custom
             * arguments lack -y) - give it an immediate EOF instead */
            process.getOutputStream().close();

            if (!process.waitFor(10, TimeUnit.MINUTES))
            {
                process.destroy();
                deleteTemp(folder, tempName);

                return null;
            }

            File merged = findTemp(folder, tempName);

            if (process.exitValue() != 0 || merged == null)
            {
                deleteTemp(folder, tempName);

                return null;
            }

            /* Swap the merged file in place of the recorded video */
            String extension = merged.getName().substring(tempName.length());
            File result = new File(folder, movieName + extension);

            if (video.exists() && !video.delete())
            {
                /* The original video can't be replaced (e.g. opened in a player) -
                 * keep the merged file under the temp name */
                return merged;
            }

            return merged.renameTo(result) ? result : merged;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return null;
        }
    }

    private static File findTemp(File folder, String tempName)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return null;
        }

        String prefix = tempName + ".";

        for (File file : files)
        {
            if (file.isFile() && file.getName().startsWith(prefix))
            {
                return file;
            }
        }

        return null;
    }

    private static void deleteTemp(File folder, String tempName)
    {
        File temp = findTemp(folder, tempName);

        if (temp != null)
        {
            temp.delete();
        }
    }
}
