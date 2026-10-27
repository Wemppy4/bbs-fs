package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderMenu;
import mchorse.bbs_mod.utils.iris.ShaderOptions;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Picks what a curve clip animates, from one panel: the world (time of day, weather, brightness and
 * the like) and, with a shader pack on, the pack's own options, in the pack's menu
 * ({@link UIShaderMenuPanel}). The world's curves sit under the search field and stay in sight
 * while the pack's menu is browsed. Options that can't be animated stay grey, so the menu keeps
 * its layout.
 *
 * <p>A click adds a curve and leaves the panel open for the next one; an added curve reads as a
 * picked row, and a second click takes the track out again. Changing the pack's options is not its
 * business: that is the pack's settings, opened from the utility panel (F6).</p>
 */
public class UICurvePicker extends UIShaderMenuPanel
{
    private final Set<String> added;
    private final Consumer<String> onAdd;
    private final Consumer<String> onRemove;

    private final UIElement worldGrid;

    /**
     * Opens the picker with the menu of the pack that's on, if there is one.
     *
     * @param existing ids of the channels the clip has already
     * @param onAdd receives the channel id of every curve picked
     * @param onRemove receives the channel id of every added curve clicked again
     */
    public static void open(UIContext context, Collection<String> existing, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        ShaderMenu menu = null;
        Map<String, String> lang = Collections.emptyMap();

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
            /* An Iris build whose option-menu API differs from the one we compiled against: no
             * menu to mirror, so every animatable option goes on the "not in the menu" page */
            menu = flatMenu();
        }

        open(context, new UICurvePicker(menu, lang, existing, onAdd, onRemove));
    }

    private static ShaderMenu flatMenu()
    {
        Set<String> curvable = ShaderCurves.getCurvable();

        if (curvable.isEmpty())
        {
            return null;
        }

        ShaderMenu menu = new ShaderMenu("", new ShaderMenu.Screen(ShaderMenu.MAIN_SCREEN, 2, Collections.emptyList()), new HashMap<>());

        for (String optionId : curvable)
        {
            menu.notInMenu.add(ShaderMenu.Cell.option(optionId, false, false, true, null, null, Collections.emptyList(), -1));
        }

        return menu;
    }

    private UICurvePicker(ShaderMenu menu, Map<String, String> lang, Collection<String> existing, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        super(UIKeys.CAMERA_PANELS_CURVE_ADD);

        this.added = new HashSet<>(existing);
        this.onAdd = onAdd;
        this.onRemove = onRemove;

        this.setMenu(menu, lang);

        this.worldGrid = new UIElement();
        this.worldGrid.relative(this.content).w(1F).h(this.worldHeight());
        this.worldGrid.grid(MARGIN).items(this.columns).padding(PADDING).height(UIShaderMenuCell.HEIGHT);

        this.content.add(this.worldGrid);

        this.refresh();
    }

    private int worldHeight()
    {
        return gridHeight(WorldCurve.ALL.size(), this.columns);
    }

    /** A curve cell was clicked: adds the track, or takes it out if the clip has it already. */
    private void toggle(String channelId, UIShaderMenuCell cell)
    {
        if (this.added.remove(channelId))
        {
            this.onRemove.accept(channelId);
            cell.added(false);
        }
        else
        {
            this.added.add(channelId);
            this.onAdd.accept(channelId);
            cell.added(true);
        }
    }

    /** Enter while searching adds the first hit that isn't in the clip yet. */
    @Override
    public void confirm()
    {
        if (!this.browser.isSearching())
        {
            return;
        }

        for (UIShaderMenuCell cell : this.view.getChildren(UIShaderMenuCell.class))
        {
            if (cell.kind == UIShaderMenuCell.Kind.CURVE && !cell.isAdded())
            {
                cell.activate();

                return;
            }
        }
    }

    /* === The menu === */

    @Override
    protected boolean counts(ShaderMenu.Cell option)
    {
        return option.curvable;
    }

    /** An animatable option, with its value now for a note; one that can't be animated is grey. */
    @Override
    protected UIShaderMenuCell optionCell(ShaderMenu.Cell option, String where)
    {
        String label = this.browser.optionLabel(option.optionId);
        UIShaderMenuCell cell = option.curvable
            ? this.curveCell(CurveClip.SHADER_CURVES_PREFIX + option.optionId, label)
            : UIShaderMenuCell.option(label).muted(true);

        cell.note(where == null ? this.browser.valueLabel(option) : where);
        cell.tooltip(IKey.constant(label + " (" + option.optionId + ")"));

        return cell;
    }

    @Override
    protected UIShaderMenuCell profileCell(ShaderMenu.Cell cell)
    {
        return UIShaderMenuCell.option(UIKeys.CAMERA_PANELS_SHADER_PROFILE.get()).muted(true).note(this.browser.profileLabel(cell.profileName));
    }

    @Override
    protected int addResults(String query)
    {
        String worldTitle = UIKeys.CAMERA_PANELS_CURVES_WORLD.get();
        int found = 0;

        for (WorldCurve curve : WorldCurve.ALL)
        {
            if (ShaderMenuBrowser.matches(curve.label.get(), curve.id, query))
            {
                this.view.add(this.curveCell(curve.id, curve.label.get()).icon(curve.icon).note(worldTitle));
                found += 1;
            }
        }

        return found;
    }

    /* === The world === */

    @Override
    protected int topHeight()
    {
        return this.browser.isSearching() ? super.topHeight() : this.worldHeight();
    }

    @Override
    protected void placeTop(int y)
    {
        /* The grid's own padding keeps it off the search field */
        this.worldGrid.setVisible(!this.browser.isSearching());
        this.worldGrid.y(y);
    }

    @Override
    protected void refresh()
    {
        this.worldGrid.removeAll();

        for (WorldCurve curve : WorldCurve.ALL)
        {
            this.worldGrid.add(this.worldCell(curve));
        }

        super.refresh();
    }

    private UIShaderMenuCell curveCell(String channelId, String label)
    {
        return UIShaderMenuCell.curve(label, (cell) -> this.toggle(channelId, cell)).added(this.added.contains(channelId));
    }

    private UIShaderMenuCell worldCell(WorldCurve curve)
    {
        UIShaderMenuCell cell = this.curveCell(curve.id, curve.label.get()).icon(curve.icon);

        if (curve.color != null)
        {
            cell.swatch(curve.color.getAsInt());
        }
        else
        {
            Double value = curve.value.get();

            if (value != null)
            {
                cell.note(UINumericInput.format(value));
            }
        }

        cell.tooltip(curve.label);

        return cell;
    }
}
