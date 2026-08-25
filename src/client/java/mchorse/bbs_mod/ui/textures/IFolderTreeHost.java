package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.resources.Link;

/**
 * What a {@link UIFolderTree} needs from whoever shows it: where clicks go, which folder is
 * the current one, and — for a host that lets textures be dragged — the drag whose target the
 * tree reports while painting.
 */
public interface IFolderTreeHost
{
    public void navigate(Link folder);

    public boolean isCurrentFolder(Link folder);

    /** The drag in progress, or null for a host without one (a save dialog). */
    public default TextureDrag getDrag()
    {
        return null;
    }

    /** The button went up over the tree. */
    public default void release()
    {}
}
