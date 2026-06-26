package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.DollyZoomClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
public class UIDollyZoomClip extends UIClip<DollyZoomClip>
{
    public UITrackpad focus;

    public UIDollyZoomClip(DollyZoomClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.focus = new UITrackpad((value) -> this.clip.focus.set(value.floatValue()));
        this.focus.tooltip(UIKeys.CAMERA_PANELS_FOCUS_DISTANCE);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:dolly_zoom"), this.focus));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.focus.setValue(this.clip.focus.get());
    }
}