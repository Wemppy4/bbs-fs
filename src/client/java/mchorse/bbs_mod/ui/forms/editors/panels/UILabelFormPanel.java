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
import mchorse.bbs_mod.utils.colors.Color;

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

        this.text = new UITextbox(10000, (t) -> this.form.text.set(t));
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
        this.fontSize = new UITrackpad((value) -> this.form.fontSize.set(value.intValue()));
        this.fontSize.limit(FontManager.MIN_SIZE, FontManager.MAX_SIZE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_FONT_SIZE);
        this.lineHeight = new UITrackpad((value) -> this.form.lineHeight.set(value.intValue()));
        this.lineHeight.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_LINE_HEIGHT);
        this.billboard = new UIToggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, (b) -> this.form.billboard.set(b.getValue()));
        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.max = new UITrackpad((value) -> this.form.max.set(value.intValue()));
        this.max.limit(-1, Integer.MAX_VALUE, true).increment(10);
        this.anchorX = new UITrackpad((value) -> this.form.anchorX.set(value.floatValue()));
        this.anchorX.values(0.01F);
        this.anchorY = new UITrackpad((value) -> this.form.anchorY.set(value.floatValue()));
        this.anchorY.values(0.01F);
        this.anchorLines = new UIToggle(UIKeys.FORMS_EDITORS_LABEL_ANCHOR_LINES, (value) -> this.form.anchorLines.set(value.getValue()));

        this.shadowX = new UITrackpad((value) -> this.form.shadowX.set(value.floatValue()));
        this.shadowX.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);
        this.shadowY = new UITrackpad((value) -> this.form.shadowY.set(value.floatValue()));
        this.shadowY.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);
        this.shadowColor = new UIColor((value) -> this.form.shadowColor.set(Color.rgba(value))).withAlpha();

        this.background = new UIColor((value) -> this.form.background.set(Color.rgba(value))).withAlpha();
        this.offset = new UITrackpad((value) -> this.form.offset.set(value.floatValue()));

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