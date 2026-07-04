package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Editor for the {@code physics} keyframe track: pick a chain (by root bone) and
 * keyframe its scalars (weight, gravity, damping, stiffness, enabled), layered over
 * the form's physics config at playback. Mirrors {@link UIIKKeyframeFactory} but
 * lists physics chains instead of IK ones; the owning form is read from the sheet
 * (the physics track is not a form property).
 */
public class UIPhysicsKeyframeFactory extends UIKeyframeFactory<PhysicsControls>
{
    public UIStringList chains;
    public UITrackpad weight;
    public UITrackpad gravity;
    public UITrackpad damping;
    public UITrackpad stiffness;
    public UIToggle enabled;

    private ModelForm form;
    private String selected = "";
    private boolean syncing;

    public UIPhysicsKeyframeFactory(Keyframe<PhysicsControls> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

        if (sheet != null && sheet.form instanceof ModelForm modelForm)
        {
            this.form = modelForm;
        }

        this.chains = new UIStringList((l) ->
        {
            this.selected = l.isEmpty() ? "" : l.get(0);
            this.display();
        });
        this.chains.background().h(UIConstants.LIST_ITEM_HEIGHT * 6);

        this.weight = new UITrackpad((v) -> this.edit((control) -> control.weight = v.floatValue()));
        this.weight.limit(0D, 1D).increment(0.1D).values(0.1D, 0.05D, 0.2D);

        this.gravity = new UITrackpad((v) -> this.edit((control) -> control.gravity = v.floatValue()));
        this.gravity.increment(0.1D).values(0.1D, 0.05D, 0.2D);

        this.damping = new UITrackpad((v) -> this.edit((control) -> control.damping = v.floatValue()));
        this.damping.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);

        this.stiffness = new UITrackpad((v) -> this.edit((control) -> control.stiffness = v.floatValue()));
        this.stiffness.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ENABLED, (b) -> this.edit((control) -> control.enabled = b.getValue()));

        this.fillChains();

        this.scroll.add(UI.column(
            this.chains,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY, this.gravity).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING, this.damping).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS, this.stiffness).marginTop(UIConstants.SECTION_GAP),
            this.enabled
        ));

        this.display();
    }

    private void fillChains()
    {
        List<String> roots = new ArrayList<>();
        ModelPhysicsConfig config = this.config();

        if (config != null && config.bones() != null)
        {
            roots.addAll(config.bones().keySet());
            Collections.sort(roots);
        }

        this.chains.setList(roots);

        if (!roots.isEmpty())
        {
            this.selected = roots.get(0);
            this.chains.setCurrentScroll(this.selected);
        }
    }

    private void display()
    {
        boolean has = !this.selected.isEmpty();

        this.weight.setEnabled(has);
        this.gravity.setEnabled(has);
        this.damping.setEnabled(has);
        this.stiffness.setEnabled(has);
        this.enabled.setEnabled(has);

        if (!has)
        {
            return;
        }

        PhysicsControl control = this.displayControl(this.selected);

        this.syncing = true;

        try
        {
            this.weight.setValue(control.weight);
            this.gravity.setValue(control.gravity);
            this.damping.setValue(control.damping);
            this.stiffness.setValue(control.stiffness);
            this.enabled.setValue(control.enabled);
        }
        finally
        {
            this.syncing = false;
        }
    }

    /** The values to show: the keyframe's own control if it already has one, otherwise the form's config (so fields don't jump to defaults before the first edit). */
    private PhysicsControl displayControl(String root)
    {
        PhysicsControls controls = this.keyframe.getValue();

        if (controls != null && controls.controls.containsKey(root))
        {
            return controls.controls.get(root);
        }

        return this.configControl(root);
    }

    private void edit(Consumer<PhysicsControl> consumer)
    {
        if (this.syncing || this.selected.isEmpty())
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            PhysicsControls controls = (PhysicsControls) selected.getValue();

            selected.preNotify();

            /* Seed a chain's control from the config the first time it is touched,
             * so an edit to one field doesn't snap the others to defaults (the
             * override REPLACES the config wholesale at playback). */
            boolean fresh = !controls.controls.containsKey(this.selected);
            PhysicsControl control = controls.get(this.selected);

            if (fresh)
            {
                control.copy(this.configControl(this.selected));
            }

            consumer.accept(control);

            selected.postNotify();
        });
    }

    private PhysicsControl configControl(String root)
    {
        PhysicsControl control = new PhysicsControl();
        ModelPhysicsConfig config = this.config();

        if (config != null && config.bones() != null)
        {
            ModelPhysicsConfig.Bone bone = config.bones().get(root);

            if (bone != null)
            {
                control.weight = bone.weight();
                control.gravity = bone.gravity();
                control.damping = bone.damping();
                control.stiffness = bone.stiffness();
            }
        }

        return control;
    }

    private ModelPhysicsConfig config()
    {
        if (this.form != null && this.form.physics.get() instanceof MapType map)
        {
            return ModelPhysicsIO.fromData(map);
        }

        return null;
    }
}
