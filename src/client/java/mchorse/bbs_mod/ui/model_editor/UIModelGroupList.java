package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The model editor's group tree: the bone tree with its groups draggable, folding and pickable
 * several at a time.
 *
 * <p>A group can be dragged anywhere, because in this tree — unlike the body part list — changing
 * a group's parent is allowed. So the caret means what it looks like: dropped between two rows,
 * the group becomes the SIBLING OF THE ROW BELOW, right before it, whatever depth that row sits
 * at; dropped past the last row it goes to the end of the roots. Dropped ONTO a row (its middle
 * quarter, the base list's rule) it goes inside that group, last. Nothing snaps and nothing is
 * refused except a drop into the group's own subtree, which would unhook it from the model.</p>
 */
public class UIModelGroupList extends UIBoneTreeList
{
    private final Supplier<Model> model;

    private BiConsumer<String, String> onMove;
    private BiConsumer<String, String> onReparent;

    public UIModelGroupList(Consumer<List<String>> callback, Supplier<Model> model)
    {
        super(callback);

        this.model = model;

        this.background();
        this.sorting();
        this.multi();
        this.collapsible();
    }

    /**
     * What to do with a group dropped between rows: the group, and the group it now comes right
     * before — null for the end of the roots.
     */
    public UIModelGroupList onMove(BiConsumer<String, String> callback)
    {
        this.onMove = callback;

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

    /**
     * Bring a group's row into view, and only as far as that. The list's own setCurrentScroll puts
     * the picked row at the very top instead, which reads as the list jumping under the cursor when
     * the pick follows a drag or an edit the eye is already watching.
     */
    public void reveal(String id)
    {
        int index = this.getList().indexOf(id);

        if (index >= 0)
        {
            this.scrollIntoView(index);
        }
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
    protected void reorder(List<String> items, int insertion)
    {
        String dragged = items.isEmpty() ? null : items.get(0);

        if (dragged == null || this.onMove == null)
        {
            return;
        }

        List<String> rows = this.visible();
        String before = insertion >= 0 && insertion < rows.size() ? rows.get(insertion) : null;

        /* The caret above the dragged row itself means "stay where you are". */
        if (dragged.equals(before))
        {
            return;
        }

        this.onMove.accept(dragged, before);
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
