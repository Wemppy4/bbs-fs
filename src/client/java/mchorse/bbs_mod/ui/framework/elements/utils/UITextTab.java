package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A tab of a {@link UITabStrip} that reads as a word rather than an icon: the label centred
 * in the tab's box, lighter under the cursor the way an icon tab tints itself. Which one is
 * active is the strip's business — it marks it with the highlight bar.
 */
public class UITextTab extends UIElement
{
    public IKey label;
    public int color = Colors.LIGHTEST_GRAY;
    public int hoverColor = Colors.WHITE;

    public UITextTab(IKey label)
    {
        super();

        this.label = label;
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        String text = this.label.get();
        int color = this.area.isInside(context) ? this.hoverColor : this.color;

        context.batcher.text(text, this.area.mx(font.getWidth(text)), this.area.my(font.getHeight() - 1), color);

        super.render(context);
    }
}
