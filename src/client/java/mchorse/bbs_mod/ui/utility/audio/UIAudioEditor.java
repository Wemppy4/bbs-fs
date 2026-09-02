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
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

public class UIAudioEditor extends UIElement
{
    private static final Area AREA = new Area();

    public UIColor color;

    private Link audio;
    private Waveform waveform;
    private List<ColorCode> colorCodes = new ArrayList<>();

    private SoundBuffer buffer;
    private SoundPlayer player;

    private Scale scale = new Scale(this.area, ScrollDirection.HORIZONTAL);

    private boolean navigating;
    private int dragging = -2;
    private int lastX;

    private ColorCode dragged;
    private ColorCode current;

    public UIAudioEditor()
    {
        this.color = new UIColor((c) -> this.current.color = c);
        this.color.relative(this).x(1F, -10).y(1F, -10).wh(80, 20).anchor(1F, 1F);

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
            }).label(UIKeys.AUDIO_CONTEXT_ADD);

            menu.icon(MenuVerb.REMOVE, () ->
            {
                this.colorCodes.remove(this.current);
                this.setCurrent(null);
            }).label(UIKeys.AUDIO_CONTEXT_REMOVE).enabled(this.current != null);
        });

        this.add(this.color);
    }

    private ColorCode createNewCode()
    {
        float time = (float) this.scale.from(this.getContext().mouseX);
        ColorCode code = new ColorCode();

        code.start = time;
        code.end = time + 0.3F;
        code.color = Colors.HSVtoRGB((float) Math.random(), 1F, 1F).getRGBColor();

        return code;
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
            this.waveform.generate(wave, null, BBSSettings.audioWaveformDensity.get(), BBSSettings.audioWaveformHeight.get());
            this.colorCodes.addAll(colorCodes);
            this.scale.viewOffset(0F, wave.getDuration(), 20);

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
            if (context.mouseButton == 0)
            {
                if (Window.isCtrlPressed())
                {
                    this.dragged = this.createNewCode();

                    this.colorCodes.add(this.dragged);
                    this.setCurrent(this.dragged);

                    return true;
                }

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
                    this.player.setPlaybackPosition((float) this.scale.from(context.mouseX));
                }
            }
            else if (context.mouseButton == 2)
            {
                this.navigating = true;
            }

            return context.mouseButton != 1;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
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
            if (context.mouseWheel != 0D)
            {
                this.scale.zoomAnchor(Scale.getAnchorX(context, this.area), Math.copySign(this.scale.getZoomFactor(), context.mouseWheel));
            }

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        this.handleMouse(context);
        this.renderAudio(context);
    }

    private void handleMouse(UIContext context)
    {
        if (this.dragged != null)
        {
            this.dragged.end = (float) this.scale.from(context.mouseX);
        }
        else
        {
            float zoom = (float) this.scale.getZoom();

            if (this.navigating)
            {
                int mouseX = context.mouseX;
                double offset = (mouseX - lastX) / zoom;

                this.scale.setShift(this.scale.getShift() - offset);
            }
            else if (this.dragging >= 0)
            {
                int mouseX = context.mouseX;
                float offset = (mouseX - lastX) / zoom;

                if (this.dragging == 0 || this.dragging == 1) this.current.start = MathUtils.clamp(this.current.start + offset, 0, this.current.end - 6F / zoom);
                if (this.dragging == 0 || this.dragging == 2) this.current.end = MathUtils.clamp(this.current.end + offset, this.current.start + 6F / zoom, Float.MAX_VALUE);
            }
            else if (this.dragging == -1 && this.player != null)
            {
                this.player.setPlaybackPosition((float) this.scale.from(context.mouseX));
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
        int cursorX = (int) this.scale.to(time);
        int x = (int) this.scale.to(0D);
        int y = this.area.my(this.waveform.getHeight());
        int x2 = (int) this.scale.to(this.waveform.getDuration());

        context.batcher.clip(this.area, context);

        this.waveform.render(context.batcher, Colors.WHITE, x, y, x2 - x, this.waveform.getHeight(), 0F, this.waveform.getDuration());

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
        float a = codeArea.isInside(context) ? 0.5F : 0.33F;
        float time = this.player == null ? 0F : this.player.getPlaybackPosition();
        boolean playing = time >= code.start && time <= code.end;

        context.batcher.gradientVBox(codeArea.x, codeArea.y, codeArea.ex(), codeArea.ey(), Colors.setA(code.color, a), Colors.setA(code.color, a + 0.25F));

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
        int y = this.area.my(this.waveform.getHeight());

        AREA.set(
            (int) this.scale.to(code.start), y,
            (int) (this.scale.to(code.end) - this.scale.to(code.start)), this.waveform.getHeight()
        );

        return AREA;
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