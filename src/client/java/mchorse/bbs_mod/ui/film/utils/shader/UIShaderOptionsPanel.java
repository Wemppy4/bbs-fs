package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderMenu;
import mchorse.bbs_mod.utils.iris.ShaderOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The settings of the shader pack that's on, opened from the utility panel (F6), in the pack's
 * menu the way the curve picker shows it ({@link UIShaderMenuPanel}): every option a cell with a
 * control that suits it, the pack's profile included, where the pack puts it.
 *
 * <ul>
 *     <li>An option the pack turned into a uniform ({@link ShaderCurves#getCurvable()}) changes at
 *     once, with no reload.</li>
 *     <li>Any other one needs the pack reloaded: its changes pile up, and the bar at the bottom
 *     applies them all with one reload, or drops them. Closing the panel applies what's left, the
 *     way Iris' menu does.</li>
 * </ul>
 *
 * <p>After every load of the pack the panel reads the menu again, since what options there are
 * and which of them can be animated may change, down to the pack's fallback builds. Iris is reached
 * through {@link ShaderOptions} only, so the panel works (and says Iris is missing) without it.
 * Curves are not its business: they are made in the curve clip's picker.</p>
 */
public class UIShaderOptionsPanel extends UIShaderMenuPanel
{
    private static final int BAR_BUTTON_WIDTH = 90;

    private Set<String> curvable = Collections.emptySet();
    private List<String> profileOrder = Collections.emptyList();
    private String pack = "";
    private boolean failed;
    private int revision;

    /** Values of options that need a reload, waiting for the bar's Apply */
    private final Map<String, String> pending = new LinkedHashMap<>();

    private final UIElement bar;

    /** Opens the settings of the pack that's on. */
    public static void open(UIContext context)
    {
        open(context, new UIShaderOptionsPanel());
    }

    private UIShaderOptionsPanel()
    {
        super(UIKeys.UTILITY_SHADER_OPTIONS);

        UIButton apply = new UIButton(UIKeys.FILM_SHADER_APPLY, (b) -> this.applyPending());
        UIButton discard = new UIButton(UIKeys.FILM_SHADER_DISCARD, (b) -> this.discardPending());

        this.bar = new UIElement();
        this.bar.relative(this.content).x(PADDING).w(1F, -PADDING * 2).h(BAR_HEIGHT);
        this.bar.row(MARGIN).preferred(0).height(BAR_HEIGHT);
        this.bar.add(UI.label((IKey) () -> UIKeys.FILM_SHADER_PENDING.format(this.pending.size()).get(), BAR_HEIGHT).labelAnchor(0, 0.5F), apply.w(BAR_BUTTON_WIDTH).h(BAR_HEIGHT), discard.w(BAR_BUTTON_WIDTH).h(BAR_HEIGHT));

        this.content.add(this.bar);

        this.revision = ShaderOptions.revision();
        this.rebuild();
    }

    private ShaderMenu menu()
    {
        return this.browser.menu;
    }

    /* === Values === */

    /** The value an option has now: waiting for a reload, or the pack's */
    String value(String optionId)
    {
        String value = this.pending.get(optionId);

        if (value == null && this.menu() != null)
        {
            value = this.menu().values.get(optionId);
        }

        return value;
    }

    boolean isCurvable(String optionId)
    {
        return this.curvable.contains(optionId);
    }

    boolean isPending(String optionId)
    {
        return this.pending.containsKey(optionId);
    }

    ShaderMenuBrowser getBrowser()
    {
        return this.browser;
    }

    /**
     * Sets an option: one the pack turned into a uniform takes the value at once, any other waits
     * for the reload the bar at the bottom offers.
     */
    void set(String optionId, String value)
    {
        ShaderMenu menu = this.menu();

        if (menu == null || value == null || value.equals(this.value(optionId)))
        {
            return;
        }

        if (this.curvable.contains(optionId))
        {
            menu.values.put(optionId, value);

            try
            {
                ShaderOptions.setLive(optionId, value);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            return;
        }

        if (value.equals(menu.values.get(optionId)))
        {
            this.pending.remove(optionId);
        }
        else
        {
            this.pending.put(optionId, value);
        }

        this.layout();
    }

    private void applyPending()
    {
        Map<String, String> values = new LinkedHashMap<>(this.pending);

        this.pending.clear();
        this.layout();

        UIContext context = this.getContext();

        ShaderOptions.reload(() -> ShaderOptions.apply(values), () ->
        {
            if (context != null)
            {
                UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.UTILITY_SHADER_ERROR));
            }
        });
    }

    private void discardPending()
    {
        this.pending.clear();
        this.layout();
    }

    /* === Profile === */

    private void openProfiles()
    {
        ShaderMenu menu = this.menu();

        if (menu == null || menu.profiles.isEmpty())
        {
            return;
        }

        UIChoiceMenu.of(new ArrayList<>(menu.profiles.keySet()))
            .label((profile) -> IKey.constant(this.browser.profileLabel(profile)))
            .current(this.currentProfile())
            .open(this.getContext(), this::applyProfile);
    }

    /** A profile sets its options the way a change by hand does: at once, or waiting for a reload. */
    private void applyProfile(String profile)
    {
        ShaderMenu menu = this.menu();
        Map<String, String> values = menu == null ? null : menu.profiles.get(profile);

        if (values == null)
        {
            return;
        }

        for (Map.Entry<String, String> entry : values.entrySet())
        {
            if (menu.values.containsKey(entry.getKey()))
            {
                this.set(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * The profile the options match, the way Iris finds it: the one that sets the most options
     * goes first. Null when none matches (a custom setup).
     */
    String currentProfile()
    {
        ShaderMenu menu = this.menu();

        if (menu == null)
        {
            return null;
        }

        for (String profile : this.profileOrder)
        {
            boolean matches = true;

            for (Map.Entry<String, String> entry : menu.profiles.get(profile).entrySet())
            {
                String value = this.value(entry.getKey());

                if (value != null && !value.equals(entry.getValue()))
                {
                    matches = false;

                    break;
                }
            }

            if (matches)
            {
                return profile;
            }
        }

        return null;
    }

    /* === The menu === */

    /** Reads the loaded pack's menu again; the screen on show stays, if the pack still has it. */
    private void rebuild()
    {
        String pack = ShaderOptions.currentPack();
        ShaderMenu menu = null;
        Map<String, String> lang = Collections.emptyMap();

        this.failed = false;

        if (ShaderOptions.IRIS)
        {
            try
            {
                menu = ShaderOptions.menu();

                if (menu != null)
                {
                    lang = ShaderOptions.lang();
                }
            }
            catch (LinkageError e)
            {
                /* An Iris build whose option-menu API differs from the one we compiled against */
                menu = null;
                this.failed = true;
            }
        }

        if (!pack.equals(this.pack) || menu == null)
        {
            this.pending.clear();
        }

        this.pack = pack;
        this.curvable = menu == null ? Collections.emptySet() : ShaderCurves.getCurvable();
        this.setMenu(menu, lang);

        if (menu == null)
        {
            this.profileOrder = Collections.emptyList();
        }
        else
        {
            /* What the reload applied, or what can now change without one, waits no more */
            ShaderMenu loaded = menu;

            this.pending.entrySet().removeIf((entry) -> this.curvable.contains(entry.getKey())
                || !loaded.values.containsKey(entry.getKey())
                || entry.getValue().equals(loaded.values.get(entry.getKey())));

            List<String> order = new ArrayList<>(menu.profiles.keySet());

            order.sort(Comparator.comparingInt((profile) -> -loaded.profiles.get(profile).size()));
            this.profileOrder = order;
        }

        double scroll = this.view.scroll.getScroll();

        this.refresh();
        this.view.scroll.setScroll(scroll);
    }

    @Override
    protected boolean counts(ShaderMenu.Cell option)
    {
        return true;
    }

    @Override
    protected UIShaderMenuCell optionCell(ShaderMenu.Cell option, String where)
    {
        return new UIShaderOptionCell(this, option).note(where);
    }

    @Override
    protected UIShaderMenuCell profileCell(ShaderMenu.Cell cell)
    {
        UIButton profile = new UIButton(() -> this.browser.profileLabel(this.currentProfile()), (b) -> this.openProfiles());

        return UIShaderMenuCell.option(UIKeys.CAMERA_PANELS_SHADER_PROFILE.get()).control(profile, UIShaderOptionCell.CONTROL_WIDTH);
    }

    @Override
    protected IKey noMenuText()
    {
        return !ShaderOptions.IRIS ? UIKeys.UTILITY_IRIS_REQUIRED
            : this.failed ? UIKeys.UTILITY_SHADER_ERROR
            : super.noMenuText();
    }

    /** The bar of changes waiting for a reload, while there are any */
    @Override
    protected int layoutBottom()
    {
        boolean bar = !this.pending.isEmpty();

        this.bar.setVisible(bar);
        this.bar.y(1F, -BAR_HEIGHT - PADDING);

        return bar ? BAR_HEIGHT + PADDING : 0;
    }

    @Override
    public void render(UIContext context)
    {
        int revision = ShaderOptions.revision();

        if (revision != this.revision)
        {
            this.revision = revision;
            this.rebuild();
        }

        super.render(context);
    }

    @Override
    public void onClose()
    {
        super.onClose();

        if (!this.pending.isEmpty())
        {
            this.applyPending();
        }
    }
}
