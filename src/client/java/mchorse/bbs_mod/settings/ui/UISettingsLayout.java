package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIconToggles;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.onboarding.Onboarding;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

        /* Which parts of the gizmo reach the screen is one question with five
         * answers, so it is asked once instead of eating five labelled rows */
        register(new ToggleStripRow(UIKeys.CONFIG_GIZMO_ELEMENTS)
            .add(BBSSettings.gizmoShowTranslate, Icons.ALL_DIRECTIONS)
            .add(BBSSettings.gizmoShowScale, Icons.SCALE)
            .add(BBSSettings.gizmoShowRotate, Icons.ORBIT)
            .add(BBSSettings.gizmoShowViewRotate, Icons.OUTLINE_SPHERE)
            .add(BBSSettings.gizmoShowSphere, Icons.SPHERE));

        register(new UIResolutionRow(BBSSettings.videoWidth, BBSSettings.videoHeight, true));
        register(new UIResolutionRow(BBSSettings.editorPreviewCustomWidth, BBSSettings.editorPreviewCustomHeight, false));
        register(new UIExportPathRow(BBSSettings.videoExportPath));
        register(new UIEncoderPathRow(BBSSettings.videoEncoderPath));

        /* The first-run flags aren't switches to flip but things to bring back */
        register(new OnboardingRow());
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

    /**
     * A run of boolean settings drawn as one {@link UIIconToggles} strip under a
     * shared label. Each cell carries its own setting's name and description in
     * its tooltip — the same bargain {@link UIResolutionRow} makes for its
     * unlabelled fields — so the page loses the labels but not what they said.
     */
    public static class ToggleStripRow implements IValueRow
    {
        private final IKey label;
        private final List<ValueBoolean> values = new ArrayList<>();
        private final List<Icon> icons = new ArrayList<>();

        public ToggleStripRow(IKey label)
        {
            this.label = label;
        }

        public ToggleStripRow add(ValueBoolean value, Icon icon)
        {
            this.values.add(value);
            this.icons.add(icon);

            return this;
        }

        @Override
        public List<BaseValue> getValues()
        {
            return new ArrayList<>(this.values);
        }

        @Override
        public List<UIElement> create(UIElement ui)
        {
            UIIconToggles toggles = new UIIconToggles(null);

            for (int i = 0; i < this.values.size(); i++)
            {
                ValueBoolean value = this.values.get(i);

                toggles.add(
                    this.icons.get(i),
                    L10n.lang(UIValueFactory.getValueLabelKey(value)),
                    L10n.lang(UIValueFactory.getValueCommentKey(value)),
                    () -> value
                );
            }

            toggles.w(toggles.getPreferredWidth());

            UIElement row = new UIElement();

            row.row(0).preferred(0).height(20);
            row.add(UI.label(this.label, 0).labelAnchor(0, 0.5F), toggles);

            return Collections.singletonList(row);
        }

    }

    /**
     * The welcome screen and the tours, as the two buttons that bring them back. The settings
     * window goes down first: both open on the dashboard itself, not in a window over it.
     */
    public static class OnboardingRow implements IValueRow
    {
        @Override
        public List<BaseValue> getValues()
        {
            return List.of(BBSSettings.onboardingWelcomeSeen, BBSSettings.onboardingToursDone);
        }

        @Override
        public List<UIElement> create(UIElement ui)
        {
            UIButton welcome = new UIButton(UIKeys.ONBOARDING_SETTINGS_SHOW_WELCOME, (b) -> this.leaveFor(ui, Onboarding::showWelcome));
            UIButton tours = new UIButton(UIKeys.ONBOARDING_SETTINGS_RESET_TOURS, (b) -> this.leaveFor(ui, Onboarding::resetTours));

            return List.of(UI.label(UIKeys.ONBOARDING_SETTINGS_TITLE, 0).labelAnchor(0, 0.5F), UI.row(4, 0, 20, welcome, tours));
        }

        private void leaveFor(UIElement ui, Consumer<UIContext> action)
        {
            UIContext context = ui.getContext();
            UIOverlayPanel settings = ui instanceof UIOverlayPanel panel ? panel : ui.getParent(UIOverlayPanel.class);

            if (settings != null)
            {
                settings.close();
            }

            action.accept(context);
        }
    }
}
