package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FFMpegUtils
{
    private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+(\\.\\d+)*");

    private static final Set<String> SKIP_DIRS = Set.of("Windows", "Program Files", "Program Files (x86)", "$Recycle.Bin", "System Volume Information", "AppData");

    /**
     * People usually are not bright enough, even though everything is stated
     * in the tutorial, they still manage to specify either wrong path to ffmpeg, or
     * they specify the path to the folder...
     *
     * This little method should simplify their lives!
     */
    private static File findFFMPEG(String path)
    {
        File file = new File(path);
        boolean isWin = OS.CURRENT == OS.WINDOWS;

        if (file.isDirectory())
        {
            String subpath = isWin ? "ffmpeg.exe" : "ffmpeg";
            File bin = new File(file, subpath);

            if (bin.isFile())
            {
                return bin;
            }

            bin = new File(file, "bin" + File.pathSeparator + subpath);

            if (bin.isFile())
            {
                return bin;
            }
        }
        else if (isWin && !file.exists())
        {
            File exe = new File(path + ".exe");

            if (exe.exists())
            {
                return exe;
            }
        }

        return file;
    }

    public static String getFFMPEG()
    {
        String encoder = BBSSettings.videoEncoderPath.get();
        File encoderPath = findFFMPEG(BBSSettings.videoEncoderPath.get());

        if (encoderPath.isFile())
        {
            encoder = encoderPath.getAbsolutePath();
        }

        return encoder;
    }

    public static boolean checkFFMPEG()
    {
        return execute(BBSMod.getGameFolder(), "-version");
    }

    /**
     * The version the configured encoder reports of itself — "6.1", "7.0.2" — or nothing when
     * it cannot be run or doesn't answer like ffmpeg. Blocks on the process, so ask from a
     * thread of your own.
     *
     * <p>A "found" that shows the version is the only proof that means anything: a path that
     * exists can still be the wrong file, and that is found out on export otherwise.</p>
     */
    public static Optional<String> version()
    {
        ProcessBuilder builder = new ProcessBuilder(getFFMPEG(), "-version");

        builder.redirectErrorStream(true);

        try
        {
            Process process = builder.start();
            String first;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
            {
                first = reader.readLine();

                /* Drain the rest so the process can exit instead of blocking on a full pipe */
                while (reader.readLine() != null);
            }

            process.waitFor();

            /* "ffmpeg version 6.1-full_build-www.gyan.dev Copyright ..." or
             * "ffmpeg version n4.4-154-g79c114e1b2-20210930 ...": the number is the part that
             * means anything to a person, the rest names the build */
            if (first != null && first.startsWith("ffmpeg version "))
            {
                String[] words = first.split(" ");
                String word = words.length > 2 ? words[2] : "?";
                Matcher number = VERSION_NUMBER.matcher(word);

                return Optional.of(number.find() ? number.group() : word);
            }
        }
        catch (Exception e)
        {}

        return Optional.empty();
    }

    public static boolean execute(File folder, String... arguments)
    {
        List<String> args = new ArrayList<String>();

        args.add(getFFMPEG());

        for (String arg : arguments)
        {
            args.add(arg);
        }

        ProcessBuilder builder = new ProcessBuilder(args);
        File log = BBSMod.getSettingsPath("converter.log");

        builder.directory(folder);
        builder.redirectErrorStream(true);
        builder.redirectOutput(log);

        try
        {
            Process start = builder.start();

            return start.waitFor() == 0;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    public static Optional<Path> findFFMpeg(Path root)
    {
        Visitor visitor = new Visitor();

        try
        {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, visitor);
        }
        catch (FFMpegFoundException found)
        {
            return Optional.of(found.foundPath);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static class Visitor extends SimpleFileVisitor<Path>
    {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        {
            Path name = dir.getFileName();

            if (name != null && SKIP_DIRS.contains(name.toString()))
            {
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        {
            Path fileName = file.getFileName();

            if (fileName != null && fileName.toString().equalsIgnoreCase("ffmpeg.exe"))
            {
                Path parent = file.getParent();

                if (parent != null)
                {
                    Path parentName = parent.getFileName();

                    if (parentName != null && parentName.toString().equalsIgnoreCase("bin"))
                    {
                        throw new FFMpegFoundException(file.toAbsolutePath());
                    }
                }
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc)
        {
            return FileVisitResult.CONTINUE;
        }
    }

    private static class FFMpegFoundException extends RuntimeException
    {
        final Path foundPath;

        public FFMpegFoundException(Path foundPath)
        {
            this.foundPath = foundPath;
        }
    }
}