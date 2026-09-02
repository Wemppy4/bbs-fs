package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/**
 * Editor for a bone's constraints keyframe: the rotation limits, laid out the way the form
 * editor's "Constraints" tab lays them out. The value is the bone property's own type, so what
 * this edits is exactly what the form stores statically.
 */
public class UIBoneConstraintKeyframeFactory extends UIKeyframeFactory<BoneConstraint>
{
    public UIToggle enabled;
    public UISliderTrackpad minX;
    public UISliderTrackpad minY;
    public UISliderTrackpad minZ;
    public UISliderTrackpad maxX;
    public UISliderTrackpad maxY;
    public UISliderTrackpad maxZ;

    private boolean syncing;

    public UIBoneConstraintKeyframeFactory(Keyframe<BoneConstraint> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        IKey axis = IKey.constant("%s (%s)");

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_ENABLED, (b) -> this.edit((c) -> c.enabled = b.getValue()));

        this.minX = this.axisTrackpad((v) -> this.edit((c) -> c.minX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X));
        this.minY = this.axisTrackpad((v) -> this.edit((c) -> c.minY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y));
        this.minZ = this.axisTrackpad((v) -> this.edit((c) -> c.minZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z));
        this.maxX = this.axisTrackpad((v) -> this.edit((c) -> c.maxX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X));
        this.maxY = this.axisTrackpad((v) -> this.edit((c) -> c.maxY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y));
        this.maxZ = this.axisTrackpad((v) -> this.edit((c) -> c.maxZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z));

        this.scroll.add(UI.column(
            this.enabled.marginTop(UIConstants.SECTION_GAP),
            UI.label(IKey.constant("%s / %s").format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX)).marginTop(UIConstants.SECTION_GAP),
            UI.label(UIKeys.GENERAL_X),
            UI.row(this.minX, this.maxX),
            UI.label(UIKeys.GENERAL_Y),
            UI.row(this.minY, this.maxY),
            UI.label(UIKeys.GENERAL_Z),
            UI.row(this.minZ, this.maxZ)
        ));

        this.display();
    }

    private void display()
    {
        BoneConstraint c = this.keyframe.getValue();

        if (c == null)
        {
            c = BoneConstraint.DEFAULT;
        }

        this.syncing = true;

        try
        {
            this.enabled.setValue(c.enabled);
            this.minX.setValue(c.minX);
            this.minY.setValue(c.minY);
            this.minZ.setValue(c.minZ);
            this.maxX.setValue(c.maxX);
            this.maxY.setValue(c.maxY);
            this.maxZ.setValue(c.maxZ);
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(Consumer<BoneConstraint> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            BoneConstraint c = (BoneConstraint) selected.getValue();

            if (c == null)
            {
                return;
            }

            selected.preNotify();
            consumer.accept(c);
            selected.postNotify();
        });
    }

    private UISliderTrackpad axisTrackpad(Consumer<Double> callback, int color, IKey tooltip)
    {
        UISliderTrackpad trackpad = new UISliderTrackpad(callback).angle180();

        trackpad.textbox.setColor(color);
        trackpad.tooltip(tooltip);

        return trackpad;
    }
}
