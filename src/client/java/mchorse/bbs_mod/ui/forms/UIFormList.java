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
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Marquee;
import mchorse.bbs_mod.ui.utils.ScrollZoomAnchor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
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
    public static final int BAR_HEIGHT = 20;
    public static final int STATUS_HEIGHT = 16;
    private static final long DOUBLE_CLICK = 300;
    private static final int AUTO_SCROLL_EDGE = 24;
    private static final int AUTO_SCROLL_SPEED = 6;

    public IUIFormList palette;

    public UIScrollView forms;

    public UIElement bar;
    public UITextbox search;
    public UIIcon edit;
    public UIIcon close;
    public UIIcon categoryFilter;
    public UIIcon collapseAll;
    public UIIcon expandAll;

    public final FormSelection selection = new FormSelection();
    public final FormDrag drag = new FormDrag();

    /** Shift + drag band, in the scroll view's content coordinates. */
    public final Marquee marquee = new Marquee();

    /* Where a Shift-press landed: a band that goes nowhere extends the pick to that form */
    private UIFormCategory marqueeCategory;
    private Form marqueeForm;

    private UIFormCategory recent;
    private List<UIFormCategory> categories = new ArrayList<>();

    private long lastUpdate;
    private int lastScroll;
    private boolean pendingScrollToSelected;

    /** A header pressed but not yet released: a release without a drag collapses it. */
    private UIFormCategory pressedHeader;

    /* The last plain click, to tell a double-click */
    private Form lastClicked;
    private long lastClickTime;

    /* The quick action under the cursor this frame, and where its label goes */
    private CellAction hoveredAction;
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

        /* The bar sits along the top, as dark as the category headers, with a status line
         * about the chosen form under it; the list scrolls under both */
        this.bar.relative(this).xy(0, 0).w(1F).h(BAR_HEIGHT).row(0).height(BAR_HEIGHT);
        this.forms.relative(this).xy(0, BAR_HEIGHT + STATUS_HEIGHT).w(1F).h(1F, -BAR_HEIGHT - STATUS_HEIGHT);
        this.close.w(20);

        this.categoryFilter = new UIIcon(Icons.FILTER, this::openMorphCategoryFilter);
        this.categoryFilter.tooltip(UIKeys.MORPHING_FILTER_CATEGORIES, Direction.TOP);
        this.categoryFilter.w(20);
        this.collapseAll = new UIIcon(Icons.COLLAPSE_ALL, (b) -> this.setAllExpanded(false));
        this.collapseAll.tooltip(UIKeys.FORMS_LIST_COLLAPSE_ALL, Direction.TOP);
        this.collapseAll.w(20);
        this.expandAll = new UIIcon(Icons.EXPAND_ALL, (b) -> this.setAllExpanded(true));
        this.expandAll.tooltip(UIKeys.FORMS_LIST_EXPAND_ALL, Direction.TOP);
        this.expandAll.w(20);
        this.addToBar(this.categoryFilter, this.collapseAll, this.expandAll, this.search, this.edit, this.close);

        this.add(this.forms, this.bar);

        this.search.keys().register(Keys.FORMS_FOCUS, this::focusSearchInput);

        this.markContainer();
        this.setupForms(BBSModClient.getFormCategories());
    }

    /**
     * Put controls on the top bar. The row lays out its children but leaves the height of
     * those that size themselves (icons, the search box) alone, so it is set here — the bar
     * is one strip and everything on it fills it.
     */
    public void addToBar(UIElement... elements)
    {
        for (UIElement element : elements)
        {
            element.h(BAR_HEIGHT);
        }

        this.bar.add(elements);
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
            this.categories.get(this.categories.size() - 1).marginBottom(20);
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

        ScrollZoomAnchor.keep(this.forms.scroll, context.mouseY - this.forms.area.y, this::rowAt, this::rowPlacement, () ->
        {
            BBSSettings.formCellSize.set(size);
            this.afterSearchLayout();
        });
    }

    /** A row of cells, or a category's header (row -1) — what the zoom keeps under the cursor. */
    private record Row(UIFormCategory category, int row)
    {}

    private Row rowAt(int contentY)
    {
        for (UIFormCategory category : this.categories)
        {
            int top = category.area.y - this.forms.area.y;

            if (contentY < top || contentY >= top + category.area.h)
            {
                continue;
            }

            FormGridLayout layout = category.getLayout();
            int y = contentY - top;

            if (y >= layout.getRowY(0) && layout.getRows() > 0)
            {
                return new Row(category, Math.min(layout.getRows() - 1, (y - layout.getRowY(0)) / (layout.getCellHeight() + FormGridLayout.GAP)));
            }

            return new Row(category, -1);
        }

        return null;
    }

    private ScrollZoomAnchor.Placement rowPlacement(Row row)
    {
        FormGridLayout layout = row.category().getLayout();
        int top = row.category().area.y - this.forms.area.y;

        if (row.row() < 0)
        {
            return new ScrollZoomAnchor.Placement(top, FormGridLayout.HEADER);
        }

        return new ScrollZoomAnchor.Placement(top + layout.getRowY(row.row()), layout.getCellHeight());
    }

    /* Collapsing */

    private void setAllExpanded(boolean expanded)
    {
        for (UIFormCategory category : this.categories)
        {
            category.category.visible.set(expanded);
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

    /** Shift went down in a category: arm a band from the cursor, in the scroll view's content space. */
    public void pressMarquee(UIFormCategory category, Form form, UIContext context)
    {
        this.marqueeCategory = category;
        this.marqueeForm = form;
        this.marquee.press(context.mouseX - this.forms.area.x, context.mouseY - this.forms.area.y);
    }

    private void releaseMarquee()
    {
        if (this.marquee.isActive())
        {
            Area band = this.marquee.getArea();
            Area local = new Area();

            for (UIFormCategory category : this.categories)
            {
                local.copy(band);
                local.x -= category.area.x - this.forms.area.x;
                local.y -= category.area.y - this.forms.area.y;

                for (Form form : category.getFormsIn(local))
                {
                    this.selection.add(form, category.category);
                }
            }
        }
        else if (this.marqueeForm != null && this.marqueeCategory != null)
        {
            this.selection.range(this.marqueeForm, this.marqueeCategory.category, this.marqueeCategory.getForms());
        }

        this.marquee.reset();
        this.marqueeCategory = null;
        this.marqueeForm = null;
    }

    public void pressForm(UIFormCategory category, Form form, List<Form> order, UIContext context)
    {
        if (Window.isCtrlPressed())
        {
            this.selection.toggle(form, category.category);
        }
        else
        {
            long now = System.currentTimeMillis();
            boolean twice = this.lastClicked == form && now - this.lastClickTime < DOUBLE_CLICK;

            this.lastClicked = form;
            this.lastClickTime = now;

            /* A plain click on one of several picked forms keeps the group, so it can be
             * dragged as a whole; anywhere else it starts a new one */
            if (!this.selection.contains(form) || !this.selection.isGroup())
            {
                this.selection.set(form, category.category);
            }

            category.select(form, true);

            if (twice)
            {
                this.lastClicked = null;
                this.palette.confirm();

                return;
            }
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

    public void runAction(UIFormCategory category, Form form, CellAction action)
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

    public void setHoveredAction(CellAction action, int x, int y)
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
        if (this.marquee.isPressed())
        {
            this.releaseMarquee();
        }

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
        if ((this.drag.isPressed() || this.pressedHeader != null || this.marquee.isPressed()) && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            this.release();
        }

        this.marquee.update(context.mouseX - this.forms.area.x, context.mouseY - this.forms.area.y + (int) this.forms.scroll.getScroll());
        this.drag.update(context.mouseX, context.mouseY);
        this.drag.clearTarget();
        this.hoveredAction = null;
        this.autoScroll(context);

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + BAR_HEIGHT, BBSSettings.color(BBSSettings.chromeSurface(), Colors.A50));
        this.renderStatus(context);

        DiffuseLighting.enableGuiDepthLighting();

        super.render(context);

        DiffuseLighting.disableGuiDepthLighting();

        if (this.pendingScrollToSelected && this.forms.area.w > 0)
        {
            this.scrollToSelectedForm();

            this.pendingScrollToSelected = false;
        }

        if (this.marquee.isActive())
        {
            context.batcher.clip(this.forms.area, context);
            this.marquee.render(context, this.forms.area.x, this.forms.area.y - (int) this.forms.scroll.getScroll());
            context.batcher.unclip(context);
        }

        if (this.hoveredAction != null && !this.drag.isActive())
        {
            CellActionBar.renderLabel(context, this.hoveredAction, this.hoveredActionX, this.hoveredActionY);
        }

        if (this.drag.isActive())
        {
            this.renderGhost(context);
        }
    }

    /**
     * The line under the bar: the chosen form — its type icon, name, id and hotkey — or,
     * with several picked, how many.
     */
    private void renderStatus(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int y = this.area.y + BAR_HEIGHT;
        int textY = y + (STATUS_HEIGHT - font.getHeight()) / 2 + 1;
        int x = this.area.x + 4;

        batcher.box(this.area.x, y, this.area.ex(), y + STATUS_HEIGHT, BBSSettings.color(BBSSettings.chromeSurface(), Colors.A50));

        Form selected = this.getSelected();

        if (this.selection.isGroup())
        {
            batcher.textShadow(UIKeys.FORMS_LIST_STATUS_SELECTED.format(String.valueOf(this.selection.size())).get(), x, textY, Colors.LIGHTEST_GRAY);

            return;
        }

        if (selected == null)
        {
            return;
        }

        batcher.icon(selected.getIcon(), x, y + STATUS_HEIGHT / 2, 0F, 0.5F);
        x += 20;

        String name = selected.getDisplayName();

        batcher.textShadow(name, x, textY);
        x += font.getWidth(name) + 8;

        String id = selected.getFormId();

        batcher.text(id, x, textY, Colors.GRAY);
        x += font.getWidth(id) + 8;

        if (selected.hotkey.get() > 0)
        {
            batcher.textCard(KeyCodes.getName(selected.hotkey.get()), x + 2, textY, Colors.WHITE, Colors.A50, 2);
        }
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
        int h = FormGridLayout.cellHeightFor(size);
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
