package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.io.File;

public class UILabelFormPanel extends UIFormPanel<LabelForm>
{
    /* The three swatches stay fields: leaving the panel has to dismiss a picker left standing. */
    public UIColor color;
    public UIColor shadowColor;
    public UIColor background;

    public UILabelFormPanel(UIForm editor)
    {
        super(editor);

        UIButton pickFont = new UIButton(UIKeys.FORMS_EDITORS_LABEL_FONT_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.FORMS_EDITORS_LABEL_FONT_PICK, FontManager.getFontLinks(), (l) -> this.form.font.set(l));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.font.get()));
        });
        UIIcon openFontFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            File folder = FontManager.getFolder();

            folder.mkdirs();
            UIUtils.openFolder(folder);
        });

        UITrackpad fontSize = UIValues.trackpad(() -> this.form.fontSize);
        UITrackpad lineHeight = UIValues.trackpad(() -> this.form.lineHeight);
        UITrackpad max = UIValues.trackpad(() -> this.form.max);
        UITrackpad anchorX = UIValues.trackpad(() -> this.form.anchorX);
        UITrackpad anchorY = UIValues.trackpad(() -> this.form.anchorY);
        UITrackpad shadowX = UIValues.trackpad(() -> this.form.shadowX);
        UITrackpad shadowY = UIValues.trackpad(() -> this.form.shadowY);

        fontSize.limit(FontManager.MIN_SIZE, FontManager.MAX_SIZE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_FONT_SIZE);
        lineHeight.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_LINE_HEIGHT);
        max.limit(-1, Integer.MAX_VALUE, true).increment(10);
        anchorX.values(0.01F);
        anchorY.values(0.01F);
        shadowX.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);
        shadowY.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);

        this.color = UIValues.color(() -> this.form.color).withAlpha();
        this.shadowColor = UIValues.color(() -> this.form.shadowColor).withAlpha();
        this.background = UIValues.color(() -> this.form.background).withAlpha();

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_LABEL),
            UIValues.textbox(10000, () -> this.form.text),
            UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard),
            this.color,
            max
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_FONT).marginTop(UIConstants.SECTION_GAP),
            UI.row(pickFont, openFontFolder),
            UI.row(fontSize, lineHeight)
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_ANCHOR).marginTop(UIConstants.SECTION_GAP),
            UI.row(anchorX, anchorY),
            UIValues.toggle(UIKeys.FORMS_EDITORS_LABEL_ANCHOR_LINES, () -> this.form.anchorLines)
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_SHADOW_OFFSET).marginTop(UIConstants.SECTION_GAP),
            UI.row(shadowX, shadowY)
        );

        this.options.add(UI.labelRow(UIKeys.FORMS_EDITORS_LABEL_SHADOW_COLOR, this.shadowColor).marginTop(UIConstants.SECTION_GAP));

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_BACKGROUND).marginTop(UIConstants.SECTION_GAP),
            this.background,
            UIValues.trackpad(() -> this.form.offset)
        );
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        this.color.picker.removeFromParent();
        this.shadowColor.picker.removeFromParent();
        this.background.picker.removeFromParent();
    }
}
