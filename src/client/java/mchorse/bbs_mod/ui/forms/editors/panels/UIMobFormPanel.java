package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UIMobFormPanel extends UIPoseFormPanel<MobForm>
{
    private static List<String> mobIDs;

    public UIButton pick;
    public UIColor color;
    public UIToggle action;
    public UIToggle slim;
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
        this.color = new UIColor((c) -> this.form.color.set(new Color().set(c))).withAlpha();
        this.action = new UIToggle(UIKeys.FORMS_EDITORS_MOB_ACTION, (b) -> this.form.action.set(b.getValue()));
        this.slim = new UIToggle(UIKeys.FORMS_EDITOR_SLIM, (b) ->
        {
            this.form.slim.set(b.getValue());
            this.refreshPoseEditor();
        });
        this.slim.tooltip(UIKeys.FORMS_EDITOR_SLIM_TOOLTIP);

        this.mobID = new UISearchList<>(new UIStringList((l) ->
        {
            if (!l.isEmpty())
            {
                this.form.mobID.set(l.get(0));
                this.refreshPoseEditor();
            }
        }));
        this.mobID.list.background().add(mobIDs);
        this.mobID.h(20 + 16 * 8);

        this.mobNBT = new UITextarea<>((t) ->
        {
            this.form.mobNBT.set(t);
            this.refreshPoseEditor();
        });
        this.mobNBT.background().h(160);
        this.mobNBT.wrap();

        this.options.add(this.pick, this.color, this.action, this.slim, this.mobID, this.mobNBT, this.poseEditor);
    }

    @Override
    public void startEdit(MobForm form)
    {
        super.startEdit(form);

        this.color.setColor(this.form.color.get().getARGBColor());
        this.action.setValue(this.form.action.get());
        this.slim.setValue(this.form.slim.get());
        this.mobID.list.setCurrentScroll(this.form.mobID.get());
        this.mobNBT.setText(this.form.mobNBT.get());
        this.refreshPoseEditor();
    }

    private void refreshPoseEditor()
    {
        if (this.form == null)
        {
            return;
        }

        this.bindPose(this.form, "");
        this.poseEditor.fillGroups(FormUtilsClient.getBoneHierarchy(this.form), true);
    }
}
