package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.resources.Pixels;

public class UITextureExtractOverlayPanel extends UIOverlayPanel
{
    public UITrackpad frames;
    public UITrackpad frameWidth;
    public UITrackpad frameHeight;
    public UITrackpad frameStepX;
    public UITrackpad frameStepY;
    public UIButton extract;

    public UITextureExtractOverlayPanel(Link link, Pixels pixels)
    {
        super(UIKeys.TEXTURES_EXTRACT_FRAMES_TITLE);

        this.title.tooltip(UIKeys.TEXTURES_EXTRACT_FRAMES_TOOLTIP);

        this.frames = new UITrackpad(null);
        this.frames.limit(1).integer().setValue(1);
        this.frameWidth = new UITrackpad(null);
        this.frameWidth.limit(1, pixels.width).integer().setValue(pixels.width);
        this.frameHeight = new UITrackpad(null);
        this.frameHeight.limit(1, pixels.height).integer().setValue(pixels.height);
        this.frameStepX = new UITrackpad(null);
        this.frameStepX.limit(0).integer();
        this.frameStepY = new UITrackpad(null);
        this.frameStepY.limit(0).integer();
        this.extract = new UIButton(UIKeys.TEXTURES_EXTRACT_FRAMES_EXTRACT, (b) ->
        {
            UITextureManagerPanel.extractTexture(
                link, pixels,
                (int) this.frames.getValue(),
                (int) this.frameWidth.getValue(), (int) this.frameHeight.getValue(),
                (int) this.frameStepX.getValue(), (int) this.frameStepY.getValue()
            );

            this.close();
        });

        this.content.column(UIConstants.MARGIN).vertical().stretch().padding(UIConstants.SCROLL_PADDING);
        this.content.add(UI.labelRow(UIKeys.TEXTURES_EXTRACT_FRAMES_FRAMES, this.frames));
        this.content.add(UI.label(UIKeys.TEXTURES_EXTRACT_FRAMES_RESOLUTION).marginTop(UIConstants.SECTION_GAP), this.frameWidth, this.frameHeight);
        this.content.add(UI.label(UIKeys.TEXTURES_EXTRACT_FRAMES_STEP).marginTop(UIConstants.SECTION_GAP), this.frameStepX, this.frameStepY);
        this.content.add(this.extract.marginTop(UIConstants.SECTION_GAP));
    }
}