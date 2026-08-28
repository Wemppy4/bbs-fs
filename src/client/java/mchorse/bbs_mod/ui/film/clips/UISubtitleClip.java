package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;

public class UISubtitleClip extends UIClip<SubtitleClip>
{
    public UIPlacement placement;
    public UIColor color;
    public UIToggle textShadow;
    public UIColor background;
    public UITrackpad backgroundOffset;
    public UITrackpad shadow;
    public UIToggle shadowOpaque;
    public UIPropTransform transform;
    public UITrackpad lineHeight;
    public UITrackpad maxWidth;
    public UIButton pickImage;
    public UIToggle imageRight;
    public UITrackpad imageScale;

    public UISubtitleClip(SubtitleClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.placement = new UIPlacement((p) -> this.editor.editMultiple(this.clip.placement, (value) ->
        {
            value.set(p.copy());
        }));

        this.color = new UIColor((c) -> this.editor.editMultiple(this.clip.color, (value) ->
        {
            value.set(c);
        }));
        this.color.withAlpha();
        this.textShadow = new UIToggle(UIKeys.CAMERA_PANELS_SUBTITLE_TEXT_SHADOW, (b) -> this.editor.editMultiple(this.clip.textShadow, (value) ->
        {
            value.set(b.getValue());
        }));

        this.background = new UIColor((c) -> this.editor.editMultiple(this.clip.background, (value) ->
        {
            value.set(c);
        })).withAlpha();
        this.backgroundOffset = new UITrackpad((v) -> this.editor.editMultiple(this.clip.backgroundOffset, (value) ->
        {
            value.set(v.floatValue());
        }));
        this.shadow = new UITrackpad((v) -> this.editor.editMultiple(this.clip.shadow, (value) ->
        {
            value.set(v.floatValue());
        })).limit(0);
        this.shadowOpaque = new UIToggle(UIKeys.CAMERA_PANELS_SUBTITLE_OPAQUE, (b) -> this.editor.editMultiple(this.clip.shadowOpaque, (value) ->
        {
            value.set(b.getValue());
        }));

        this.transform = new UIPropTransform().callbacks(
            () -> this.editor.editMultiple(this.clip.transform, IValueNotifier::preNotify),
            () -> this.editor.editMultiple(this.clip.transform, (t) ->
            {
                t.set(this.transform.getTransform().copy());
                t.postNotify();
            })
        );

        this.lineHeight = new UITrackpad((v) -> this.editor.editMultiple(this.clip.lineHeight, (value) ->
        {
            value.set(v.intValue());
        }));
        this.lineHeight.limit(0).integer().tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_LINE_HEIGHT, Direction.BOTTOM);
        this.maxWidth = new UITrackpad((v) -> this.editor.editMultiple(this.clip.maxWidth, (value) ->
        {
            value.set(v.intValue());
        }));
        this.maxWidth.limit(0).integer().tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_MAX_WIDTH, Direction.BOTTOM);
        this.pickImage = new UIButton(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE_PICK, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.image.get(), (l) -> this.editor.editMultiple(this.clip.image, (value) -> value.set(l)));
        });
        this.imageRight = new UIToggle(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE_RIGHT, (b) -> this.editor.editMultiple(this.clip.imageRight, (value) -> value.set(b.getValue())));
        this.imageScale = new UITrackpad((v) -> this.editor.editMultiple(this.clip.imageScale, (value) -> value.set(v.floatValue())));
        this.imageScale.limit(0);
        this.imageScale.tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE_SIZE, Direction.BOTTOM);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_TEXT, this.color, this.textShadow));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_PLACEMENT, this.placement.fields()));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_BACKGROUND, this.background, this.backgroundOffset));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_SHADOW, this.shadow, this.shadowOpaque));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_TRANSFORM, this.transform));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_CONSTRAINT, UI.row(this.lineHeight, this.maxWidth)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE, this.pickImage, this.imageRight, this.imageScale));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.placement.setPlacement(this.clip.placement.get());
        this.color.setColor(this.clip.color.get());
        this.textShadow.setValue(this.clip.textShadow.get());
        this.background.setColor(this.clip.background.get());
        this.backgroundOffset.setValue(this.clip.backgroundOffset.get());
        this.shadow.setValue(this.clip.shadow.get());
        this.shadowOpaque.setValue(this.clip.shadowOpaque.get());
        this.transform.setTransform(this.clip.transform.get());
        this.lineHeight.setValue(this.clip.lineHeight.get());
        this.maxWidth.setValue(this.clip.maxWidth.get());
        this.imageRight.setValue(this.clip.imageRight.get());
        this.imageScale.setValue(this.clip.imageScale.get());
    }
}
