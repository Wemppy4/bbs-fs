package mchorse.bbs_mod.utils.categories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.core.StableIds;
import mchorse.bbs_mod.settings.values.core.ValueStableList;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A list's folders, in the order they are shown in.
 *
 * <p>Only folders the author has touched are recorded here — a folder gets a record when it is
 * made, moved, folded or painted. Everything else about the tree is read off the items themselves,
 * so this list never has to be kept in step with them: an item brought in from elsewhere carries a
 * path no record knows, and it is simply shown at the end.</p>
 */
public class Categories extends ValueStableList<Category>
{
    public Categories(String id)
    {
        super(id);
    }

    @Override
    protected Category create(String id)
    {
        return new Category(id);
    }

    /**
     * Data written before folders had records holds a plain list of names here, because the name
     * was all a folder was. Both shapes are read.
     */
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
            Category category = this.create("");

            if (item.isString())
            {
                category.path.set(CategoryPath.normalize(item.asString()));
            }
            else
            {
                category.fromData(item);
                category.path.set(CategoryPath.normalize(category.path.get()));

                if (item.isMap())
                {
                    category.setId(item.asMap().getString(StableIds.KEY));
                }
            }

            if (!category.path.get().isEmpty() && this.getByPath(category.path.get()) == null)
            {
                this.add(category);
            }
        }
    }

    public Category getByPath(String path)
    {
        for (Category category : this.list)
        {
            if (category.path.get().equals(path))
            {
                return category;
            }
        }

        return null;
    }

    /** Every recorded folder's path, in the order the records are in. */
    public List<String> getPaths()
    {
        List<String> paths = new ArrayList<>();

        for (Category category : this.list)
        {
            paths.add(category.path.get());
        }

        return paths;
    }

    /**
     * The record for a folder, made if the folder has none yet. Its ancestors are recorded too:
     * a folder cannot be shown without them, and without records of their own they would drift to
     * the end of the list, taking their child with them.
     */
    public Category ensure(String path)
    {
        String normalized = CategoryPath.normalize(path);

        if (normalized.isEmpty())
        {
            return null;
        }

        Category existing = this.getByPath(normalized);

        if (existing != null)
        {
            return existing;
        }

        String parent = CategoryPath.parent(normalized);

        this.ensure(parent);

        Category category = this.create("");

        category.path.set(normalized);

        /* A top folder is born with a colour of its own, so a list of them reads apart at a glance
         * instead of waiting for someone to paint it. A folder inside another is born without one:
         * it wears its parent's stripe, which is what says the two belong together. */
        if (parent.isEmpty())
        {
            category.color.set(Colors.randomBright());
        }

        this.add(category);

        return category;
    }

    /** Whether a folder is open; one nobody has folded yet is. */
    public boolean isExpanded(String path)
    {
        Category category = this.getByPath(path);

        return category == null || category.expanded.get();
    }

    public void setExpanded(String path, boolean expanded)
    {
        Category category = this.ensure(path);

        if (category != null)
        {
            category.expanded.set(expanded);
        }
    }

    /**
     * Drop one folder's record. What was inside it is not this list's business: the caller decides
     * where those folders and replays go, and says so by {@link #renameSubtree renaming} them.
     */
    public void removeRecord(String path)
    {
        Iterator<Category> it = this.list.iterator();

        while (it.hasNext())
        {
            if (it.next().path.get().equals(path))
            {
                it.remove();
            }
        }
    }

    /**
     * Put a folder where the tree should show it: right before another folder, or last of all when
     * none is named. Its records travel as a block — the order is read by walking these records and
     * opening each path's ancestors first, so a record left behind would pull its folder back to
     * where the child sits.
     */
    public void moveBefore(String path, String before)
    {
        Category category = this.ensure(path);

        if (category == null)
        {
            return;
        }

        if (!before.isEmpty())
        {
            this.ensure(before);
        }

        List<Category> subtree = new ArrayList<>();

        for (Category other : this.list)
        {
            if (CategoryPath.isInside(other.path.get(), path))
            {
                subtree.add(other);
            }
        }

        /* By identity: two folders with the same fields are equal by content, and a list that
         * looks them up that way would take out the wrong one. */
        for (Category record : subtree)
        {
            int index = CollectionUtils.getIndex(this.list, record);

            if (index >= 0)
            {
                this.list.remove(index);
            }
        }

        Category target = before.isEmpty() ? null : this.getByPath(before);
        int at = target == null ? this.list.size() : CollectionUtils.getIndex(this.list, target);

        this.list.addAll(at < 0 ? this.list.size() : at, subtree);
    }

    /**
     * Rename or move a folder: its own record and every record inside it follow the new head. The
     * items are swept by the caller — they hold their path themselves.
     */
    public void renameSubtree(String from, String to)
    {
        for (Category category : this.list)
        {
            String path = category.path.get();
            String moved = CategoryPath.reparent(path, from, to);

            if (!moved.equals(path))
            {
                category.path.set(moved);
            }
        }
    }
}
