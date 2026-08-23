package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelActionBar;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelTopBar;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.IUITabsHost;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UITabList;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * A dashboard panel that edits something: a strip of tabs and actions along the top, and the
 * editor filling everything underneath it.
 *
 * <p>Where the actions sit and how they look is decided once, here and in {@link UIPanelTopBar},
 * so every editor panel reads the same way. Subclasses only contribute buttons through
 * {@link #actions()} and place their content with {@link #layoutUnderTopBar(UIElement)}.</p>
 *
 * <p>Tabs come with the panel rather than being switched on per panel: whatever an editor opens,
 * it can have several of them open at once. A subclass only says what an id means — see
 * {@link IUITabsHost}.</p>
 */
public abstract class UIEditorDashboardPanel extends UIDashboardPanel implements IUITabsHost
{
    public final UIPanelTopBar topBar;
    public final UIElement editor;
    public final UITabList tabs;

    protected boolean update;

    public UIEditorDashboardPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.tabs = new UITabList(this);

        this.topBar = new UIPanelTopBar();
        this.topBar.relative(this).w(1F).h(UIPanelTopBar.HEIGHT);
        this.tabs.setBar(this.topBar.enableTabs(this.tabs));

        this.editor = new UIElement();

        this.layoutUnderTopBar(this.editor);
        this.add(this.topBar, this.editor);

        this.keys().register(Keys.OPEN_NEW_TAB, this.tabs::addTab);
    }

    /** The buttons of this panel's top bar. */
    public UIPanelActionBar actions()
    {
        return this.topBar.actions;
    }

    /**
     * Stretch an element across everything below the top bar — the editor itself, and the
     * selection screens that cover it.
     */
    protected <T extends UIElement> T layoutUnderTopBar(T element)
    {
        element.relative(this).y(UIPanelTopBar.HEIGHT).w(1F).h(1F, -UIPanelTopBar.HEIGHT);

        return element;
    }

    /* IUITabsHost — what a tab holds is the subclass's business */

    @Override
    public IKey getNewTabLabel()
    {
        return UIKeys.PANELS_TABS_NEW_TAB;
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.FOLDER;
    }

    @Override
    public void open()
    {
        super.open();

        this.update = true;
    }

    @Override
    public void appear()
    {
        super.appear();

        if (this.update)
        {
            this.update = false;

            this.requestNames();
        }
    }

    public abstract void requestNames();
}
