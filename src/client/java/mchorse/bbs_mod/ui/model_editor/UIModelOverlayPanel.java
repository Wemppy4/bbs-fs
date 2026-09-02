package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Consumer;

/**
 * The model editor's data manager. Models are assets living in the assets folder, so this is a pure
 * picker: same folder browser as everywhere else, minus create/duplicate/rename/remove — and the
 * landing screen drops its "new" entry for the same reason.
 */
public class UIModelOverlayPanel extends UIDataOverlayPanel<ModelConfig>
{
    public UIModelOverlayPanel(IKey title, UIDataDashboardPanel<ModelConfig> panel, Consumer<String> callback)
    {
        super(title, panel, callback);

        /* Same icon the tabs and the landing screen use, so a model reads as a model everywhere. */
        this.namesList.setFileIcon(Icons.POSE);
    }

    @Override
    public boolean showActionButtons()
    {
        return false;
    }
}
