package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIBlockStateEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import net.minecraft.block.state.IBlockState;

public class UIBlockStateKeyframeFactory extends UIKeyframeFactory<IBlockState>
{
    private UIBlockStateEditor editor;

    public UIBlockStateKeyframeFactory(UITrackValue<IBlockState> track, UIKeyframes editor)
    {
        super(track, editor);

        this.editor = new UIBlockStateEditor(this::setValue);
        this.editor.setBlockState(track.getValue());

        this.scroll.add(this.editor);
    }
    @Override
    public void update()
    {
        this.editor.setBlockState(this.getDisplayValue());
    }

}