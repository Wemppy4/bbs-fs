package mchorse.bbs_mod.ui.utils;

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
}
