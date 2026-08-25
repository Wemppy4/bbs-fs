package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIRecentFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.morphing.UIMorphFormCategoryFilterOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.DiffuseLighting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The list of form categories with the search bar under it.
 *
 * <p>Besides the categories themselves it owns everything that spans them: the cell size
 * (Ctrl + wheel zooms it), the {@link #selection multi-selection}, and a {@link #drag} —
 * forms between and within categories, user categories among themselves. Categories paint
 * and hit-test their own cells and call back here with what was pressed.</p>
 */
public class UIFormList extends UIElement
{
    public static final int ZOOM_STEP = 8;
    private static final int AUTO_SCROLL_EDGE = 24;
    private static final int AUTO_SCROLL_SPEED = 6;

    public IUIFormList palette;

    public UIScrollView forms;

    public UIElement bar;
    public UITextbox search;
    public UIIcon edit;
    public UIIcon close;
    public UIIcon categoryFilter;

    public final FormSelection selection = new FormSelection();
    public final FormDrag drag = new FormDrag();

    private UIFormCategory recent;
    private List<UIFormCategory> categories = new ArrayList<>();

    private long lastUpdate;
    private int lastScroll;
    private boolean pendingScrollToSelected;

    /** A header pressed but not yet released: a release without a drag collapses it. */
    private UIFormCategory pressedHeader;

    /* The quick action under the cursor this frame, and where its label goes */
    private FormCellAction hoveredAction;
    private int hoveredActionX;
    private int hoveredActionY;

    public UIFormList(IUIFormList palette)
    {
        this.palette = palette;

        this.forms = UI.scrollView(0, 0);
        this.forms.scroll.cancelScrolling();
        this.bar = new UIElement();
        this.search = new UITextbox(100, this::onSearchQuery).placeholder(UIKeys.FORMS_LIST_SEARCH);
        this.edit = new UIIcon(Icons.EDIT, this::edit);
        this.edit.tooltip(UIKeys.FORMS_LIST_EDIT, Direction.TOP);
        this.close = new UIIcon(Icons.CLOSE, this::close);

        this.forms.full(this);
        this.bar.relative(this).x(10).y(1F, -30).w(1F, -20).h(20).row().height(20);
        this.close.w(20);

        this.categoryFilter = new UIIcon(Icons.FILTER, this::openMorphCategoryFilter);
        this.categoryFilter.tooltip(UIKeys.MORPHING_FILTER_CATEGORIES, Direction.TOP);
        this.categoryFilter.w(20);
        this.bar.add(this.categoryFilter, this.search, this.edit, this.close);

        this.add(this.forms, this.bar);

        this.search.keys().register(Keys.FORMS_FOCUS, this::focusSearchInput);

        this.markContainer();
        this.setupForms(BBSModClient.getFormCategories());
    }

    private void openMorphCategoryFilter(UIIcon b)
    {
        Set<String> disabled = BBSSettings.disabledMorphFormCategories.get();
        FormCategories formCategories = BBSModClient.getFormCategories();
        UIMorphFormCategoryFilterOverlayPanel panel = new UIMorphFormCategoryFilterOverlayPanel(
            disabled,
            formCategories.getAllCategories()
        );

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

        panel.onClose(e ->
        {
            BBSSettings.disabledMorphFormCategories.set(disabled);
            Form selected = this.getSelected();
            this.setupForms(formCategories);
            this.setSelected(selected);
        });
    }

    public void focusSearchInput()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            this.search.clickItself(context);
        }
    }

    public int getCellSize()
    {
        return BBSSettings.formCellSize.get();
    }

    public void setupForms(FormCategories forms)
    {
        this.categories.clear();
        this.forms.removeAll();

        for (FormCategory category : forms.getAllCategories())
        {
            if (BBSSettings.disabledMorphFormCategories.get().contains(category.visible.getId()))
            {
                continue;
            }

            UIFormCategory uiCategory = category.createUI(this);

            this.forms.add(uiCategory);
            this.categories.add(uiCategory);

            if (uiCategory instanceof UIRecentFormCategory)
            {
                this.recent = uiCategory;
            }
        }

        if (!this.categories.isEmpty())
        {
            this.categories.get(this.categories.size() - 1).marginBottom(40);
        }

        this.resize();

        this.lastUpdate = forms.getLastUpdate();
        this.applySearchFromTextbox();
        this.reconcile();
    }

    private void onSearchQuery(String search)
    {
        this.applySearchFilter(search);
    }

    private void applySearchFromTextbox()
    {
        this.applySearchFilter(this.search.getText());
    }

    private void applySearchFilter(String raw)
    {
        String s = raw == null ? "" : raw.trim();

        for (UIFormCategory category : this.categories)
        {
            category.search(s);
        }

        this.afterSearchLayout();
    }

    private void afterSearchLayout()
    {
        int columnW = Math.max(this.getCellSize(), this.forms.area.w);

        for (UIFormCategory category : this.categories)
        {
            category.refreshLayoutForSearch(columnW);
        }

        this.forms.resize();
        this.resize();
    }

    private void edit(UIIcon b)
    {
        this.palette.toggleEditor();
    }

    private void close(UIIcon b)
    {
        this.palette.exit();
    }

    /* Zoom */

    @Override
    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        if (Window.isCtrlPressed() && context.mouseWheel != 0 && this.forms.area.isInside(context))
        {
            this.zoom(context, context.mouseWheel > 0 ? ZOOM_STEP : -ZOOM_STEP);

            return this;
        }

        return super.childrenMouseScrolled(context);
    }

    /**
     * Grow or shrink the cells, keeping whatever row is under the cursor where it is on
     * screen — zooming that jumps the list around is worse than no zoom at all.
     */
    private void zoom(UIContext context, int delta)
    {
        int old = this.getCellSize();
        int size = MathUtils.clamp(old + delta, FormGridLayout.MIN_CELL, FormGridLayout.MAX_CELL);

        if (size == old)
        {
            return;
        }

        int mouseY = context.mouseY - this.forms.area.y;
        int contentY = mouseY + (int) this.forms.scroll.getScroll();
        UIFormCategory anchor = null;
        int row = -1;
        float offset = 0;

        for (UIFormCategory category : this.categories)
        {
            int top = category.area.y - this.forms.area.y;

            if (contentY >= top && contentY < top + category.area.h)
            {
                FormGridLayout layout = category.getLayout();
                int y = contentY - top;

                anchor = category;

                if (y >= layout.getRowY(0) && layout.getRows() > 0)
                {
                    row = Math.min(layout.getRows() - 1, (y - layout.getRowY(0)) / (layout.getCellHeight() + FormGridLayout.GAP));
                    offset = (y - layout.getRowY(row)) / (float) layout.getCellHeight();
                }
                else
                {
                    offset = y;
                }

                break;
            }
        }

        BBSSettings.formCellSize.set(size);
        this.afterSearchLayout();

        if (anchor != null)
        {
            FormGridLayout layout = anchor.getLayout();
            int top = anchor.area.y - this.forms.area.y;
            int y = row >= 0 ? top + layout.getRowY(row) + (int) (offset * layout.getCellHeight()) : top + (int) offset;

            this.forms.scroll.setScroll(y - mouseY);
        }
    }

    /* Selection */

    public void selectCategory(UIFormCategory category, Form form, boolean notify)
    {
        this.deselect();

        category.selected = form;

        if (notify)
        {
            this.palette.accept(form);
        }
    }

    public void deselect()
    {
        for (UIFormCategory category : this.categories)
        {
            category.selected = null;
        }
    }

    public UIFormCategory getSelectedCategory()
    {
        for (UIFormCategory category : this.categories)
        {
            if (category.selected != null)
            {
                return category;
            }
        }

        return null;
    }

    public Form getSelected()
    {
        UIFormCategory category = this.getSelectedCategory();

        return category == null ? null : category.selected;
    }

    public void setSelected(Form form)
    {
        boolean found = false;

        this.deselect();

        for (UIFormCategory category : this.categories)
        {
            int index = category.category.getForms().indexOf(form);

            if (index == -1)
            {
                category.selected = null;
            }
            else
            {
                found = true;

                category.select(category.category.getForms().get(index), false);
            }
        }

        if (!found && form != null && this.recent != null)
        {
            Form copy = FormUtils.copy(form);

            this.recent.category.addForm(copy);
            this.recent.select(copy, false);
        }
    }

    /** The category that holds a form, or null once it's been removed. */
    public FormCategory categoryOf(Form form)
    {
        for (UIFormCategory category : this.categories)
        {
            if (FormSelection.identityIndex(category.category.getForms(), form) != -1)
            {
                return category.category;
            }
        }

        return null;
    }

    /** Whether every picked form lives in a category the user may take it out of. */
    public boolean canModifySelection()
    {
        for (Form form : this.selection.getForms())
        {
            FormCategory category = this.categoryOf(form);

            if (category == null || !category.canModify(form))
            {
                return false;
            }
        }

        return !this.selection.isEmpty();
    }

    public void copySelectionTo(FormCategory to, boolean move)
    {
        for (Form form : new ArrayList<>(this.selection.getForms()))
        {
            FormCategory from = this.categoryOf(form);

            if (move && from != null && from.canModify(form) && from != to)
            {
                from.removeForm(form);
                to.addForm(form);
            }
            else if (!move)
            {
                to.addForm(FormUtils.copy(form));
            }
        }

        this.reconcile();
    }

    public void removeSelection()
    {
        for (Form form : new ArrayList<>(this.selection.getForms()))
        {
            FormCategory from = this.categoryOf(form);

            if (from != null && from.canModify(form))
            {
                from.removeForm(form);
            }
        }

        this.reconcile();
    }

    /**
     * Drop marks that point at forms no longer in any category — after a removal, a move,
     * or a rebuild of the categories.
     */
    public void reconcile()
    {
        List<FormCategory> models = new ArrayList<>();

        for (UIFormCategory category : this.categories)
        {
            models.add(category.category);

            if (category.selected != null && FormSelection.identityIndex(category.category.getForms(), category.selected) == -1)
            {
                category.selected = null;
            }
        }

        this.selection.retain(models);
    }

    /* Pointer, reported by categories */

    public void pressForm(UIFormCategory category, Form form, List<Form> order, UIContext context)
    {
        if (Window.isCtrlPressed())
        {
            this.selection.toggle(form, category.category);
        }
        else if (Window.isShiftPressed())
        {
            this.selection.range(form, category.category, order);
        }
        else
        {
            /* A plain click on one of several picked forms keeps the group, so it can be
             * dragged as a whole; anywhere else it starts a new one */
            if (!this.selection.contains(form) || !this.selection.isGroup())
            {
                this.selection.set(form, category.category);
            }

            category.select(form, true);
        }

        List<Form> payload = this.selection.contains(form) ? new ArrayList<>(this.selection.getForms()) : Collections.singletonList(form);

        this.drag.pressForms(payload, category.category, context.globalX(context.mouseX), context.globalY(context.mouseY));
    }

    public void pressHeader(UIFormCategory category, UIContext context)
    {
        this.pressedHeader = category;

        if (category.category instanceof UserFormCategory user)
        {
            this.drag.pressCategory(user, context.globalX(context.mouseX), context.globalY(context.mouseY));
        }
    }

    public void clickEmpty()
    {
        this.selection.clear();
    }

    public void runAction(UIFormCategory category, Form form, FormCellAction action)
    {
        boolean group = this.selection.isGroup() && this.selection.contains(form);

        switch (action)
        {
            case EDIT ->
            {
                this.selection.set(form, category.category);
                category.select(form, true);
                this.palette.toggleEditor();
            }
            case DUPLICATE ->
            {
                for (Form f : group ? new ArrayList<>(this.selection.getForms()) : Collections.singletonList(form))
                {
                    FormCategory from = this.categoryOf(f);

                    if (from != null && from.canModify(f))
                    {
                        from.insertForm(FormSelection.identityIndex(from.getForms(), f) + 1, FormUtils.copy(f));
                    }
                }
            }
            case REMOVE ->
            {
                if (group)
                {
                    this.removeSelection();
                }
                else
                {
                    category.category.removeForm(form);
                    this.reconcile();
                }
            }
        }
    }

    public void setHoveredAction(FormCellAction action, int x, int y)
    {
        this.hoveredAction = action;
        this.hoveredActionX = x;
        this.hoveredActionY = y;
    }

    public UIFormCategory getPressedHeader()
    {
        return this.pressedHeader;
    }

    /** The button went up: finish the drag if one is on, and forget whatever was pressed. */
    private void release()
    {
        if (this.drag.isActive())
        {
            this.drop();
        }

        this.pressedHeader = null;
        this.drag.reset();
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.release();

        return false;
    }

    /* Drop */

    private void drop()
    {
        UIFormCategory target = this.drag.getTarget();

        if (target == null)
        {
            return;
        }

        if (this.drag.getKind() == FormDrag.Kind.FORMS)
        {
            this.dropForms(target, this.drag.getInsertion());
        }
        else
        {
            this.dropCategory(target, this.drag.getInsertion());
        }
    }

    /**
     * Forms dropped into a category land before the form that was at the caret — the same
     * form for every one of them, so a group keeps its order. Within their own category they
     * are rearranged (when its order is manual); from another they move if that category can
     * let them go and copy otherwise. Ctrl always copies.
     */
    private void dropForms(UIFormCategory target, int insertion)
    {
        FormCategory into = target.category;

        if (!into.canModify(null))
        {
            return;
        }

        List<Form> view = target.getForms();
        Form before = null;

        for (int i = insertion; i < view.size(); i++)
        {
            if (!this.drag.isDragging(view.get(i)))
            {
                before = view.get(i);

                break;
            }
        }

        boolean copy = Window.isCtrlPressed();
        boolean rearrangeable = into.getSort().isRearrangeable();

        for (Form form : this.drag.getForms())
        {
            FormCategory from = this.categoryOf(form);
            int index = before == null || !rearrangeable ? into.getForms().size() : FormSelection.identityIndex(into.getForms(), before);

            if (from == into && !copy)
            {
                if (rearrangeable)
                {
                    into.moveForm(form, index);
                }

                continue;
            }

            if (!copy && from != null && from.canModify(form))
            {
                from.removeForm(form);
                into.insertForm(index, form);
            }
            else
            {
                into.insertForm(index, FormUtils.copy(form));
            }
        }

        this.reconcile();
    }

    private void dropCategory(UIFormCategory target, int insertion)
    {
        if (!(target.category instanceof UserFormCategory user))
        {
            return;
        }

        UserFormSection section = BBSModClient.getFormCategories().getUserForms();

        section.moveUserCategory(this.drag.getCategory(), section.categories.indexOf(user) + insertion);
    }

    /* Scrolling */

    /**
     * Request the list to scroll so that the currently selected form becomes
     * visible. The actual scrolling is deferred to {@link #render(UIContext)}
     * because it needs the list's real (resized) width to lay the categories
     * out at their final height.
     */
    public void scrollToSelected()
    {
        this.pendingScrollToSelected = true;
    }

    private void scrollToSelectedForm()
    {
        UIFormCategory category = this.getSelectedCategory();

        if (category == null)
        {
            return;
        }

        /* Categories only learn their real height once they render (until then
         * they keep the inflated, width-0 height from setupForms), so the bounds
         * of off-screen categories above the selection are stale. Lay them all
         * out at the real width first so the offsets below are final. */
        this.afterSearchLayout();

        int contentY = category.area.y - this.forms.area.y;
        int formY = category.getFormY(category.selected);
        int itemHeight = FormGridLayout.HEADER;

        if (formY > 0)
        {
            contentY += formY;
            itemHeight = category.getLayout().getCellHeight();
        }

        /* Center the selected form (or the category header when it's collapsed)
         * within the visible area; scrollTo() clamps to the scroll bounds. */
        this.forms.scroll.setScroll(contentY - (this.forms.area.h - itemHeight) / 2);
    }

    private void autoScroll(UIContext context)
    {
        if (!this.drag.isActive() || !this.forms.area.isInside(context))
        {
            return;
        }

        if (context.mouseY < this.forms.area.y + AUTO_SCROLL_EDGE)
        {
            this.forms.scroll.scrollBy(-AUTO_SCROLL_SPEED);
        }
        else if (context.mouseY > this.forms.area.ey() - AUTO_SCROLL_EDGE)
        {
            this.forms.scroll.scrollBy(AUTO_SCROLL_SPEED);
        }
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        FormCategories categories = BBSModClient.getFormCategories();

        if (this.lastScroll >= 0)
        {
            this.forms.scroll.scrollTo(this.lastScroll);

            this.lastScroll = -1;
        }

        if (this.lastUpdate != categories.getLastUpdate())
        {
            this.lastScroll = (int) this.forms.scroll.getScroll();

            Form selected = this.getSelected();

            this.setupForms(categories);
            this.setSelected(selected);
        }

        /* A release swallowed by another element (a button, an overlay) must not leave a
         * drag hanging on the cursor */
        if ((this.drag.isPressed() || this.pressedHeader != null) && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            this.release();
        }

        this.drag.update(context.mouseX, context.mouseY);
        this.drag.clearTarget();
        this.hoveredAction = null;
        this.autoScroll(context);

        DiffuseLighting.enableGuiDepthLighting();

        super.render(context);

        DiffuseLighting.disableGuiDepthLighting();

        if (this.pendingScrollToSelected && this.forms.area.w > 0)
        {
            this.scrollToSelectedForm();

            this.pendingScrollToSelected = false;
        }

        this.renderStatus(context);

        if (this.hoveredAction != null && !this.drag.isActive())
        {
            FormCellRenderer.renderActionLabel(context, this.hoveredAction, this.hoveredActionX, this.hoveredActionY);
        }

        if (this.drag.isActive())
        {
            this.renderGhost(context);
        }
    }

    /** Name and id of the chosen form, in one line above the search — the id lives nowhere else. */
    private void renderStatus(UIContext context)
    {
        Form selected = this.getSelected();

        if (selected == null)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        String name = selected.getDisplayName();
        String id = selected.getFormId();
        int x = this.search.area.x;
        int y = this.search.area.y - 18;
        int w = font.getWidth(name) + 8 + font.getWidth(id) + 8;

        context.batcher.box(x, y, x + w, y + 16, Colors.A50);
        context.batcher.textShadow(name, x + 4, y + 4);
        context.batcher.textShadow(id, x + 4 + font.getWidth(name) + 8, y + 4, Colors.LIGHTER_GRAY);
    }

    /** What's being carried, drawn beside the cursor: a small stack of the forms, or the category's name. */
    private void renderGhost(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();
        int x = context.mouseX + 10;
        int y = context.mouseY + 10;
        boolean landing = this.drag.getTarget() != null;

        if (this.drag.getKind() == FormDrag.Kind.CATEGORY)
        {
            batcher.textCard(this.drag.getCategory().getProcessedTitle(), x + 4, y + 4, Colors.WHITE, landing ? Colors.A100 | primary : Colors.A75, 4);

            return;
        }

        List<Form> forms = this.drag.getForms();
        int size = Math.min(this.getCellSize(), 48);
        int h = FormGridLayout.heightFor(size);
        int stack = Math.min(3, forms.size());

        for (int i = stack - 1; i >= 0; i--)
        {
            int ox = x + i * 4;
            int oy = y + i * 4;

            batcher.box(ox, oy, ox + size, oy + h, BBSSettings.color(BBSSettings.raisedSurface(), landing ? Colors.A100 : Colors.A50));
            batcher.outline(ox, oy, ox + size, oy + h, landing ? Colors.A100 | primary : BBSSettings.dividerColor(), 1);

            if (i == 0)
            {
                FormUtilsClient.renderPreview(forms.get(0), context, ox, oy, ox + size, oy + h);
            }
        }

        if (forms.size() > 1)
        {
            batcher.textCard(String.valueOf(forms.size()), x + size - 4, y - 4, Colors.WHITE, Colors.A100 | primary, 3);
        }

        if (landing && this.isCopyDrop())
        {
            batcher.textCard("+", x - 4, y - 4, Colors.WHITE, Colors.A100 | primary, 3);
        }
    }

    /** Whether the drop in progress would leave the originals where they are. */
    private boolean isCopyDrop()
    {
        if (Window.isCtrlPressed())
        {
            return true;
        }

        for (Form form : this.drag.getForms())
        {
            FormCategory from = this.categoryOf(form);

            if (from == null || !from.canModify(form))
            {
                return true;
            }
        }

        return false;
    }
}
