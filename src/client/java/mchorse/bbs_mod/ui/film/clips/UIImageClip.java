package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UIImageClip extends UIClip<ImageClip>
{
    public UIButton pickTexture;
    public UIToggle fullscreen;
    public UIToggle smooth;
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad windowX;
    public UITrackpad windowY;
    public UITrackpad anchorX;
    public UITrackpad anchorY;
    public UITrackpad scale;
    public UIColor color;
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
        this.fullscreen = new UIToggle(UIKeys.CAMERA_PANELS_IMAGE_FULLSCREEN, (b) -> this.editor.editMultiple(this.clip.fullscreen, (value) ->
        {
            value.set(b.getValue());
        }));
        this.smooth = new UIToggle(UIKeys.CAMERA_PANELS_IMAGE_SMOOTH, (b) -> this.editor.editMultiple(this.clip.smooth, (value) ->
        {
            value.set(b.getValue());
        }));

        this.x = new UITrackpad((v) -> this.editor.editMultiple(this.clip.x, (value) ->
        {
            value.set(v.intValue());
        }));
        this.x.integer();
        this.y = new UITrackpad((v) -> this.editor.editMultiple(this.clip.y, (value) ->
        {
            value.set(v.intValue());
        }));
        this.y.integer();

        this.windowX = new UITrackpad((v) -> this.editor.editMultiple(this.clip.windowX, (value) ->
        {
            value.set(v.floatValue());
        }));
        this.windowY = new UITrackpad((v) -> this.editor.editMultiple(this.clip.windowY, (value) ->
        {
            value.set(v.floatValue());
        }));

        this.anchorX = new UITrackpad((v) -> this.editor.editMultiple(this.clip.anchorX, (value) ->
        {
            value.set(v.floatValue());
        }));
        this.anchorY = new UITrackpad((v) -> this.editor.editMultiple(this.clip.anchorY, (value) ->
        {
            value.set(v.floatValue());
        }));

        this.scale = new UITrackpad((v) -> this.editor.editMultiple(this.clip.scale, (value) ->
        {
            value.set(v.floatValue());
        }));
        this.scale.limit(0);
        this.color = new UIColor((c) -> this.editor.editMultiple(this.clip.color, (value) ->
        {
            value.set(c);
        })).withAlpha();

        this.transform = new UIPropTransform().callbacks(
            () -> this.editor.editMultiple(this.clip.transform, IValueNotifier::preNotify),
            () -> this.editor.editMultiple(this.clip.transform, (t) ->
            {
                t.set(this.transform.getTransform().copy());
                t.postNotify();
            })
        );
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_IMAGE, this.pickTexture, this.fullscreen, this.smooth));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_SCALE, this.scale, this.color));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_OFFSET, UI.row(this.x, this.y)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_WINDOW, UI.row(this.windowX, this.windowY)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_ANCHOR, UI.row(this.anchorX, this.anchorY)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_TRANSFORM, this.transform));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.fullscreen.setValue(this.clip.fullscreen.get());
        this.smooth.setValue(this.clip.smooth.get());
        this.x.setValue(this.clip.x.get());
        this.y.setValue(this.clip.y.get());
        this.windowX.setValue(this.clip.windowX.get());
        this.windowY.setValue(this.clip.windowY.get());
        this.anchorX.setValue(this.clip.anchorX.get());
        this.anchorY.setValue(this.clip.anchorY.get());
        this.scale.setValue(this.clip.scale.get());
        this.color.setColor(this.clip.color.get());
        this.transform.setTransform(this.clip.transform.get());
    }
}
