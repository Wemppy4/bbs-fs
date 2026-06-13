package mchorse.bbs_mod.ui.particles.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Clickable field that opens the MoLang editor for a particle expression. Unlike a plain button it
 * surfaces the expression's content directly: an icon, a colored separator bar and a live preview of
 * the stored code, so a section's expressions can be told apart and read at a glance. The property's
 * name lives in the tooltip (this element shows the code, not the label).
 */
public class UIMolangExpression extends UIClickable<UIMolangExpression>
{
    private static final int BAR_WIDTH_PX = 1;
    private static final int BAR_INSET_PX = 1;
    private static final int CODE_GAP_PX = 4;

    public Icon icon = Icons.CODE;
    public Supplier<MolangExpression> expression;
    public int barColor;

    public UIMolangExpression(Supplier<MolangExpression> expression, Consumer<UIMolangExpression> callback)
    {
        super(callback);

        this.expression = expression;
        this.h(UIConstants.CONTROL_HEIGHT);
    }

    public UIMolangExpression icon(Icon icon)
    {
        this.icon = icon;

        return this;
    }

    /** Override the separator bar color (e.g. per color channel); defaults to the theme's primary. */
    public UIMolangExpression barColor(int color)
    {
        this.barColor = color | Colors.A100;

        return this;
    }

    @Override
    protected UIMolangExpression get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        this.area.render(context.batcher, this.hover ? BBSSettings.chromeSurface() : BBSSettings.deepSurface());

        int gutter = this.area.h;
        int barX = this.area.x + gutter + 2;
        int bar = this.barColor == 0 ? BBSSettings.primaryColor(Colors.A100) : this.barColor;

        /* Light icon cell, parted from the code preview by a full-height hairline. */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.setA(Colors.WHITE, 0.1F));
        context.batcher.box(barX, this.area.y + BAR_INSET_PX, barX + BAR_WIDTH_PX, this.area.ey() - BAR_INSET_PX, bar);

        context.batcher.icon(this.icon, this.hover ? Colors.LIGHTEST_GRAY : Colors.WHITE, this.area.x + gutter / 2, this.area.my(), 0.5F, 0.5F);

        MolangExpression expression = this.expression == null ? null : this.expression.get();
        String code = expression == null ? "" : expression.toString();

        FontRenderer font = context.batcher.getFont();
        int textX = barX + BAR_WIDTH_PX + CODE_GAP_PX;
        String preview = font.limitToWidth(code, this.area.ex() - textX - CODE_GAP_PX);
        int textColor = code.isEmpty() ? Colors.setA(Colors.WHITE, 0.35F) : Colors.setA(Colors.WHITE, 0.9F);

        context.batcher.text(preview, textX, this.area.my(font.getHeight()), textColor, true);

        this.renderLockedArea(context);
    }
}
