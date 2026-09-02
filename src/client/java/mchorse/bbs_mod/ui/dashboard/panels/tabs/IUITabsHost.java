package mchorse.bbs_mod.ui.dashboard.panels.tabs;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;

/**
 * The editor behind a {@link UITabList}: it says what a tab's id means, and the list does the rest.
 *
 * <p>Everything a tab needs is expressed as an id — a data path for the panels that edit saved
 * documents, a sound link for the audio editor. A tab with a null id is one with nothing open
 * in it yet.</p>
 */
public interface IUITabsHost
{
    /** Show whatever this id refers to; null means show nothing. */
    void openTab(String id);

    /** Id of what is showing right now, or null when the tab is empty. */
    String getOpenId();

    /** Flush what is showing before the tabs move away from it. */
    default void saveOpen()
    {}

    /** Label of a tab with nothing open in it. */
    IKey getNewTabLabel();

    /** Icon of a tab; the id is null for a tab with nothing open in it. */
    Icon getTabIcon(String id);

    /** Tooltip of a tab, or null for none. */
    default IKey getTabTooltip(String id)
    {
        return null;
    }
}
