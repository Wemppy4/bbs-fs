package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Width and height as a single strip: two fields carrying their names in the
 * tooltip instead of a label, the swap between them, and an icon on either side
 * marking which one is which. A resolution is read as a pair, so it is entered
 * as a pair.
 */
public class UIResolutionRow implements UISettingsLayout.IValueRow
{
    private final ValueInt width;
    private final ValueInt height;

    /**
     * Whether the fields offer the video resolution presets on right click —
     * only the exported video has them, a preview size doesn't.
     */
    private final boolean presets;

    public UIResolutionRow(ValueInt width, ValueInt height, boolean presets)
    {
        this.width = width;
        this.height = height;
        this.presets = presets;
    }

    @Override
    public List<BaseValue> getValues()
    {
        return Arrays.asList(this.width, this.height);
    }

    @Override
    public List<UIElement> create(UIElement ui)
    {
        UINumericInput<?> width = UIValueFactory.intUI(this.width, null);
        UINumericInput<?> height = UIValueFactory.intUI(this.height, null);
        UIIcon swap = new UIIcon(Icons.REFRESH, (b) -> this.swap(ui));

        width.tooltip(this.tooltip(this.width));
        height.tooltip(this.tooltip(this.height));
        swap.tooltip(UIKeys.VIDEO_SETTINGS_SWAP);

        /* As wide as the icon and as tall as the fields it stands between - a
         * field is CONTROL_HEIGHT tall, not the 20 its row slot gets, and an icon
         * draws itself centered in its own area */
        swap.wh(Icons.REFRESH.w, UIConstants.CONTROL_HEIGHT);

        if (this.presets && ui instanceof UISettingsOverlayPanel panel)
        {
            width.context(panel::addVideoPresets);
            height.context(panel::addVideoPresets);
        }

        UIElement row = new UIElement();

        row.row(4).height(UIConstants.CONTROL_HEIGHT);
        row.add(new UIIconMark(Icons.HORIZONTAL), width, swap, height, new UIIconMark(Icons.VERTICAL));

        return Collections.singletonList(row);
    }

    /**
     * Without a label of its own a field has to say what it is, so the tooltip
     * carries the name with the description under it.
     */
    private IKey tooltip(ValueInt value)
    {
        return IKey.comp(Arrays.asList(
            L10n.lang(UIValueFactory.getValueLabelKey(value)),
            IKey.constant("\n"),
            L10n.lang(UIValueFactory.getValueCommentKey(value))
        ));
    }

    private void swap(UIElement ui)
    {
        int width = this.width.get();

        this.width.set(this.height.get());
        this.height.set(width);

        if (ui instanceof UISettingsOverlayPanel panel)
        {
            panel.refresh();
        }
    }

    /**
     * An icon standing in for a label — it marks which field it sits next to
     * and nothing more, so it doesn't take a click.
     */
    public static class UIIconMark extends UIElement
    {
        private final Icon icon;

        public UIIconMark(Icon icon)
        {
            this.icon = icon;

            this.wh(icon.w, UIConstants.CONTROL_HEIGHT);
        }

        @Override
        public void render(UIContext context)
        {
            context.batcher.icon(this.icon, Colors.WHITE, this.area.mx(), this.area.my(), 0.5F, 0.5F);

            super.render(context);
        }
    }
}
