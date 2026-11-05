package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.VideoExportSettings;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.video.VideoPlayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Puts a sequence's video together out of its films' recorded pieces with ffmpeg alone, without
 * the game: each piece is cut to the part its clip shows, the places no film covers (or whose film
 * has no piece yet) are black, the clips' fades take the picture to black, and the sequence's
 * sound goes under it all. The picture is encoded once, with the output part of the sequence's
 * encoder arguments.
 *
 * <p>Everything about the timeline is read when the assembler is made, on the game's thread; the
 * work itself runs on a thread of its own ({@link #start()}).
 */
public class SequenceAssembler
{
    /** Past this many characters the command line won't fit on Windows: the graph goes in a file */
    private static final int MAX_COMMAND_LENGTH = 30000;

    /** How far before the end of a piece a seek still finds a frame, in seconds */
    private static final float END_MARGIN = 0.25F;

    private final VideoExportSettings settings;
    private final File folder;
    private final String name;
    private final int width;
    private final int height;
    private final int frameRate;
    private final int ticks;
    private final int totalFrames;
    private final List<Segment> segments = new ArrayList<>();
    private final List<AudioClip> sound = new ArrayList<>();

    private Thread thread;
    private volatile Process process;
    private volatile boolean cancelled;
    private volatile boolean done;
    private volatile int frames;
    private volatile File result;

    /**
     * @param name The video's base name in the settings' folder (the extension is up to the
     * encoder's arguments)
     */
    public SequenceAssembler(Sequence sequence, VideoExportSettings settings, String name)
    {
        this.settings = settings;
        this.folder = settings.getFolder();
        this.name = name;
        this.width = settings.width();
        this.height = settings.height();
        this.frameRate = Math.max(1, settings.frameRate());
        this.ticks = sequence.calculateDuration();
        this.totalFrames = this.frameAt(this.ticks);

        this.readSegments(sequence);

        if (settings.exportAudio())
        {
            this.readSound(sequence);
        }
    }

    /** The first video frame shown on or after the given tick */
    private int frameAt(int tick)
    {
        return Math.round(tick * this.frameRate / 20F);
    }

    /**
     * Split the timeline where the film shown changes: a run of ticks showing one film's piece
     * (the clip on the highest layer there) or nothing (black).
     */
    private void readSegments(Sequence sequence)
    {
        /* Whether a clip has its piece is asked of the disk, once per clip */
        Map<FilmClip, Boolean> parts = new IdentityHashMap<>();
        FilmClip current = null;
        int start = 0;

        for (int tick = 0; tick <= this.ticks; tick++)
        {
            FilmClip clip = tick < this.ticks ? sequence.getFilmClipAt(tick) : null;

            if (clip != null && !parts.computeIfAbsent(clip, FilmClip::hasPart))
            {
                clip = null;
            }

            if (tick == this.ticks || clip != current)
            {
                if (tick > start)
                {
                    this.addSegment(current, start, tick);
                }

                current = clip;
                start = tick;
            }
        }
    }

    private void addSegment(FilmClip clip, int from, int to)
    {
        int first = this.frameAt(from);
        int count = this.frameAt(to) - first;

        if (count <= 0)
        {
            return;
        }

        Segment segment = new Segment(count);

        if (clip != null)
        {
            segment.file = clip.getPartFile();
            /* The piece is the whole film from its first tick */
            segment.seconds = first / (float) this.frameRate - (clip.tick.get() - clip.offset.get()) / 20F;
            segment.fade = this.readFade(clip, first, count);
        }

        this.segments.add(segment);
    }

    /**
     * How dark the clip's fades make each of the segment's frames (0 to 255), or null when
     * they leave all of them alone.
     */
    private int[] readFade(FilmClip clip, int first, int count)
    {
        if (!clip.envelope.enabled.get())
        {
            return null;
        }

        int[] alphas = new int[count];
        boolean any = false;

        for (int i = 0; i < count; i++)
        {
            float tick = (first + i) * 20F / this.frameRate - clip.tick.get();
            float factor = MathUtils.clamp(clip.envelope.factorEnabled(clip.duration.get(), tick), 0F, 1F);

            alphas[i] = Math.round((1F - factor) * 255F);
            any = any || alphas[i] > 0;
        }

        return any ? alphas : null;
    }

    /**
     * The sequence's sound track: its own audio clips, and the sound of each film's piece where
     * the film clip keeps its film's sound, trimmed the way the clip is.
     */
    private void readSound(Sequence sequence)
    {
        for (Clip clip : sequence.clips.get())
        {
            if (clip instanceof AudioClip audio && audio.enabled.get())
            {
                this.sound.add(audio);
            }
            else if (clip instanceof FilmClip film && film.enabled.get() && film.filmAudio.get() && film.hasPart())
            {
                AudioClip piece = new AudioClip();

                piece.audio.set(film.getPartLink());
                piece.tick.set(film.tick.get());
                piece.duration.set(film.duration.get());
                piece.offset.set(film.offset.get());
                piece.layer.set(film.layer.get());
                this.sound.add(piece);
            }
        }
    }

    public String getName()
    {
        return this.name;
    }

    public boolean isDone()
    {
        return this.done;
    }

    /** The finished video, or null when it failed or was cancelled */
    public File getResult()
    {
        return this.result;
    }

    /** How much of the video is encoded, from 0 to 1 */
    public float getProgress()
    {
        return this.totalFrames <= 0 ? 0F : MathUtils.clamp(this.frames / (float) this.totalFrames, 0F, 1F);
    }

    public void start()
    {
        this.thread = new Thread(this::run, "BBS Sequence Assembler");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /**
     * Stop ffmpeg; whatever it wrote is deleted.
     */
    public void cancel()
    {
        this.cancelled = true;

        Process process = this.process;

        if (process != null)
        {
            process.destroyForcibly();
        }
    }

    private void run()
    {
        File audio = null;
        File script = null;

        try
        {
            if (!this.sound.isEmpty())
            {
                audio = new File(this.folder, this.name + ".wav");

                if (!AudioRenderer.renderAudio(audio, this.sound, this.ticks, 48000, 0, 0))
                {
                    audio = null;
                }
            }

            if (this.cancelled)
            {
                return;
            }

            List<String> args = this.buildCommand(audio);

            if (args == null)
            {
                return;
            }

            int length = 0;

            for (String arg : args)
            {
                length += arg.length() + 3;
            }

            if (length > MAX_COMMAND_LENGTH)
            {
                int index = args.indexOf("-filter_complex");

                script = new File(this.folder, this.name + ".tmp.filter");
                Files.writeString(script.toPath(), args.get(index + 1), StandardCharsets.UTF_8);
                args.set(index, "-filter_complex_script");
                args.set(index + 1, script.getName());
            }

            System.out.println("Assembling a sequence with following arguments: " + args);

            File log = BBSSettings.videoEncoderLog.get() ? new File(this.folder, this.name + ".log") : BBSMod.getSettingsPath("video.log");
            ProcessBuilder builder = new ProcessBuilder(args);

            builder.directory(this.folder);
            builder.redirectError(log);

            Process process = builder.start();

            this.process = process;

            /* ffmpeg must never wait on the console (an overwrite prompt) */
            process.getOutputStream().close();

            if (this.cancelled)
            {
                process.destroyForcibly();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;

                while ((line = reader.readLine()) != null)
                {
                    if (line.startsWith("frame="))
                    {
                        try
                        {
                            this.frames = Integer.parseInt(line.substring(6).trim());
                        }
                        catch (NumberFormatException e)
                        {}
                    }
                }
            }

            int exit = process.waitFor();
            File video = VideoExportSession.findVideo(this.folder, this.name);

            if (exit == 0 && !this.cancelled && video != null)
            {
                this.result = video;
            }
            else if (video != null)
            {
                video.delete();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            /* The sound is in the video now; a failed one gets it back by recording again */
            if (audio != null)
            {
                audio.delete();
            }

            if (script != null)
            {
                script.delete();
            }

            this.process = null;
            this.done = true;
        }
    }

    /**
     * The whole ffmpeg command: the pieces as inputs, each seeked to where its segment starts, the
     * sound, the graph that cuts, darkens and joins them, and the output part of the sequence's
     * encoder arguments. Null when the arguments say nothing ffmpeg could write to.
     */
    private List<String> buildCommand(File audio)
    {
        List<String> output = outputArguments(this.settings.getArguments(audio != null), this.name, this.width, this.height, this.frameRate);

        if (output == null)
        {
            System.err.println("The sequence's encoder arguments have no input (-i) to cut the output part from!");

            return null;
        }

        List<String> args = new ArrayList<>();
        StringBuilder graph = new StringBuilder();
        Map<File, Float> durations = new HashMap<>();
        String size = this.width + ":" + this.height;
        int inputs = 0;

        args.add(FFMpegUtils.getFFMPEG());
        args.add("-y");
        args.add("-hide_banner");
        args.add("-nostats");
        args.add("-progress");
        args.add("pipe:1");

        for (int i = 0; i < this.segments.size(); i++)
        {
            Segment segment = this.segments.get(i);
            String label = "[s" + i + "]";

            if (segment.file == null)
            {
                graph.append("color=c=black:s=").append(this.width).append("x").append(this.height).append(":r=").append(this.frameRate)
                    .append(",trim=end_frame=").append(segment.count).append(",setsar=1").append(label).append(';');

                continue;
            }

            float duration = durations.computeIfAbsent(segment.file, SequenceAssembler::probeDuration);
            boolean pastEnd = duration > 0F && segment.seconds > duration - END_MARGIN;
            String filters;

            if (pastEnd)
            {
                /* The clip runs on past its film: its last frame stays up, as the preview shows it.
                 * A seek past the end would leave ffmpeg with no frame to hold (and crash 4.x). */
                args.add("-sseof");
                args.add(seconds(-END_MARGIN * 2));
                filters = "reverse,trim=end_frame=1,setpts=PTS-STARTPTS,fps=" + this.frameRate;
            }
            else
            {
                /* A quarter of a frame early, so rounding never drops the first one */
                float seek = Math.max(0F, segment.seconds - 0.25F / this.frameRate);

                if (seek > 0F)
                {
                    args.add("-ss");
                    args.add(seconds(seek));
                }

                args.add("-t");
                args.add(seconds(segment.count / (float) this.frameRate + 1F));
                filters = "setpts=PTS-STARTPTS,fps=" + this.frameRate;
            }

            args.add("-i");
            args.add(segment.file.getAbsolutePath());

            String piece = segment.fade == null ? label : "[p" + i + "]";

            graph.append('[').append(inputs).append(":v]").append(filters)
                .append(",scale=").append(size).append(",setsar=1")
                /* A piece that ends before the clip does keeps its last frame */
                .append(",tpad=stop_mode=clone:stop=-1,trim=end_frame=").append(segment.count).append(",setpts=PTS-STARTPTS")
                .append(piece).append(';');

            if (segment.fade != null)
            {
                /* Black over the frames, as much as the fade hides them: a small picture whose
                 * opacity is worked out per frame, stretched over the whole frame */
                graph.append("color=c=black:s=16x16:r=").append(this.frameRate)
                    .append(",format=rgba,geq=r=0:g=0:b=0:a='").append(fadeExpression(segment.fade)).append('\'')
                    .append(",trim=end_frame=").append(segment.count).append(",scale=").append(size).append(":flags=neighbor[m").append(i).append("];")
                    .append(piece).append("[m").append(i).append("]overlay=format=auto:eof_action=pass").append(label).append(';');
            }

            inputs += 1;
        }

        for (int i = 0; i < this.segments.size(); i++)
        {
            graph.append("[s").append(i).append(']');
        }

        graph.append("concat=n=").append(this.segments.size()).append(":v=1:a=0[v]");

        if (audio != null)
        {
            args.add("-i");
            args.add(audio.getAbsolutePath());
        }

        args.add("-filter_complex");
        args.add(graph.toString());
        args.add("-map");
        args.add("[v]");

        if (audio != null)
        {
            args.add("-map");
            args.add(inputs + ":a");
        }

        args.addAll(output);

        return args;
    }

    /** Seconds the way ffmpeg reads them: never in scientific notation */
    private static String seconds(float seconds)
    {
        return String.format(Locale.ROOT, "%.4f", seconds);
    }

    /**
     * The opacity of the black over each frame as an expression of the frame's number, a term
     * for every run of frames darkened alike.
     */
    private static String fadeExpression(int[] alphas)
    {
        StringBuilder expression = new StringBuilder();
        int start = 0;

        for (int i = 1; i <= alphas.length; i++)
        {
            if (i < alphas.length && alphas[i] == alphas[start])
            {
                continue;
            }

            if (alphas[start] > 0)
            {
                if (expression.length() > 0)
                {
                    expression.append('+');
                }

                expression.append("between(N,").append(start).append(',').append(i - 1).append(")*").append(alphas[start]);
            }

            start = i;
        }

        return expression.length() == 0 ? "0" : expression.toString();
    }

    /**
     * What comes after the encoder arguments' last input: how the video is encoded and where it
     * goes. The filters are left out (the pieces are filtered by the graph already) and so are
     * the stream choices, which the assembler makes itself. Null when there is no input to cut at.
     */
    public static List<String> outputArguments(String arguments, String name, int width, int height, int frameRate)
    {
        String[] tokens = arguments.trim().split(" +");
        int start = -1;

        for (int i = 0; i < tokens.length - 1; i++)
        {
            if (tokens[i].equals("-i"))
            {
                start = i + 2;
            }
        }

        if (start < 0)
        {
            return null;
        }

        Predicate<String> skipWithValue = (token) -> token.equals("-vf") || token.startsWith("-filter:v") || token.equals("-filter_complex") || token.equals("-map");
        List<String> output = new ArrayList<>();

        for (int i = start; i < tokens.length; i++)
        {
            String token = tokens[i];

            if (skipWithValue.test(token))
            {
                i += 1;

                continue;
            }

            token = token.replace("%WIDTH%", String.valueOf(width));
            token = token.replace("%HEIGHT%", String.valueOf(height));
            token = token.replace("%FPS%", String.valueOf(frameRate));
            token = token.replace("%NAME%", name);

            output.add(token);
        }

        return output;
    }

    /**
     * How long the piece is in seconds, or 0 when ffmpeg can't tell.
     */
    private static float probeDuration(File file)
    {
        VideoPlayer player = new VideoPlayer(file);

        try
        {
            player.ensureProbed();

            return player.isValid() ? player.getDuration() : 0F;
        }
        finally
        {
            player.delete();
        }
    }

    /**
     * A run of the timeline's frames showing one film's piece, or black.
     */
    private static class Segment
    {
        public final int count;

        /** The piece's video file, null for black */
        public File file;
        /** Where in the piece the segment's first frame is, in seconds */
        public float seconds;
        /** How dark the fades make each frame, null when they don't */
        public int[] fade;

        public Segment(int count)
        {
            this.count = count;
        }
    }
}
