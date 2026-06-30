package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.ui.dashboard.panels.UISelectionScreen;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The Model Editor's landing screen — a searchable list of models to open. Models are assets, so the
 * create/duplicate/rename/remove actions are off; this is a pure picker.
 */
public class UIModelSelectionScreen extends UISelectionScreen<ModelConfig>
{
    public UIModelSelectionScreen(UIModelEditorPanel panel)
    {
        super(panel);
    }

    @Override
    protected Icon getFileIcon()
    {
        return Icons.POSE;
    }

    @Override
    protected boolean showActionButtons()
    {
        return false;
    }
}
