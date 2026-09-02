package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMobFormPanel;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;

public class UIMobForm extends UIForm<MobForm>
{
    public UIMobFormPanel mobPanel;

    public UIMobForm()
    {
        super();

        this.mobPanel = new UIMobFormPanel(this);
        this.mobPanel.poseEditor.transform.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.mobPanel.poseEditor.transform));
        this.mobPanel.poseEditor.transform.worldTransform(new FormBoneWorldProvider(this));
        this.defaultPanel = this.mobPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MOB_TITLE, MobForm.ICON);
        this.registerDefaultPanels();
    }

    @Override
    public UIPoseEditor getPoseEditor()
    {
        return this.mobPanel.poseEditor;
    }
}