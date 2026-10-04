package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
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

    private boolean timelineSelection;
    private String activePath = "";

    /** Opt-in: ordinary form-editing lists keep their single-object semantics. */
    public UIForms timelineSelection()
    {
        this.timelineSelection = true;
        this.multi();
        this.context(menu ->
        {
            int index = this.getIndexAtCursor(this.getContext());
            if (index < 0 || index >= this.visible().size()) return;
            String path = this.visible().get(index).getPath();
            menu.action(Icons.LIMB, L10n.lang("bbs.ui.timeline.select_branch"), () -> this.selectBranch(path));
        });
        return this;
    }

    private void selectBranch(String path)
    {
        this.activePath = path;
        this.setCurrent(this.list.stream().filter(entry -> path.isEmpty()
            || entry.getPath().equals(path) || entry.getPath().startsWith(path + "/")).collect(Collectors.toList()));
        this.fireCallback();
    }

    public String getActivePath()
    {
        List<FormEntry> selected = this.getCurrent();
        if (selected.stream().noneMatch(entry -> entry.getPath().equals(this.activePath)))
        {
            this.activePath = selected.isEmpty() ? "" : selected.get(selected.size() - 1).getPath();
        }
        return this.activePath;
    }

    public void setCurrentPaths(Set<String> paths, String active)
    {
        this.activePath = active;
        List<FormEntry> selected = this.list.stream().filter(entry -> paths.contains(entry.getPath())).collect(Collectors.toList());
        if (selected.isEmpty() && !this.list.isEmpty()) selected = Collections.singletonList(this.list.get(0));
        this.setCurrent(selected);
        this.getActivePath();
    }

    public Map<String, String> selectedNames(Set<String> paths)
    {
        Map<String, String> names = new LinkedHashMap<>();
        for (FormEntry entry : this.list)
        {
            if (paths.contains(entry.getPath())) names.put(entry.getPath(), entry.toString());
        }
        Map<String, Long> counts = names.values().stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        names.replaceAll((path, name) -> counts.get(name) > 1 ? name + " · " + (path.isEmpty() ? "root" : path) : name);
        return names;
    }

    @Override
    protected boolean pressItem(int index, UIContext context)
    {
        if (this.timelineSelection && index >= 0 && index < this.visible().size())
        {
            this.activePath = this.visible().get(index).getPath();
        }
        return super.pressItem(index, context);
    }

    @Override
    protected void applySelectionOnClick(FormEntry item, int index)
    {
        if (this.timelineSelection && !Window.isCtrlPressed() && !Window.isShiftPressed())
        {
            this.setCurrent(item);
        }
        else
        {
            super.applySelectionOnClick(item, index);
        }
    }

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
        if (form != null)
        {
            this.setupRecursively(form, 1, 0);
        }
    }

    /** Restore a stable selection after a refresh; a removed part returns to the root. */
    public String setCurrentPath(String path)
    {
        if (this.list.isEmpty())
        {
            return "";
        }

        FormEntry selected = this.list.get(0);

        for (FormEntry entry : this.list)
        {
            if (entry.getPath().equals(path))
            {
                selected = entry;
                break;
            }
        }

        this.setCurrent(selected);
        this.scroll.scrollIntoView(this.getIndex() * this.scroll.scrollItemSize);

        return selected.getPath();
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
        return this.onReorder == null || item.part == null || super.dragPayload(item) == null ? null : Collections.singletonList(item);
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

        public String getPath()
        {
            return this.part == null ? "" : StringUtils.combinePaths(FormUtils.getPath(this.form), this.part.getId());
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

                return this.form == entry.form
                    && this.part == entry.part
                    && this.depth == entry.depth;
            }

            return false;
        }

        @Override
        public int hashCode()
        {
            return 31 * (31 * System.identityHashCode(this.form) + System.identityHashCode(this.part)) + this.depth;
        }

        @Override
        public String toString()
        {
            if (this.part == null)
            {
                return this.form == null ? "-" : this.form.getDisplayName();
            }
            else if (this.part.getForm() == null)
            {
                return "-";
            }

            return this.part.getForm().getDisplayName();
        }
    }
}
