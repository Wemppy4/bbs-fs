package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValue;

/**
 * A {@link ValueList} whose elements keep their identity for life. Where the base list renumbers
 * ids by position on every change ({@link #sync()}), this one assigns each element a
 * {@link StableIds stable id} once and persists it with the element, so anything that addresses the
 * element by id (track keys, anchors, selectors, undo paths, client-server sync) survives
 * insertion, removal and reordering.
 *
 * <p>The list, not the element, owns the id's persistence: it is written next to the element's own
 * data on the way out and restored on the way in. An element serialized outside its list (a
 * clipboard replay, a preset) simply carries no id and is assigned a fresh one on insertion —
 * which is exactly right for a copy: references to the original must not follow it.
 *
 * <p>{@link #add} is the single door: whatever comes in without an id, with a legacy positional id
 * or with an id the list already contains (a duplicated element) leaves it holding a fresh unique
 * one. Data read from disk passes through a converter first and arrives with valid unique ids,
 * which {@link #add} preserves untouched.
 */
public abstract class ValueStableList <T extends BaseValue> extends ValueList<T>
{
    public ValueStableList(String id)
    {
        super(id);
    }

    @Override
    public void add(T value)
    {
        this.ensureId(value);
        super.add(value);
    }

    @Override
    public void add(int index, T value)
    {
        this.ensureId(value);
        super.add(index, value);
    }

    private void ensureId(T value)
    {
        if (!StableIds.isStableId(value.getId()) || this.get(value.getId()) != null)
        {
            String id;

            do
            {
                id = StableIds.generate();
            }
            while (this.get(id) != null);

            value.setId(id);
        }
    }

    /**
     * Renumbering by position is the exact thing stable ids exist to prevent; a call that used to
     * be routine bookkeeping is a data-corrupting bug here, and it fails loudly instead of quietly
     * rewriting every id.
     */
    @Override
    public void sync()
    {
        throw new UnsupportedOperationException("A stable-id list must never be renumbered by position");
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (T value : this.getAllTyped())
        {
            BaseType data = value.toData();

            if (data.isMap())
            {
                data.asMap().putString(StableIds.KEY, value.getId());
            }

            list.add(data);
        }

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.list.clear();

        if (!data.isList())
        {
            return;
        }

        for (BaseType item : data.asList())
        {
            T value = this.create("");

            value.fromData(item);

            if (item.isMap())
            {
                value.setId(item.asMap().getString(StableIds.KEY));
            }

            this.add(value);
        }
    }
}
