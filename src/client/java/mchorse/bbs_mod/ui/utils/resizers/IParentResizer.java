package mchorse.bbs_mod.ui.utils.resizers;

import mchorse.bbs_mod.ui.utils.Area;

public interface IParentResizer
{
    public default void apply(Area area, IResizer resizer, ChildResizer child)
    {}
}
