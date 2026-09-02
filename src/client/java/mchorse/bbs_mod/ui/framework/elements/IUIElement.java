package mchorse.bbs_mod.ui.framework.elements;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;

/**
 * Everything the UI tree walks over. Only what an element actually takes part in has to be
 * written down: laying out, input and drawing all default to "not mine", so a element that
 * only listens for keys or only paints says just that.
 */
public interface IUIElement
{
    /**
     * Should be called when position has to be recalculated
     */
    public default void resize()
    {}

    /**
     * Whether this element is enabled (and can accept any input)
     */
    public default boolean isEnabled()
    {
        return false;
    }

    /**
     * Whether this element is visible
     */
    public default boolean isVisible()
    {
        return true;
    }

    /**
     * Mouse was clicked
     */
    public default IUIElement mouseClicked(UIContext context)
    {
        return null;
    }

    /**
     * Mouse wheel was scrolled
     */
    public default IUIElement mouseScrolled(UIContext context)
    {
        return null;
    }

    /**
     * Mouse was released
     */
    public default IUIElement mouseReleased(UIContext context)
    {
        return null;
    }

    /**
     * Key was typed
     */
    public default IUIElement keyPressed(UIContext context)
    {
        return null;
    }

    /**
     * Text was inputted
     */
    public default IUIElement textInput(UIContext context)
    {
        return null;
    }

    /**
     * Determines whether this element can be rendered on the screen
     */
    public default boolean canBeRendered(Area viewport)
    {
        return false;
    }

    /**
     * Draw its components on the screen
     */
    public default void render(UIContext context)
    {}
}
