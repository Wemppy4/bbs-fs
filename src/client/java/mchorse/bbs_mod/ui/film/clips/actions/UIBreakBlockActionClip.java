package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.blocks.BreakBlockActionClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;

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

        this.x = new UITrackpad((v) -> this.editor.editMultiple(this.clip.x, (x) -> x.set(v.intValue())));
        this.x.integer();
        this.y = new UITrackpad((v) -> this.editor.editMultiple(this.clip.y, (y) -> y.set(v.intValue())));
        this.y.integer();
        this.z = new UITrackpad((v) -> this.editor.editMultiple(this.clip.z, (z) -> z.set(v.intValue())));
        this.z.integer();
        this.progress = new UITrackpad((v) -> this.editor.editMultiple(this.clip.progress, (progress) -> progress.set(v.intValue())));
        this.progress.integer();

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

        this.panels.add(this.section(UIKeys.ACTIONS_BLOCK_POSITION, UI.row(this.x, this.y, this.z)).marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.section(UIKeys.ACTIONS_BLOCK_PROGRESS, this.progress).marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.x.setValue(this.clip.x.get());
        this.y.setValue(this.clip.y.get());
        this.z.setValue(this.clip.z.get());
        this.progress.setValue(this.clip.progress.get());
    }
}
