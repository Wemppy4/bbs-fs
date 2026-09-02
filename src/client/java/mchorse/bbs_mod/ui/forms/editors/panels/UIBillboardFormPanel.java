package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UICropOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;

/**
 * The first panel built entirely out of bound fields: every control here reads the property it
 * edits by itself, so there is no {@code startEdit} pouring values in and no field of this class
 * to pour them into. A control is written once, where it is placed.
 */
public class UIBillboardFormPanel extends UIFormPanel<BillboardForm>
{
    /** Kept as a field because the form editor's pick-texture key presses this button. */
    public UIButton pick;

    public UIBillboardFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });

        UIValues.resettable(this.pick, () -> this.form.texture, null);

        UIButton openCrop = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_EDIT_CROP, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UICropOverlayPanel(this.form.texture.get(), this.form.crop.get()), 0.5F, 0.5F);
        });

        this.options.add(
            this.pick,
            UIValues.color(() -> this.form.color).direction(Direction.LEFT).withAlpha(),
            UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard),
            UIValues.toggle(UIKeys.TEXTURES_LINEAR, () -> this.form.linear),
            UIValues.toggle(UIKeys.TEXTURES_MIPMAP, () -> this.form.mipmap)
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_BILLBOARD_CROP).marginTop(UIConstants.SECTION_GAP),
            openCrop,
            UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_RESIZE_CROP, () -> this.form.resizeCrop)
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_BILLBOARD_UV_SHIFT).marginTop(UIConstants.SECTION_GAP),
            UI.row(
                UIValues.trackpad(() -> this.form.offsetX).tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_OFFSET_X),
                UIValues.trackpad(() -> this.form.offsetY).tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_OFFSET_Y)
            ),
            UIValues.trackpad(() -> this.form.rotation).tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_ROTATION),
            UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_SHADING, () -> this.form.shading)
        );
    }
}
