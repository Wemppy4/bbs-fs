package mchorse.bbs_mod.ui.framework.elements.input.items;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Which branches of a tree are unfolded. Kept apart from the tree's rows so a listing can
 * be rebuilt (a refresh, a search) without the user's folds being lost with it.
 *
 * <p>Only exceptions to the default are stored: with {@code defaultExpanded} the set holds
 * the collapsed keys, otherwise the expanded ones. A huge tree therefore costs nothing
 * until someone touches it.</p>
 *
 * @param <K> what identifies a branch (a path, a bone name)
 */
public class FoldState<K>
{
    private final Set<K> exceptions = new HashSet<>();
    private boolean defaultExpanded;
    private Runnable onChange;

    public FoldState()
    {}

    public FoldState(boolean defaultExpanded)
    {
        this.defaultExpanded = defaultExpanded;
    }

    public FoldState<K> onChange(Runnable listener)
    {
        this.onChange = listener;

        return this;
    }

    private void changed()
    {
        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    public boolean isExpanded(K key)
    {
        return this.exceptions.contains(key) != this.defaultExpanded;
    }

    public void set(K key, boolean expanded)
    {
        boolean changed = expanded == this.defaultExpanded ? this.exceptions.remove(key) : this.exceptions.add(key);

        if (changed)
        {
            this.changed();
        }
    }

    public void toggle(K key)
    {
        this.set(key, !this.isExpanded(key));
    }

    public void expandAll(Collection<K> keys)
    {
        boolean changed = false;

        for (K key : keys)
        {
            changed |= this.defaultExpanded ? this.exceptions.remove(key) : this.exceptions.add(key);
        }

        if (changed)
        {
            this.changed();
        }
    }

    /** Fold everything: the default becomes collapsed and the exceptions are forgotten. */
    public void collapseAll()
    {
        if (this.defaultExpanded || !this.exceptions.isEmpty())
        {
            this.defaultExpanded = false;
            this.exceptions.clear();
            this.changed();
        }
    }
}
