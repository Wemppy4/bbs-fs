package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.io.File;

/**
 * The slice of an editor panel that {@link UILandingScreen} stands on: what this panel edits, how
 * to open one of them, and where the list of all of them is.
 *
 * <p>An interface rather than the panel class itself, because not everything an editor opens is a
 * saved document. The film, particle and model panels open data a repository owns; the audio
 * editor opens sound files no repository knows about, and both want the same way back into
 * yesterday's work.</p>
 *
 * <p>What a panel cannot offer it simply leaves alone: with no {@link #getCreateLabel()} the "new"
 * row is not on the menu, with no {@link #getDataFolder()} neither is the folder.</p>
 */
public interface ILandingHost
{
    /** What this panel edits; the header above the menu. */
    IKey getTitle();

    /** Icon of an entry &mdash; the same one its tab wears. */
    Icon getTabIcon(String id);

    /** Under which key the recently opened are kept in the settings. */
    String getRecentType();

    /** Open an entry, the way picking it out of the list would. */
    void pickData(String id);

    /** The row that leads to the list of everything this panel can open. */
    default IKey getListLabel()
    {
        return UIKeys.PANELS_KEYS_OPEN_DATA_MANAGER;
    }

    void openDataManager();

    /**
     * Ask what still exists; the answer comes back through {@link UILandingScreen#fillNames}.
     * Over the network that takes a moment, which is why the list is drawn before it arrives.
     */
    void requestNames();

    /** The row that makes a new one, or null when this panel has nothing to create. */
    default IKey getCreateLabel()
    {
        return null;
    }

    default void addNewData(UIContext context)
    {}

    /** The folder the entries live in, or null when they don't live in one. */
    default File getDataFolder()
    {
        return null;
    }

    /** Show an entry in the list instead of opening it. */
    default void showInList(String id)
    {
        this.openDataManager();
    }
}
