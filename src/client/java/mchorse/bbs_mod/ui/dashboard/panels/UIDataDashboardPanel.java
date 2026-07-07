package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.DataTab;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.IUITabs;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UIDataTabs;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.ui.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;

public abstract class UIDataDashboardPanel <T extends ValueGroup> extends UICRUDDashboardPanel implements IUITabs
{
    public UIIcon saveIcon;

    public final List<DataTab> tabs = new ArrayList<>();
    public int currentTab = -1;
    public UIDataTabs tabBar;

    protected T data;

    private boolean openedBefore;
    private boolean tabsEnabled;

    private Timer savingTimer = new Timer(0);

    public UIDataDashboardPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.saveIcon = new UIIcon(Icons.SAVED, (b) -> this.save());

        this.iconBar.add(this.saveIcon);

        /* A separate element is needed to make save keybind a more priority than other keybinds, because
         * the keybinds are processed afterwards. */
        UIElement savePlease = new UIElement().noCulling();

        savePlease.keys().register(Keys.SAVE, () -> 
        {
            UIUtils.playClick();
            this.save();
        }).active(() -> this.data != null);
        savePlease.keys().register(Keys.OPEN_NEW_TAB, this::addTab).active(this::areTabsEnabled);
        this.add(savePlease);
    }

    protected final void enableTabs()
    {
        if (this.tabsEnabled)
        {
            return;
        }

        this.tabsEnabled = true;

        this.tabBar = new UIDataTabs(this);
        this.tabBar.relative(this).w(1F).h(UIDataTabs.TABS_HEIGHT_PX);
        this.setupTabsLayout();
        this.add(this.tabBar);

        this.tabs.add(new DataTab(null));
        this.currentTab = 0;
        this.tabBar.sync();
    }

    private void setupTabsLayout()
    {
        if (!this.tabsEnabled)
        {
            return;
        }

        int tabsHeight = UIDataTabs.TABS_HEIGHT_PX;
        int sidebarWidth = Math.max(0, this.getSidebarWidthPx());

        this.tabBar.setRightInsetPx(this.getTabsRightInsetPx());
        this.iconBar.relative(this).x(1F, -sidebarWidth).y(tabsHeight).w(sidebarWidth).h(1F, -tabsHeight).column(0).stretch();
        this.iconBar.setVisible(sidebarWidth > 0);

        if (sidebarWidth > 0)
        {
            this.editor.relative(this).y(tabsHeight).wTo(this.iconBar.area).h(1F, -tabsHeight);
        }
        else
        {
            this.editor.relative(this).y(tabsHeight).w(1F).h(1F, -tabsHeight);
        }
    }

    protected int getSidebarWidthPx()
    {
        return 20;
    }

    protected int getTabsRightInsetPx()
    {
        return 0;
    }

    public boolean areTabsEnabled()
    {
        return this.tabsEnabled;
    }

    public IKey getNewTabLabel()
    {
        return UIKeys.PANELS_TABS_NEW_TAB;
    }

    public Icon getTabIcon(DataTab tab)
    {
        return tab != null && tab.dataId == null ? Icons.SEARCH : Icons.FOLDER;
    }

    public DataTab getCurrentDataTab()
    {
        return this.currentTab >= 0 && this.currentTab < this.tabs.size() ? this.tabs.get(this.currentTab) : null;
    }

    public boolean isNewTab(DataTab tab)
    {
        return tab != null && tab.dataId == null;
    }

    /* IUITabs — index-based adapters over the DataTab list */

    @Override
    public int getTabCount()
    {
        return this.tabs.size();
    }

    @Override
    public int getCurrentTab()
    {
        return this.currentTab;
    }

    @Override
    public IKey getTabLabel(int index)
    {
        DataTab tab = this.tabs.get(index);

        return tab.dataId == null ? this.getNewTabLabel() : IKey.raw(tab.dataId);
    }

    @Override
    public IKey getTabTooltip(int index)
    {
        return null;
    }

    @Override
    public Icon getTabIcon(int index)
    {
        return this.getTabIcon(this.tabs.get(index));
    }

    @Override
    public boolean isNewTab(int index)
    {
        return this.isNewTab(this.tabs.get(index));
    }

    @Override
    public boolean canCloseTab(int index)
    {
        return index >= 0 && index < this.tabs.size();
    }

    @Override
    public void closeOtherTabs(int index)
    {
        if (index >= 0 && index < this.tabs.size())
        {
            this.closeOtherTabs(this.tabs.get(index));
        }
    }

    @Override
    public void closeTabsLeft(int index)
    {
        if (index >= 0 && index < this.tabs.size())
        {
            this.closeTabsLeft(this.tabs.get(index));
        }
    }

    @Override
    public void closeTabsRight(int index)
    {
        if (index >= 0 && index < this.tabs.size())
        {
            this.closeTabsRight(this.tabs.get(index));
        }
    }

    public int findNewTabIndex()
    {
        for (int i = 0; i < this.tabs.size(); i++)
        {
            if (this.isNewTab(this.tabs.get(i)))
            {
                return i;
            }
        }

        return -1;
    }

    public boolean canAddNewTab()
    {
        return this.findNewTabIndex() < 0;
    }

    public void addTab()
    {
        if (!this.tabsEnabled)
        {
            this.openDataManager();

            return;
        }

        int index = this.findNewTabIndex();

        if (index >= 0)
        {
            this.switchTab(index);

            return;
        }

        this.tabs.add(new DataTab(null));
        this.switchTab(this.tabs.size() - 1);
    }

    public void closeTab(DataTab tab)
    {
        if (tab == null)
        {
            return;
        }

        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTab(index);
        }
    }

    public void closeTab(int index)
    {
        if (!this.tabsEnabled || index < 0 || index >= this.tabs.size())
        {
            return;
        }

        if (this.tabs.size() <= 1)
        {
            if (this.data != null)
            {
                this.save();
            }

            this.tabs.get(0).dataId = null;
            this.currentTab = 0;
            this.fill(null);

            return;
        }

        boolean wasCurrent = this.currentTab == index;

        if (wasCurrent && this.data != null)
        {
            this.save();
            this.data = null;
        }

        this.tabs.remove(index);

        if (this.currentTab >= index)
        {
            this.currentTab = Math.max(0, this.currentTab - 1);
        }

        if (wasCurrent)
        {
            this.switchTab(this.currentTab, true);
        }
        else if (this.tabBar != null)
        {
            this.tabBar.sync();
        }
    }

    public void closeOtherTabs(DataTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTabsKeeping((i) -> i == index, index);
        }
    }

    public void closeTabsLeft(DataTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTabsKeeping((i) -> i >= index, index);
        }
    }

    public void closeTabsRight(DataTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTabsKeeping((i) -> i <= index, index);
        }
    }

    private void closeTabsKeeping(IntPredicate keep, int targetIndex)
    {
        if (!this.tabsEnabled || this.tabs.size() <= 1 || targetIndex < 0 || targetIndex >= this.tabs.size())
        {
            return;
        }

        if (this.data != null)
        {
            this.save();
        }

        DataTab target = this.tabs.get(targetIndex);
        ArrayList<DataTab> kept = new ArrayList<>();

        for (int i = 0; i < this.tabs.size(); i++)
        {
            if (keep.test(i))
            {
                kept.add(this.tabs.get(i));
            }
        }

        if (kept.isEmpty())
        {
            kept.add(target);
        }

        this.tabs.clear();
        this.tabs.addAll(kept);

        int newIndex = this.tabs.indexOf(target);

        if (newIndex < 0)
        {
            newIndex = 0;
        }

        this.currentTab = -1;
        this.switchTab(newIndex, true);
    }

    public void switchTab(DataTab tab)
    {
        if (!this.tabsEnabled || tab == null)
        {
            return;
        }

        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.switchTab(index);
        }
    }

    public void switchTab(int index)
    {
        if (!this.tabsEnabled || index < 0 || index >= this.tabs.size())
        {
            return;
        }

        this.switchTab(index, false);
    }

    private void switchTab(int index, boolean force)
    {
        if (!force && this.currentTab == index)
        {
            return;
        }

        if (this.currentTab >= 0 && this.currentTab < this.tabs.size() && this.data != null)
        {
            this.save();
            this.tabs.get(this.currentTab).dataId = this.data.getId();
        }

        this.currentTab = index;

        DataTab tab = this.tabs.get(index);

        if (tab.dataId == null)
        {
            this.fill(null);
        }
        else
        {
            this.requestData(tab.dataId);
        }
    }

    public void onDataRenamed(String from, String to)
    {
        if (!this.tabsEnabled || from == null || to == null || from.equals(to))
        {
            return;
        }

        boolean changed = false;

        for (DataTab tab : this.tabs)
        {
            if (from.equals(tab.dataId))
            {
                tab.dataId = to;
                changed = true;
            }
        }

        if (changed && this.tabBar != null)
        {
            this.tabBar.sync();
        }
    }

    public void onDataFolderRenamed(String fromPath, String name)
    {
        if (!this.tabsEnabled || fromPath == null || name == null || name.trim().isEmpty())
        {
            return;
        }

        String oldPrefix = fromPath + "/";
        int slash = fromPath.lastIndexOf('/');
        String parentPath = slash >= 0 ? fromPath.substring(0, slash + 1) : "";
        String newPrefix = parentPath + name + "/";
        boolean changed = false;

        for (DataTab tab : this.tabs)
        {
            if (tab.dataId != null && tab.dataId.startsWith(oldPrefix))
            {
                tab.dataId = newPrefix + tab.dataId.substring(oldPrefix.length());
                changed = true;
            }
        }

        if (changed && this.tabBar != null)
        {
            this.tabBar.sync();
        }
    }

    public void onDataRemoved(String id)
    {
        if (!this.tabsEnabled || id == null)
        {
            return;
        }

        boolean changed = false;

        for (DataTab tab : this.tabs)
        {
            if (id.equals(tab.dataId))
            {
                tab.dataId = null;
                changed = true;
            }
        }

        if (this.data != null && id.equals(this.data.getId()))
        {
            this.fill(null);

            return;
        }

        if (changed && this.tabBar != null)
        {
            this.tabBar.sync();
        }
    }

    public void onDataFolderRemoved(String path)
    {
        if (!this.tabsEnabled || path == null || path.isEmpty())
        {
            return;
        }

        String prefix = path.endsWith("/") ? path : path + "/";
        boolean changed = false;

        for (DataTab tab : this.tabs)
        {
            if (tab.dataId != null && tab.dataId.startsWith(prefix))
            {
                tab.dataId = null;
                changed = true;
            }
        }

        if (this.data != null && this.data.getId() != null && this.data.getId().startsWith(prefix))
        {
            this.fill(null);

            return;
        }

        if (changed && this.tabBar != null)
        {
            this.tabBar.sync();
        }
    }

    public T getData()
    {
        return this.data;
    }

    /**
     * Get the content type of this panel
     */
    public abstract ContentType getType();

    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        return new UIDataOverlayPanel<>(this.getTitle(), this, this::pickData);
    }

    @Override
    protected void openDataManager()
    {
        super.openDataManager();
    }

    @Override
    public void pickData(String id)
    {
        if (this.tabsEnabled)
        {
            if (this.currentTab < 0 || this.currentTab >= this.tabs.size())
            {
                if (this.tabs.isEmpty())
                {
                    this.tabs.add(new DataTab(null));
                }

                this.currentTab = 0;
            }

            this.tabs.get(this.currentTab).dataId = id;
            this.requestData(id);

            if (this.tabBar != null)
            {
                this.tabBar.sync();
            }

            return;
        }

        this.save();
        this.requestData(id);
    }

    public void requestData(String id)
    {
        this.getType().getRepository().load(id, (data) -> this.fill((T) data));
    }

    /* Data population */

    public void fill(T data)
    {
        this.data = data;

        if (this.tabsEnabled && this.currentTab >= 0 && this.currentTab < this.tabs.size())
        {
            this.tabs.get(this.currentTab).dataId = data == null ? null : data.getId();
        }

        this.saveIcon.setEnabled(data != null);
        this.editor.setVisible(data != null);
        this.overlay.dupe.setEnabled(data != null);
        this.overlay.rename.setEnabled(data != null);
        this.overlay.remove.setEnabled(data != null);

        this.fillData(data);

        if (data != null && data.getId() != null)
        {
            this.overlay.namesList.setCurrentFile(data.getId());
        }

        if (this.tabsEnabled && this.tabBar != null)
        {
            this.tabBar.sync();
        }

        this.savingTimer.mark(BBSSettings.editorPeriodicSave.get() * 1000L);
    }

    protected abstract void fillData(T data);

    public void fillDefaultData(T data)
    {}

    public void fillNames(Collection<String> names)
    {
        String value;

        if (this.tabsEnabled)
        {
            DataTab tab = this.getCurrentDataTab();

            value = tab == null ? null : tab.dataId;
        }
        else
        {
            value = this.data == null ? null : this.data.getId();
        }

        if (value == null && this.data != null)
        {
            value = this.data.getId();

            if (this.tabsEnabled)
            {
                DataTab tab = this.getCurrentDataTab();

                if (tab != null && tab.dataId == null)
                {
                    tab.dataId = value;
                }
            }
        }

        this.overlay.namesList.fill(names);

        if (value != null)
        {
            this.overlay.namesList.setCurrentFile(value);
        }
    }

    @Override
    public void resize()
    {
        super.resize();
        this.setupTabsLayout();

        if (!this.openedBefore && this.getContext() != null && this.shouldAutoOpenListOnFirstResize())
        {
            this.openDataManager();

            this.openedBefore = true;
        }
    }

    /** If false, the list overlay is not auto-opened when the panel is first shown. Default true. */
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return true;
    }

    @Override
    public void requestNames()
    {
        UIDataUtils.requestNames(this.getType(), this::fillNames);
    }

    public void save()
    {
        if (!this.update && this.data != null && this.editor.isEnabled())
        {
            this.forceSave();
        }
    }

    public void forceSave()
    {
        this.getType().getRepository().save(this.data.getId(), this.data.toData().asMap());
    }

    @Override
    public void open()
    {
        super.open();

        int seconds = BBSSettings.editorPeriodicSave.get();

        if (seconds > 0)
        {
            this.savingTimer.mark(seconds * 1000L);
        }
    }

    @Override
    public void close()
    {
        super.close();

        this.save();
    }

    @Override
    public void render(UIContext context)
    {
        if (this.data == null)
        {
            double ticks = context.getTickTransition() % 15D;
            double factor = Math.abs(ticks / 15D * 2 - 1F);

            int x = this.openOverlay.area.x - 10 + (int) Interpolations.SINE_INOUT.interpolate(-10, 0, factor);
            int y = this.openOverlay.area.my();

            context.batcher.icon(Icons.ARROW_RIGHT, x, y, 0.5F, 0.5F);
        }

        super.render(context);

        if (!this.editor.isEnabled() && this.data != null)
        {
            this.renderLockedArea(context);
        }

        this.checkPeriodicSave(context);
    }

    private void checkPeriodicSave(UIContext context)
    {
        if (this.data == null)
        {
            return;
        }

        int seconds = BBSSettings.editorPeriodicSave.get();

        if (seconds > 0)
        {
            if (this.savingTimer.check() && this.canSave(context))
            {
                this.savingTimer.mark(seconds * 1000L);

                this.save();
                context.notifySuccess(UIKeys.PANELS_SAVED_NOTIFICATION.format(this.data.getId()));
            }
        }
    }

    protected boolean canSave(UIContext context)
    {
        return true;
    }
}
