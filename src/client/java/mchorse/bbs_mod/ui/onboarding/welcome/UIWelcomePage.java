package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * One tab of the welcome screen: a slogan on top and one decision under it, centered in what
 * is left — the title above and the dots below are centered, so a block hugging the top left
 * reads as lopsided. A tab that has something to look up when it comes on screen (whether the
 * encoder is there) does it in {@link #onShown()}, so the screen doesn't pay for every tab
 * while showing the first.
 */
public abstract class UIWelcomePage extends UIElement
{
    protected static final int SLOGAN_H = 16;
    protected static final int BODY_Y = SLOGAN_H + 12;

    protected static final int DIMMED = Colors.setA(Colors.WHITE, 0.7F);
    protected static final int MUTED = Colors.setA(Colors.WHITE, 0.5F);

    /** What the tab is called under the dots. */
    public final IKey name;

    /** The decision; it sizes itself to its rows and is centered under the slogan. */
    protected final UIElement body;

    public UIWelcomePage(IKey name, IKey slogan)
    {
        this.name = name;

        UILabel label = UI.label(slogan, SLOGAN_H).color(DIMMED);

        label.labelAnchor(0.5F, 0.5F);
        label.relative(this).xy(0, 0).w(1F).h(SLOGAN_H);

        this.body = new UIElement();
        this.body.relative(this).x(0.5F).y(BODY_Y).w(1F).anchorX(0.5F);

        this.add(label, this.body);
    }

    /** A body narrower than the tab, for rows that read better as a column than as a spread. */
    protected void narrow(int width)
    {
        this.body.w(width);
    }

    public void onShown()
    {}

    /**
     * The body only knows its height once its rows are laid out, so it is placed twice: first
     * to measure, then in the middle of the room under the slogan.
     */
    @Override
    public void resize()
    {
        super.resize();

        int room = this.area.h - BODY_Y;
        int y = BODY_Y + Math.max(0, (room - this.body.area.h) / 2);

        if (this.body.getFlex().y.offset != y)
        {
            this.body.y(y);
            super.resize();
        }
    }
}
