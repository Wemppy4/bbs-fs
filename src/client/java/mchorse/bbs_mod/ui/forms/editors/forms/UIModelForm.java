package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIActionsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelConstraintsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelIKFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelPhysicsFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class UIModelForm extends UIForm<ModelForm>
{
    public UIModelFormPanel modelPanel;

    public UIModelForm()
    {
        this.modelPanel = new UIModelFormPanel(this);
        this.modelPanel.poseEditor.transform.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.modelPanel.poseEditor.transform));
        this.modelPanel.poseEditor.transform.worldTransform(new FormBoneWorldProvider(this));
        this.modelPanel.poseEditor.transform.rotationConstrained(() ->
        {
            ModelForm form = this.form;
            ModelInstance instance = form == null ? null : ModelFormRenderer.getModel(form);

            return instance != null && ModelIKRuntime.isRotationConstrained(instance.model, form, this.modelPanel.poseEditor.groups.list.getCurrentFirst());
        });
        this.defaultPanel = this.modelPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MODEL_POSE, Icons.POSE);
        this.registerPanel(new UIModelIKFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_IK, Icons.IK);
        this.registerPanel(new UIModelPhysicsFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TITLE, Icons.PHYSICS);
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
    public UIPropTransform getEditableTransform()
    {
        return this.modelPanel.poseEditor.transform;
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.put("bones", DataStorageUtils.stringListToData(this.modelPanel.poseEditor.groups.list.getCurrent()));
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.has("bones"))
        {
            this.modelPanel.poseEditor.restoreSelection(DataStorageUtils.stringListFromData(data.get("bones")));
        }
    }

    @Override
    public Matrix4f getOrigin(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), this.modelPanel.poseEditor.transform.isLocal());
    }

    @Override
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), true);
    }

    @Override
    public TransformSpace getGizmoSpace()
    {
        return this.modelPanel.poseEditor.transform.getSpace();
    }

    private String bonePath()
    {
        return StringUtils.combinePaths(FormUtils.getPath(this.form), this.modelPanel.poseEditor.groups.list.getCurrentFirst());
    }

    /**
     * The additive euler base under the pose editor's channels for the picked
     * bone ({@link FormUtils#additivePoseRotationBase}): the total comes from
     * the bone's EVALUATED channels in the capture (rest + actions + the whole
     * pose stack) with the pose track's own contribution subtracted, so gizmo
     * deltas compose at the bone's effective angles. {@code null} for any other
     * transform editor — only the pose panel edits a pose-stacked track.
     */
    public Vector3f poseRotationBase(UIPropTransform transform, float transition)
    {
        if (transform != this.modelPanel.poseEditor.transform)
        {
            return null;
        }

        String bone = this.modelPanel.poseEditor.groups.list.getCurrentFirst();

        if (bone == null)
        {
            return null;
        }

        return FormUtils.additivePoseRotationBase(this.form.pose, bone, this.getEvaluatedRotation(transition, this.bonePath()));
    }

    @Override
    public boolean toggleBoneSelection(String bone)
    {
        if (!this.modelPanel.poseEditor.hasBone(bone))
        {
            return false;
        }

        this.modelPanel.poseEditor.selectBone(bone, true);

        return true;
    }
}
