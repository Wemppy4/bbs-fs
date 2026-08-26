package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * A row of controls of one height — the bar along the top of a browser. The row resizer lays
 * its children out but leaves the height of those that size themselves (icons, text boxes)
 * alone; a strip sets it on everything added, so the bar is one band and everything on it
 * fills it.
 */
public class UIStrip extends UIElement
{
    private final int height;

    public UIStrip(int height)
    {
        this.height = height;

        this.row(0).height(height);
    }

    public int getHeight()
    {
        return this.height;
    }

    @Override
    public void add(IUIElement... elements)
    {
        for (IUIElement element : elements)
        {
            if (element instanceof UIElement uiElement)
            {
                uiElement.h(this.height);
            }
        }

        super.add(elements);
    }
}
