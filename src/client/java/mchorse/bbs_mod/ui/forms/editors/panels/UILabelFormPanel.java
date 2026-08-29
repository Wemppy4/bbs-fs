package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
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
    public UITextbox text;
    public UIButton pickFont;
    public UIIcon openFontFolder;
    public UITrackpad fontSize;
    public UITrackpad lineHeight;
    public UIToggle billboard;
    public UIColor color;
    public UITrackpad max;
    public UITrackpad anchorX;
    public UITrackpad anchorY;
    public UIToggle anchorLines;

    public UITrackpad shadowX;
    public UITrackpad shadowY;
    public UIColor shadowColor;

    public UIColor background;
    public UITrackpad offset;

    public UILabelFormPanel(UIForm editor)
    {
        super(editor);

        this.text = UIValues.textbox(10000, () -> this.form.text);
        this.pickFont = new UIButton(UIKeys.FORMS_EDITORS_LABEL_FONT_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.FORMS_EDITORS_LABEL_FONT_PICK, FontManager.getFontLinks(), (l) -> this.form.font.set(l));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.font.get()));
        });
        this.openFontFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            File folder = FontManager.getFolder();

            folder.mkdirs();
            UIUtils.openFolder(folder);
        });
        this.fontSize = UIValues.trackpad(() -> this.form.fontSize);
        this.fontSize.limit(FontManager.MIN_SIZE, FontManager.MAX_SIZE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_FONT_SIZE);
        this.lineHeight = UIValues.trackpad(() -> this.form.lineHeight);
        this.lineHeight.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_LINE_HEIGHT);
        this.billboard = UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard);
        this.color = UIValues.color(() -> this.form.color).withAlpha();
        this.max = UIValues.trackpad(() -> this.form.max);
        this.max.limit(-1, Integer.MAX_VALUE, true).increment(10);
        this.anchorX = UIValues.trackpad(() -> this.form.anchorX);
        this.anchorX.values(0.01F);
        this.anchorY = UIValues.trackpad(() -> this.form.anchorY);
        this.anchorY.values(0.01F);
        this.anchorLines = UIValues.toggle(UIKeys.FORMS_EDITORS_LABEL_ANCHOR_LINES, () -> this.form.anchorLines);

        this.shadowX = UIValues.trackpad(() -> this.form.shadowX);
        this.shadowX.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);
        this.shadowY = UIValues.trackpad(() -> this.form.shadowY);
        this.shadowY.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);
        this.shadowColor = UIValues.color(() -> this.form.shadowColor).withAlpha();

        this.background = UIValues.color(() -> this.form.background).withAlpha();
        this.offset = UIValues.trackpad(() -> this.form.offset);

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_LABEL), this.text, this.billboard, this.color, this.max);

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_FONT).marginTop(UIConstants.SECTION_GAP), UI.row(this.pickFont, this.openFontFolder), UI.row(this.fontSize, this.lineHeight));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_ANCHOR).marginTop(UIConstants.SECTION_GAP), UI.row(this.anchorX, this.anchorY), this.anchorLines);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_SHADOW_OFFSET).marginTop(UIConstants.SECTION_GAP), UI.row(this.shadowX, this.shadowY));
        this.options.add(UI.labelRow(UIKeys.FORMS_EDITORS_LABEL_SHADOW_COLOR, this.shadowColor).marginTop(UIConstants.SECTION_GAP));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_BACKGROUND).marginTop(UIConstants.SECTION_GAP), this.background, this.offset);
    }

    @Override
    public void startEdit(LabelForm form)
    {
        super.startEdit(form);

        this.text.setText(form.text.get());
        this.fontSize.setValue(form.fontSize.get());
        this.lineHeight.setValue(form.lineHeight.get());
        this.billboard.setValue(form.billboard.get());
        this.color.setColor(form.color.get().getARGBColor());
        this.max.setValue(form.max.get());
        this.anchorX.setValue(form.anchorX.get());
        this.anchorY.setValue(form.anchorY.get());
        this.anchorLines.setValue(form.anchorLines.get());

        this.shadowX.setValue(form.shadowX.get());
        this.shadowY.setValue(form.shadowY.get());
        this.shadowColor.setColor(form.shadowColor.get().getARGBColor());

        this.background.setColor(form.background.get().getARGBColor());
        this.offset.setValue(form.offset.get());
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