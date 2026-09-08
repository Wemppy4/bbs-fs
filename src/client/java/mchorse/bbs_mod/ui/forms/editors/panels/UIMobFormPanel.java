package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.mob.MobRig;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIMobEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIMobFormPanel extends UIFormPanel<MobForm>
{
    public UIButton pick;
    public UIModelPoseEditor poseEditor;
    public UIMobEditor mob;

    public UIMobFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITOR_MODEL_PICK_TEXTURE, (b) ->
        {
            Link link = this.form.texture.get();

            UITexturePicker.open(this.getContext(), link, (l) -> this.form.texture.set(l));
        });
        UIToggle slim = UIValues.toggle(UIKeys.FORMS_EDITOR_SLIM, () -> this.form.slim);

        slim.tooltip(UIKeys.FORMS_EDITOR_SLIM_TOOLTIP);

        this.poseEditor = new UIModelPoseEditor();
        this.poseEditor.transform.barBackground();

        this.mob = new UIMobEditor((id, nbt) ->
        {
            this.form.mobID.set(id);
            this.form.mobNBT.set(nbt);
        });
        /* Another mob is another skeleton: the bone list and the pose group are only right
         * again once the whole panel is refilled, which is what startEdit does. */
        this.mob.onClose(() -> this.editor.startEdit(this.form));

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_MOB_ID), this.mob, this.pick, slim, this.poseEditor);
    }

    @Override
    public void startEdit(MobForm form)
    {
        super.startEdit(form);

        this.mob.set(this.form.mobID.get(), this.form.mobNBT.get());

        MobRig rig = MobFormRenderer.getRig(this.form);

        this.poseEditor.setValuePose(form.pose);
        /* The mob id is the pose group, so a chicken's presets don't show up under a zombie. */
        this.poseEditor.setPose(form.pose.get(), this.form.mobID.get());
        /* No flipped-parts table: vanilla part names are already left_/right_, which is exactly
         * what Pose's own mirror rule matches. */
        this.poseEditor.fillGroups(rig, null, true, null);

        this.options.resize();
    }

    @Override
    public void pickBone(String bone)
    {
        super.pickBone(bone);

        this.poseEditor.selectBone(bone);
    }
}
