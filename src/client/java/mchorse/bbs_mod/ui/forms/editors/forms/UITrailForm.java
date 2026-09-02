package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UITrailFormPanel;

public class UITrailForm extends UIForm<TrailForm>
{
    public UITrailFormPanel trailFormPanel;

    public UITrailForm()
    {
        super();

        this.trailFormPanel = new UITrailFormPanel(this);
        this.defaultPanel = this.trailFormPanel;

        this.registerPanel(this.trailFormPanel, UIKeys.FORMS_EDITORS_TRAIL_TITLE, TrailForm.ICON);
        this.registerDefaultPanels();
    }
}