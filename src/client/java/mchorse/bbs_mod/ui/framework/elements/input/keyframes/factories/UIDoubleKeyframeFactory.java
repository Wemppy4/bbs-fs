package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIDoubleKeyframeFactory extends UINumericKeyframeFactory<Double>
{
    public UIDoubleKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
    }

    @Override
    protected double getNumericValue(Double value)
    {
        return value;
    }

    @Override
    protected void setKeyframeValue(Keyframe<Double> keyframe, double value)
    {
        keyframe.setValue(value);
    }
}
