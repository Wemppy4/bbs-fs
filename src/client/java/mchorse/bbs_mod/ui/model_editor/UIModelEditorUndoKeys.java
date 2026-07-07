package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Invisible overlay that captures Ctrl+Z and Ctrl+Y for undo/redo in the model editor,
 * so they take priority over focused controls and other single-key binds.
 */
public class UIModelEditorUndoKeys extends UIElement
{
    public UIModelEditorUndoKeys(UIModelEditorPanel panel)
    {
        this.keys().ignoreFocus();
        this.keys().register(Keys.UNDO, panel::undo).category(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE);
        this.keys().register(Keys.REDO, panel::redo).category(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE);
        this.noCulling();
    }
}
