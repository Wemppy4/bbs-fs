package mchorse.bbs_mod.ui.framework.elements.input.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * The items a user has picked to act on together — drag them, copy or move them, remove
 * them. One selection serves rows of a list, cells of a grid and nodes of a tree alike:
 * it only knows items and the order they were picked in.
 *
 * <p>Shift-click extends from an anchor, but only within the anchor's {@code scope} (a form
 * category, say) — across scopes the orders aren't comparable, so it's a plain add.</p>
 *
 * <p>What "the same item" means is pluggable: by default {@link Object#equals}, or a
 * predicate handed to the constructor (identity for forms, which may look alike).</p>
 *
 * @param <T> what's picked
 */
public class Selection<T>
{
    private final List<T> items = new ArrayList<>();
    private final BiPredicate<T, T> sameness;
    private T anchor;
    private Object anchorScope;
    private Runnable onChange;

    public Selection()
    {
        this(null);
    }

    public Selection(BiPredicate<T, T> same)
    {
        this.sameness = same;
    }

    /** Whether two items are the same pick. */
    protected boolean same(T a, T b)
    {
        return this.sameness == null ? Objects.equals(a, b) : this.sameness.test(a, b);
    }

    /** Called after every change made through this class' own methods. */
    public Selection<T> onChange(Runnable listener)
    {
        this.onChange = listener;

        return this;
    }

    protected void changed()
    {
        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

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

    /** The first picked item, or null — what single-selection hosts act on. */
    public T getFirst()
    {
        return this.items.isEmpty() ? null : this.items.get(0);
    }

    public T getAnchor()
    {
        return this.anchor;
    }

    public void clear()
    {
        boolean had = !this.items.isEmpty() || this.anchor != null;

        this.items.clear();
        this.anchor = null;
        this.anchorScope = null;

        if (had)
        {
            this.changed();
        }
    }

    public void set(T item, Object scope)
    {
        this.items.clear();
        this.anchor = null;
        this.anchorScope = null;

        this.add(item, scope);
    }

    public void setAll(Collection<T> items)
    {
        this.items.clear();
        this.anchor = null;
        this.anchorScope = null;

        for (T item : items)
        {
            if (item != null && !this.contains(item))
            {
                this.items.add(item);
            }
        }

        this.anchor = this.items.isEmpty() ? null : this.items.get(this.items.size() - 1);
        this.changed();
    }

    public void add(T item, Object scope)
    {
        if (item != null && !this.contains(item))
        {
            this.items.add(item);
        }

        this.anchor = item;
        this.anchorScope = scope;
        this.changed();
    }

    public void remove(T item)
    {
        int index = this.indexOf(this.items, item);

        if (index == -1)
        {
            return;
        }

        this.items.remove(index);

        if (this.anchor != null && this.same(this.anchor, item))
        {
            this.anchor = this.items.isEmpty() ? null : this.items.get(this.items.size() - 1);
            this.anchorScope = this.items.isEmpty() ? null : this.anchorScope;
        }

        this.changed();
    }

    public void toggle(T item, Object scope)
    {
        if (this.indexOf(this.items, item) == -1)
        {
            this.add(item, scope);
        }
        else
        {
            this.remove(item);
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

        this.changed();
    }

    /** Forget items that no longer exist. */
    public void retain(Predicate<T> exists)
    {
        boolean removed = this.items.removeIf((item) -> !exists.test(item));

        if (this.anchor != null && !this.contains(this.anchor))
        {
            this.anchor = null;
            this.anchorScope = null;
            removed = true;
        }

        if (removed)
        {
            this.changed();
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
