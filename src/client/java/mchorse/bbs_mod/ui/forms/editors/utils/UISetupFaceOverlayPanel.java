package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPicker;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.function.BiConsumer;

/**
 * Asks what a face is to be built out of before it is built: which rig plays the eyes, and how
 * far down the face sits on the head.
 */
public class UISetupFaceOverlayPanel extends UIOverlayPanel
{
    public UIButton eyesRig;
    public UITrackpad verticalOffset;
    public UIButton confirm;

    private String model = "player/eyes";
    private final BiConsumer<String, Double> callback;

    public UISetupFaceOverlayPanel(BiConsumer<String, Double> callback)
    {
        super(UIKeys.FORMS_EDITOR_SETUP_FACE_TITLE);

        this.callback = callback;
        this.eyesRig = new UIButton(IKey.constant(this.model), (b) -> this.pickModel());
        this.verticalOffset = new UITrackpad();
        this.verticalOffset.setValue(0D);
        this.verticalOffset.tooltip(UIKeys.FORMS_EDITOR_SETUP_FACE_VERTICAL_OFFSET_HINT);
        this.confirm = new UIButton(UIKeys.GENERAL_CONFIRM, (b) -> this.confirm());

        this.content.column(UIConstants.MARGIN).vertical().stretch().padding(UIConstants.SCROLL_PADDING);
        this.content.add(
            UI.label(UIKeys.FORMS_EDITOR_SETUP_FACE_EYES_RIG), this.eyesRig.marginBottom(UIConstants.SCROLL_PADDING),
            UI.label(UIKeys.FORMS_EDITOR_SETUP_FACE_VERTICAL_OFFSET), this.verticalOffset.marginBottom(UIConstants.SCROLL_PADDING),
            this.confirm
        );
    }

    private void pickModel()
    {
        UIModelPicker.open(this.getContext(), this.model, (model) ->
        {
            this.model = model;
            this.eyesRig.label = IKey.constant(model);
        });
    }

    @Override
    public void confirm()
    {
        this.callback.accept(this.model, this.verticalOffset.getValue());
        this.close();
    }
}
