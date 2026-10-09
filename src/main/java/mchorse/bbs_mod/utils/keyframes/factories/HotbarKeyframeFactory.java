package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.film.replays.Hotbar;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.interps.IInterp;

/** Nine item stacks in a list, cell by cell - each one written the way an item key writes it. */
public class HotbarKeyframeFactory implements IKeyframeFactory<Hotbar>
{
    @Override
    public Hotbar fromData(BaseType data)
    {
        Hotbar hotbar = new Hotbar();

        if (data != null && data.isList())
        {
            ListType list = data.asList();

            for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE && i < list.size(); i++)
            {
                hotbar.set(i, KeyframeFactories.ITEM_STACK.fromData(list.get(i)));
            }
        }

        return hotbar;
    }

    @Override
    public BaseType toData(Hotbar value)
    {
        ListType list = new ListType();

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            list.add(KeyframeFactories.ITEM_STACK.toData(value.get(i)));
        }

        return list;
    }

    @Override
    public Hotbar createEmpty()
    {
        return new Hotbar();
    }

    @Override
    public boolean compare(Object a, Object b)
    {
        return a instanceof Hotbar hotbarA && b instanceof Hotbar hotbarB && hotbarA.isSame(hotbarB);
    }

    @Override
    public boolean isStepped()
    {
        return true;
    }

    @Override
    public Hotbar copy(Hotbar value)
    {
        return value.copy();
    }

    @Override
    public Hotbar interpolate(Hotbar preA, Hotbar a, Hotbar b, Hotbar postB, IInterp interpolation, float x)
    {
        return a;
    }
}
