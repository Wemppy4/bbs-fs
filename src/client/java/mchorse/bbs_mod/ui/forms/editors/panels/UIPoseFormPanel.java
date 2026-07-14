package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.PoseForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIPoseFormEditor;

/** Shared form-panel plumbing for forms that expose editable bone poses. */
public abstract class UIPoseFormPanel <T extends Form & PoseForm> extends UIFormPanel<T>
{
    public final UIPoseFormEditor poseEditor;

    public UIPoseFormPanel(UIForm editor)
    {
        super(editor);

        this.poseEditor = new UIPoseFormEditor();
        this.poseEditor.transform.barBackground();
    }

    protected void bindPose(T form, String poseGroup)
    {
        this.poseEditor.setValuePose(form.getPose());
        this.poseEditor.setPose(form.getPose().get(), poseGroup);
    }

    @Override
    public void pickBone(String bone)
    {
        super.pickBone(bone);

        this.poseEditor.selectBone(bone);
    }
}
