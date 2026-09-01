package mchorse.bbs_mod.ui.dashboard.panels.tabs;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;

/**
 * The open documents of an editor panel: which ones are open, which one is showing, and all the
 * bookkeeping of opening, switching, closing and renaming them.
 *
 * <p>None of it knows what a document is — that is the {@link IUITabsHost}'s business — so every
 * editor panel can have tabs, not just the ones backed by a data repository.</p>
 */
public class UITabList implements IUITabs, Iterable<DataTab>
{
    private final IUITabsHost host;
    private final List<DataTab> tabs = new ArrayList<>();

    private UIDataTabs bar;
    private int current;

    public UITabList(IUITabsHost host)
    {
        this.host = host;

        this.tabs.add(new DataTab(null));
        this.current = 0;
    }

    /** Bind the strip that draws these tabs. */
    public void setBar(UIDataTabs bar)
    {
        this.bar = bar;

        this.sync();
    }

    public void sync()
    {
        if (this.bar != null)
        {
            this.bar.sync();
        }
    }

    public int size()
    {
        return this.tabs.size();
    }

    public DataTab get(int index)
    {
        return this.tabs.get(index);
    }

    public int indexOf(DataTab tab)
    {
        return this.tabs.indexOf(tab);
    }

    @Override
    public Iterator<DataTab> iterator()
    {
        return this.tabs.iterator();
    }

    public DataTab getCurrent()
    {
        return this.current >= 0 && this.current < this.tabs.size() ? this.tabs.get(this.current) : null;
    }

    public String getCurrentId()
    {
        DataTab tab = this.getCurrent();

        return tab == null ? null : tab.dataId;
    }

    /** Record what the panel ended up showing, after a load finished or the tab was cleared. */
    public void setOpenId(String id)
    {
        DataTab tab = this.getCurrent();

        if (tab != null)
        {
            tab.dataId = id;
        }

        this.sync();
    }

    /** Open something in the current tab, replacing whatever was in it. */
    public void pick(String id)
    {
        if (this.tabs.isEmpty())
        {
            this.tabs.add(new DataTab(null));
        }

        if (this.current < 0 || this.current >= this.tabs.size())
        {
            this.current = 0;
        }

        /* Swapping the document inside a tab drops the old one just like switching tabs does, so it
         * has to flush it first — otherwise edits made since the last periodic save never reach disk. */
        this.host.saveOpen();

        this.tabs.get(this.current).dataId = id;

        this.host.openTab(id);
        this.sync();
    }

    /** Index of the first tab with nothing open in it, or -1. */
    public int findEmptyTab()
    {
        for (int i = 0; i < this.tabs.size(); i++)
        {
            if (this.tabs.get(i).dataId == null)
            {
                return i;
            }
        }

        return -1;
    }

    /* IUITabs — index based, for the strip */

    @Override
    public boolean areTabsEnabled()
    {
        return true;
    }

    @Override
    public int getTabCount()
    {
        return this.tabs.size();
    }

    @Override
    public int getCurrentTab()
    {
        return this.current;
    }

    @Override
    public IKey getTabLabel(int index)
    {
        String id = this.tabs.get(index).dataId;

        return id == null ? this.host.getNewTabLabel() : IKey.raw(id);
    }

    @Override
    public IKey getTabTooltip(int index)
    {
        return this.host.getTabTooltip(this.tabs.get(index).dataId);
    }

    @Override
    public Icon getTabIcon(int index)
    {
        return this.host.getTabIcon(this.tabs.get(index).dataId);
    }

    @Override
    public IKey getNewTabLabel()
    {
        return this.host.getNewTabLabel();
    }

    @Override
    public boolean isNewTab(int index)
    {
        return this.tabs.get(index).dataId == null;
    }

    @Override
    public boolean canCloseTab(int index)
    {
        return index >= 0 && index < this.tabs.size();
    }

    @Override
    public void addTab()
    {
        int index = this.findEmptyTab();

        if (index >= 0)
        {
            this.switchTab(index);

            return;
        }

        this.tabs.add(new DataTab(null));
        this.switchTab(this.tabs.size() - 1);
    }

    @Override
    public void switchTab(int index)
    {
        if (index >= 0 && index < this.tabs.size())
        {
            this.switchTab(index, false);
        }
    }

    public void switchTab(DataTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.switchTab(index);
        }
    }

    private void switchTab(int index, boolean force)
    {
        if (!force && this.current == index)
        {
            return;
        }

        if (this.current >= 0 && this.current < this.tabs.size())
        {
            String open = this.host.getOpenId();

            if (open != null)
            {
                this.host.saveOpen();
                this.tabs.get(this.current).dataId = open;
            }
        }

        this.current = index;

        this.host.openTab(this.tabs.get(index).dataId);
        this.sync();
    }

    @Override
    public void closeTab(int index)
    {
        if (index < 0 || index >= this.tabs.size())
        {
            return;
        }

        /* The last tab is never removed, it is emptied — a panel always has a tab to open into. */
        if (this.tabs.size() <= 1)
        {
            this.host.saveOpen();

            this.tabs.get(0).dataId = null;
            this.current = 0;

            this.host.openTab(null);
            this.sync();

            return;
        }

        boolean wasCurrent = this.current == index;

        if (wasCurrent)
        {
            this.host.saveOpen();
        }

        this.tabs.remove(index);

        if (this.current >= index)
        {
            this.current = Math.max(0, this.current - 1);
        }

        if (wasCurrent)
        {
            this.switchTab(this.current, true);
        }
        else
        {
            this.sync();
        }
    }

    public void closeTab(DataTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTab(index);
        }
    }

    @Override
    public void closeOtherTabs(int index)
    {
        this.closeTabsKeeping((i) -> i == index, index);
    }

    @Override
    public void closeTabsLeft(int index)
    {
        this.closeTabsKeeping((i) -> i >= index, index);
    }

    @Override
    public void closeTabsRight(int index)
    {
        this.closeTabsKeeping((i) -> i <= index, index);
    }

    private void closeTabsKeeping(IntPredicate keep, int targetIndex)
    {
        if (this.tabs.size() <= 1 || targetIndex < 0 || targetIndex >= this.tabs.size())
        {
            return;
        }

        this.host.saveOpen();

        DataTab target = this.tabs.get(targetIndex);
        List<DataTab> kept = new ArrayList<>();

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

        int newIndex = Math.max(0, this.tabs.indexOf(target));

        this.current = -1;
        this.switchTab(newIndex, true);
    }

    /* Mirroring what the data manager did to the documents behind the tabs */

    public boolean renameId(String from, String to)
    {
        return this.rewrite((id) -> id.equals(from) ? to : id);
    }

    public boolean renameFolder(String fromPath, String name)
    {
        String oldPrefix = fromPath + "/";
        int slash = fromPath.lastIndexOf('/');
        String parent = slash >= 0 ? fromPath.substring(0, slash + 1) : "";
        String newPrefix = parent + name + "/";

        return this.rewrite((id) -> id.startsWith(oldPrefix) ? newPrefix + id.substring(oldPrefix.length()) : id);
    }

    /** The document is gone: the tabs that held it stay open, but empty. */
    public boolean forgetId(String id)
    {
        return this.rewrite((tabId) -> tabId.equals(id) ? null : tabId);
    }

    public boolean forgetFolder(String path)
    {
        String prefix = path.endsWith("/") ? path : path + "/";

        return this.rewrite((id) -> id.startsWith(prefix) ? null : id);
    }

    private boolean rewrite(UnaryOperator<String> mapper)
    {
        boolean changed = false;

        for (DataTab tab : this.tabs)
        {
            if (tab.dataId == null)
            {
                continue;
            }

            String mapped = mapper.apply(tab.dataId);

            if (mapped == null ? tab.dataId != null : !mapped.equals(tab.dataId))
            {
                tab.dataId = mapped;
                changed = true;
            }
        }

        if (changed)
        {
            this.sync();
        }

        return changed;
    }
}
