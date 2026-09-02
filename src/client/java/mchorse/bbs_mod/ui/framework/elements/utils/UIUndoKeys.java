package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Invisible overlay that captures Ctrl+Z and Ctrl+Y for undo/redo, so they work on top of
 * everything else an editor binds — focused controls and single-key binds alike (the film
 * panel's Y for the body fix toggle, for one).
 *
 * <p>Both are strict: sitting above everything else means nothing downstream gets a look in,
 * so an extra modifier has to make this step aside, or longer combos ending in Z or Y
 * (Ctrl+Alt+Z for the layout history) would be swallowed here and silently run the wrong undo.
 *
 * <p>Add it over the editor's own area: {@code this.add(new UIUndoKeys(this::undo, this::redo).full(this))}.
 */
public class UIUndoKeys extends UIElement
{
    public UIUndoKeys(Runnable undo, Runnable redo)
    {
        this.keys().ignoreFocus();
        this.keys().register(Keys.UNDO, undo).strict().category(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE);
        this.keys().register(Keys.REDO, redo).strict().category(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE);
        this.noCulling();
    }
}
