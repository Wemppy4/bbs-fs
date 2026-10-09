package mchorse.bbs_mod.ui.utils.resizers;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;

/**
 * How an element's area gets its numbers. Every stage is optional — a resizer writes down only
 * the ones it takes part in, and the rest stay out of the way.
 */
public interface IResizer
{
    public default void preApply(Area area)
    {}

    public default void apply(Area area)
    {}

    public default void postApply(Area area)
    {}

    public default void add(UIElement parent, UIElement child)
    {}

    public default void remove(UIElement parent, UIElement child)
    {}

    public default int getX()
    {
        return 0;
    }

    public default int getY()
    {
        return 0;
    }

    public default int getW()
    {
        return 0;
    }

    public default int getH()
    {
        return 0;
    }

    /**
     * Height when laid out at the given width, for a layout whose height depends on it (a name :
     * control row that moves the name on its own line once it gets narrow). A parent that knows
     * the width it is about to hand out asks this instead of {@link #getH()}; 0 or less means
     * the width isn't known yet.
     */
    public default int getH(int w)
    {
        return this.getH();
    }
}
