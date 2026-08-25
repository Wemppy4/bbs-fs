package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataStringifier;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormSort;
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
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.forms.FormCellRenderer;
import mchorse.bbs_mod.ui.forms.FormDrag;
import mchorse.bbs_mod.ui.forms.FormGridLayout;
import mchorse.bbs_mod.ui.forms.FormSelection;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One category in a form list: a header band that collapses it, and a grid of form cells.
 *
 * <p>The category paints and hit-tests its own cells (it's the one that knows its local
 * geometry — see {@link FormGridLayout}), and hands every decision that outlives a single
 * category — what's selected, what's being dragged, what a drop does — to the
 * {@link UIFormList list} that owns it.</p>
 */
public class UIFormCategory extends UIElement
{
    private static final int SORT_BUTTON = 20;

    public UIFormList list;
    public FormCategory category;

    /** The form the list has chosen in this category — the editor's and the morph's. */
    public Form selected;

    /**
     * The form under the cursor when the context menu opened. Menus act on it rather than on
     * {@link #selected}, so a right-click doesn't have to choose a form to work on it.
     */
    private Form contextForm;

    private final FormGridLayout layout = new FormGridLayout();
    private final FormCellRenderer.State state = new FormCellRenderer.State();

    private int last;
    private String search = "";

    /* The category's forms as shown: sorted, then narrowed by search */
    private final List<Form> view = new ArrayList<>();
    private int viewMod = -1;
    private String viewSearch;

    /* What's under the cursor, refreshed every frame */
    private int hoverIndex = -1;
    private int hoverAction = -1;
    private boolean hoverHeader;
    private boolean hoverSort;

    public UIFormCategory(FormCategory category, UIFormList list)
    {
        this.category = category;
        this.list = list;

        this.context(this::buildContextMenu);

        this.h(UIConstants.CONTROL_HEIGHT);
    }

    /**
     * The form a context menu (or a subclass adding to it) should act on: the one it was
     * opened over, or the selected one when opened over the header.
     */
    public Form getContextForm()
    {
        return this.contextForm == null ? this.selected : this.contextForm;
    }

    /**
     * Whether a menu opened over a form should act on the whole multi-selection instead —
     * the form is one of several picked.
     */
    public boolean isGroupContext()
    {
        Form form = this.getContextForm();

        return form != null && this.list.selection.isGroup() && this.list.selection.contains(form);
    }

    public FormGridLayout getLayout()
    {
        return this.layout;
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
                this.list.selection.set(form, this.category);
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

        menu.action(Icons.ADD, UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_CATEGORY, () ->
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
        });

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

        menu.action(Icons.LIST, UIKeys.FORMS_CATEGORIES_SORT, () -> this.openSortMenu(this.getContext()));
    }

    private void buildGroupContextMenu(ContextMenuManager menu, UserFormSection userForms)
    {
        String count = String.valueOf(this.list.selection.size());

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

            menu.action(Icons.REMOVE, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_SELECTED.format(count), Colors.RED, this.list::removeSelection);
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

    private void openSortMenu(UIContext context)
    {
        FormCategories formCategories = BBSModClient.getFormCategories();

        UIChoiceMenu.of(FormSort.values())
            .current(this.category.getSort())
            .icon((sort) -> sort.icon)
            .label((sort) -> sort.label)
            .open(context, (sort) -> formCategories.setSort(this.category, sort));
    }

    /* Content */

    public void search(String search)
    {
        this.search = search.toLowerCase();
    }

    /** The forms as displayed: in the category's sort order, narrowed by the search. */
    public List<Form> getForms()
    {
        if (this.viewMod != this.category.getModCount() || !this.search.equals(this.viewSearch))
        {
            this.view.clear();

            for (Form form : this.category.getSort().sorted(this.category.getForms()))
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

    private boolean isHiddenBySearch()
    {
        return !this.search.isEmpty() && this.getForms().isEmpty();
    }

    /**
     * Pixel height for the category at a given column width (used before {@link #area} is laid out).
     */
    public int computeContentHeight(int columnWidth)
    {
        if (this.isHiddenBySearch())
        {
            return 0;
        }

        return this.layout.set(columnWidth, this.list.getCellSize(), this.getForms().size()).getContentHeight(this.category.visible.get());
    }

    public void refreshLayoutForSearch(int columnWidth)
    {
        int h = this.computeContentHeight(columnWidth);

        if (this.last != h)
        {
            this.last = h;
            this.h(h);
        }
    }

    /**
     * Where a form sits, relative to the category's top: its cell when the category is
     * expanded, the header otherwise. -1 when the form isn't shown here.
     */
    public int getFormY(Form form)
    {
        int index = FormSelection.identityIndex(this.getForms(), form);

        if (index == -1)
        {
            return -1;
        }

        return this.category.visible.get() ? this.layout.getY(index) : 0;
    }

    public void toggle()
    {
        this.category.visible.set(!this.category.visible.get());
    }

    /* Input */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || this.isHiddenBySearch())
        {
            return false;
        }

        int x = context.mouseX - this.area.x;
        int y = context.mouseY - this.area.y;
        int button = context.mouseButton;

        if (this.layout.isHeader(y))
        {
            this.contextForm = null;

            if (button != 0)
            {
                return false;
            }

            if (this.isSortButton(x))
            {
                this.openSortMenu(context);
            }
            else
            {
                this.list.pressHeader(this, context);
            }

            return true;
        }

        int index = this.category.visible.get() ? this.layout.getIndex(x, y) : -1;
        Form form = index == -1 ? null : this.getForms().get(index);

        if (button == 1)
        {
            this.contextForm = form;

            return false;
        }

        if (button != 0)
        {
            return false;
        }

        if (form == null)
        {
            this.list.clickEmpty();

            return true;
        }

        CellAction[] actions = CellAction.of(this.category.canModify(null));

        if (CellActionBar.fits(this.layout.getCellWidth()) && index == this.hoverIndex)
        {
            int action = CellActionBar.getAction(this.layout.getX(index), this.layout.getY(index), this.layout.getCellWidth(), actions.length, x, y);

            if (action != -1)
            {
                this.list.runAction(this, form, actions[action]);

                return true;
            }
        }

        this.list.pressForm(this, form, this.getForms(), context);

        return true;
    }

    /**
     * A header collapses on release, not on press, so that a press can also begin dragging
     * the category. The list finishes the drag itself, after every category has had its say.
     */
    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.list.getPressedHeader() == this && !this.list.drag.isActive() && this.area.isInside(context) && this.layout.isHeader(context.mouseY - this.area.y))
        {
            this.toggle();
        }

        return false;
    }

    private boolean isSortButton(int x)
    {
        return x >= this.area.w - SORT_BUTTON - 2 && x < this.area.w - 2;
    }

    public void select(Form form, boolean notify)
    {
        if (this.list != null)
        {
            this.list.selectCategory(this, form, notify);
        }

        this.selected = form;
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        int layoutWidth = Math.max(this.list.getCellSize(), this.area.w);
        int h = this.computeContentHeight(layoutWidth);

        if (this.isHiddenBySearch())
        {
            this.syncHeight(h);

            return;
        }

        super.render(context);

        this.updateHover(context);
        this.reportDropTarget(context);

        this.renderHeader(context);

        if (this.category.visible.get())
        {
            this.renderCells(context);
        }

        if (this.list.drag.isTarget(this))
        {
            this.renderDropTarget(context);
        }

        if (this.hoverHeader || this.hoverAction != -1)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
        }

        this.syncHeight(h);
    }

    private void syncHeight(int h)
    {
        if (this.last != h)
        {
            this.last = h;
            this.h(h);

            UIElement container = this.getParentContainer();

            if (container != null)
            {
                container.resize();
            }
        }
    }

    private void updateHover(UIContext context)
    {
        boolean inside = this.area.isInside(context) && !this.list.drag.isActive() && !context.hasContextMenu();
        int x = context.mouseX - this.area.x;
        int y = context.mouseY - this.area.y;

        this.hoverHeader = inside && this.layout.isHeader(y);
        this.hoverSort = this.hoverHeader && this.isSortButton(x);
        this.hoverIndex = inside && this.category.visible.get() ? this.layout.getIndex(x, y) : -1;
        this.hoverAction = -1;

        if (this.hoverIndex != -1 && CellActionBar.fits(this.layout.getCellWidth()))
        {
            int actions = CellAction.of(this.category.canModify(null)).length;

            this.hoverAction = CellActionBar.getAction(this.layout.getX(this.hoverIndex), this.layout.getY(this.hoverIndex), this.layout.getCellWidth(), actions, x, y);

            if (this.hoverAction != -1)
            {
                int ax = CellActionBar.getX(this.layout.getX(this.hoverIndex), this.layout.getCellWidth(), actions) + this.hoverAction * CellActionBar.BUTTON + CellActionBar.BUTTON / 2;
                int ay = this.layout.getY(this.hoverIndex) + CellActionBar.HEIGHT;

                this.list.setHoveredAction(CellAction.of(this.category.canModify(null))[this.hoverAction], context.globalX(this.area.x + ax), context.globalY(this.area.y + ay));
            }
        }
    }

    /** While something is dragged over this category, tell the list where it would land. */
    private void reportDropTarget(UIContext context)
    {
        FormDrag drag = this.list.drag;

        if (!drag.isActive() || !this.area.isInside(context))
        {
            return;
        }

        int x = context.mouseX - this.area.x;
        int y = context.mouseY - this.area.y;

        if (drag.getKind() == FormDrag.Kind.FORMS)
        {
            if (!this.category.canModify(null))
            {
                return;
            }

            int count = this.getForms().size();

            drag.setTarget(this, this.category.visible.get() ? this.layout.getInsertion(x, y) : count);
        }
        else if (this.category instanceof UserFormCategory && !drag.isDragging(this.category))
        {
            drag.setTarget(this, y < this.area.h / 2 ? 0 : 1);
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
        boolean expanded = this.category.visible.get();
        boolean dragged = this.list.drag.isDragging(this.category);

        /* The band is chrome, like the bars around a panel, and stays readable over the
         * world when the palette has no background of its own */
        batcher.box(x, y, ex, ey, BBSSettings.color(BBSSettings.chromeSurface(), Colors.A50));

        if (this.hoverHeader)
        {
            batcher.box(x, y, ex, ey, CellActionBar.ink(Colors.A6));
        }

        int textColor = dragged ? Colors.GRAY : Colors.WHITE;
        int my = y + FormGridLayout.HEADER / 2;

        batcher.icon(this.category.icon, this.hoverHeader ? Colors.LIGHTEST_GRAY : Colors.WHITE, x + 12, my, 0.5F, 0.5F);
        UISection.renderArrow(context, x + 23, my, expanded);

        String title = this.category.getProcessedTitle();
        String count = String.valueOf(this.category.getForms().size());
        int textY = y + (FormGridLayout.HEADER - font.getHeight()) / 2 + 1;

        batcher.textShadow(title, x + 32, textY, textColor);
        batcher.text(count, x + 32 + font.getWidth(title) + 6, textY, Colors.GRAY);

        this.renderSortButton(context, ex - SORT_BUTTON - 2, y);
    }

    /** Shows itself when the header is hovered, and stays lit while a sort other than manual is on. */
    private void renderSortButton(UIContext context, int x, int y)
    {
        boolean sorted = this.category.getSort() != FormSort.MANUAL;

        if (!sorted && !this.hoverHeader)
        {
            return;
        }

        int color = sorted ? BBSSettings.primaryColor.get() | Colors.A100 : (this.hoverSort ? Colors.LIGHTEST_GRAY : Colors.WHITE);

        context.batcher.icon(Icons.LIST, color, x + SORT_BUTTON / 2, y + FormGridLayout.HEADER / 2, 0.5F, 0.5F);
    }

    private void renderCells(UIContext context)
    {
        List<Form> forms = this.getForms();
        CellAction[] actions = CellAction.of(this.category.canModify(null));
        int cellW = this.layout.getCellWidth();
        int cellH = this.layout.getCellHeight();

        /* Only what the scroll view shows; the categories above and below are laid
         * out but needn't be drawn */
        int scroll = (int) this.list.forms.scroll.getScroll();
        int top = this.list.forms.area.y + scroll;
        int bottom = this.list.forms.area.ey() + scroll;

        for (int i = 0; i < forms.size(); i++)
        {
            int cx = this.area.x + this.layout.getX(i);
            int cy = this.area.y + this.layout.getY(i);

            if (cy + cellH < top || cy > bottom)
            {
                continue;
            }

            Form form = forms.get(i);

            this.state.reset();
            this.state.hover = i == this.hoverIndex;
            this.state.selected = form == this.selected;
            this.state.picked = this.list.selection.contains(form);
            this.state.dragged = this.list.drag.isDragging(form);
            this.state.hoveredAction = this.state.hover ? this.hoverAction : -1;

            FormCellRenderer.render(context, form, cx, cy, cellW, cellH, this.state, actions);
        }
    }

    /** The caret between cells (or the edge line between categories) where the drop lands. */
    private void renderDropTarget(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();
        int insertion = this.list.drag.getInsertion();

        batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A12 | primary);

        if (this.list.drag.getKind() == FormDrag.Kind.CATEGORY)
        {
            int y = insertion == 0 ? this.area.y : this.area.ey() - 2;

            batcher.box(this.area.x, y, this.area.ex(), y + 2, Colors.A100 | primary);

            return;
        }

        int count = this.getForms().size();

        if (!this.category.visible.get() || count == 0)
        {
            batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.y + FormGridLayout.HEADER, Colors.A100 | primary, 1);

            return;
        }

        int cellH = this.layout.getCellHeight();
        int x;
        int y;

        if (insertion < count)
        {
            x = this.area.x + this.layout.getX(insertion) - FormGridLayout.GAP / 2 - 1;
            y = this.area.y + this.layout.getY(insertion);
        }
        else
        {
            x = this.area.x + this.layout.getX(count - 1) + this.layout.getCellWidth() + FormGridLayout.GAP / 2 - 1;
            y = this.area.y + this.layout.getY(count - 1);
        }

        batcher.box(x, y, x + 2, y + cellH, Colors.A100 | primary);
    }
}
