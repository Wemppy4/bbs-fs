package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class UIKeyframeSheetFilterOverlayPanel extends UIOverlayPanel
{
    private final List<UIToggle> toggles = new ArrayList<>();

    public UIKeyframeSheetFilterOverlayPanel(Set<String> disabled, Set<String> keys)
    {
        this(disabled, keys, null);
    }

    public UIKeyframeSheetFilterOverlayPanel(Set<String> disabled, Set<String> keys, Map<String, Integer> keyToColor)
    {
        this(disabled, keys, keyToColor, null);
    }

    public UIKeyframeSheetFilterOverlayPanel(Set<String> disabled, Set<String> keys, Map<String, Integer> keyToColor, Map<String, String> keyToLabel)
    {
        super(UIKeys.FILM_REPLAY_FILTER_SHEETS_TITLE);

        /* Expand the legacy "hide everything" sentinel into concrete keys so the toggles below stay consistent. */
        if (!keys.isEmpty() && disabled.remove(Form.DISABLED_ALL))
        {
            disabled.addAll(keys);
        }

        UIButton toggleAll = new UIButton(this.toggleAllLabel(disabled, keys), (b) ->
        {
            boolean enableAll = disabled.containsAll(keys);

            for (String key : keys)
            {
                if (enableAll)
                {
                    disabled.remove(key);
                }
                else
                {
                    disabled.add(key);
                }
            }

            for (UIToggle toggle : this.toggles)
            {
                toggle.setValue(enableAll);
            }

            b.label = this.toggleAllLabel(disabled, keys);
        });

        UIScrollView scrollView = UI.scrollView(4, 6);

        toggleAll.relative(this.content).x(6).y(6).w(1F, -12).h(UIConstants.CONTROL_HEIGHT);
        scrollView.relative(this.content).x(0).y(6 + UIConstants.CONTROL_HEIGHT + 4).w(1F).h(1F, -(6 + UIConstants.CONTROL_HEIGHT + 4));
        this.content.add(toggleAll, scrollView);

        for (String key : keys)
        {
            int color = keyToColor != null && keyToColor.containsKey(key) ? keyToColor.get(key) : UIReplaysEditor.getColor(key);
            String label = keyToLabel == null ? key : keyToLabel.getOrDefault(key, key);
            UIToggle toggle = new UICoolToggle(key, IKey.constant(label), color, (b) ->
            {
                if (disabled.contains(key))
                {
                    disabled.remove(key);
                }
                else
                {
                    disabled.add(key);
                }

                toggleAll.label = this.toggleAllLabel(disabled, keys);
            });

            toggle.h(UIConstants.CONTROL_HEIGHT);
            toggle.setValue(!disabled.contains(key));
            this.toggles.add(toggle);
            scrollView.add(toggle);
        }
    }

    private IKey toggleAllLabel(Set<String> disabled, Set<String> keys)
    {
        boolean allDisabled = !keys.isEmpty() && disabled.containsAll(keys);

        return allDisabled ? UIKeys.FILM_REPLAY_FILTER_SHEETS_ENABLE_ALL : UIKeys.FILM_REPLAY_FILTER_SHEETS_DISABLE_ALL;
    }

    public static class UICoolToggle extends UIToggle
    {
        private String key;
        private int color;

        public UICoolToggle(String key, IKey label, int color, Consumer<UIToggle> callback)
        {
            super(label, callback);

            this.key = key;
            this.color = color;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            int x = this.area.x;
            int y = this.area.y;
            int w = this.area.w;
            int h = this.area.h;
            Icon icon = UIReplaysEditor.getIcon(this.key);

            context.batcher.box(x, y, x + 2, y + h, Colors.A100 | color);
            context.batcher.gradientHBox(x + 2, y, x + 24, y + h, Colors.A25 | color, color);
            context.batcher.icon(icon, x + 2, y + h / 2, 0F, 0.5F);

            this.area.x += 20;
            this.area.w -= 20;

            super.renderSkin(context);

            this.area.x = x;
            this.area.w = w;
        }
    }
}
