package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.mob.MobRig;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UIMobFormPanel extends UIFormPanel<MobForm>
{
    private static List<String> mobIDs;

    public UIButton pick;
    public UIToggle slim;
    public UIModelPoseEditor poseEditor;
    public UISearchList<String> mobID;
    public UITextarea<TextLine> mobNBT;

    static
    {
        mobIDs = new ArrayList<>();

        for (RegistryKey<EntityType<?>> key : Registries.ENTITY_TYPE.getKeys())
        {
            mobIDs.add(key.getValue().toString());
        }

        mobIDs.sort(Comparator.comparing((a) -> a));
    }

    public UIMobFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITOR_MODEL_PICK_TEXTURE, (b) ->
        {
            Link link = this.form.texture.get();

            UITexturePicker.open(this.getContext(), link, (l) -> this.form.texture.set(l));
        });
        this.slim = UIValues.toggle(UIKeys.FORMS_EDITOR_SLIM, () -> this.form.slim);
        this.slim.tooltip(UIKeys.FORMS_EDITOR_SLIM_TOOLTIP);

        this.poseEditor = new UIModelPoseEditor();
        this.poseEditor.transform.barBackground();

        this.mobID = new UISearchList<>(new UIStringList((l) -> this.form.mobID.set(l.get(0))));
        this.mobID.list.background().add(mobIDs);
        this.mobID.h(20 + 16 * 8);

        this.mobNBT = new UITextarea<>((t) -> this.form.mobNBT.set(t));
        this.mobNBT.background().h(160);
        this.mobNBT.wrap();

        this.options.add(this.pick, this.slim, this.poseEditor, this.mobID, this.mobNBT);
    }

    @Override
    public void startEdit(MobForm form)
    {
        super.startEdit(form);

        this.slim.setValue(this.form.slim.get());
        this.mobID.list.setCurrentScroll(this.form.mobID.get());
        this.mobNBT.setText(this.form.mobNBT.get());

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