package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.blocks.BreakBlockActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;import mchorse.bbs_mod.ui.utils.UI;

public class UIBreakBlockActionClip extends UIActionClip<BreakBlockActionClip>
{
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad z;
    public UITrackpad progress;

    public UIBreakBlockActionClip(BreakBlockActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.x = this.trackpad(this.clip.x);
        this.y = this.trackpad(this.clip.y);
        this.z = this.trackpad(this.clip.z);
        this.progress = this.trackpad(this.clip.progress);

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

        this.panels.add(this.section(UIKeys.ACTIONS_BLOCK_POSITION, UI.row(this.x, this.y, this.z)));
        this.panels.add(this.section(UIKeys.ACTIONS_BLOCK_PROGRESS, this.progress));
    }
}
