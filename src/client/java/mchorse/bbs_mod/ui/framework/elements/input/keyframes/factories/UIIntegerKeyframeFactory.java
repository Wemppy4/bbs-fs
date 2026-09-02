package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIIntegerKeyframeFactory extends UINumericKeyframeFactory<Integer>
{
    public UIIntegerKeyframeFactory(Keyframe<Integer> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
        this.value.integer();
    }

    @Override
    protected double getNumericValue(Integer value)
    {
        return value;
    }

    @Override
    protected void setKeyframeValue(Keyframe<Integer> keyframe, double value)
    {
        keyframe.setValue((int) value);
    }
}
