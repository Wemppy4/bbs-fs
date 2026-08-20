package mchorse.bbs_mod.ui.framework.elements.input.keyframes.overlays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Renaming and recolouring of a single timeline track. The edit is keyed by the track's kind
 * ({@link UIKeyframeSheet#getFilterKey()}) and stored in the settings, so it survives the session
 * and shows up on that track in every film.
 */
public class UITrackStyleOverlayPanel extends UIOverlayPanel
{
    public UITextbox name;
    public UIColor color;

    private final UIKeyframeSheet sheet;
    private final Runnable callback;

    public UITrackStyleOverlayPanel(UIKeyframeSheet sheet, Runnable callback)
    {
        super(L10n.lang("bbs.ui.keyframes.track_style.title"));

        this.sheet = sheet;
        this.callback = callback;

        String key = sheet.getFilterKey();

        this.name = new UITextbox(100, (text) ->
        {
            BBSSettings.trackStyles.setName(key, text);
            this.update();
        });
        this.name.placeholder(sheet.defaultTitle);
        this.name.setText(BBSSettings.trackStyles.name(key, ""));

        this.color = new UIColor((value) ->
        {
            BBSSettings.trackStyles.setColor(key, value);
            this.update();
        });
        this.color.setColor(sheet.color);

        UIButton reset = new UIButton(L10n.lang("bbs.ui.keyframes.track_style.reset"), (b) -> this.reset());

        UIElement column = UI.column(
            UI.label(L10n.lang("bbs.ui.keyframes.track_style.key").format(key)).color(Colors.LIGHTER_GRAY),
            UI.label(L10n.lang("bbs.ui.keyframes.track_style.name")).marginTop(6),
            this.name,
            UI.label(L10n.lang("bbs.ui.keyframes.track_style.color")).marginTop(6),
            this.color,
            reset.marginTop(10)
        );

        column.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -12);

        this.content.add(column);

        /* The picker lives next to the overlay, not inside it, so it would outlive the panel */
        this.onClose((e) -> this.color.picker.removeFromParent());
    }

    private void reset()
    {
        BBSSettings.trackStyles.reset(this.sheet.getFilterKey());

        this.name.setText("");
        /* setColor() doesn't fire the picker's callback, so this can't write the override back */
        this.color.setColor(this.sheet.defaultColor);

        this.update();
    }

    private void update()
    {
        this.sheet.applyStyle();

        if (this.callback != null)
        {
            this.callback.run();
        }
    }
}
