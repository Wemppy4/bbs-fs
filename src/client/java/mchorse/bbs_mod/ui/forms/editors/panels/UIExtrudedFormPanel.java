package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIExtrudedFormPanel extends UIFormPanel<ExtrudedForm>
{
    public UIButton pick;
    public UIColor color;
    public UIToggle billboard;
    public UIToggle shading;

    public UIExtrudedFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });
        this.color = UIValues.color(() -> this.form.color);
        this.color.withAlpha();
        this.billboard = UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard);
        this.shading = UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_SHADING, () -> this.form.shading);

        this.options.add(this.pick, this.color, this.billboard, this.shading);
    }

    @Override
    public void startEdit(ExtrudedForm form)
    {
        super.startEdit(form);

        this.color.setColor(form.color.get().getARGBColor());
        this.billboard.setValue(form.billboard.get());
        this.shading.setValue(form.shading.get());
    }
}