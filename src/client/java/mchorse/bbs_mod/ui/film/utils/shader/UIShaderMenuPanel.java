package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.iris.ShaderMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A shader pack's options menu in a panel, laid out the way Iris lays it out: one screen at a
 * time in the pack's columns, with the way back up as breadcrumbs. The curve picker and the pack's
 * settings are both this panel; they differ in what an option's cell does (adds a curve, or
 * carries a control) and in which options count for them.
 *
 * <ul>
 *     <li>A search field on top looks through all of it at once, sub-screens included; a hit
 *     says where it lives.</li>
 *     <li>A link with nothing that counts behind it is dimmed, and the options that count but
 *     the menu leaves out get a page of their own at the end of the main screen.</li>
 * </ul>
 *
 * <p>The panel opens in one size whichever screen is shown, the one of the tallest screen, so it
 * doesn't jump while browsing.</p>
 */
public abstract class UIShaderMenuPanel extends UIOverlayPanel
{
    protected static final int PADDING = 6;
    protected static final int MARGIN = 4;

    /** Height of the search field, and of a bar under the menu */
    protected static final int BAR_HEIGHT = 20;

    private static final int COLUMN_WIDTH = 210;
    private static final int ICONS_WIDTH = 20;
    private static final int TITLE_HEIGHT = 20;
    private static final int MAX_WIDTH = 680;
    private static final int MAX_MENU_HEIGHT = 300;

    /** The panel's own page with the options that count but the pack's menu leaves out */
    protected static final String NOT_IN_MENU = "#not_in_menu";

    protected ShaderMenuBrowser browser = new ShaderMenuBrowser(null, Collections.emptyMap());

    /** Columns of the main screen, and of the panel's own pages */
    protected int columns = 2;

    /** Options that count but no screen of the menu shows, in the order they're shown */
    private List<ShaderMenu.Cell> notInMenu = Collections.emptyList();

    protected final UITextbox search;
    protected final UIShaderMenuCrumbs crumbs;
    protected final UIScrollView view;
    private final GridResizer viewGrid;

    /** Opens the panel in the size its menu asks for, as far as the screen has room. */
    protected static void open(UIContext context, UIShaderMenuPanel panel)
    {
        int width = Math.min(panel.preferredWidth(), (int) (context.menu.width * 0.95F));
        int height = Math.min(panel.preferredHeight(), (int) (context.menu.height * 0.9F));

        UIOverlay.addOverlay(context, panel, width, height);
    }

    protected UIShaderMenuPanel(IKey title)
    {
        super(title);

        this.search = new UITextbox(100, this::filter);
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).xy(PADDING, PADDING).w(1F, -PADDING * 2).h(BAR_HEIGHT);

        this.crumbs = new UIShaderMenuCrumbs();
        this.crumbs.callback = this::goTo;
        this.crumbs.relative(this.content).w(1F).h(UIShaderMenuCrumbs.HEIGHT);

        this.view = new UIScrollView();
        this.view.relative(this.content).w(1F);
        this.view.scroll.scrollSpeed = 20;
        this.viewGrid = this.view.grid(MARGIN);
        this.viewGrid.padding(PADDING).height(UIShaderMenuCell.HEIGHT);

        this.content.add(this.search, this.crumbs, this.view);
    }

    /* === What the owner decides === */

    /** Whether an option counts here: searched, keeping its links lit, listed when the menu leaves it out */
    protected abstract boolean counts(ShaderMenu.Cell option);

    /**
     * The cell of an option.
     *
     * @param where for a search hit, the screen the option lives on; null otherwise
     */
    protected abstract UIShaderMenuCell optionCell(ShaderMenu.Cell option, String where);

    /** The cell of the pack's profile, where the menu puts it */
    protected abstract UIShaderMenuCell profileCell(ShaderMenu.Cell cell);

    /** Search hits of the owner's own, put before the menu's; returns how many */
    protected int addResults(String query)
    {
        return 0;
    }

    /** What the panel says while there's no menu to show */
    protected IKey noMenuText()
    {
        return UIKeys.CAMERA_PANELS_CURVES_NO_SHADERS;
    }

    /** Height of what goes between the search field and the menu; just a gap by default */
    protected int topHeight()
    {
        return MARGIN;
    }

    /** Puts what goes between the search field and the menu at the given height. */
    protected void placeTop(int y)
    {}

    /** Lays out what goes under the menu; returns the height it takes, padding included. */
    protected int layoutBottom()
    {
        return 0;
    }

    /* === Sizing === */

    protected int preferredWidth()
    {
        int content = this.columns * COLUMN_WIDTH + (this.columns - 1) * MARGIN + PADDING * 2;

        return Math.min(MAX_WIDTH, content + ICONS_WIDTH);
    }

    /** Fits the tallest screen of the menu, so browsing doesn't make the panel jump. */
    protected int preferredHeight()
    {
        ShaderMenu menu = this.browser.menu;
        int height = TITLE_HEIGHT + PADDING + BAR_HEIGHT + this.topHeight() + UIShaderMenuCrumbs.HEIGHT;
        int menuHeight = PADDING;

        if (menu != null)
        {
            ShaderMenu.Screen main = menu.mainScreen;

            menuHeight = gridHeight(this.notInMenu.size(), this.columns);
            menuHeight = Math.max(menuHeight, gridHeight(main.cells.size() + (this.notInMenu.isEmpty() ? 0 : 1), main.columnCount));

            for (ShaderMenu.Screen screen : menu.subScreens.values())
            {
                menuHeight = Math.max(menuHeight, gridHeight(screen.cells.size(), screen.columnCount));
            }

            menuHeight = Math.min(MAX_MENU_HEIGHT, menuHeight);
        }

        return height + menuHeight;
    }

    protected static int gridHeight(int count, int columns)
    {
        columns = Math.max(1, columns);

        int rows = Math.max(1, (count + columns - 1) / columns);

        return rows * UIShaderMenuCell.HEIGHT + (rows - 1) * MARGIN + PADDING * 2;
    }

    /* === State === */

    /**
     * Takes the pack's menu, null when there's none. The screen on show and the search stay, as
     * far as the menu still has them.
     */
    protected void setMenu(ShaderMenu menu, Map<String, String> lang)
    {
        ShaderMenuBrowser browser = new ShaderMenuBrowser(menu, lang);

        browser.addPage(NOT_IN_MENU, UIKeys.CAMERA_PANELS_CURVES_NOT_IN_MENU.get());
        browser.follow(this.browser);

        this.browser = browser;
        this.columns = menu == null ? 2 : Math.max(2, menu.mainScreen.columnCount);

        List<ShaderMenu.Cell> notInMenu = new ArrayList<>();

        if (menu != null)
        {
            for (ShaderMenu.Cell cell : menu.notInMenu)
            {
                if (this.counts(cell))
                {
                    notInMenu.add(cell);
                }
            }

            notInMenu.sort((a, b) -> browser.optionLabel(a.optionId).compareToIgnoreCase(browser.optionLabel(b.optionId)));
        }

        this.notInMenu = notInMenu;
    }

    private void filter(String text)
    {
        this.browser.setQuery(text);
        this.refresh();
    }

    private void openScreen(String screenId)
    {
        if (this.browser.open(screenId))
        {
            this.refresh();
        }
    }

    /** A breadcrumb was clicked: back up to that screen. */
    private void goTo(int depth)
    {
        this.browser.goTo(depth);
        this.refresh();
    }

    /* === Building the parts === */

    /** Fills the menu with the screen on show, or with what the search found, from the top. */
    protected void refresh()
    {
        this.view.removeAll();

        if (this.browser.isSearching())
        {
            this.fillResults();
        }
        else
        {
            this.fillScreen();
        }

        this.view.scroll.scrollTo(0);
        this.layout();
    }

    /** Puts the parts one under another, the menu taking the height that's left. */
    protected void layout()
    {
        int y = PADDING + BAR_HEIGHT;

        this.placeTop(y);
        y += this.topHeight();

        this.crumbs.y(y);
        y += UIShaderMenuCrumbs.HEIGHT;

        this.view.y(y).h(1F, -y - this.layoutBottom());

        if (this.hasParent())
        {
            this.resize();
        }
    }

    private void fillScreen()
    {
        ShaderMenu menu = this.browser.menu;

        if (menu == null)
        {
            this.crumbs.crumbs(Collections.singletonList(this.noMenuText().get()), true);

            return;
        }

        List<String> crumbs = new ArrayList<>();

        for (String screenId : this.browser.getPath())
        {
            crumbs.add(this.browser.screenTitle(screenId));
        }

        this.crumbs.crumbs(crumbs, false);

        String screenId = this.browser.getScreen();

        if (NOT_IN_MENU.equals(screenId))
        {
            this.viewGrid.items(this.columns);

            for (ShaderMenu.Cell cell : this.notInMenu)
            {
                this.view.add(this.optionCell(cell, null));
            }

            return;
        }

        ShaderMenu.Screen screen = menu.getScreen(screenId);

        if (screen == null)
        {
            return;
        }

        this.viewGrid.items(Math.max(1, screen.columnCount));

        for (ShaderMenu.Cell cell : screen.cells)
        {
            this.view.add(this.menuCell(cell));
        }

        if (screen == menu.mainScreen && !this.notInMenu.isEmpty())
        {
            this.view.add(this.linkCell(NOT_IN_MENU, this.notInMenu.size()));
        }
    }

    private void fillResults()
    {
        String query = this.browser.getQuery();
        String notInMenuTitle = this.browser.screenTitle(NOT_IN_MENU);

        this.viewGrid.items(1);

        int found = this.addResults(query);

        for (ShaderMenuBrowser.Hit hit : this.browser.index(this::counts))
        {
            if (hit.matches(query))
            {
                this.view.add(this.optionCell(hit.cell, hit.where));
                found += 1;
            }
        }

        for (ShaderMenu.Cell cell : this.notInMenu)
        {
            if (ShaderMenuBrowser.matches(this.browser.optionLabel(cell.optionId), cell.optionId, query))
            {
                this.view.add(this.optionCell(cell, notInMenuTitle));
                found += 1;
            }
        }

        IKey title = found == 0 ? UIKeys.CAMERA_PANELS_CURVES_NOT_FOUND : UIKeys.CAMERA_PANELS_CURVES_FOUND.format(found);

        this.crumbs.crumbs(Collections.singletonList(title.get()), true);
    }

    private UIShaderMenuCell menuCell(ShaderMenu.Cell cell)
    {
        if (cell.type == ShaderMenu.CellType.OPTION && cell.optionId != null)
        {
            return this.optionCell(cell, null);
        }
        else if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
        {
            return this.linkCell(cell.targetScreenId, this.browser.countOptions(cell.targetScreenId, this::counts));
        }
        else if (cell.type == ShaderMenu.CellType.PROFILE)
        {
            return this.profileCell(cell);
        }

        return UIShaderMenuCell.empty();
    }

    private UIShaderMenuCell linkCell(String screenId, int count)
    {
        return UIShaderMenuCell.link(this.browser.screenTitle(screenId), (cell) -> this.openScreen(screenId)).note("›").muted(count == 0);
    }
}
