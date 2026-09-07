package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataStringifier;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.FormCellRenderer;
import mchorse.bbs_mod.ui.forms.FormGridLayout;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.items.ItemDrag;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItemGrid;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One category in a form list: a header band that collapses it, and a grid of form cells.
 *
 * <p>An {@link UIItemGrid#embedded() embedded} grid: the list's scroll view lays the
 * categories out down a column, and they all share the list's selection and drag, so a pick
 * runs across categories and a drag started in one can drop into another. Everything that
 * outlives a single category — what a drop does, what a quick action does, the band
 * stretched over several categories — is handed to the {@link UIFormList list}.</p>
 */
public class UIFormCategory extends UIItemGrid<Form>
{
    public UIFormList list;
    public FormCategory category;

    /** The form the list has chosen in this category — the editor's and the morph's. */
    public Form selected;

    /**
     * The form under the cursor when the context menu opened. Menus act on it rather than on
     * {@link #selected}, so a right-click doesn't have to choose a form to work on it.
     */
    private Form contextForm;

    private String search = "";

    /* The category's forms as shown: narrowed by search */
    private final List<Form> view = new ArrayList<>();
    private int viewMod = -1;
    private String viewSearch;

    /* The header under the cursor, refreshed every frame along with the cells */
    private boolean hoverHeader;

    /* The slice of the scroll view the cells must fall into to be worth painting */
    private final Area window = new Area();

    public UIFormCategory(FormCategory category, UIFormList list)
    {
        super(null, (a, b) -> a == b, list.selection, list.drag, new FormGridLayout());

        this.category = category;
        this.list = list;

        this.embedded().multi().sorting();
        this.setCellSize(list.getCellSize());
        this.context(this::buildContextMenu);
    }

    /**
     * The form a context menu (or a subclass adding to it) should act on: the one it was
     * opened over, or the selected one when opened over the header.
     */
    public Form getContextForm()
    {
        return this.contextForm == null ? this.selected : this.contextForm;
    }

    /** Offer pasting whatever form is on the clipboard; silent when it doesn't hold one. */
    protected void pasteFormAction(ContextMenuManager menu)
    {
        try
        {
            Form form = FormUtils.fromData(Window.getClipboardMap());

            menu.action(Icons.PASTE, UIKeys.FORMS_CATEGORIES_CONTEXT_PASTE_FORM, () -> this.category.addForm(form));
        }
        catch (Exception e)
        {}
    }

    /** Offer removing one form. With several picked the group menu already offers their removal. */
    protected void removeFormAction(ContextMenuManager menu, Form form)
    {
        if (this.isGroupContext())
        {
            return;
        }

        menu.icon(MenuVerb.REMOVE, () ->
        {
            this.category.removeForm(form);
            this.list.reconcile();
        }).label(UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FORM);
    }

    /**
     * Whether a menu opened over a form should act on the whole multi-selection instead —
     * the form is one of several picked.
     */
    public boolean isGroupContext()
    {
        Form form = this.getContextForm();

        return form != null && this.selection.isGroup() && this.selection.contains(form);
    }

    @Override
    public FormGridLayout getLayout()
    {
        return (FormGridLayout) this.layout;
    }

    private void buildContextMenu(ContextMenuManager menu)
    {
        FormCategories formCategories = BBSModClient.getFormCategories();
        UserFormSection userForms = formCategories.getUserForms();
        Form form = this.getContextForm();

        if (this.isGroupContext())
        {
            this.buildGroupContextMenu(menu, userForms);

            return;
        }

        menu.action(Icons.EDIT, UIKeys.GENERAL_EDIT, () ->
        {
            if (form != null)
            {
                this.selection.set(form, this.category);
                this.select(form, true);
            }

            this.list.palette.toggleEditor();
        });

        if (form instanceof ModelForm modelForm)
        {
            menu.action(Icons.FOLDER, UIKeys.FORMS_CATEGORIES_CONTEXT_OPEN_MODEL_FOLDER, () ->
            {
                UIUtils.openFolder(BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelForm.model.get() + "/"));
            });
        }

        menu.icon(MenuVerb.ADD, () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
                UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_TITLE,
                UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_DESCRIPTION,
                (str) ->
                {
                    userForms.addUserCategory(new UserFormCategory(IKey.constant(str), formCategories.preferences.visible(UUID.randomUUID().toString()), userForms));
                    list.setupForms(formCategories);
                }
            ));
        }).label(UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_CATEGORY);

        if (form != null)
        {
            menu.action(Icons.COPY, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_FORM, () -> Window.setClipboard(FormUtils.toData(form)));
            menu.action(Icons.COPY, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_TO_CATEGORY, () ->
            {
                this.getContext().replaceContextMenu((m) -> this.addCategoryTargets(m, userForms, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_TO, (to) -> to.addForm(FormUtils.copy(form))));
            });

            if (this.category.canModify(form))
            {
                menu.action(Icons.MOVE_TO, UIKeys.FORMS_CATEGORIES_CONTEXT_MOVE_TO_CATEGORY, () ->
                {
                    this.getContext().replaceContextMenu((m) -> this.addCategoryTargets(m, userForms, UIKeys.FORMS_CATEGORIES_CONTEXT_MOVE_TO, (to) ->
                    {
                        this.category.removeForm(form);
                        to.addForm(form);
                        this.list.reconcile();
                    }));
                });
            }

            menu.action(Icons.COPY, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_COMMAND, () ->
            {
                MapType data = FormUtils.toData(form);
                DataStringifier stringifier = new DataStringifier();
                String name = MinecraftClient.getInstance().player.getGameProfile().getName();

                stringifier.jsonLike();
                stringifier.indent = "";

                Window.setClipboard("/bbs morph " + name + " " + stringifier.toString(data));
            });

            Collection<PlayerListEntry> playerList = MinecraftClient.getInstance().getNetworkHandler().getPlayerList();

            if (playerList.size() > 1)
            {
                menu.action(Icons.ARROW_RIGHT, UIKeys.FORMS_CATEGORIES_CONTEXT_SHARE_FORM, () ->
                {
                    this.getContext().replaceContextMenu((newMenu) ->
                    {
                        for (PlayerListEntry entry : playerList)
                        {
                            if (entry.getProfile().getId().equals(MinecraftClient.getInstance().player.getGameProfile().getId()))
                            {
                                continue;
                            }

                            newMenu.action(Icons.ARROW_RIGHT, IKey.constant(entry.getProfile().getName()), () ->
                            {
                                ClientNetwork.sendSharedForm(form, entry.getProfile().getId());
                            });
                        }
                    });
                });
            }
        }
    }

    private void buildGroupContextMenu(ContextMenuManager menu, UserFormSection userForms)
    {
        String count = String.valueOf(this.selection.size());

        menu.action(Icons.COPY, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_SELECTED.format(count), () ->
        {
            this.getContext().replaceContextMenu((m) -> this.addCategoryTargets(m, userForms, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_TO, (to) -> this.list.copySelectionTo(to, false)));
        });

        if (this.list.canModifySelection())
        {
            menu.action(Icons.MOVE_TO, UIKeys.FORMS_CATEGORIES_CONTEXT_MOVE_SELECTED.format(count), () ->
            {
                this.getContext().replaceContextMenu((m) -> this.addCategoryTargets(m, userForms, UIKeys.FORMS_CATEGORIES_CONTEXT_MOVE_TO, (to) -> this.list.copySelectionTo(to, true)));
            });

            menu.icon(MenuVerb.REMOVE, this.list::removeSelection).label(UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_SELECTED.format(count));
        }
    }

    private void addCategoryTargets(ContextMenuManager menu, UserFormSection userForms, IKey label, Consumer<FormCategory> action)
    {
        for (UserFormCategory formCategory : userForms.categories)
        {
            if (formCategory == this.category)
            {
                continue;
            }

            menu.action(Icons.ADD, label.format(formCategory.getProcessedTitle()), () -> action.accept(formCategory));
        }
    }

    /* Content */

    public void search(String search)
    {
        this.search = search.toLowerCase();
    }

    /** The forms as displayed: in the order the user arranged them, narrowed by the search. */
    public List<Form> getForms()
    {
        if (this.viewMod != this.category.getModCount() || !this.search.equals(this.viewSearch))
        {
            this.view.clear();

            for (Form form : this.category.getForms())
            {
                if (this.search.isEmpty() || form.getFormId().toLowerCase().contains(this.search) || form.getDisplayName().toLowerCase().contains(this.search))
                {
                    this.view.add(form);
                }
            }

            this.viewMod = this.category.getModCount();
            this.viewSearch = this.search;
        }

        return this.view;
    }

    @Override
    protected List<Form> visible()
    {
        return this.getForms();
    }

    private boolean isHiddenBySearch()
    {
        return !this.search.isEmpty() && this.getForms().isEmpty();
    }

    @Override
    protected boolean isExpanded()
    {
        return this.category.visible.get();
    }

    /** A category the search leaves nothing in folds away entirely, band included. */
    @Override
    protected int contentSize()
    {
        return this.isHiddenBySearch() ? 0 : super.contentSize();
    }

    /** Shift-ranges run within one category. */
    @Override
    protected Object scope()
    {
        return this.category;
    }

    /**
     * Where a form sits, relative to the category's top: its cell when the category is
     * expanded, the header otherwise. -1 when the form isn't shown here.
     */
    public int getFormY(Form form)
    {
        int index = this.selection.indexOf(this.getForms(), form);

        if (index == -1)
        {
            return -1;
        }

        return this.isExpanded() ? this.layout.getY(index) : 0;
    }

    public void toggle()
    {
        this.category.visible.set(!this.category.visible.get());
    }

    /** The forms whose cells overlap an area given relative to this category's top-left. */
    public List<Form> getFormsIn(Area area)
    {
        List<Form> hit = new ArrayList<>();

        if (!this.isExpanded() || this.isHiddenBySearch())
        {
            return hit;
        }

        List<Form> forms = this.getForms();

        for (int index : this.layout.getIndicesIn(area))
        {
            hit.add(forms.get(index));
        }

        return hit;
    }

    public void select(Form form, boolean notify)
    {
        if (this.list != null)
        {
            this.list.selectCategory(this, form, notify);
        }

        this.selected = form;
    }

    /* Cell hooks */

    @Override
    protected String caption(Form form)
    {
        return form.getDisplayName();
    }

    /** Zoomed out past the name strip (or with a name longer than it), the cell says its name by the cursor. */
    @Override
    protected boolean showsCaption(UIContext context, Form form, int cellWidth)
    {
        return FormCellRenderer.showsWholeName(context, form, cellWidth);
    }

    @Override
    protected boolean onOpen(Form form)
    {
        /* Ctrl is picking, not choosing */
        if (Window.isCtrlPressed())
        {
            return false;
        }

        this.list.palette.confirm();

        return true;
    }

    @Override
    protected boolean onDelete(List<Form> forms)
    {
        if (!this.list.canModifySelection())
        {
            return false;
        }

        this.list.removeSelection();

        return true;
    }

    /* Input */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || this.isHiddenBySearch())
        {
            return false;
        }

        int x = this.contentX(context);
        int y = this.contentY(context);
        int button = context.mouseButton;

        if (this.layout.isHeader(y))
        {
            this.contextForm = null;

            if (button != 0)
            {
                return false;
            }

            this.list.pressHeader(this, context);

            return true;
        }

        if (button == 1)
        {
            int index = this.indexAt(x, y);

            this.contextForm = index == -1 ? null : this.visible().get(index);

            return false;
        }

        return super.subMouseClicked(context);
    }

    /** Shift + drag stretches a band across the categories; a Shift-click that goes nowhere extends the pick. */
    @Override
    protected boolean pressItem(int index, UIContext context)
    {
        if (Window.isShiftPressed())
        {
            this.list.pressMarquee(this, this.visible().get(index), context);

            return true;
        }

        return super.pressItem(index, context);
    }

    @Override
    protected boolean pressEmpty(UIContext context)
    {
        if (Window.isShiftPressed())
        {
            this.list.pressMarquee(this, null, context);

            return true;
        }

        /* A press over nothing drops the pick; the band is the list's, not this grid's */
        this.selection.clear();
        this.fireCallback();

        return true;
    }

    @Override
    protected void applySelectionOnClick(Form form, int index)
    {
        super.applySelectionOnClick(form, index);

        /* Ctrl only picks; a plain click also chooses the form for the editor and the morph */
        if (!Window.isCtrlPressed())
        {
            this.select(form, true);
        }
    }

    /** Every category's forms can be carried — a read-only one's get copied by the drop. */
    @Override
    protected List<Form> dragPayload(Form form)
    {
        return this.selection.contains(form) ? new ArrayList<>(this.selection.getItems()) : Collections.singletonList(form);
    }

    /**
     * A header collapses on release, not on press, so that a press can also begin dragging
     * the category. The list finishes the drag itself, after every category has had its say.
     */
    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.list.getPressedHeader() == this && !this.list.categoryDrag.isActive() && this.area.isInside(context) && this.layout.isHeader(this.contentY(context)))
        {
            this.toggle();
        }

        return super.subMouseReleased(context);
    }

    /** The keyboard walks the cells; it's the list's scroll view that has to follow. */
    @Override
    protected void scrollIntoView(int index)
    {
        if (index < 0 || index >= this.layout.getCount())
        {
            return;
        }

        UIScrollView forms = this.list.forms;
        int y = this.area.y - forms.area.y + this.layout.getY(index);

        forms.scroll.scrollIntoView(y, this.layout.getCellHeight() + this.layout.getGap(), this.layout.getGap());
    }

    /* Drop */

    /** The drag is the list's: only the category under the cursor speaks, and clears what it said on leaving. */
    @Override
    protected void updateDropTarget(boolean inside, int x, int y)
    {
        if (inside)
        {
            this.drag.clearTarget();
            this.reportDropTarget(x, y);
        }
        else if (this.drag.getTarget() == this)
        {
            this.drag.clearTarget();
        }
    }

    /** A category that can't take forms in offers no slot. */
    @Override
    protected void reportDropTarget(int x, int y)
    {
        if (this.category.canModify(null))
        {
            super.reportDropTarget(x, y);
        }
    }

    @Override
    protected void reorder(List<Form> forms, int insertion)
    {
        this.list.dropForms(this, insertion, forms);
    }

    @Override
    protected void onDrop(Object target, List<Form> forms)
    {
        if (target instanceof UIFormCategory category)
        {
            this.list.dropForms(category, this.drag.getInsertion(), forms);
        }
    }

    /** While a user category is dragged over this one, say whether it would land above or below. */
    private void reportCategoryDropTarget(UIContext context)
    {
        ItemDrag<UserFormCategory> drag = this.list.categoryDrag;

        if (!drag.isActive() || !this.area.isInside(context) || !(this.category instanceof UserFormCategory user) || drag.isDragging(user))
        {
            return;
        }

        drag.setTarget(this, this.contentY(context) < this.area.h / 2 ? 0 : 1);
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        if (this.isHiddenBySearch())
        {
            /* Nothing to show, but the height must still follow (down to nothing) */
            this.relayout();

            return;
        }

        super.render(context);

        if (this.hoverHeader)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
        }
    }

    @Override
    protected void updateHover(UIContext context)
    {
        if (this.list.categoryDrag.isActive())
        {
            this.hoverIndex = -1;
            this.hoverHeader = false;

            return;
        }

        super.updateHover(context);

        boolean inside = this.area.isInside(context) && !this.drag.isActive() && !context.hasContextMenu();

        this.hoverHeader = inside && this.layout.isHeader(this.contentY(context));
    }

    /** Only what the scroll view shows; the categories above and below are laid out but needn't be drawn. */
    @Override
    protected Area visibleWindow()
    {
        UIScrollView forms = this.list.forms;

        this.window.set(forms.area.x, forms.area.y + (int) forms.scroll.getScroll(), forms.area.w, forms.area.h);

        return this.window;
    }

    @Override
    protected void renderContent(UIContext context)
    {
        this.updateHover(context);
        this.reportCategoryDropTarget(context);

        this.renderHeader(context);

        if (this.isExpanded())
        {
            this.renderCells(context);
        }

        if (this.list.categoryDrag.isTarget(this))
        {
            this.renderCategoryDropTarget(context);
        }
    }

    private void renderHeader(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int x = this.area.x;
        int y = this.area.y;
        int ex = this.area.ex();
        int ey = y + FormGridLayout.HEADER;
        boolean expanded = this.isExpanded();
        boolean dragged = this.category instanceof UserFormCategory user && this.list.categoryDrag.isDragging(user);

        /* The band is chrome, like the bars around a panel, and stays readable over the
         * world when the palette has no background of its own */
        batcher.box(x, y, ex, ey, BBSSettings.color(BBSSettings.chromeSurface(), Colors.A50));

        /* A category holding the chosen form wears the pick itself: folded or scrolled away, the
         * form cannot say where it lives, and its category is the only thing left that can. */
        RowStyle.row(batcher, x, y, this.area.w, FormGridLayout.HEADER, 0, false, this.hoverHeader, this.selected != null);

        boolean lit = this.hoverHeader || this.selected != null;
        int textColor = dragged ? RowStyle.textColor(lit, Colors.GRAY) : RowStyle.textColor(lit);
        int my = y + FormGridLayout.HEADER / 2;

        int ix = x + UIList.ROW_PADDING;
        int textX = x + UIList.iconRowTextX(UIList.ROW_PADDING);

        UISection.renderArrow(context, ix + UIList.ARROW_SLOT / 2F, my, expanded, RowStyle.iconColor(lit));
        batcher.icon(this.category.icon, RowStyle.iconColor(lit), ix + UIList.ARROW_SLOT + UIList.ICON_SLOT / 2F, my, 0.5F, 0.5F);

        String title = this.category.getProcessedTitle();
        String count = String.valueOf(this.category.getForms().size());
        int textY = y + (FormGridLayout.HEADER - font.getHeight()) / 2 + 1;

        batcher.textShadow(title, textX, textY, textColor);
        batcher.text(count, textX + font.getWidth(title) + 6, textY, Colors.GRAY);
    }

    @Override
    protected void renderCell(UIContext context, Form form, int x, int y, int w, int h, CellState state)
    {
        /* The chosen form keeps its frame inside a group too — it's the one the editor edits */
        state.selected = form == this.selected;

        FormCellRenderer.render(context, form, x, y, w, h, state);
    }

    /** The whole category lights up under a drop, with the caret between cells on top. */
    @Override
    protected void renderInsertion(UIContext context, int insertion)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A12 | BBSSettings.primaryColor.get());

        super.renderInsertion(context, insertion);
    }

    /** The edge line between categories where a dragged category lands. */
    private void renderCategoryDropTarget(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();
        int y = this.list.categoryDrag.getInsertion() == 0 ? this.area.y : this.area.ey() - 2;

        batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A12 | primary);
        batcher.box(this.area.x, y, this.area.ex(), y + 2, Colors.A100 | primary);
    }
}
