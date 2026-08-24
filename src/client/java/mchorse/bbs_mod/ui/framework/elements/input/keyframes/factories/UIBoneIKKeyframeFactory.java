package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/**
 * Editor for one bone's IK keyframe: the chain's animatable scalars (enabled, weight, softness,
 * pole switch and angle), laid out the way the form editor's IK tab lays them out. The value is
 * the bone property's own type, so what this edits is exactly what the form stores statically.
 * Replaced the whole-form editor that listed every chain in one keyframe.
 */
public class UIBoneIKKeyframeFactory extends UIKeyframeFactory<IKControl>
{
    public UIToggle enabled;
    public UISliderTrackpad weight;
    public UISliderTrackpad softness;
    public UIToggle pole;
    public UISliderTrackpad poleAngle;

    private boolean syncing;

    public UIBoneIKKeyframeFactory(Keyframe<IKControl> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_ENABLED, (b) -> this.edit((c) -> c.enabled = b.getValue()));

        this.weight = new UISliderTrackpad((v) -> this.edit((c) -> c.weight = v.floatValue()));
        this.weight.normalized();
        this.weight.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT);

        this.softness = new UISliderTrackpad((v) -> this.edit((c) -> c.softness = v.floatValue()));
        this.softness.normalized();
        this.softness.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS);

        this.pole = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, (b) -> this.edit((c) -> c.pole = b.getValue()));

        this.poleAngle = new UISliderTrackpad((v) -> this.edit((c) -> c.poleAngle = v.floatValue()));
        this.poleAngle.angle180();
        this.poleAngle.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE);

        this.scroll.add(UI.column(
            this.enabled.marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS, this.softness),
            this.pole.marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE, this.poleAngle)
        ));

        this.display();
    }

    private void display()
    {
        IKControl c = this.keyframe.getValue();

        if (c == null)
        {
            c = IKControl.DEFAULT;
        }

        this.syncing = true;

        try
        {
            this.enabled.setValue(c.enabled);
            this.weight.setValue(c.weight);
            this.softness.setValue(c.softness);
            this.pole.setValue(c.pole);
            this.poleAngle.setValue(c.poleAngle);
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(Consumer<IKControl> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            IKControl c = (IKControl) selected.getValue();

            if (c == null)
            {
                return;
            }

            selected.preNotify();
            consumer.accept(c);
            selected.postNotify();
        });
    }
}
