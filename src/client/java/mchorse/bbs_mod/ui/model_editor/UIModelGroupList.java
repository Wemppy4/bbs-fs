package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The model editor's group tree: the bone tree with its groups draggable — among their siblings
 * with the caret, and into another group by dropping onto it. The caret only ever stands where the
 * group can go among its own siblings (the body part list's rule): the slot under the cursor is
 * pulled to the nearest of those, so it never sits inside a subtree the drop would then skip.
 */
public class UIModelGroupList extends UIBoneTreeList
{
    private final Supplier<Model> model;

    private BiConsumer<String, Integer> onReorder;
    private BiConsumer<String, String> onReparent;

    public UIModelGroupList(Consumer<List<String>> callback, Supplier<Model> model)
    {
        super(callback);

        this.model = model;

        this.background();
        this.sorting();
    }

    /** What to do with a group dragged among its siblings: the group, and its place among the others. */
    public UIModelGroupList onReorder(BiConsumer<String, Integer> callback)
    {
        this.onReorder = callback;

        return this;
    }

    /** What to do with a group dropped onto another: the group, and its new parent. */
    public UIModelGroupList onReparent(BiConsumer<String, String> callback)
    {
        this.onReparent = callback;

        return this;
    }

    private ModelGroup group(String id)
    {
        Model model = this.model.get();

        return model == null || id == null ? null : model.getGroup(id);
    }

    private String dragged()
    {
        List<String> items = this.drag.getItems();

        return items.isEmpty() ? null : items.get(0);
    }

    /** The group whose row is under the cursor, for a row's menu; null off the rows. */
    public String atCursor(UIContext context)
    {
        int index = this.getIndexAtCursor(context);

        return index < 0 || index >= this.getList().size() ? null : this.getList().get(index);
    }

    /** Whether {@code group} is inside the subtree of the group named {@code ancestor}. */
    private static boolean isInside(ModelGroup group, String ancestor)
    {
        for (ModelGroup parent = group.parent; parent != null; parent = parent.parent)
        {
            if (parent.id.equals(ancestor))
            {
                return true;
            }
        }

        return false;
    }

    /** A group takes a drop from any group but itself and the ones inside it. */
    @Override
    protected boolean acceptsDrop(String element)
    {
        String dragged = this.dragged();
        ModelGroup target = this.group(element);

        return dragged != null && target != null && !dragged.equals(element) && !isInside(target, dragged);
    }

    @Override
    protected void reportDropTarget(int x, int y)
    {
        super.reportDropTarget(x, y);

        /* A caret: pulled to the nearest slot among the dragged group's siblings — or dropped
         * altogether when it has none to be reordered against. */
        if (this.drag.getTarget() != this)
        {
            return;
        }

        List<Integer> slots = this.siblingSlots(this.dragged());

        if (slots.isEmpty())
        {
            this.drag.clearTarget();

            return;
        }

        int raw = this.drag.getInsertion();
        int nearest = slots.get(0);

        for (int slot : slots)
        {
            if (Math.abs(slot - raw) < Math.abs(nearest - raw))
            {
                nearest = slot;
            }
        }

        this.drag.setTarget(this, nearest);
    }

    /**
     * The slots the dragged group may land in, as rows of the list: before each of its siblings
     * but itself, and past the last of them — past that one's whole subtree, not between its
     * children. In sibling order, so a slot's place in the list is the group's place among them.
     */
    private List<Integer> siblingSlots(String dragged)
    {
        List<Integer> slots = new ArrayList<>();
        ModelGroup group = this.group(dragged);

        if (group == null)
        {
            return slots;
        }

        List<ModelGroup> siblings = group.parent == null ? this.model.get().topGroups : group.parent.children;
        List<String> rows = this.getList();
        ModelGroup last = null;

        for (ModelGroup sibling : siblings)
        {
            int row = sibling == group ? -1 : rows.indexOf(sibling.id);

            if (row >= 0)
            {
                slots.add(row);
                last = sibling;
            }
        }

        if (last == null)
        {
            return slots;
        }

        int end = rows.indexOf(last.id) + 1;

        while (end < rows.size())
        {
            ModelGroup below = this.group(rows.get(end));

            if (below == null || !isInside(below, last.id))
            {
                break;
            }

            end++;
        }

        slots.add(end);

        return slots;
    }

    @Override
    protected void reorder(List<String> items, int insertion)
    {
        String dragged = items.isEmpty() ? null : items.get(0);

        if (dragged == null || this.onReorder == null)
        {
            return;
        }

        int index = this.siblingSlots(dragged).indexOf(insertion);

        if (index >= 0)
        {
            this.onReorder.accept(dragged, index);
        }
    }

    @Override
    protected void onDrop(Object target, List<String> items)
    {
        if (target instanceof String parent && !items.isEmpty() && this.onReparent != null)
        {
            this.onReparent.accept(items.get(0), parent);
        }
    }
}
