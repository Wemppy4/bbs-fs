package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Consumer;

/**
 * The model editor's data manager. Models are assets living in the assets folder, so this is a pure
 * picker: same folder browser as everywhere else, minus create/duplicate/rename/remove — the same
 * trim {@link UIModelSelectionScreen} makes to the landing screen.
 */
public class UIModelOverlayPanel extends UIDataOverlayPanel<ModelConfig>
{
    public UIModelOverlayPanel(IKey title, UIDataDashboardPanel<ModelConfig> panel, Consumer<String> callback)
    {
        super(title, panel, callback);

        /* Same file icon the landing screen uses, so a model reads as a model in both lists. */
        this.namesList.setFileIcon(Icons.POSE);
    }

    @Override
    protected boolean showActionButtons()
    {
        return false;
    }
}
