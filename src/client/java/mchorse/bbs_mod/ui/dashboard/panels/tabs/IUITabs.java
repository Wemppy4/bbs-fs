package mchorse.bbs_mod.ui.dashboard.panels.tabs;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;

/**
 * Host of a {@link UIDataTabs} strip. Index-based so any owner (data dashboard panels, the texture
 * editor, …) can drive the same tab widget without sharing a data model.
 */
public interface IUITabs
{
    boolean areTabsEnabled();

    int getTabCount();

    int getCurrentTab();

    IKey getTabLabel(int index);

    /** Tooltip for the tab, or null for none. */
    IKey getTabTooltip(int index);

    Icon getTabIcon(int index);

    /** Label used to size the "new tab" and measure the strip; only meaningful when {@link #isNewTab(int)} can be true. */
    IKey getNewTabLabel();

    boolean isNewTab(int index);

    /** A tab that is the home of the strip rather than a document: shown as its icon alone, the label goes to the tooltip. */
    default boolean isPinnedTab(int index)
    {
        return false;
    }

    /** A tab on show beside the current one, in the other half of a split screen: open, but not the one worked on. */
    default boolean isSideTab(int index)
    {
        return false;
    }

    /** The owner's own actions in a tab's context menu, ahead of the closing every tab has. */
    default void fillTabMenu(ContextMenuManager menu, int index)
    {}

    boolean canCloseTab(int index);

    void addTab();

    void switchTab(int index);

    void closeTab(int index);

    void closeOtherTabs(int index);

    void closeTabsLeft(int index);

    void closeTabsRight(int index);
}
