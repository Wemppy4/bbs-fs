package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;

public class UIImageClip extends UIClip<ImageClip>
{
    public UIButton pickTexture;
    public UIToggle fullscreen;
    public UIToggle smooth;
    public UIColor color;
    public UIPlacement placement;
    public UIPropTransform transform;

    public UIImageClip(ImageClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickTexture = new UIButton(UIKeys.CAMERA_PANELS_IMAGE_PICK, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.texture.get(), (l) -> this.editor.editMultiple(this.clip.texture, (value) -> value.set(l)));
        });
        this.fullscreen = this.toggle(UIKeys.CAMERA_PANELS_IMAGE_FULLSCREEN, this.clip.fullscreen);
        this.smooth = this.toggle(UIKeys.CAMERA_PANELS_IMAGE_SMOOTH, this.clip.smooth);
        this.color = this.color(this.clip.color).withAlpha();
        this.placement = this.placement(this.clip.placement, new Placement());
        this.transform = this.transform(this.clip.transform);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_IMAGE, this.pickTexture, this.fullscreen, this.smooth, this.color));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_PLACEMENT, this.placement.fields()));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_TRANSFORM, this.transform));
    }
}
