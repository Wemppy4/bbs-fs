package mchorse.bbs_mod.ui.dashboard.textures.frames;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.PixelMacro;
import mchorse.bbs_mod.ui.dashboard.textures.UIPixelsEditor;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureEditor;
import mchorse.bbs_mod.ui.dashboard.textures.UITexturePainter;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureAnimation;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIStrip;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The strip under the canvas of an animated texture: a transport bar — play, step, the counter,
 * the duration of the picked frames, a new frame — over the {@link UIFrameStrip row of frames}.
 * One per painter, pointed at whichever editor is on show, the way the layers panel is.
 *
 * <p>Playing runs on the client's ticks, the same clock the game plays the texture by, and stops
 * the moment the user picks a frame by hand.</p>
 */
public class UIFramesPanel extends UIElement
{
    public static final int BAR_HEIGHT = 20;
    public static final int MAX_TIME = 32000;

    private final UITexturePainter painter;
    private UITextureEditor editor;

    private final UIFrameStrip strip;
    private final UIIcon play;
    private final UILabel counter;
    private final UITrackpad time;
    private final UIIcon onion;

    /* Kept here, for the session: it's a way of looking, not a fact about the texture */
    private boolean onionSkin;

    /* Playing: the tick it started on, and how far into the run the frame on show was */
    private boolean playing;
    private long playStart;
    private int playOffset;

    public UIFramesPanel(UITexturePainter painter)
    {
        this.painter = painter;

        this.play = new UIIcon(() -> this.playing ? Icons.PAUSE : Icons.PLAY, (b) -> this.togglePlaying());
        this.play.tooltip(UIKeys.TEXTURES_FRAMES_PLAY);

        /* Shift turns a step into a jump to the end, and a copy into a blank frame */
        UIIcon prev = new UIIcon(Icons.FRAME_PREV, (b) -> this.stepOrJump(-1));
        UIIcon next = new UIIcon(Icons.FRAME_NEXT, (b) -> this.stepOrJump(1));
        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addFrame(!Window.isShiftPressed()));

        prev.tooltip(UIKeys.TEXTURES_FRAMES_PREV);
        next.tooltip(UIKeys.TEXTURES_FRAMES_NEXT);
        add.tooltip(UIKeys.TEXTURES_FRAMES_ADD);

        this.counter = UI.label(IKey.raw(""));
        this.counter.labelAnchor(0.5F, 0.5F);
        this.counter.w(50);

        this.time = new UITrackpad((v) -> this.applyTime(v.intValue()));
        this.time.limit(0, MAX_TIME).integer();
        this.time.tooltip(UIKeys.TEXTURES_FRAMES_TIME);
        this.time.w(60);

        this.onion = new UIIcon(Icons.ONION_SKIN, (b) -> this.toggleOnionSkin());
        this.onion.tooltip(UIKeys.TEXTURES_FRAMES_ONION);
        this.onion.highlight(() -> this.onionSkin, Direction.BOTTOM);

        UIStrip bar = new UIStrip(BAR_HEIGHT);

        bar.relative(this).w(1F).h(BAR_HEIGHT);
        bar.add(this.play, prev, next, this.counter, this.time, this.onion, add);

        this.strip = new UIFrameStrip(this);
        this.strip.relative(this).y(BAR_HEIGHT).w(1F).h(1F, -BAR_HEIGHT);

        this.add(bar, this.strip);
    }

    /** Point the panel at the editor on show (or nothing); whatever was playing stops. */
    public void setEditor(UITextureEditor editor)
    {
        this.editor = editor;
        this.playing = false;

        if (editor != null)
        {
            editor.setOnionSkin(this.onionSkin);
        }

        this.sync();
    }

    private void toggleOnionSkin()
    {
        this.onionSkin = !this.onionSkin;

        if (this.editor != null)
        {
            this.editor.setOnionSkin(this.onionSkin);
        }
    }

    public Document document()
    {
        return this.editor == null ? null : this.editor.getDocument();
    }

    public TextureAnimation animation()
    {
        Document document = this.document();

        return document == null ? null : document.animation;
    }

    /** The cells' checkerboard is the canvas's: the brightness set in the editor's options. */
    public int checkerboardColor()
    {
        return UIPixelsEditor.checkerboardColor(this.painter.getBackgroundBrightness());
    }

    /** Position of the frame on show, or -1. */
    public int shown()
    {
        return this.editor == null || this.animation() == null ? -1 : this.editor.getFrame();
    }

    private TextureAnimation.Frame shownFrame()
    {
        TextureAnimation animation = this.animation();
        int shown = this.shown();

        return animation == null || shown < 0 || shown >= animation.frames.size() ? null : animation.frames.get(shown);
    }

    /**
     * The frames an edit from the bar works on: the pick, or failing that the frame on show.
     */
    private List<TextureAnimation.Frame> target()
    {
        if (!this.strip.selection.isEmpty())
        {
            return new ArrayList<>(this.strip.selection.getItems());
        }

        TextureAnimation.Frame shown = this.shownFrame();

        return shown == null ? Collections.emptyList() : Collections.singletonList(shown);
    }

    /* What the strip and the bar ask for */

    public void show(int position)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;
        this.editor.setFrame(position);
        this.sync();
    }

    public void step(int delta)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;
        this.editor.stepFrame(delta);
        this.selectShown();
        this.sync();
    }

    /** Straight to a frame of the order, picking it, the way a click on it would. */
    public void jump(int position)
    {
        if (this.editor == null || this.animation() == null)
        {
            return;
        }

        this.playing = false;
        this.editor.setFrame(position);
        this.selectShown();
        this.sync();
    }

    public void first()
    {
        this.jump(0);
    }

    public void last()
    {
        TextureAnimation animation = this.animation();

        if (animation != null)
        {
            this.jump(animation.frames.size() - 1);
        }
    }

    /** A step, or with Shift held a jump to that end of the order. */
    private void stepOrJump(int delta)
    {
        if (!Window.isShiftPressed())
        {
            this.step(delta);
        }
        else if (delta < 0)
        {
            this.first();
        }
        else
        {
            this.last();
        }
    }

    public void insert(int position)
    {
        if (this.editor == null || this.animation() == null)
        {
            return;
        }

        this.playing = false;
        this.editor.insertFrame(position);
        this.selectShown();
        this.sync();
    }

    /**
     * A new frame after the one on show: a copy of it — what nearly every next frame of a
     * pixel animation starts as — or a blank one.
     */
    public void addFrame(boolean copy)
    {
        TextureAnimation.Frame shown = this.shownFrame();

        if (copy && shown != null)
        {
            this.duplicate(Collections.singletonList(shown));
        }
        else
        {
            this.insert(this.shown() + 1);
        }
    }

    /** Copies of frames dropped between others with Ctrl held. */
    public void copyTo(List<TextureAnimation.Frame> frames, int insertion)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;

        List<TextureAnimation.Frame> copies = this.editor.duplicateFramesAt(frames, insertion);

        if (!copies.isEmpty())
        {
            this.strip.selection.setAll(copies);
        }

        this.sync();
    }

    public void duplicate(List<TextureAnimation.Frame> frames)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;

        List<TextureAnimation.Frame> copies = this.editor.duplicateFrames(frames);

        if (!copies.isEmpty())
        {
            this.strip.selection.setAll(copies);
        }

        this.sync();
    }

    public void remove(List<TextureAnimation.Frame> frames)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;

        if (!this.editor.removeFrames(frames))
        {
            this.getContext().notifyError(UIKeys.TEXTURES_FRAMES_LAST);

            return;
        }

        this.strip.selection.clear();
        this.selectShown();
        this.sync();
    }

    public void move(List<TextureAnimation.Frame> frames, int insertion)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;
        this.editor.moveFrames(frames, insertion);
        this.sync();
    }

    public void reverse(List<TextureAnimation.Frame> frames)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;
        this.editor.reverseFrames(frames);
        this.sync();
    }

    public void pingPong(List<TextureAnimation.Frame> frames)
    {
        if (this.editor == null)
        {
            return;
        }

        this.playing = false;

        List<TextureAnimation.Frame> added = this.editor.pingPong(frames);

        if (!added.isEmpty())
        {
            this.strip.selection.setAll(added);
        }

        this.sync();
    }

    public void macro(PixelMacro macro, List<TextureAnimation.Frame> frames)
    {
        if (this.editor != null)
        {
            this.editor.applyMacroToFrames(macro, frames);
        }
    }

    /** Ask for a duration in ticks for the given frames. */
    public void askTime(List<TextureAnimation.Frame> frames)
    {
        TextureAnimation animation = this.animation();

        if (this.editor == null || animation == null || frames.isEmpty())
        {
            return;
        }

        UINumberOverlayPanel panel = new UINumberOverlayPanel(UIKeys.TEXTURES_FRAMES_TIME_TITLE, UIKeys.TEXTURES_FRAMES_TIME_MESSAGE, (v) ->
        {
            this.editor.setFrameTime(frames, v.intValue());
            this.sync();
        });

        panel.value.limit(0, MAX_TIME).integer().setValue(animation.timeOf(frames.get(0)));

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void applyTime(int ticks)
    {
        if (this.editor == null)
        {
            return;
        }

        this.editor.setFrameTime(this.target(), ticks);
    }

    /* Keeping up with the editor */

    /** The frame on show becomes the pick, the way a click on it would. */
    private void selectShown()
    {
        TextureAnimation.Frame shown = this.shownFrame();

        if (shown != null)
        {
            this.strip.selection.set(shown, null);
        }
    }

    /**
     * Bring the strip in line with the editor after anything changed the animation — an edit
     * here, an undo on the canvas, another texture shown: frames that are gone leave the pick,
     * an empty pick falls back on the frame on show, and the bar reads the frame on show.
     */
    public void sync()
    {
        TextureAnimation animation = this.animation();

        if (animation == null)
        {
            this.strip.selection.clear();
            this.playing = false;

            return;
        }

        this.strip.selection.retain(animation.frames::contains);

        if (this.strip.selection.isEmpty())
        {
            this.selectShown();
        }

        TextureAnimation.Frame shown = this.shownFrame();

        if (shown != null)
        {
            this.time.setValue(animation.timeOf(shown));
            this.strip.scrollTo(this.shown());
        }
    }

    /** Ctrl + wheel over the bar flips the frames too; over the row of cells the strip does it itself. */
    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (Window.isCtrlPressed() && context.mouseWheel != 0 && this.area.isInside(context) && this.animation() != null)
        {
            this.step(context.mouseWheel > 0 ? -1 : 1);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    /* Playing */

    public void togglePlaying()
    {
        TextureAnimation animation = this.animation();

        if (this.playing || animation == null)
        {
            this.playing = false;

            return;
        }

        int offset = 0;

        for (int i = 0, shown = this.shown(); i < shown && i < animation.frames.size(); i++)
        {
            offset += animation.timeOf(animation.frames.get(i));
        }

        this.playing = true;
        this.playStart = this.getContext().getTick();
        this.playOffset = offset;
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.chromeSurface());

        TextureAnimation animation = this.animation();

        if (animation != null && this.editor != null)
        {
            if (this.playing)
            {
                int frame = animation.frameAt(this.playOffset + (int) (context.getTick() - this.playStart));

                if (frame != this.editor.getFrame())
                {
                    this.editor.setFrame(frame);
                    this.selectShown();
                    this.time.setValue(animation.timeOf(animation.frames.get(frame)));
                }
            }

            this.counter.label = IKey.raw((this.shown() + 1) + "/" + animation.frames.size());
        }

        super.render(context);
    }
}
