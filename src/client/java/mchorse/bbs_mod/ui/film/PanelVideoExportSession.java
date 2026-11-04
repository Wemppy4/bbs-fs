package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoExportSettings;
import mchorse.bbs_mod.utils.clips.Clips;
import org.joml.Vector2i;

import java.io.File;
import java.util.List;

/**
 * Video export of the film panel's preview texture. Renders the audio track,
 * restarts the film to the (loop-aware) start, pauses/resumes the editor around
 * the warm-up, and drives the {@link UIFilmRecorder} overlay.
 */
public class PanelVideoExportSession extends VideoExportSession
{
    private final UIFilmRecorder ui;
    private final UIFilmPanel editor;

    private int duration;
    private int start;
    private int end;
    private boolean restorePaused;
    private long startedAtMs;

    public PanelVideoExportSession(UIFilmRecorder ui, UIFilmPanel editor)
    {
        this.ui = ui;
        this.editor = editor;
    }

    public boolean start(VideoExportSettings settings, int duration, int textureId, int width, int height)
    {
        this.duration = duration;

        long delayMs = (long) (Math.max(0F, BBSSettings.videoDelay.get()) * 1000F);

        return this.begin(settings, textureId, width, height, delayMs);
    }

    @Override
    protected boolean prepare()
    {
        try
        {
            if (this.settings.exportAudio())
            {
                Clips camera = this.editor.getData().camera;
                List<AudioClip> audioClips = camera.getClips(AudioClip.class);

                String name = StringUtils.createTimestampFilename() + ".wav";
                File file = new File(this.settings.getFolder(), name);
                Vector2i range = BBSSettings.editorLoop.get() ? this.editor.getLoopingRange() : new Vector2i();

                if (AudioRenderer.renderAudio(file, audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
                {
                    this.audioFile = file;
                }
            }
        }
        catch (Exception e)
        {
            UIOverlay.addOverlay(this.editor.getContext(), new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, IKey.constant(e.getMessage())));

            return false;
        }

        this.restorePaused = this.editor.getController().isPaused();

        int min = this.editor.cameraEditor.clips.loopMin;
        int max = this.editor.cameraEditor.clips.loopMax;
        boolean looping = BBSSettings.editorLoop.get();

        this.start = looping ? Math.min(min, max) : 0;
        this.end = looping && min != max ? Math.max(min, max) : this.duration;

        this.editor.setCursor(this.start);
        this.editor.fitTimelinesToExport(this.start, this.end);
        this.editor.notifyServer(ActionState.RESTART);

        if (this.ui.resetReplays)
        {
            this.editor.getController().createEntities();
        }

        this.ui.attachOverlay();

        return true;
    }

    @Override
    protected String getMovieName()
    {
        Film film = this.editor.getData();
        String base = StringUtils.resolveExportFilename(
            this.settings.filenameFormat(),
            film == null ? "" : film.getId(),
            this.width,
            this.height,
            this.settings.getCaptureFrameRate(),
            film == null ? 0 : film.camera.calculateDuration()
        );

        return uniqueName(this.settings.getFolder(), base);
    }

    /**
     * Avoid overwriting a previous export - and, with it, hanging ffmpeg (whose default args
     * carry no {@code -y}): when a file with this base name already exists, append " (n)".
     */
    private static String uniqueName(File folder, String base)
    {
        String candidate = base;

        for (int i = 1; nameTaken(folder, candidate); i++)
        {
            candidate = base + " (" + i + ")";
        }

        return candidate;
    }

    private static boolean nameTaken(File folder, String base)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return false;
        }

        String prefix = (base + ".").toLowerCase();

        for (File file : files)
        {
            String name = file.getName().toLowerCase();

            /* Only a video of an earlier export takes the name: the audio track this
             * export just rendered sits in the same folder under the same base name and
             * must not bump every audio export to a "(1)" */
            if (name.startsWith(prefix) && !isExportArtifact(name.substring(prefix.length())))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onWarmupStarted()
    {
        this.editor.getController().setPaused(true);
    }

    /**
     * How much of the exported range is already recorded, from 0 to 1. Zero until the
     * recording itself starts.
     */
    public float getProgress(float transition)
    {
        if (this.state != State.RECORDING || this.end <= this.start)
        {
            return 0F;
        }

        return MathUtils.clamp((this.editor.getCursor(transition) - this.start) / (float) (this.end - this.start), 0F, 1F);
    }

    /**
     * Rough time left, extrapolated from how long the recorded part took. -1 until there is
     * enough of it to go on.
     */
    public long getRemainingMs(float progress)
    {
        long elapsed = System.currentTimeMillis() - this.startedAtMs;

        if (this.state != State.RECORDING || progress < 0.01F || elapsed < 1000L)
        {
            return -1L;
        }

        return (long) (elapsed * (1F - progress) / progress);
    }

    @Override
    protected void onRecordingStarted()
    {
        this.startedAtMs = System.currentTimeMillis();
        this.editor.getController().setPaused(false);
        this.editor.togglePlayback();
    }

    @Override
    protected boolean isFinished()
    {
        return !this.editor.isRunning() || this.editor.getCursor() >= this.end;
    }

    @Override
    protected void teardown(boolean cancelled)
    {
        this.editor.getController().setPaused(this.restorePaused);
        this.editor.preview.applySize();
        this.editor.restoreTimelineViews();

        if (this.editor.isRunning())
        {
            this.editor.togglePlayback();
        }

        if (!cancelled)
        {
            this.ui.showFinished(this.getRecordingName());
        }

        this.ui.detachOverlay();
    }
}
