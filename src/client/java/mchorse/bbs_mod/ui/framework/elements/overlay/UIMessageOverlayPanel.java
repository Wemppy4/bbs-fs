package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;

public class UIMessageOverlayPanel extends UIOverlayPanel
{
    /** The width a message dialog asks for: the longest strings wrap into three or four lines at it. */
    public static final int WIDTH = 280;

    /** Distance from the top of the content to the message. */
    protected static final int TOP = 12;

    /** Gap between the message and whatever sits under it. */
    protected static final int GAP = 10;

    public UIText message;

    /**
     * The block pinned under the message &mdash; a button, a bar of fields. Its height is part of
     * the height this panel asks for; null means the message is all there is to it.
     */
    protected UIElement bottom;

    /** Whether the panel is only as tall as its own content; see {@link #fillsOverlay()}. */
    private boolean fits = true;

    public UIMessageOverlayPanel(IKey title, IKey message)
    {
        super(title);

        this.message = new UIText().text(message).textAnchorX(0.5F);
        this.message.relative(this.content).x(0.5F).y(TOP).w(0.7F).anchorX(0.5F);

        this.content.add(this.message);
    }

    /**
     * The panel holds something that wants room of its own &mdash; a folder list, a text area
     * &mdash; so it takes the size the overlay hands it instead of hugging its message.
     */
    protected void fillsOverlay()
    {
        this.fits = false;
    }

    @Override
    public boolean isResizable()
    {
        return !this.fits;
    }

    @Override
    public int getPreferredWidth()
    {
        return this.fits ? WIDTH : super.getPreferredWidth();
    }

    @Override
    public int getContentHeight()
    {
        /* Before the first pass the message has no height yet, and a sum without it would be wrong */
        if (!this.fits || this.message.area.h <= 0)
        {
            return -1;
        }

        int height = this.content.getFlex().y.offset + TOP + this.message.area.h + GAP;

        /* The block is pinned to the bottom by a negative offset, which is the padding under it */
        if (this.bottom != null)
        {
            height += this.bottom.area.h - this.bottom.getFlex().y.offset;
        }

        return height;
    }
}
