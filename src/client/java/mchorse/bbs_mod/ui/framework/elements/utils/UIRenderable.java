package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.utils.Area;

import java.util.function.Consumer;

/** An element that only paints: a hook for drawing something extra inside a parent's area. */
public class UIRenderable implements IUIElement
{
    public Consumer<UIContext> callback;

    public UIRenderable(Consumer<UIContext> callback)
    {
        this.callback = callback;
    }

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return true;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.callback != null)
        {
            this.callback.accept(context);
        }
    }
}
