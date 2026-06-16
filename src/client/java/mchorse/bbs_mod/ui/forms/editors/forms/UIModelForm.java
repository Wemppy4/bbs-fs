package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIActionsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelConstraintsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelIKFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelPhysicsFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.TransformSpace;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;

public class UIModelForm extends UIForm<ModelForm>
{
    public UIModelFormPanel modelPanel;

    public UIModelForm()
    {
        this.modelPanel = new UIModelFormPanel(this);
        this.modelPanel.poseEditor.transform.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.modelPanel.poseEditor.transform));
        this.defaultPanel = this.modelPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MODEL_POSE, Icons.POSE);
        this.registerPanel(new UIModelIKFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_IK, Icons.LIMB);
        this.registerPanel(new UIModelPhysicsFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TITLE, Icons.DROP);
        this.registerPanel(new UIModelConstraintsFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_TITLE, Icons.LOCKED);
        this.registerPanel(new UIActionsFormPanel(this), UIKeys.FORMS_EDITORS_ACTIONS_TITLE, Icons.MORE);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.modelPanel)
            {
                this.setPanel(this.modelPanel);
            }

            this.modelPanel.pick.clickItself();
        });
    }

    @Override
    protected UIPropTransform getFormEditableTransform()
    {
        return this.modelPanel.poseEditor.transform;
    }

    @Override
    protected Matrix4f getFormOrigin(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), this.modelPanel.poseEditor.transform.getSpace());
    }

    @Override
    protected Matrix4f getFormOriginMatrix(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), TransformSpace.LOCAL);
    }

    private String bonePath()
    {
        return StringUtils.combinePaths(FormUtils.getPath(this.form), this.modelPanel.poseEditor.groups.getCurrentFirst());
    }
}
