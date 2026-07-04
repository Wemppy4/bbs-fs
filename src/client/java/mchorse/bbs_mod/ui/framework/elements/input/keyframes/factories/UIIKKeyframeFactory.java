package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.ik.ModelIKConfig;
import mchorse.bbs_mod.cubic.ik.ModelIKIO;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * Editor for the {@code ik} keyframe track: pick a chain (by tip) and keyframe
 * its scalars (weight, softness, pole on/off, enabled), layered over the form's
 * IK config at playback. Mirrors {@link UIPoseKeyframeFactory} but lists chains
 * instead of bones; the owning form is read from the sheet (the IK track is not
 * a form property).
 */
public class UIIKKeyframeFactory extends UIKeyframeFactory<IKControls>
{
    public UIStringList chains;
    public UITrackpad weight;
    public UITrackpad softness;
    public UITrackpad poleAngle;
    public UIToggle enabled;
    public UIToggle pole;

    private ModelForm form;
    private String selected = "";
    private boolean syncing;

    public UIIKKeyframeFactory(Keyframe<IKControls> keyframe, UIKeyframes editor)
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

        this.softness = new UITrackpad((v) -> this.edit((control) -> control.softness = v.floatValue()));
        this.softness.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);

        this.poleAngle = new UITrackpad((v) -> this.edit((control) -> control.poleAngle = v.floatValue()));
        this.poleAngle.limit(-180D, 180D).increment(5D).values(1D, 0.5D, 5D);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_ENABLED, (b) -> this.edit((control) -> control.enabled = b.getValue()));
        this.pole = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, (b) -> this.edit((control) -> control.pole = b.getValue()));

        this.fillChains();

        this.scroll.add(UI.column(
            this.chains,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS, this.softness).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE, this.poleAngle).marginTop(UIConstants.SECTION_GAP),
            this.enabled,
            this.pole
        ));

        this.display();
    }

    private void fillChains()
    {
        List<String> tips = new ArrayList<>();
        ModelIKConfig config = this.config();

        if (config != null && config.chains() != null)
        {
            for (ModelIKConfig.Chain chain : config.chains())
            {
                if (chain != null && chain.enabled() && chain.tip() != null && !chain.tip().isEmpty())
                {
                    tips.add(chain.tip());
                }
            }
        }

        this.chains.setList(tips);

        if (!tips.isEmpty())
        {
            this.selected = tips.get(0);
            this.chains.setCurrentScroll(this.selected);
        }
    }

    private void display()
    {
        boolean has = !this.selected.isEmpty();

        this.weight.setEnabled(has);
        this.softness.setEnabled(has);
        this.poleAngle.setEnabled(has);
        this.enabled.setEnabled(has);
        this.pole.setEnabled(has);

        if (!has)
        {
            return;
        }

        IKControl control = this.displayControl(this.selected);

        this.syncing = true;

        try
        {
            this.weight.setValue(control.weight);
            this.softness.setValue(control.softness);
            this.poleAngle.setValue(control.poleAngle);
            this.enabled.setValue(control.enabled);
            this.pole.setValue(control.pole);
        }
        finally
        {
            this.syncing = false;
        }
    }

    /** The values to show: the keyframe's own control if it already has one, otherwise the form's config (so fields don't jump to defaults before the first edit). */
    private IKControl displayControl(String tip)
    {
        IKControls controls = this.keyframe.getValue();

        if (controls != null && controls.controls.containsKey(tip))
        {
            return controls.controls.get(tip);
        }

        return this.configControl(tip);
    }

    private void edit(Consumer<IKControl> consumer)
    {
        if (this.syncing || this.selected.isEmpty())
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            IKControls controls = (IKControls) selected.getValue();

            selected.preNotify();

            /* Seed a chain's control from the config the first time it is touched,
             * so an edit to one field doesn't snap the others to defaults (the
             * override REPLACES the config wholesale at playback). */
            boolean fresh = !controls.controls.containsKey(this.selected);
            IKControl control = controls.get(this.selected);

            if (fresh)
            {
                control.copy(this.configControl(this.selected));
            }

            consumer.accept(control);

            selected.postNotify();
        });
    }

    private IKControl configControl(String tip)
    {
        IKControl control = new IKControl();
        ModelIKConfig config = this.config();

        if (config != null && config.chains() != null)
        {
            for (ModelIKConfig.Chain chain : config.chains())
            {
                if (chain != null && tip.equals(chain.tip()))
                {
                    control.weight = chain.weight();
                    control.softness = chain.softness();
                    control.poleAngle = chain.poleAngle();
                    control.pole = chain.pole();
                    control.enabled = chain.enabled();

                    break;
                }
            }
        }

        return control;
    }

    private ModelIKConfig config()
    {
        if (this.form != null && this.form.ik.get() instanceof MapType map)
        {
            return ModelIKIO.fromData(map);
        }

        return null;
    }
}
