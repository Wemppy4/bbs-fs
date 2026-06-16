package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.clips.Clips;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

public class UIFilmRecorder extends UIElement
{
    public UIFilmPanel editor;

    private UIExit exit = new UIExit(this);
    private int end;

    public boolean resetReplays = true;

    private boolean preparing;
    private long startRecordingAtMs;
    private File pendingAudioFile;
    private int pendingTextureId;
    private int pendingWidth;
    private int pendingHeight;
    private boolean restorePaused;

    private boolean stopCancelled;
    private FinishedListener finishedListener;

    public UIFilmRecorder(UIFilmPanel editor)
    {
        super();

        this.editor = editor;

        this.noCulling();
    }

    public boolean isRecording()
    {
        return getRecorder().isRecording();
    }

    public boolean isExporting()
    {
        return this.preparing || this.isRecording();
    }

    /**
     * Sets a one-shot listener invoked when the current export finishes.
     * Invoked exactly once after {@link #stop()} completes its teardown,
     * then cleared. Pass {@code null} to remove the listener.
     */
    public void setFinishedListener(FinishedListener listener)
    {
        this.finishedListener = listener;
    }

    /**
     * Cancel the current export. Equivalent to {@link #stop()} but marks the
     * teardown as user-initiated, so listeners can distinguish "finished
     * naturally" from "aborted".
     */
    public void cancel()
    {
        if (!this.isExporting())
        {
            return;
        }

        this.stopCancelled = true;
        this.stop();
    }

    private UIContext getUIContext()
    {
        return this.editor.getContext();
    }

    private VideoRecorder getRecorder()
    {
        return BBSModClient.getVideoRecorder();
    }

    private boolean isRunning()
    {
        return this.editor.isRunning();
    }

    public void openMovies()
    {
        UIUtils.openFolder(BBSRendering.getVideoFolder());
    }

    public void startRecording(int duration, Texture texture)
    {
        this.startRecording(duration, texture.id, texture.width, texture.height);
    }

    public void startRecording(int duration, int id, int w, int h)
    {
        VideoRecorder recorder = this.getRecorder();
        UIContext context = this.getUIContext();

        if (this.isRunning() || this.preparing || recorder.isRecording() || duration <= 0)
        {
            return;
        }

        this.restorePaused = this.editor.getController().isPaused();

        int min = this.editor.cameraEditor.clips.loopMin;
        int max = this.editor.cameraEditor.clips.loopMax;
        boolean looping = BBSSettings.editorLoop.get();

        this.end = looping && min != max ? Math.max(min, max) : duration;

        this.editor.setCursor(looping ? Math.min(min, max) : 0);
        this.editor.notifyServer(ActionState.RESTART);

        if (this.resetReplays)
        {
            this.editor.getController().createEntities();
        }

        context.menu.main.setEnabled(false);
        context.menu.overlay.add(this);
        context.menu.getRoot().add(this.exit);

        File audioFile = null;

        try
        {
            if (BBSSettings.videoExportAudio.get())
            {
                Clips camera = this.editor.getData().camera;
                List<AudioClip> audioClips = camera.getClips(AudioClip.class);

                String name = StringUtils.createTimestampFilename() + ".wav";
                File file = new File(BBSRendering.getVideoFolder(), name);
                Vector2i range = BBSSettings.editorLoop.get() ? this.editor.getLoopingRange() : new Vector2i();

                if (AudioRenderer.renderAudio(file, audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
                {
                    audioFile = file;
                }
            }
        }
        catch (Exception e)
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, IKey.constant(e.getMessage())));
            this.stopCancelled = true;
            this.stop();
            return;
        }

        this.pendingAudioFile = audioFile;
        this.pendingTextureId = id;
        this.pendingWidth = w;
        this.pendingHeight = h;

        float delaySeconds = Math.max(0F, BBSSettings.videoDelay.get());
        long delayMs = (long) (delaySeconds * 1000F);

        if (delayMs > 0)
        {
            this.editor.getController().setPaused(true);

            this.preparing = true;
            this.startRecordingAtMs = System.currentTimeMillis() + delayMs;
        }
        else
        {
            this.preparing = false;
            this.startRecordingAtMs = 0L;
            this.beginRecording(context, recorder);
        }
    }

    private void beginRecording(UIContext context, VideoRecorder recorder)
    {
        if (recorder.isRecording())
        {
            return;
        }

        try
        {
            recorder.startRecording(this.pendingAudioFile, this.pendingTextureId, this.pendingWidth, this.pendingHeight);
        }
        catch (Exception e)
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, IKey.constant(e.getMessage())));
            this.stopCancelled = true;
            this.stop();
            return;
        }

        this.editor.getController().setPaused(false);
        this.editor.togglePlayback();
    }

    public void stop()
    {
        UIContext context = this.getUIContext();

        context.render.postRunnable(this.exit::removeFromParent);

        this.preparing = false;
        this.startRecordingAtMs = 0L;

        if (this.getRecorder().isRecording())
        {
            try
            {
                this.getRecorder().stopRecording();
            }
            catch (Exception e)
            {}
        }

        this.pendingAudioFile = null;
        this.pendingTextureId = 0;
        this.pendingWidth = 0;
        this.pendingHeight = 0;

        this.editor.getController().setPaused(this.restorePaused);

        this.editor.restorePreviewSize();

        if (this.isRunning())
        {
            this.editor.togglePlayback();
        }

        context.menu.main.setEnabled(true);
        context.render.postRunnable(this::removeFromParent);

        boolean cancelled = this.stopCancelled;
        this.stopCancelled = false;

        FinishedListener listener = this.finishedListener;
        this.finishedListener = null;

        if (listener != null)
        {
            listener.onFinished(cancelled);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.preparing)
        {
            if (BBSSettings.recordingOverlays.get())
            {
                long remainingMs = Math.max(0L, this.startRecordingAtMs - System.currentTimeMillis());
                int countdown = Math.max(0, (int) Math.ceil(remainingMs / 50D));
                Area previewArea = this.editor.preview.getViewport();
                int x = previewArea.x + 5;
                int y = previewArea.y + 5;

                BBSRendering.renderRecordingTimerOverlay(context.batcher, String.valueOf(TimeUtils.toSeconds(countdown)), x, y);
            }

            if (System.currentTimeMillis() >= this.startRecordingAtMs)
            {
                this.preparing = false;
                this.beginRecording(context, this.getRecorder());
            }

            return;
        }

        int ticks = this.editor.getCursor();

        if (!this.isRecording())
        {
            return;
        }

        if (!this.isRunning() || ticks >= this.end)
        {
            this.stop();
        }
    }

    public static class UIExit extends UIElement
    {
        private UIFilmRecorder recorder;

        public UIExit(UIFilmRecorder recorder)
        {
            this.recorder = recorder;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.recorder.cancel();

                return true;
            }

            return super.subKeyPressed(context);
        }
    }

    @FunctionalInterface
    public interface FinishedListener
    {
        void onFinished(boolean cancelled);
    }
}
