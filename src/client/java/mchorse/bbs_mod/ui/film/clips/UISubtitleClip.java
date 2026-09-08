package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.io.File;

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
    public UIButton pickFont;
    public UIIcon openFontFolder;
    public UITrackpad fontSize;
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

        this.placement = this.placement(this.clip.placement, new Placement(SubtitleClip.DEFAULT_SCALE));

        this.color = this.color(this.clip.color).withAlpha();
        this.textShadow = this.toggle(UIKeys.CAMERA_PANELS_SUBTITLE_TEXT_SHADOW, this.clip.textShadow);

        this.background = this.color(this.clip.background).withAlpha();
        this.backgroundOffset = this.trackpad(this.clip.backgroundOffset);
        this.shadow = this.trackpad(this.clip.shadow).limit(0);
        this.shadowOpaque = this.toggle(UIKeys.CAMERA_PANELS_SUBTITLE_OPAQUE, this.clip.shadowOpaque);

        this.transform = this.transform(this.clip.transform);

        this.pickFont = new UIButton(UIKeys.CAMERA_PANELS_SUBTITLE_FONT_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.CAMERA_PANELS_SUBTITLE_FONT_PICK, FontManager.getFontLinks(), (l) ->
            {
                this.editor.editMultiple(this.clip.font, (value) -> value.set(l));
            });

            UIOverlay.addOverlay(this.getContext(), panel.set(this.clip.font.get()));
        });
        this.openFontFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            File folder = FontManager.getFolder();

            folder.mkdirs();
            UIUtils.openFolder(folder);
        });
        this.fontSize = this.trackpad(this.clip.fontSize);
        this.fontSize.limit(FontManager.MIN_SIZE, FontManager.MAX_SIZE, true).tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_FONT_SIZE, Direction.BOTTOM);
        this.lineHeight = this.trackpad(this.clip.lineHeight);
        this.lineHeight.limit(0).tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_LINE_HEIGHT, Direction.BOTTOM);
        this.maxWidth = this.trackpad(this.clip.maxWidth);
        this.maxWidth.limit(0).tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_MAX_WIDTH, Direction.BOTTOM);
        this.pickImage = new UIButton(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE_PICK, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.image.get(), (l) -> this.editor.editMultiple(this.clip.image, (value) -> value.set(l)));
        });
        this.imageRight = this.toggle(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE_RIGHT, this.clip.imageRight);
        this.imageScale = this.trackpad(this.clip.imageScale).limit(0);
        this.imageScale.tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE_SIZE, Direction.BOTTOM);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_TEXT, this.color, this.textShadow));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_FONT, UI.row(this.pickFont, this.openFontFolder), this.fontSize));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_PLACEMENT, this.placement.fields()));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_BACKGROUND, this.background, this.backgroundOffset));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_SHADOW, this.shadow, this.shadowOpaque));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_TRANSFORM, this.transform));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_CONSTRAINT, UI.row(this.lineHeight, this.maxWidth)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_IMAGE, this.pickImage, this.imageRight, this.imageScale));
    }
}
