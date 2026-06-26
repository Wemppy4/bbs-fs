package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;

public class UIShakeClip extends UIClip<ShakeClip>
{
    public UITrackpad shake;
    public UITrackpad shakeAmount;
    public UIBitToggle active;

    public UIShakeClip(ShakeClip modifier, IUIClipsDelegate editor)
    {
        super(modifier, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.shake = new UITrackpad((value) -> this.clip.shake.set(value.floatValue()));
        this.shake.tooltip(UIKeys.CAMERA_PANELS_SHAKE, Direction.BOTTOM);

        this.shakeAmount = new UITrackpad((value) -> this.clip.shakeAmount.set(value.floatValue()));
        this.shakeAmount.tooltip(UIKeys.CAMERA_PANELS_SHAKE_AMOUNT, Direction.BOTTOM);

        this.active = new UIBitToggle((value) -> this.clip.active.set(value)).all();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:shake"), UI.row(UIConstants.MARGIN, 0, 20, this.shake, this.shakeAmount)).marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.active);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.shake.setValue(this.clip.shake.get());
        this.shakeAmount.setValue(this.clip.shakeAmount.get());
        this.active.setValue(this.clip.active.get());
    }
}