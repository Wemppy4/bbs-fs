package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.film.sequence.SequenceFilms;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoExportSettings;
import mchorse.bbs_mod.utils.clips.Clip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Video export of a sequence, in one of two kinds:
 *
 * <ul>
 *     <li>a film's piece: the film played by itself from its start to its end, in the sequence
 *     editor's preview, with the film's own sound;</li>
 *     <li>the sequence: every film shown as its recorded piece, trimmed and placed the way the
 *     clips say, with the sequence's sound, pictures and subtitles over them.</li>
 * </ul>
 *
 * Either way it is recorded at the sequence's own export settings.
 */
public class SequenceExportSession extends VideoExportSession
{
    private final UISequencePanel panel;

    /** The piece's film clip, or null when the whole sequence is recorded */
    private FilmClip clip;
    private Film film;
    private String name;
    private int end;
    private long startedAtMs;

    /** The video the last recording that ran to its end went into */
    private File recorded;

    public SequenceExportSession(UISequencePanel panel)
    {
        this.panel = panel;
    }

    public boolean isPart()
    {
        return this.clip != null;
    }

    /**
     * Record the clip's film by itself, into a file of the given base name.
     */
    public boolean startPart(VideoExportSettings settings, FilmClip clip, Film film, String name, int textureId, int width, int height)
    {
        this.clip = clip;
        this.film = film;
        this.name = name;
        this.end = SequenceFilms.getDuration(film);

        return this.start(settings, textureId, width, height);
    }

    /**
     * Record the whole sequence out of its films' pieces.
     */
    public boolean startSequence(VideoExportSettings settings, int textureId, int width, int height)
    {
        this.clip = null;
        this.film = null;
        this.name = null;
        this.end = this.panel.getData().calculateDuration();

        return this.start(settings, textureId, width, height);
    }

    private boolean start(VideoExportSettings settings, int textureId, int width, int height)
    {
        this.recorded = null;

        long delayMs = (long) (Math.max(0F, BBSSettings.videoDelay.get()) * 1000F);
        boolean started = this.begin(settings, textureId, width, height, delayMs);

        if (!started)
        {
            this.clip = null;
            this.film = null;
        }

        return started;
    }

    @Override
    protected boolean prepare()
    {
        if (this.settings.exportAudio())
        {
            try
            {
                List<AudioClip> clips = this.clip != null ? this.film.camera.getClips(AudioClip.class) : this.getSequenceSound();
                File file = new File(this.settings.getFolder(), StringUtils.createTimestampFilename() + ".wav");

                if (!clips.isEmpty() && AudioRenderer.renderAudio(file, clips, this.end, 48000, 0, 0))
                {
                    this.audioFile = file;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        SequencePlayer player = this.panel.getPlayer();

        this.panel.setPlaying(false);
        this.panel.getRunner().shutdown();

        if (this.clip != null)
        {
            player.solo(this.clip, 0);
        }
        else
        {
            player.setPartsOnly(true);
            this.panel.setCursor(0);
        }

        return true;
    }

    /**
     * The sequence's sound track: its own audio clips, and the sound of each film's piece where
     * the film clip keeps its film's sound, trimmed the way the clip is.
     */
    private List<AudioClip> getSequenceSound()
    {
        Sequence sequence = this.panel.getData();
        List<AudioClip> clips = new ArrayList<>();

        for (Clip clip : sequence.clips.get())
        {
            if (clip instanceof AudioClip audio && audio.enabled.get())
            {
                clips.add(audio);
            }
            else if (clip instanceof FilmClip film && film.enabled.get() && film.filmAudio.get() && film.hasPart())
            {
                AudioClip piece = new AudioClip();
                Link link = film.getPartLink();

                piece.audio.set(link);
                piece.tick.set(film.tick.get());
                piece.duration.set(film.duration.get());
                piece.offset.set(film.offset.get());
                piece.layer.set(film.layer.get());
                clips.add(piece);
            }
        }

        return clips;
    }

    @Override
    protected String getMovieName()
    {
        if (this.name != null)
        {
            return this.name;
        }

        Sequence sequence = this.panel.getData();
        String id = sequence.getId();
        String base = StringUtils.resolveExportFilename(
            this.settings.filenameFormat(),
            id.substring(id.lastIndexOf('/') + 1),
            this.width,
            this.height,
            this.settings.getCaptureFrameRate(),
            this.end
        );

        return uniqueName(this.settings.getFolder(), base);
    }

    /**
     * A name no video in the folder has yet, so nothing gets overwritten (and ffmpeg never stops
     * to ask about it).
     */
    public static String uniqueName(File folder, String base)
    {
        String candidate = base;

        for (int i = 1; findVideo(folder, candidate) != null; i++)
        {
            candidate = base + " (" + i + ")";
        }

        return candidate;
    }

    /**
     * The video recorded under the given base name: the extension is up to the encoder's
     * arguments. Null when there is none.
     */
    public static File findVideo(File folder, String base)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return null;
        }

        String prefix = (base + ".").toLowerCase();

        for (File file : files)
        {
            String name = file.getName().toLowerCase();

            if (file.isFile() && name.startsWith(prefix) && !isExportArtifact(name.substring(prefix.length())))
            {
                return file;
            }
        }

        return null;
    }

    @Override
    protected void onWarmupStarted()
    {
        this.panel.setPlaying(false);
    }

    @Override
    protected void onRecordingStarted()
    {
        this.startedAtMs = System.currentTimeMillis();
        this.panel.setPlaying(true);
    }

    /**
     * How much of the recording is done, from 0 to 1.
     */
    public float getProgress()
    {
        if (this.state != State.RECORDING || this.end <= 0)
        {
            return 0F;
        }

        int tick = this.clip != null ? this.panel.getPlayer().getTick() : this.panel.getCursor();

        return MathUtils.clamp(tick / (float) this.end, 0F, 1F);
    }

    public long getStartedAtMs()
    {
        return this.startedAtMs;
    }

    @Override
    protected boolean isFinished()
    {
        if (!this.panel.isRunning())
        {
            return true;
        }

        int tick = this.clip != null ? this.panel.getPlayer().getTick() : this.panel.getCursor();

        return tick >= this.end;
    }

    @Override
    protected void teardown(boolean cancelled)
    {
        String name = this.getRecordingName();

        this.recorded = cancelled || name == null ? null : findVideo(this.settings.getFolder(), name);

        SequencePlayer player = this.panel.getPlayer();

        this.panel.setPlaying(false);
        player.solo(null, 0);
        player.setPartsOnly(false);
        player.stop();
        this.panel.getRunner().shutdown();
        this.panel.preview.applySize();

        this.clip = null;
        this.film = null;
    }

    /**
     * The video the last recording that ran to its end went into, or null.
     */
    public File getRecordedVideo()
    {
        return this.recorded;
    }
}
