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
}
