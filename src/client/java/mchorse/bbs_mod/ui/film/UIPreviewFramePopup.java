package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIPopup;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.framework.elements.utils.UITextTab;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UIConstants;

/**
 * The preview's frame at hand, from the "Frame" icon of its bar: how sharp the preview renders,
 * whether it renders at the export's size, and the framing guides. Everything here is a setting
 * of the viewport category, changed in place.
 */
public class UIPreviewFramePopup extends UIPopup
{
    /** The resolution multipliers the strip offers; the setting itself is a slider in between. */
    private static final float[] SCALES = {0.5F, 1F, 2F, 3F};

    private final UIWorldPreview preview;
    private final UIToggle exportSize;

    public UIPreviewFramePopup(UIWorldPreview preview)
    {
        super(null);

        this.preview = preview;

        UITabStrip scale = new UITabStrip(ScrollDirection.HORIZONTAL)
        {
            @Override
            protected boolean pressTab(int index, UIContext context)
            {
                this.select(index);

                return true;
            }
        };

        /* No multiplier is marked at the export's size: there the render is the export's own */
        scale.fixed();
        scale.active(() -> BBSSettings.editorPreviewExportSize.get() ? -1 : closestScale(BBSSettings.editorPreviewResolutionScale.get()));
        scale.onSelect(this::pickScale);
        scale.tooltip(L10n.lang(UIValueFactory.getValueLabelKey(BBSSettings.editorPreviewResolutionScale)));

        int tabWidth = (WIDTH - UIConstants.SCROLL_PADDING * 2) / SCALES.length;

        for (float value : SCALES)
        {
            String label = "×" + (value == (int) value ? String.valueOf((int) value) : String.valueOf(value));

            scale.addTab(new UITextTab(IKey.constant(label))).w(tabWidth).h(UIConstants.CONTROL_HEIGHT);
        }

        scale.h(UIConstants.CONTROL_HEIGHT);

        this.exportSize = UIValueFactory.booleanUI(BBSSettings.editorPreviewExportSize, (t) -> this.preview.applySize());

        this.column.add(
            scale,
            this.exportSize,
            toggle(BBSSettings.editorRuleOfThirds),
            toggle(BBSSettings.editorCenterLines),
            toggle(BBSSettings.editorCrosshair),
            toggle(BBSSettings.editorSafeZones)
        );
    }

    /** A multiplier picked at the export's size means the preview's own size again. */
    private void pickScale(int index)
    {
        BBSSettings.editorPreviewResolutionScale.set(SCALES[index]);
        BBSSettings.editorPreviewExportSize.set(false);
        this.exportSize.setValue(false);
        this.preview.applySize();
    }

    private static UIToggle toggle(ValueBoolean value)
    {
        return UIValueFactory.booleanUI(value, null);
    }

    private static int closestScale(float value)
    {
        int closest = 0;

        for (int i = 1; i < SCALES.length; i++)
        {
            if (Math.abs(SCALES[i] - value) < Math.abs(SCALES[closest] - value))
            {
                closest = i;
            }
        }

        return closest;
    }
}
