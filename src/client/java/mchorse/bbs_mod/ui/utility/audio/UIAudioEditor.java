package mchorse.bbs_mod.ui.utility.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.ColorCode;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

public class UIAudioEditor extends UITimelineCanvas
{
    private static final Area AREA = new Area();
    private static final String CLIPBOARD_KEY = "_CopyColorCodes";
    /** Edits are written this long after the last change, so a drag doesn't write on every frame. */
    private static final long AUTOSAVE_DELAY = 1000L;

    public UIColor color;
    public UIColor primary;
    public UIColor secondary;

    /** Writes the color codes of the open audio, set by the panel. */
    public Runnable saver;

    private Link audio;
    private Waveform waveform;
    private List<ColorCode> colorCodes = new ArrayList<>();

    private SoundBuffer buffer;
    private SoundPlayer player;

    private int dragging = -2;

    /** Stretches the waveform strip vertically, 1 is the height from the settings. */
    private float verticalZoom = 1F;
    private float verticalZoomTarget = 1F;
    private long verticalZoomTime;

    /** Cue markers of the open audio, handed back to the waveform whenever the codes change. */
    private float[] cues;

    /** The code being drawn with Ctrl, and the time the drawing started at. */
    private ColorCode dragged;
    private float draggedAnchor;
    private ColorCode current;

    private boolean dirty;
    private long lastChange;

    public UIAudioEditor()
    {
        this.color = new UIColor((c) ->
        {
            this.current.color = c;
            this.markDirty();
        });
        this.color.relative(this).x(1F, -10).y(1F, -10).wh(80, 20).anchor(1F, 1F);

        /* Brushes for drawing codes: Ctrl + left mouse draws with the first, Ctrl + right mouse with the second */
        this.primary = new UIColor((c) -> BBSSettings.audioEditorPrimaryColor.set(c)).noLabel();
        this.primary.setColor(BBSSettings.audioEditorPrimaryColor.get());
        this.primary.tooltip(UIKeys.AUDIO_PRIMARY_COLOR);
        this.primary.relative(this).x(10).y(1F, -10).wh(20, 20).anchor(0F, 1F);
        this.secondary = new UIColor((c) -> BBSSettings.audioEditorSecondaryColor.set(c)).noLabel();
        this.secondary.setColor(BBSSettings.audioEditorSecondaryColor.get());
        this.secondary.tooltip(UIKeys.AUDIO_SECONDARY_COLOR);
        this.secondary.relative(this.primary).x(1F, 5).wh(20, 20);

        this.context((menu) ->
        {
            if (this.waveform == null)
            {
                return;
            }

            menu.icon(MenuVerb.ADD, () ->
            {
                ColorCode code = this.createNewCode();

                this.colorCodes.add(code);
                this.setCurrent(code);
                this.markDirty();
            }).label(UIKeys.AUDIO_CONTEXT_ADD);

            menu.icon(MenuVerb.REMOVE, this::removeCurrent).label(UIKeys.AUDIO_CONTEXT_REMOVE).enabled(this.current != null);
            if (this.current != null)
            {
                menu.action(Icons.COPY, UIKeys.GENERAL_COPY, this::copyCurrent);
                menu.action(Icons.SHIFT_TO, UIKeys.AUDIO_CONTEXT_TRIM, this::trimToCursor);
            }

            if (Window.getClipboardMap(CLIPBOARD_KEY) != null)
            {
                menu.action(Icons.PASTE, UIKeys.GENERAL_PASTE, this::paste);
            }
        });

        this.keys().register(Keys.COPY, this::copyCurrent).active(() -> this.current != null);
        this.keys().register(Keys.PASTE, this::paste).active(() -> this.waveform != null);
        this.keys().register(Keys.CLIP_DURATION, this::trimToCursor).label(UIKeys.AUDIO_CONTEXT_TRIM).active(() -> this.current != null);
        this.keys().register(Keys.DELETE, this::removeCurrent).label(UIKeys.AUDIO_CONTEXT_REMOVE).active(() -> this.current != null);

        this.add(this.color, this.primary, this.secondary);
    }

    private ColorCode createNewCode()
    {
        float time = (float) this.xAxis.from(this.getContext().mouseX);
        ColorCode code = new ColorCode();

        code.start = time;
        code.end = time + 0.3F;
        code.color = BBSSettings.audioEditorPrimaryColor.get();

        return code;
    }

    private void removeCurrent()
    {
        this.colorCodes.remove(this.current);
        this.setCurrent(null);
        this.markDirty();
    }

    private void copyCurrent()
    {
        MapType data = new MapType();
        ListType codes = new ListType();

        codes.add(this.current.toData());
        data.put("codes", codes);
        Window.setClipboard(data, CLIPBOARD_KEY);
    }

    /** Pastes under the mouse, or at the playback cursor when the mouse is elsewhere, keeping the gaps between codes. */
    private void paste()
    {
        MapType data = Window.getClipboardMap(CLIPBOARD_KEY);

        if (data == null || this.waveform == null)
        {
            return;
        }

        UIContext context = this.getContext();
        float time = this.area.isInside(context) ? (float) this.xAxis.from(context.mouseX) : this.player.getPlaybackPosition();
        List<ColorCode> pasted = new ArrayList<>();
        float min = Float.MAX_VALUE;

        for (BaseType type : data.getList("codes"))
        {
            if (type.isList())
            {
                ColorCode code = new ColorCode();

                code.fromData(type.asList());
                pasted.add(code);
                min = Math.min(min, code.start);
            }
        }

        for (ColorCode code : pasted)
        {
            code.end += time - min;
            code.start += time - min;
            this.colorCodes.add(code);
        }

        if (!pasted.isEmpty())
        {
            this.setCurrent(pasted.get(pasted.size() - 1));
            this.markDirty();
        }
    }

    /** Like M in the clips: the end moves to the cursor, or the start when the cursor is before the code. */
    private void trimToCursor()
    {
        float time = this.player == null ? 0F : this.player.getPlaybackPosition();

        if (time > this.current.start)
        {
            this.current.end = time;
        }
        else
        {
            this.current.start = time;
        }

        this.markDirty();
    }

    /**
     * A drawn code paints over the codes under it: they are cut back to its edges,
     * split in two when it lies inside, or removed when it covers them whole.
     */
    private void paintOver(ColorCode painted)
    {
        List<ColorCode> pieces = new ArrayList<>();

        this.colorCodes.removeIf((code) -> code != painted && code.start >= painted.start && code.end <= painted.end);

        for (ColorCode code : this.colorCodes)
        {
            if (code == painted || code.end <= painted.start || code.start >= painted.end)
            {
                continue;
            }

            if (code.start < painted.start && code.end > painted.end)
            {
                pieces.add(new ColorCode(painted.end, code.end, code.color));
                code.end = painted.start;
            }
            else if (code.start < painted.start)
            {
                code.end = painted.start;
            }
            else
            {
                code.start = painted.end;
            }
        }

        this.colorCodes.addAll(pieces);
    }

    private void markDirty()
    {
        this.refreshWaveform();
        this.dirty = true;
        this.lastChange = System.currentTimeMillis();
    }

    /** The waveform tints itself with the codes, like the audio clip does; the simple one caches it in tiles. */
    private void refreshWaveform()
    {
        if (this.waveform != null && !BBSSettings.audioWaveformDetailed.get())
        {
            this.waveform.render(this.colorCodes, this.cues);
        }
    }

    public void save()
    {
        if (this.audio != null && this.saver != null)
        {
            this.saver.run();
        }

        this.dirty = false;
    }

    public void saveIfDirty()
    {
        if (this.dirty)
        {
            this.save();
        }
    }

    public boolean isEditing()
    {
        return this.waveform != null;
    }

    public SoundPlayer getPlayer()
    {
        return this.player;
    }

    public void togglePlayback()
    {
        if (this.player == null)
        {
            return;
        }

        if (!this.player.isPlaying())
        {
            this.player.play();
        }
        else
        {
            this.player.pause();
        }
    }

    public Link getAudio()
    {
        return this.audio;
    }

    public List<ColorCode> getColorCodes()
    {
        return this.colorCodes;
    }

    private void setCurrent(ColorCode code)
    {
        this.current = code;

        this.color.setVisible(code != null);

        if (code != null)
        {
            this.color.setColor(code.color);
        }
    }

    public void setup(Link audio)
    {
        this.saveIfDirty();
        this.colorCodes.clear();
        this.setCurrent(null);

        if (audio == null)
        {
            this.audio = null;

            this.delete();

            return;
        }

        try
        {
            Wave wave = AudioReader.read(BBSMod.getProvider(), audio);

            if (wave.getBytesPerSample() > 2)
            {
                wave = wave.convertTo16();
            }

            List<ColorCode> colorCodes = BBSModClient.getSounds().readColorCodes(audio);

            if (colorCodes == null)
            {
                colorCodes = new ArrayList<>();
            }

            this.audio = audio;
            this.waveform = new Waveform();
            this.cues = wave.getCues();
            this.waveform.generate(wave, this.colorCodes, BBSSettings.audioWaveformDensity.get(), BBSSettings.audioWaveformHeight.get());
            this.colorCodes.addAll(colorCodes);
            this.xAxis.viewOffset(0F, wave.getDuration(), 20);

            this.buffer = new SoundBuffer(null, wave, this.waveform);
            this.player = new SoundPlayer(this.buffer);

            this.player.setRelative(true);
            this.player.play();
            this.player.pause();

            this.setCurrent(null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void delete()
    {
        if (this.waveform != null)
        {
            this.waveform.delete();
            this.waveform = null;
        }

        if (this.player != null)
        {
            this.player.delete();
            this.player = null;
        }

        if (this.buffer != null)
        {
            this.buffer.delete();
            this.buffer = null;
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context))
        {
            if ((context.mouseButton == 0 || context.mouseButton == 1) && Window.isCtrlPressed() && this.waveform != null)
            {
                this.dragged = this.createNewCode();
                this.dragged.end = this.dragged.start;
                this.draggedAnchor = this.dragged.start;

                if (context.mouseButton == 1)
                {
                    this.dragged.color = BBSSettings.audioEditorSecondaryColor.get();
                }

                this.colorCodes.add(this.dragged);
                this.setCurrent(this.dragged);

                return true;
            }

            if (context.mouseButton == 0)
            {
                if (this.current != null)
                {
                    Area codeArea = this.getColorCodeArea(this.current);

                    if (codeArea.isInside(context))
                    {
                        this.dragging = this.getColorCodeHandle(context, codeArea);

                        return true;
                    }
                }

                for (ColorCode code : this.colorCodes)
                {
                    Area codeArea = this.getColorCodeArea(code);

                    if (codeArea.isInside(context))
                    {
                        this.setCurrent(code);

                        this.dragging = this.getColorCodeHandle(context, codeArea);

                        return true;
                    }
                }

                this.dragging = -1;

                if (this.player != null)
                {
                    this.player.setPlaybackPosition((float) this.xAxis.from(context.mouseX));
                }
            }
            else if (context.mouseButton == 2)
            {
                this.setMouse(context.mouseX, context.mouseY);
                this.startNavigating();
            }

            return context.mouseButton != 1;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.dragged != null)
        {
            /* A click without a drag still makes a code you can see and grab */
            if ((this.dragged.end - this.dragged.start) * this.xAxis.getZoom() < 6)
            {
                this.dragged.end = this.dragged.start + 0.3F;
            }

            this.paintOver(this.dragged);
            this.markDirty();
        }
        else if (this.dragging >= 0)
        {
            this.markDirty();
        }

        this.dragged = null;
        this.navigating = false;
        this.dragging = -2;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context))
        {
            if (context.mouseWheel != 0D && Window.isShiftPressed())
            {
                float factor = context.mouseWheel > 0 ? 1.15F : 1F / 1.15F;

                this.verticalZoomTarget = MathUtils.clamp(this.verticalZoomTarget * factor, 1F, 50F);
            }
            else if (context.mouseWheel != 0D)
            {
                this.zoomTimeAt(context, context.mouseWheel);
            }

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.navigating || this.dragging >= -1 || this.dragged != null)
        {
            this.xAxis.stopZoom();
        }
        else
        {
            this.xAxis.updateZoom();
        }

        this.updateVerticalZoom();
        this.handleMouse(context);
        this.renderAudio(context);

        if (this.dirty && this.dragged == null && this.dragging < 0 && System.currentTimeMillis() - this.lastChange > AUTOSAVE_DELAY)
        {
            this.save();
        }
    }

    /** Eases the strip height towards the wheel's target, in the same steps the time axis zooms in. */
    private void updateVerticalZoom()
    {
        float smoothing = BBSSettings.getScrollSmoothingIntensity();
        long now = System.nanoTime();

        if (smoothing <= 0F || Math.abs(this.verticalZoomTarget - this.verticalZoom) < 0.001F)
        {
            this.verticalZoom = this.verticalZoomTarget;
        }
        else
        {
            double seconds = Math.min(0.1D, (now - this.verticalZoomTime) / 1_000_000_000D);
            float factor = (float) (1D - Math.exp(-seconds / (0.05D * smoothing)));

            this.verticalZoom += (this.verticalZoomTarget - this.verticalZoom) * factor;
        }

        this.verticalZoomTime = now;
    }

    private void handleMouse(UIContext context)
    {
        if (this.dragged != null)
        {
            float time = MathUtils.clamp((float) this.xAxis.from(context.mouseX), 0F, this.waveform.getDuration());

            this.dragged.start = Math.min(this.draggedAnchor, time);
            this.dragged.end = Math.max(this.draggedAnchor, time);
            this.refreshWaveform();
        }
        else
        {
            float zoom = (float) this.xAxis.getZoom();

            if (this.navigating)
            {
                this.navigateTime(context.mouseX - this.lastX);
            }
            else if (this.dragging >= 0)
            {
                int mouseX = context.mouseX;
                float offset = (mouseX - this.lastX) / zoom;

                if (this.dragging == 0 || this.dragging == 1) this.current.start = MathUtils.clamp(this.current.start + offset, 0, this.current.end - 6F / zoom);
                if (this.dragging == 0 || this.dragging == 2) this.current.end = MathUtils.clamp(this.current.end + offset, this.current.start + 6F / zoom, Float.MAX_VALUE);

                this.refreshWaveform();
            }
            else if (this.dragging == -1 && this.player != null)
            {
                this.player.setPlaybackPosition((float) this.xAxis.from(context.mouseX));
            }
        }

        this.lastX = context.mouseX;
    }

    private void renderAudio(UIContext context)
    {
        if (this.waveform == null)
        {
            return;
        }

        float time = this.player.getPlaybackPosition();
        int cursorX = (int) this.xAxis.to(time);
        int x = (int) this.xAxis.to(0D);
        int h = this.getStripHeight();
        int y = this.area.my(h);
        int x2 = (int) this.xAxis.to(this.waveform.getDuration());

        context.batcher.clip(this.area, context);

        this.waveform.render(context.batcher, Colors.WHITE, x, y, x2 - x, h, 0F, this.waveform.getDuration());

        for (ColorCode code : this.colorCodes)
        {
            if (code != this.current)
            {
                this.renderColorCode(context, code, false);
            }
        }

        if (this.current != null)
        {
            this.renderColorCode(context, this.current, true);
        }

        UIClips.renderCursor(context, String.format("%.1f/%.1f", time, waveform.getDuration()), this.area, cursorX);

        context.batcher.unclip(context);
    }

    private void renderColorCode(UIContext context, ColorCode code, boolean selected)
    {
        Area codeArea = this.getColorCodeArea(code);
        int codeHandler = this.getColorCodeHandle(context, codeArea);
        float time = this.player == null ? 0F : this.player.getPlaybackPosition();
        boolean playing = time >= code.start && time <= code.end;

        /* The waveform itself is tinted with the code, as in the audio clip; only the hover is added here */
        if (codeArea.isInside(context))
        {
            context.batcher.box(codeArea.x, codeArea.y, codeArea.ex(), codeArea.ey(), Colors.setA(code.color, 0.2F));
        }

        if (selected || playing)
        {
            context.batcher.outline(codeArea.x, codeArea.y, codeArea.ex(), codeArea.ey(), selected ? Colors.WHITE : Colors.ACTIVE | Colors.A100);
        }

        if (selected)
        {
            if (codeHandler == 1 || this.dragging == 1)
            {
                context.batcher.icon(Icons.CLIP_HANLDE_LEFT, Colors.WHITE, codeArea.x, codeArea.y + 10, 0F, 0.5F);
            }
            else if (codeHandler == 2 || this.dragging == 2)
            {
                context.batcher.icon(Icons.CLIP_HANLDE_RIGHT, Colors.WHITE, codeArea.ex(), codeArea.y + 10, 1F, 0.5F);
            }
        }
    }

    private Area getColorCodeArea(ColorCode code)
    {
        int h = this.getStripHeight();
        int y = this.area.my(h);

        AREA.set(
            (int) this.xAxis.to(code.start), y,
            (int) (this.xAxis.to(code.end) - this.xAxis.to(code.start)), h
        );

        return AREA;
    }

    private int getStripHeight()
    {
        return Math.round(this.waveform.getHeight() * this.verticalZoom);
    }

    private int getColorCodeHandle(UIContext context, Area area)
    {
        if (!area.isInside(context))
        {
            return -1;
        }

        if (context.mouseX < area.x + 10)
        {
            return 1;
        }
        else if (context.mouseX >= area.ex() - 10)
        {
            return 2;
        }

        return 0;
    }
}