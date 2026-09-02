package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.DragClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
public class UIDragClip extends UIClip<DragClip>
{
    public UIToggle deterministic;
    public UISliderTrackpad factor;
    public UITrackpad rate;
    public UIBitToggle active;

    public UIDragClip(DragClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.deterministic = this.bind(new UIToggle(UIKeys.CAMERA_PANELS_DETERMINISTIC, (b) ->
        {
            this.clip.deterministic.set(b.getValue());
            this.clip.resetCache();
        }), () -> this.deterministic.setValue(this.clip.deterministic.get()));
        this.deterministic.tooltip(UIKeys.CAMERA_PANELS_DETERMINISTIC_TOOLTIP);

        this.factor = this.bind(new UISliderTrackpad((value) -> this.clip.factor.set(value.floatValue())), () -> this.factor.setValue(this.clip.factor.get()));
        this.factor.limit(this.clip.factor).values(0.05F, 0.01F, 0.2F).increment(0.1F).tooltip(UIKeys.CAMERA_PANELS_FACTOR_TOOLTIP);

        this.rate = this.trackpad(this.clip.rate);
        this.rate.limit(this.clip.rate).tooltip(UIKeys.CAMERA_PANELS_RATE_TOOLTIP);

        this.active = this.bind(new UIBitToggle((value) ->
        {
            this.clip.active.set(value);
            this.clip.resetCache();
        }).all(), () -> this.active.setValue(this.clip.active.get()));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:drag"), this.deterministic));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_FACTOR, this.factor, this.rate), this.active);
    }
}