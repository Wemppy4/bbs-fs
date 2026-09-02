package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIVideoFormPanel;

public class UIVideoForm extends UIForm<VideoForm>
{
    public UIVideoForm()
    {
        super();

        this.defaultPanel = new UIVideoFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_VIDEO_TITLE, VideoForm.VIDEO_ICON);
        this.registerDefaultPanels();
    }
}
