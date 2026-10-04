package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import mchorse.bbs_mod.forms.values.ValueModelTransformationMode;

public class UIItemFormPanel extends UIFormPanel<ItemForm>
{
    public UIButton modelTransform;
    public UIItemStack itemStackEditor;

    public UIItemFormPanel(UIForm editor)
    {
        super(editor);

        this.modelTransform = new UIButton(IKey.EMPTY, (b) ->
        {
            this.getContext().replaceContextMenu((menu) ->
            {
                for (TransformType value : TransformType.values())
                {
                    if (this.form.modelTransform.get() == value)
                    {
                        menu.action(Icons.LINE, IKey.constant(ValueModelTransformationMode.asString(value)), true, () -> {});
                    }
                    else
                    {
                        menu.action(Icons.LINE, IKey.constant(ValueModelTransformationMode.asString(value)), () -> this.setModelTransform(value));
                    }
                }
            });
        });

        this.itemStackEditor = new UIItemStack((itemStack) -> this.form.stack.set(itemStack.copy()));

        this.options.add(UIValues.color(() -> this.form.color).withAlpha(), UI.labelRow(UIKeys.FORMS_EDITORS_ITEM_TRANSFORMS, this.modelTransform), this.itemStackEditor);
    }

    private void setModelTransform(TransformType value)
    {
        this.form.modelTransform.set(value);

        this.modelTransform.label = IKey.constant(ValueModelTransformationMode.asString(value));
    }

    @Override
    public void startEdit(ItemForm form)
    {
        super.startEdit(form);

        this.modelTransform.label = IKey.constant(ValueModelTransformationMode.asString(form.modelTransform.get()));
        this.itemStackEditor.setStack(form.stack.get());
    }
}
