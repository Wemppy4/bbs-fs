package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.ui.utils.bones.BoneRole;

/**
 * An editor that owns a {@link BoneSelection} — the film editor and the form editor.
 *
 * <p>Panels find their host by walking up the widget tree (see
 * {@code UIElement.getAncestor}), so the selection follows the editor a panel is shown in
 * rather than being a single value for the entire mod.</p>
 */
public interface IBoneSelectionHost
{
    BoneSelection getBoneSelection();

    /**
     * A mark the host puts next to a bone in a pose's bone list, or null — the film marks the bones
     * that have a motion path. {@code bonePath} addresses the bone the way the film's targets do.
     */
    default BoneRole markBone(String bonePath)
    {
        return null;
    }
}
