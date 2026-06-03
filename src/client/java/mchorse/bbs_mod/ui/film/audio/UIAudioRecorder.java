package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.io.File;

public class UIAudioRecorder extends UIElement
{
    private static String lastInput = "";

    /** Count-in before the scene starts playing and the microphone is captured. */
    private static final long COUNT_IN_MS = 1000L;
    /** How long a mouse button must be held to confirm finishing/cancelling. */
    private static final long HOLD_MS = 500L;
    /** Side length (px) of the hold-progress square drawn at the cursor. */
    private static final int HOLD_SQUARE = 60;

    private final OpenALRecorder recorder;
    private final UIFilmPanel filmPanel;
    /** Cursor tick the recording was started from; the scene returns here when it ends. */
    private final int originCursor;

    private float volume;
    private float[][] waveform;

    private final long startTime = System.currentTimeMillis();
    private boolean recording;
    private boolean ended;

    /** Held mouse button driving the confirm gesture: 0 = LMB (finish), 1 = RMB (cancel), -1 = none. */
    private int holdButton = -1;
    private long holdStart;

    public UIAudioRecorder(UIFilmPanel filmPanel, OpenALRecorder recorder, int originCursor)
    {
        this.filmPanel = filmPanel;
        this.recorder = recorder;
        this.originCursor = originCursor;

        this.eventPropagataion(EventPropagation.BLOCK);
    }

    public static void addOption(UIFilmPanel filmPanel, ContextMenuManager menu)
    {
        UIContext context = filmPanel.getContext();
        String suggestion = suggestAudioName(filmPanel);
        String value = lastInput.isEmpty() ? suggestion : lastInput;

        menu.action(Icons.SOUND, UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE, () ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_TITLE,
                UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_DESCRIPTION,
                (t) ->
                {
                    String newT = t.isEmpty() ? suggestion : t;
                    int origin = filmPanel.getCursor();
                    UIElement overlay = context.menu.overlay;

                    /* The wave is delivered on the recorder thread once it stops (and only when
                     * finished, not cancelled), so bounce the clip placement onto the main thread. */
                    OpenALRecorder recorder = new OpenALRecorder((wave) ->
                        MinecraftClient.getInstance().execute(() -> saveRecording(filmPanel, newT, value, origin, wave))
                    );

                    UIAudioRecorder audioRecorder = new UIAudioRecorder(filmPanel, recorder, origin);

                    audioRecorder.full(overlay);
                    audioRecorder.resize();
                    overlay.add(audioRecorder);
                }
            );

            panel.text.setText(value);
            panel.text.path();

            UIOverlay.addOverlay(context, panel);
        });
    }

    /**
     * Default recording name: the film's id (which carries its folder path) followed by the
     * first free trailing number, so repeated recordings of the same film don't clash. Falls
     * back to a timestamp when no film is loaded or it has no id yet.
     */
    private static String suggestAudioName(UIFilmPanel filmPanel)
    {
        Film film = filmPanel.getData();
        String base = film == null ? null : film.getId();

        if (base == null || base.isEmpty())
        {
            return StringUtils.createTimestampFilename();
        }

        File folder = BBSMod.getAudioFolder();
        int number = 1;

        while (new File(folder, base + "/" + number + ".wav").exists())
        {
            number++;
        }

        return base + "/" + number;
    }

    /**
     * Write the recorded wave and drop an audio clip onto the timeline at the tick the
     * recording started from, on a fresh top layer. Runs on the main thread.
     */
    private static void saveRecording(UIFilmPanel filmPanel, String name, String defaultName, int tick, Wave wave)
    {
        try
        {
            File file = new File(BBSMod.getAudioFolder(), name + ".wav");
            Clips clips = filmPanel.cameraEditor.clips.getClips();
            AudioClientClip clip = new AudioClientClip();

            file.getParentFile().mkdirs();
            WaveWriter.write(file, wave);

            clip.audio.set(Link.assets("audio/" + name + ".wav"));
            clip.tick.set(tick);
            clip.duration.set((int) (wave.getDuration() * 20));
            clip.layer.set(clips.getTopLayer() + 1);

            clips.addClip(clip);
            filmPanel.cameraEditor.clips.clearSelection();
            filmPanel.cameraEditor.clips.pickClip(clip);

            lastInput = name.equals(defaultName) ? "" : name;
        }
        catch (Exception e)
        {}
    }

    /** After the count-in: begin capturing the microphone and play the scene from the cursor. */
    private void startRecording()
    {
        this.recording = true;

        new Thread(this.recorder, "BBS microphone recorder").start();

        if (!this.filmPanel.isRunning())
        {
            this.filmPanel.togglePlayback();
        }
    }

    /**
     * End the take. {@code cancel} discards it (no file, no clip); otherwise the recorder
     * delivers the wave and {@link #saveRecording} places the clip. Either way playback stops
     * and the cursor snaps back to where the recording began.
     */
    private void end(UIContext context, boolean cancel)
    {
        if (this.ended)
        {
            return;
        }

        this.ended = true;

        /* Nothing was captured during the count-in, so an early exit is always a discard. */
        if (this.recording && !cancel)
        {
            this.recorder.stop();
        }
        else
        {
            this.recorder.cancel();
        }

        if (this.filmPanel.isRunning())
        {
            this.filmPanel.togglePlayback();
        }

        this.filmPanel.setCursor(this.originCursor);

        context.render.postRunnable(this::removeFromParent);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        /* LMB begins a finish hold, RMB a cancel hold; the gesture completes in render()
         * once the button has been held for HOLD_MS. */
        if (!this.ended && (context.mouseButton == 0 || context.mouseButton == 1))
        {
            this.holdButton = context.mouseButton;
            this.holdStart = System.currentTimeMillis();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        /* Keep Escape as an instant cancel so the overlay never leaks the recorder if the
         * mouse gesture is unavailable; finishing/cancelling is otherwise mouse-driven. */
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.end(context, true);

            return true;
        }

        return super.subKeyPressed(context);
    }

    /**
     * Drive the held-button confirm gesture: bail if the button was released early,
     * otherwise draw the centre-filling square at the cursor and complete the action once
     * it has been held for {@link #HOLD_MS}. Green ({@link Colors#POSITIVE}) finishes,
     * red ({@link Colors#NEGATIVE}) cancels.
     */
    private void renderHold(UIContext context)
    {
        if (!Window.isMouseButtonPressed(this.holdButton))
        {
            this.holdButton = -1;

            return;
        }

        float progress = Math.min(1F, (System.currentTimeMillis() - this.holdStart) / (float) HOLD_MS);
        int color = this.holdButton == 1 ? Colors.NEGATIVE : Colors.POSITIVE;
        int cx = context.mouseX;
        int cy = context.mouseY;
        int half = HOLD_SQUARE / 2;
        int fill = (int) (half * progress);

        context.batcher.box(cx - half, cy - half, cx + half, cy + half, Colors.A50 | color);
        context.batcher.box(cx - fill, cy - fill, cx + fill, cy + fill, Colors.A100 | color);

        if (progress >= 1F)
        {
            boolean cancel = this.holdButton == 1;

            this.holdButton = -1;
            this.end(context, cancel);
        }
    }

    @Override
    public void render(UIContext context)
    {
        long elapsed = System.currentTimeMillis() - this.startTime;

        if (!this.recording && !this.ended && elapsed >= COUNT_IN_MS)
        {
            this.startRecording();
        }

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);

        if (this.recording)
        {
            this.renderRecording(context);
        }
        else
        {
            this.renderCountdown(context, elapsed);
        }

        if (this.holdButton != -1)
        {
            this.renderHold(context);
        }

        super.render(context);
    }

    private void renderCountdown(UIContext context, long elapsed)
    {
        float remaining = Math.max(0F, (COUNT_IN_MS - elapsed) / 1000F);
        String label = UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_COUNTDOWN.format(remaining).get();
        int x = this.area.mx();
        int y = this.area.my();
        int w = context.batcher.getFont().getWidth(label);

        context.batcher.icon(Icons.SPHERE, Colors.setA(Colors.RED, 0.5F), x - w / 2 - 12, y + context.batcher.getFont().getHeight() / 2, 0.5F, 0.5F);
        context.batcher.textShadow(label, x - w / 2, y);
    }

    private void renderRecording(UIContext context)
    {
        this.volume = Lerps.lerp(this.volume, Interpolations.CUBIC_OUT.interpolate(0F, 1F, this.recorder.getVolume()), 0.5F);

        String label = UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_LABEL
            .format(this.recorder.getTime() / 1000F)
            .get();
        int x = this.area.mx();
        int y = this.area.my();
        int w = context.batcher.getFont().getWidth(label);
        double volume = Interpolations.EXP_OUT.interpolate(0F, 1F, this.volume);

        context.batcher.icon(Icons.SPHERE, Colors.RED | Colors.A100, x - w / 2 - 12, y + context.batcher.getFont().getHeight() / 2, 0.5F, 0.5F);
        context.batcher.textShadow(label, x - w / 2, y);

        label = UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_SUBLABEL.get();
        w = context.batcher.getFont().getWidth(label);

        context.batcher.textShadow(label, x - w / 2, this.area.y(0.75F));

        x -= w / 2;

        context.batcher.box(x, y + 16, x + w, y + 20, Colors.A100);
        context.batcher.box(x, y + 16, x + (int) (w * volume), y + 20, Colors.WHITE);

        this.renderWaveform(context, x, y + 24, w, 28);
    }

    /**
     * Live incoming microphone waveform: a mirrored peak envelope scrolling in from the
     * right, clamped to the same width as the volume bar above it.
     */
    private void renderWaveform(UIContext context, int x, int top, int w, int h)
    {
        if (w <= 0)
        {
            return;
        }

        this.waveform = this.recorder.getWaveform(this.waveform);

        float[] peak = this.waveform[0];
        float[] average = this.waveform[1];
        int n = peak.length;
        int mid = top + h / 2;
        int half = h / 2;
        int averageColor = Colors.mulRGB(Colors.WHITE, 0.8F);

        context.batcher.box(x, top, x + w, top + h, Colors.A50);

        for (int px = 0; px < w; px++)
        {
            int idx = Math.min(n - 1, (int) (px / (float) w * n));
            int cx = x + px;

            /* Bright peak envelope, then the darker mean amplitude on top as an inner core,
             * matching mchorse.bbs_mod.audio.Waveform's max/average layering. */
            int peakAmp = (int) (Interpolations.EXP_OUT.interpolate(0F, 1F, Math.min(1F, peak[idx])) * half);
            int avgAmp = (int) (Interpolations.EXP_OUT.interpolate(0F, 1F, Math.min(1F, average[idx])) * half);

            context.batcher.box(cx, mid - peakAmp, cx + 1, mid + peakAmp + 1, Colors.WHITE);
            context.batcher.box(cx, mid - avgAmp, cx + 1, mid + avgAmp + 1, averageColor);
        }
    }
}