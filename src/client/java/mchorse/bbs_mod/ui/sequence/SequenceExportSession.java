package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.SequenceFilms;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoExportSettings;

import java.io.File;
import java.util.List;

/**
 * Video export of a film's piece for a sequence: the film played by itself from its start to its
 * end, in the sequence editor's preview, with the film's own sound, at the sequence's own export
 * settings. The pieces are then put together without the game (see
 * {@link mchorse.bbs_mod.film.sequence.SequenceAssembler}).
 */
public class SequenceExportSession extends VideoExportSession
{
    private final UISequencePanel panel;

    /** The piece's film clip */
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

    /**
     * Record the clip's film by itself, into a file of the given base name.
     */
    public boolean startPart(VideoExportSettings settings, FilmClip clip, Film film, String name, int textureId, int width, int height)
    {
        this.clip = clip;
        this.film = film;
        this.name = name;
        this.end = SequenceFilms.getDuration(film);
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
                List<AudioClip> clips = this.film.camera.getClips(AudioClip.class);
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

        player.solo(this.clip, 0);

        return true;
    }

    @Override
    protected String getMovieName()
    {
        return this.name;
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

        return MathUtils.clamp(this.panel.getPlayer().getTick() / (float) this.end, 0F, 1F);
    }

    /** How many ticks the recording that runs takes, 0 before one is set up */
    public int getEnd()
    {
        return this.end;
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

        return this.panel.getPlayer().getTick() >= this.end;
    }

    @Override
    protected void teardown(boolean cancelled)
    {
        String name = this.getRecordingName();

        this.recorded = cancelled || name == null ? null : findVideo(this.settings.getFolder(), name);

        SequencePlayer player = this.panel.getPlayer();

        this.panel.setPlaying(false);
        player.solo(null, 0);
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
