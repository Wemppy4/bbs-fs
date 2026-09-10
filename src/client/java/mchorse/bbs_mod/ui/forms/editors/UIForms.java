package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The form being edited and its body parts, as a tree of rows. A part can be carried to a new
 * place among its own siblings - the caret says where it would land. It stays with its parent:
 * a part is addressed by the path of the parts it hangs under, and the tracks of a film hold
 * those paths, so re-hanging one somewhere else is not a drag away.
 */
public class UIForms extends UIList<UIForms.FormEntry>
{
    /** How far a row is pushed right per level of the tree. */
    public static final int INDENT = 10;

    /** Where a carried part was let go: the part and its new place among its siblings. */
    public BiConsumer<BodyPart, Integer> onReorder;

    public UIForms(Consumer<List<FormEntry>> callback)
    {
        super(callback);

        this.sorting();
        this.scroll.cancelScrolling();
    }

    public void setCurrentForm(Form form)
    {
        FormEntry toSelect = null;

        for (FormEntry entry : this.list)
        {
            if (entry.getForm() == form)
            {
                toSelect = entry;

                break;
            }
        }

        if (toSelect != null)
        {
            this.setCurrentScroll(toSelect);
        }
    }

    public void setForm(Form form)
    {
        this.clear();

        this.add(new FormEntry(form, null, 0, 0, true));
        this.setupRecursively(form, 1, 0);
    }

    private void setupRecursively(Form parent, int depth, int lines)
    {
        List<BodyPart> parts = parent.parts.getAllTyped();

        for (int i = 0; i < parts.size(); i++)
        {
            BodyPart part = parts.get(i);
            boolean last = i == parts.size() - 1;

            this.add(new FormEntry(parent, part, depth, lines, last));

            if (part.getForm() != null)
            {
                this.setupRecursively(part.getForm(), depth + 1, childGuideLines(lines, depth, last));
            }
        }
    }

    @Override
    protected void renderElementPart(UIContext context, FormEntry element, int i, int x, int y, boolean hover, boolean selected)
    {
        this.renderTreeGuides(context, x, y, element.depth, element.lines, element.last, x + this.rowContentX(element));
        super.renderElementPart(context, element, i, x, y, hover, selected);

        Form form = element.getForm();

        if (form != null && BBSSettings.listModelPreview.get())
        {
            x += this.area.w - 40;

            context.batcher.clip(x, y, 40, 20, context);

            y -= 10;

            FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);

            context.batcher.unclip(context);
        }
    }

    @Override
    protected int indent(FormEntry element)
    {
        return element.depth * INDENT;
    }

    @Override
    protected int indentStep()
    {
        return INDENT;
    }

    @Override
    protected String elementToString(UIContext context, int i, FormEntry element)
    {
        return element.toString();
    }

    /* Dragging a body part among its siblings */

    /** The form itself is the root of the tree: it has nowhere to go. */
    @Override
    protected List<FormEntry> dragPayload(FormEntry item)
    {
        /* One part at a time: a body part's slot is worked out from its own siblings, so a group
         * dragged at once would need a slot each, and parts of different parents have none in common. */
        return item.part == null || super.dragPayload(item) == null ? null : Collections.singletonList(item);
    }

    /**
     * The slots a carried part may land in: before each of its siblings, and after the last of
     * them. A sibling's own children sit between those slots, so the raw slot under the cursor
     * is pulled to the nearest one - the caret then always stands where the part can actually
     * go, instead of somewhere inside a subtree it would silently skip out of.
     */
    private List<Integer> siblingSlots(Form parent)
    {
        List<Integer> slots = new ArrayList<>();
        int last = -1;
        int depth = 0;

        for (int i = 0; i < this.list.size(); i++)
        {
            FormEntry entry = this.list.get(i);

            if (entry.part != null && entry.form == parent)
            {
                slots.add(i);

                last = i;
                depth = entry.depth;
            }
        }

        if (slots.isEmpty())
        {
            return slots;
        }

        /* The slot after the last sibling sits past its children, not between them */
        int end = last + 1;

        while (end < this.list.size() && this.list.get(end).depth > depth)
        {
            end++;
        }

        slots.add(end);

        return slots;
    }

    private FormEntry draggedEntry()
    {
        List<FormEntry> items = this.drag.getItems();

        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    protected void reportDropTarget(int x, int y)
    {
        FormEntry dragged = this.draggedEntry();

        if (dragged == null || dragged.part == null)
        {
            return;
        }

        List<Integer> slots = this.siblingSlots(dragged.form);

        if (slots.isEmpty())
        {
            return;
        }

        int raw = this.insertionAt(x, y);
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

    @Override
    protected void reorder(List<FormEntry> items, int insertion)
    {
        FormEntry dragged = items.isEmpty() ? null : items.get(0);

        if (dragged == null || dragged.part == null || this.onReorder == null)
        {
            return;
        }

        int index = this.siblingSlots(dragged.form).indexOf(insertion);

        if (index != -1)
        {
            this.onReorder.accept(dragged.part, index);
        }
    }

    public static class FormEntry
    {
        public Form form;
        public BodyPart part;
        public int depth;
        public final int lines;
        public final boolean last;

        public FormEntry(Form form, BodyPart part, int depth, int lines, boolean last)
        {
            this.form = form;
            this.part = part;
            this.depth = depth;
            this.lines = lines;
            this.last = last;
        }

        public Form getForm()
        {
            return this.part == null ? this.form : this.part.getForm();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (super.equals(obj))
            {
                return true;
            }

            if (obj instanceof FormEntry)
            {
                FormEntry entry = (FormEntry) obj;

                return Objects.equals(this.form, entry.form)
                    && Objects.equals(this.part, entry.part)
                    && this.depth == entry.depth;
            }

            return false;
        }

        @Override
        public String toString()
        {
            if (this.part == null)
            {
                return this.form.getDisplayName();
            }
            else if (this.part.getForm() == null)
            {
                return "-";
            }

            return this.part.getForm().getDisplayName();
        }
    }
}
