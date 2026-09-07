package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.ITextColoring;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIButton extends UIClickable<UIButton> implements ITextColoring
{
    /** How far an icon sits from the button's left edge, and from the label on its other side. */
    private static final int ICON_MARGIN = 4;

    public IKey label;

    public int textColor = Colors.WHITE;
    public boolean textShadow = true;

    public boolean custom;
    public int customColor;
    public int customHighlightColor;
    public boolean background = true;

    private Icon icon;
    private Supplier<Icon> iconSupplier;

    public UIButton(IKey label, Consumer<UIButton> callback)
    {
        super(callback);

        this.label = label;
        this.h(UIConstants.CONTROL_HEIGHT);
    }

    public UIButton color(int color)
    {
        this.custom = true;
        this.customColor = color & Colors.RGB;
        this.customHighlightColor = this.customColor;

        return this;
    }

    public UIButton color(int color, int highlightColor)
    {
        this.custom = true;
        this.customColor = color;
        this.customHighlightColor = highlightColor;

        return this;
    }

    public UIButton textColor(int color, boolean shadow)
    {
        this.textColor = color;
        this.textShadow = shadow;

        return this;
    }

    public UIButton background(boolean background)
    {
        this.background = background;

        return this;
    }

    /**
     * A glyph before the label, saying what kind of thing this button is about — a texture, a
     * model, a folder. It sits at the left edge while the label stays centred in the button, the
     * way {@link UIChoiceButton} has always shown the option it stands for.
     *
     * <p>It is drawn in the label's colour and dims with it under the cursor: the button is one
     * thing, not a bright glyph dragging a word around.</p>
     */
    public UIButton icon(Icon icon)
    {
        this.icon = icon;

        return this;
    }

    /** The same, for a button whose icon follows what it currently stands for. */
    public UIButton icon(Supplier<Icon> icon)
    {
        this.iconSupplier = icon;

        return this;
    }

    public Icon getIcon()
    {
        if (this.iconSupplier != null)
        {
            Icon icon = this.iconSupplier.get();

            if (icon != null)
            {
                return icon;
            }
        }

        return this.icon;
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.textColor = color;
        this.textShadow = shadow;
    }

    @Override
    protected UIButton get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        int color = (this.custom ? this.customColor : BBSSettings.primaryColor.get() | Colors.A100);

        if (this.hover)
        {
            color = this.custom ? this.customHighlightColor : Colors.mulRGB(color, 0.85F);
        }

        if (this.background)
        {
            context.batcher.surfaceBox(this.area.x, this.area.y, this.area.ex(), this.area.ey(), color | Colors.A100, true, false);
        }

        int textColor = Colors.mulRGB(this.textColor, this.hover ? 0.9F : 1F);
        Icon icon = this.getIcon();

        /* The label stays centred in the button, as it is without an icon — it only gives way
         * when there is no room left, sliding clear of the glyph rather than running under it. */
        int gutter = icon == null ? 0 : ICON_MARGIN * 2 + icon.w;

        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(this.label.get(), this.area.w - 4 - gutter);
        int x = Math.max(this.area.mx(font.getWidth(label)), this.area.x + gutter);
        int y = this.area.my(font.getHeight());

        if (icon != null)
        {
            context.batcher.icon(icon, textColor, this.area.x + ICON_MARGIN, this.area.my(), 0F, 0.5F);
        }

        context.batcher.text(label, x, y, textColor, this.textShadow);

        this.renderLockedArea(context);
    }
}
