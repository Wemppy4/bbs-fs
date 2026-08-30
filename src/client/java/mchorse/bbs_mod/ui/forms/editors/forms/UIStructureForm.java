package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.panels.UIStructureFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Form editor for {@link StructureForm}: one custom panel (structure/biome pickers) plus the
 * default BBS panels (transform, etc.).
 */
public class UIStructureForm extends UIForm<StructureForm>
{
    public UIStructureForm()
    {
        super();

        this.defaultPanel = new UIStructureFormPanel(this);

        this.registerPanel(this.defaultPanel, L10n.lang("bbs.ui.forms.editors.structure.title"), Icons.BLOCK);
        this.registerDefaultPanels();
    }
}
