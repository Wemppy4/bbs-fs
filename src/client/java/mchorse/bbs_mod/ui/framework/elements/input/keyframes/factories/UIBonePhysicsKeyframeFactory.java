package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.physics.PhysicsControl;
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
 * Editor for one bone's physics keyframe: the chain's animatable scalars (enabled, weight,
 * gravity, damping, stiffness), laid out the way the form editor's physics tab lays them out.
 * The value is the bone property's own type, so what this edits is exactly what the form stores
 * statically. Replaced the whole-form editor that listed every chain in one keyframe.
 */
public class UIBonePhysicsKeyframeFactory extends UIKeyframeFactory<PhysicsControl>
{
    public UIToggle enabled;
    public UISliderTrackpad weight;
    public UISliderTrackpad gravity;
    public UISliderTrackpad damping;
    public UISliderTrackpad stiffness;

    private boolean syncing;

    public UIBonePhysicsKeyframeFactory(Keyframe<PhysicsControl> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ENABLED, (b) -> this.edit((c) -> c.enabled = b.getValue()));

        this.weight = new UISliderTrackpad((v) -> this.edit((c) -> c.weight = v.floatValue()));
        this.weight.normalized();
        this.weight.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT);

        this.gravity = new UISliderTrackpad((v) -> this.edit((c) -> c.gravity = v.floatValue()));
        this.gravity.limit(-10D, 10D).increment(0.25D).values(0.1D, 0.01D, 0.5D);
        this.gravity.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY);

        this.damping = new UISliderTrackpad((v) -> this.edit((c) -> c.damping = v.floatValue()));
        this.damping.normalized();
        this.damping.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING);

        this.stiffness = new UISliderTrackpad((v) -> this.edit((c) -> c.stiffness = v.floatValue()));
        this.stiffness.normalized();
        this.stiffness.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS);

        this.scroll.add(UI.column(
            this.enabled.marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY, this.gravity),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING, this.damping),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS, this.stiffness)
        ));

        this.display();
    }

    private void display()
    {
        PhysicsControl c = this.keyframe.getValue();

        if (c == null)
        {
            c = PhysicsControl.DEFAULT;
        }

        this.syncing = true;

        try
        {
            this.enabled.setValue(c.enabled);
            this.weight.setValue(c.weight);
            this.gravity.setValue(c.gravity);
            this.damping.setValue(c.damping);
            this.stiffness.setValue(c.stiffness);
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(Consumer<PhysicsControl> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            PhysicsControl c = (PhysicsControl) selected.getValue();

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
