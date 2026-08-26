package mchorse.bbs_mod.ui.utils.cells;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The items a user has picked in a grid to act on together — drag them, copy or move them,
 * remove them. Deliberately separate from the grid's <em>current</em> item (the one an
 * editor opens or a picker hands back): a pick is a bookkeeping mark and there can be many.
 *
 * <p>Shift-click extends from an anchor, but only within the anchor's {@code scope} (a form
 * category, say) — across scopes the orders aren't comparable, so it's a plain add.</p>
 *
 * @param <T> what's picked
 */
public abstract class GridSelection<T>
{
    private final List<T> items = new ArrayList<>();
    private T anchor;
    private Object anchorScope;

    /** Whether two items are the same pick. */
    protected abstract boolean same(T a, T b);

    public boolean contains(T item)
    {
        return this.indexOf(this.items, item) != -1;
    }

    public boolean isEmpty()
    {
        return this.items.isEmpty();
    }

    /** More than one item — the state in which cell actions and menus act on the whole pick. */
    public boolean isGroup()
    {
        return this.items.size() > 1;
    }

    public int size()
    {
        return this.items.size();
    }

    public List<T> getItems()
    {
        return Collections.unmodifiableList(this.items);
    }

    public T getAnchor()
    {
        return this.anchor;
    }

    public Object getAnchorScope()
    {
        return this.anchorScope;
    }

    public void clear()
    {
        this.items.clear();
        this.anchor = null;
        this.anchorScope = null;
    }

    public void set(T item, Object scope)
    {
        this.clear();
        this.add(item, scope);
    }

    public void add(T item, Object scope)
    {
        if (item != null && !this.contains(item))
        {
            this.items.add(item);
        }

        this.anchor = item;
        this.anchorScope = scope;
    }

    public void toggle(T item, Object scope)
    {
        int index = this.indexOf(this.items, item);

        if (index == -1)
        {
            this.add(item, scope);
        }
        else
        {
            this.items.remove(index);

            if (this.anchor != null && this.same(this.anchor, item))
            {
                this.anchor = this.items.isEmpty() ? null : this.items.get(this.items.size() - 1);
                this.anchorScope = this.items.isEmpty() ? null : scope;
            }
        }
    }

    /**
     * Pick every item between the anchor and {@code item} in {@code order} (inclusive), the
     * way Shift-click extends a selection. Without an anchor in that order it's a plain add.
     */
    public void range(T item, Object scope, List<T> order)
    {
        int from = this.anchorScope == scope || (this.anchorScope != null && this.anchorScope.equals(scope)) ? this.indexOf(order, this.anchor) : -1;
        int to = this.indexOf(order, item);

        if (from == -1 || to == -1)
        {
            this.add(item, scope);

            return;
        }

        for (int i = Math.min(from, to); i <= Math.max(from, to); i++)
        {
            T t = order.get(i);

            if (!this.contains(t))
            {
                this.items.add(t);
            }
        }
    }

    /** Forget items that no longer exist. */
    public void retain(Predicate<T> exists)
    {
        this.items.removeIf((item) -> !exists.test(item));

        if (this.anchor != null && !this.contains(this.anchor))
        {
            this.anchor = null;
            this.anchorScope = null;
        }
    }

    public int indexOf(List<T> list, T item)
    {
        if (item == null)
        {
            return -1;
        }

        for (int i = 0; i < list.size(); i++)
        {
            if (this.same(list.get(i), item))
            {
                return i;
            }
        }

        return -1;
    }
}
