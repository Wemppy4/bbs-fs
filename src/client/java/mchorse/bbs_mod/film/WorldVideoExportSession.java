package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.WorldExportWindowSession;
import mchorse.bbs_mod.utils.clips.Clips;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

import java.io.File;
import java.util.List;

/**
 * Video export of the live world, driven by the F4 (record) and F6 (play film
 * and record) keybinds. Owns the export window sizing, the custom render size,
 * and — when recording a film — pausing it for the warm-up and stopping the
 * recording once the film finishes.
 */
public class WorldVideoExportSession extends VideoExportSession
{
    private final WorldExportWindowSession windowSession = new WorldExportWindowSession();

    /** Id of the film being played and recorded (F6), or {@code null} for a plain world recording (F4). */
    private String filmId;
    /** The film being recorded (F6), used to render its audio track; {@code null} for a plain world recording (F4). */
    private Film film;
    private boolean firstTickPaused;

    public String getFilmId()
    {
        return this.filmId;
    }

    /**
     * Start a world recording. Pass a film id and its data to also play that film
     * and stop when it finishes (F6), or {@code null}/{@code null} to record the
     * live world (F4). The film is used to render its audio track when the
     * "export audio" setting is on.
     */
    public boolean start(String filmId, Film film)
    {
        if (this.isExporting() || this.getRecorder().isRecording())
        {
            return false;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        VideoSize size = this.getVideoSize(window);

        this.applyWindowSize(size);

        this.filmId = filmId;
        this.film = film;
        this.firstTickPaused = false;

        long delayMs = (long) (Math.max(0F, BBSSettings.videoDelay.get()) * 1000F);
        boolean started = this.begin(BBSRendering.getTexture().id, size.width, size.height, delayMs);

        if (!started)
        {
            this.windowSession.restore();
            this.filmId = null;
            this.film = null;

            return false;
        }

        if (filmId != null)
        {
            Films.playFilm(filmId, false);
        }

        return true;
    }

    @Override
    protected boolean prepare()
    {
        /* When recording a film (F6) with "export audio" on, render its audio track to a
         * temp WAV so the recorder muxes it into the video (same as the film-panel export).
         * Best-effort: a failure here just yields a silent video, never aborts the render. */
        if (this.film != null && BBSSettings.videoExportAudio.get())
        {
            try
            {
                Clips camera = this.film.camera;
                List<AudioClip> audioClips = camera.getClips(AudioClip.class);
                File file = new File(BBSRendering.getVideoFolder(), StringUtils.createTimestampFilename() + ".wav");

                if (AudioRenderer.renderAudio(file, audioClips, camera.calculateDuration(), 48000, 0, 0))
                {
                    this.audioFile = file;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return true;
    }

    @Override
    protected void applyExportTarget()
    {
        /* Keep the export resolution during warm-up too, so the first captured frame is already settled. */
        BBSRendering.setCustomSize(true, this.width, this.height);
    }

    @Override
    protected boolean shouldAbortWarmup()
    {
        /* We are playing a film and it is no longer running (never started, or an empty film already finished). */
        return this.filmId != null && !BBSModClient.getFilms().has(this.filmId);
    }

    @Override
    protected boolean isWarmupReady()
    {
        if (this.filmId == null || this.firstTickPaused)
        {
            return true;
        }

        BaseFilmController controller = BBSModClient.getFilms().getController(this.filmId);

        if (controller == null || controller.getTick() < 1)
        {
            return false;
        }

        if (!controller.paused)
        {
            controller.togglePause();
        }

        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendActionState(this.filmId, ActionState.PAUSE, controller.getTick());
        }

        this.firstTickPaused = true;

        return true;
    }

    @Override
    protected void onRecordingStarted()
    {
        if (this.firstTickPaused)
        {
            BaseFilmController controller = BBSModClient.getFilms().getController(this.filmId);
            int tick = 0;

            if (controller != null)
            {
                tick = Math.max(controller.getTick(), 0);

                if (controller.paused)
                {
                    controller.togglePause();
                }
            }

            if (ClientNetwork.isIsBBSModOnServer())
            {
                ClientNetwork.sendActionState(this.filmId, ActionState.PLAY, tick);
            }
        }

        BBSRendering.setCustomSize(this.getRecorder().isRecording(), this.width, this.height);
    }

    @Override
    protected boolean isFinished()
    {
        /* Plain world recording stops only on the key; a film recording stops when the film finishes (is removed). */
        return this.filmId != null && !BBSModClient.getFilms().has(this.filmId);
    }

    @Override
    protected void teardown(boolean cancelled)
    {
        if (cancelled && this.filmId != null && BBSModClient.getFilms().has(this.filmId))
        {
            Films.playFilm(this.filmId, false);
        }

        BBSRendering.setCustomSize(false, 0, 0);
        this.windowSession.restore();

        this.filmId = null;
        this.film = null;
        this.firstTickPaused = false;
    }

    private void applyWindowSize(VideoSize size)
    {
        if (BBSSettings.worldExportResizeWindow.get())
        {
            this.windowSession.begin(size.width, size.height);
        }
        else
        {
            this.windowSession.clear();
        }
    }

    private VideoSize getVideoSize(Window window)
    {
        if (BBSSettings.worldExportResizeWindow.get())
        {
            return new VideoSize(even(BBSSettings.videoWidth.get()), even(BBSSettings.videoHeight.get()));
        }

        return new VideoSize(even(window.getWidth()), even(window.getHeight()));
    }

    private static int even(int value)
    {
        value = Math.max(value, 2);

        return value % 2 == 0 ? value : value - 1;
    }

    private static class VideoSize
    {
        private final int width;
        private final int height;

        private VideoSize(int width, int height)
        {
            this.width = width;
            this.height = height;
        }
    }
}
