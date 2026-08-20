package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The folder exported videos land in. Empty means the default movies folder, so
 * both picking and opening work off wherever the videos actually go rather than
 * off what the field says.
 */
public class UIExportPathRow extends UIPathRow
{
    public UIExportPathRow(ValueString path)
    {
        super(path);
    }

    @Override
    protected IKey getTooltip()
    {
        return UIKeys.CAMERA_TOOLTIPS_PICK_EXPORT_FOLDER;
    }

    @Override
    protected void pick(UITextbox textbox)
    {
        UIFileDialogs.pickFolder(UIKeys.GENERAL_DIALOG_EXPORT_FOLDER, BBSRendering.getVideoFolder(), (file) -> this.set(textbox, file));
    }

    @Override
    protected void context(ContextMenuManager menu, UIElement element, UITextbox textbox)
    {
        menu.action(Icons.FOLDER, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () -> UIUtils.openFolder(BBSRendering.getVideoFolder()));
    }
}
