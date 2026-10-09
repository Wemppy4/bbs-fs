package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;

import java.util.function.Consumer;

/** The shake's animatable field, built the same for the shake tab and the shake track's keyframes. */
public class UIShakeControlFields
{
    public final UISliderTrackpad strength;

    public UIShakeControlFields(Consumer<Consumer<ShakeControl>> edit)
    {
        this.strength = new UISliderTrackpad(v -> edit.accept(c -> c.strength = v.floatValue()));
        this.strength.normalized().tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_STRENGTH);
    }
}
