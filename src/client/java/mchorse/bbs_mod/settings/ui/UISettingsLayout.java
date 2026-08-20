package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The values that don't fit "one labelled row per value" — a resolution reads
 * as one strip rather than as two labelled lines, and a path needs the whole
 * width. Rows are keyed by the value they start at, so a page that declares
 * nothing here keeps the plain layout.
 */
public class UISettingsLayout
{
    private static Map<BaseValue, IValueRow> rows;

    /**
     * The row that starts at this value, or null when the value draws itself.
     */
    public static IValueRow getRow(BaseValue value)
    {
        build();

        return rows.get(value);
    }

    /**
     * Built on first use rather than in a static block, since the values it is
     * keyed by only exist once the settings have been registered.
     */
    private static void build()
    {
        if (rows != null)
        {
            return;
        }

        rows = new HashMap<>();

        register(new UIResolutionRow(BBSSettings.videoWidth, BBSSettings.videoHeight, true));
        register(new UIResolutionRow(BBSSettings.editorPreviewCustomWidth, BBSSettings.editorPreviewCustomHeight, false));
        register(new UIExportPathRow(BBSSettings.videoExportPath));
        register(new UIEncoderPathRow(BBSSettings.videoEncoderPath));
    }

    private static void register(IValueRow row)
    {
        rows.put(row.getValues().get(0), row);
    }

    /**
     * A row drawing more than one setting. The page hands it the element it is
     * being built into (the settings panel itself), so a row can ask for a
     * rebuild after it changed values other than its own.
     */
    public interface IValueRow
    {
        /**
         * Every value this row draws, the first one being the one it is keyed
         * by. The page skips the rest.
         */
        public List<BaseValue> getValues();

        public List<UIElement> create(UIElement ui);
    }
}
