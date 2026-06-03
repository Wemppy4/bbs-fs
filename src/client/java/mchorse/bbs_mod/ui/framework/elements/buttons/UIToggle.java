package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.ITextColoring;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIToggle extends UIClickable<UIToggle> implements ITextColoring
{
    public IKey label;
    public int color = Colors.WHITE;
    public boolean textShadow = true;
    private boolean value;

    public UIToggle(IKey label, Consumer<UIToggle> callback)
    {
        this(label, false, callback);
    }

    public UIToggle(IKey label, boolean value, Consumer<UIToggle> callback)
    {
        super(callback);

        this.label = label;
        this.value = value;
        this.h(14);
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.color(color, shadow);
    }

    public UIToggle label(IKey label)
    {
        this.label = label;

        return this;
    }

    public UIToggle setValue(boolean value)
    {
        this.value = value;

        return this;
    }

    public UIToggle color(int color)
    {
        return this.color(color, true);
    }

    public UIToggle color(int color, boolean textShadow)
    {
        this.color = color;
        this.textShadow = textShadow;

        return this;
    }

    public boolean getValue()
    {
        return this.value;
    }

    @Override
    protected void click(int mouseWheel)
    {
        this.value = !this.value;

        super.click(mouseWheel);
    }

    @Override
    protected UIToggle get()
    {
        return this;
    }

    private static final int TRACK_W = 22;
    private static final int TRACK_H = 10;
    private static final int KNOB = 12;

    @Override
    protected void renderSkin(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(this.label.get(), this.area.w - TRACK_W - 6);

        context.batcher.text(label, this.area.x, this.area.my(font.getHeight()), this.color, this.textShadow);

        int my = this.area.my();
        int trackRight = this.area.ex() - 2;
        int trackLeft = trackRight - TRACK_W;
        int trackTop = my + KNOB / 2 - TRACK_H;
        int trackBottom = my + KNOB / 2;

        int trackFill = this.value ? Colors.A100 | BBSSettings.primaryColor.get() : 0xff3a3d41;

        /* Track background: beveled fill with a 1px inner black border, no bottom shadow */
        context.batcher.bevelBox(trackLeft, trackTop, trackRight, trackBottom, trackFill, false);

        /* Knob: 12x12, taller than the track so it pokes out the top, 1px inner black border */
        int knobLeft = trackLeft + (this.value ? TRACK_W - KNOB : 0);
        int knobTop = my - KNOB / 2;
        int knobColor = this.hover ? Colors.lerp(0xffc9cdd2, Colors.WHITE, 0.2F) : 0xffc9cdd2;

        context.batcher.bevelBox(knobLeft, knobTop, knobLeft + KNOB, knobTop + KNOB, knobColor, true);

        if (!this.isEnabled())
        {
            context.batcher.box(knobLeft, knobTop, knobLeft + KNOB, knobTop + KNOB, Colors.A50);
            context.batcher.outlinedIcon(Icons.LOCKED, trackLeft + TRACK_W / 2, my, 0.5F, 0.5F);
        }
    }
}