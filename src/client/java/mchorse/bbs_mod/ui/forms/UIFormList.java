package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIRecentFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.morphing.UIMorphFormCategoryFilterOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.DiffuseLighting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UIFormList extends UIElement
{
    public IUIFormList palette;

    public UIScrollView forms;

    public UIElement bar;
    public UITextbox search;
    public UIIcon edit;
    public UIIcon close;
    public UIIcon categoryFilter;

    private final boolean morphCategoryFilter;
    private UIFormCategory recent;
    private List<UIFormCategory> categories = new ArrayList<>();

    private long lastUpdate;
    private int lastScroll;
    private boolean pendingScrollToSelected;

    public UIFormList(IUIFormList palette)
    {
        this(palette, false);
    }

    public UIFormList(IUIFormList palette, boolean morphCategoryFilter)
    {
        this.palette = palette;
        this.morphCategoryFilter = morphCategoryFilter;

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

        if (morphCategoryFilter)
        {
            this.categoryFilter = new UIIcon(Icons.FILTER, this::openMorphCategoryFilter);
            this.categoryFilter.tooltip(UIKeys.MORPHING_FILTER_CATEGORIES, Direction.TOP);
            this.categoryFilter.w(20);
            this.bar.add(this.categoryFilter, this.search, this.edit, this.close);
        }
        else
        {
            this.categoryFilter = null;
            this.bar.add(this.search, this.edit, this.close);
        }

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

    public void setupForms(FormCategories forms)
    {
        this.categories.clear();
        this.forms.removeAll();

        for (FormCategory category : forms.getAllCategories())
        {
            if (this.morphCategoryFilter && BBSSettings.disabledMorphFormCategories.get().contains(category.visible.getId()))
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
        int columnW = Math.max(UIFormCategory.CELL_WIDTH, this.forms.area.w);

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
        int itemHeight = UIFormCategory.HEADER_HEIGHT;
        int index = category.getForms().indexOf(category.selected);

        if (category.category.visible.get() && index >= 0)
        {
            int columnW = Math.max(UIFormCategory.CELL_WIDTH, this.forms.area.w);
            int perRow = Math.max(1, columnW / UIFormCategory.CELL_WIDTH);

            contentY += UIFormCategory.HEADER_HEIGHT + (index / perRow) * UIFormCategory.CELL_HEIGHT;
            itemHeight = UIFormCategory.CELL_HEIGHT;
        }

        /* Center the selected form (or the category header when it's collapsed)
         * within the visible area; scrollTo() clamps to the scroll bounds. */
        this.forms.scroll.setScroll(contentY - (this.forms.area.h - itemHeight) / 2);
    }

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

        DiffuseLighting.enableGuiDepthLighting();

        super.render(context);

        DiffuseLighting.disableGuiDepthLighting();

        if (this.pendingScrollToSelected && this.forms.area.w > 0)
        {
            this.scrollToSelectedForm();

            this.pendingScrollToSelected = false;
        }

        /* Render form's display name and ID */
        Form selected = this.getSelected();

        if (selected != null)
        {
            String displayName = selected.getDisplayName();
            String id = selected.getFormId();
            FontRenderer font = context.batcher.getFont();

            int w = Math.max(font.getWidth(displayName), font.getWidth(id));
            int x = this.search.area.x;
            int y = this.search.area.y - 24;

            context.batcher.box(x, y, x + w + 8, this.search.area.y, Colors.A50);
            context.batcher.textShadow(displayName, x + 4, y + 4);
            context.batcher.textShadow(id, x + 4, y + 14, Colors.LIGHTEST_GRAY);
        }
    }
}