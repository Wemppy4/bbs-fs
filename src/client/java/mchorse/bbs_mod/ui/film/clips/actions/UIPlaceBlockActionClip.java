package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.blocks.PlaceBlockActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIBlockStateEditor;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;import mchorse.bbs_mod.ui.utils.UI;

public class UIPlaceBlockActionClip extends UIActionClip<PlaceBlockActionClip>
{
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad z;
    public UIToggle drop;
    public UIBlockStateEditor blockState;

    public UIPlaceBlockActionClip(PlaceBlockActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.x = new UITrackpad((v) -> this.editor.editMultiple(this.clip.x, (x) -> x.set(v.intValue())));
        this.x.integer();
        this.y = new UITrackpad((v) -> this.editor.editMultiple(this.clip.y, (x) -> x.set(v.intValue())));
        this.y.integer();
        this.z = new UITrackpad((v) -> this.editor.editMultiple(this.clip.z, (x) -> x.set(v.intValue())));
        this.z.integer();
        this.drop = new UIToggle(UIKeys.ACTIONS_BLOCK_DROP, (b) -> this.editor.editMultiple(this.clip.drop, (drop) -> drop.set(b.getValue())));
        this.blockState = new UIBlockStateEditor((state) -> this.editor.editMultiple(this.clip.state, (x) -> x.set(state)));

        this.addBlockPositionContext(
            this.x, this.y, this.z,
            () -> this.clip.x.get(), () -> this.clip.y.get(), () -> this.clip.z.get(),
            (value) -> this.editor.editMultiple(this.clip.x, (x) -> x.set(value)),
            (value) -> this.editor.editMultiple(this.clip.y, (y) -> y.set(value)),
            (value) -> this.editor.editMultiple(this.clip.z, (z) -> z.set(value))
        );
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_BLOCK_POSITION, UI.row(this.x, this.y, this.z), this.drop));
        this.panels.add(this.section(UIKeys.ACTIONS_BLOCK_STATE, this.blockState));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.x.setValue(this.clip.x.get());
        this.y.setValue(this.clip.y.get());
        this.z.setValue(this.clip.z.get());
        this.drop.setValue(this.clip.drop.get());
        this.blockState.setBlockState(this.clip.state.get());
    }
}