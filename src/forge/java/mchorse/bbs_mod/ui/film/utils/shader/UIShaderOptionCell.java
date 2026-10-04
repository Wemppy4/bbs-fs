package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.graphics.window.InputCodes;
import mchorse.bbs_mod.utils.iris.ShaderMenu;

import java.util.function.Consumer;

/**
 * A single cell of the shader-options grid, mirroring the layout of the Iris shader-options menu but
 * built from the BBS FS button surfaces and controls.
 *
 * <p>Value display by option kind:</p>
 * <ul>
 *     <li><b>boolean</b> — an embedded {@link UIToggle}, rendered <i>disabled</i> so it shows the
 *     current state behind a lock (the value can't be edited here).</li>
 *     <li><b>numeric slider</b> — a value box on a chrome surface (centered number).</li>
 *     <li><b>other string/enum</b> — a plain value box with the localized value text.</li>
 * </ul>
 *
 * <p>Interaction states: curvable &amp; not-added &rarr; active button (primary fill, hand cursor,
 * left-click fires the callback to add a curve channel); already-added &rarr; a highlighted outline, inert;
 * link &rarr; navigational (">"). Track removal stays on the keyframe sheet's RMB menu.</p>
 */
public class UIShaderOptionCell extends UIElement
{
    public static final int CELL_HEIGHT = 20;

    private static final int MIN_VALUE_WIDTH = 44;
    private static final int LABEL_PADDING = 6;
    private static final float ADDED_BORDER_LIGHTEN = 0.4F;

    public final ShaderMenu.Cell cell;

    private final Consumer<UIShaderOptionCell> callback;
    private final UIToggle toggle;

    private String label = "";
    private String value;
    private final Double numericValue;
    private boolean added;
    private boolean hover;

    public UIShaderOptionCell(ShaderMenu.Cell cell, Consumer<UIShaderOptionCell> callback)
    {
        this.cell = cell;
        this.callback = callback;

        this.value = cell.type == ShaderMenu.CellType.PROFILE
            ? (cell.profileName == null ? "" : cell.profileName)
            : (cell.currentValue == null ? "" : cell.currentValue);
        this.numericValue = parseNumeric(cell.currentValue);

        this.h(CELL_HEIGHT);

        if (cell.type == ShaderMenu.CellType.OPTION && cell.booleanOption)
        {
            this.toggle = new UIToggle(IKey.constant(""), "true".equals(cell.currentValue), null);
            this.toggle.setEnabled(false);
            this.toggle.relative(this).x(LABEL_PADDING).y(0).w(1F, -LABEL_PADDING * 2).h(1F);

            this.add(this.toggle);
        }
        else
        {
            this.toggle = null;
        }
    }

    private static Double parseNumeric(String value)
    {
        if (value == null)
        {
            return null;
        }

        try
        {
            return Double.parseDouble(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    public UIShaderOptionCell label(String label)
    {
        this.label = label == null ? "" : label;

        if (this.toggle != null)
        {
            this.toggle.label(IKey.constant(this.label));
        }

        return this;
    }

    public UIShaderOptionCell value(String value)
    {
        this.value = value == null ? "" : value;

        return this;
    }

    public UIShaderOptionCell added(boolean added)
    {
        this.added = added;

        return this;
    }

    public boolean isAdded()
    {
        return this.added;
    }

    /**
     * Whether a left click adds a curve channel (curvable option not yet animated) or navigates to a
     * sub-screen (link).
     */
    public boolean isActivatable()
    {
        if (!this.isEnabled())
        {
            return false;
        }

        if (this.cell.type == ShaderMenu.CellType.LINK)
        {
            return true;
        }

        if (this.cell.type == ShaderMenu.CellType.OPTION)
        {
            return this.cell.curvable && !this.added;
        }

        return false;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.isActivatable() && this.area.isInside(context))
        {
            UIUtils.playClick();

            if (this.callback != null)
            {
                this.callback.accept(this);
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.hover = this.area.isInside(context);

        if (this.isActivatable() && this.hover)
        {
            context.requestCursor(InputCodes.HAND_CURSOR);
        }

        this.renderSkin(context);

        super.render(context);
    }

    private void renderSkin(UIContext context)
    {
        if (this.cell.type == ShaderMenu.CellType.EMPTY)
        {
            return;
        }

        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        Area a = this.area;
        boolean activatable = this.isActivatable();
        boolean lightBackground = activatable || this.added;

        /* Background — primary tint when the cell is on a "button" (clickable/added), neutral otherwise. */
        int fill = lightBackground ? (BBSSettings.primaryColor.get() | Colors.A100) : Colors.A50;

        if (activatable && this.hover)
        {
            fill = Colors.mulRGB(fill, 0.85F);
        }

        batcher.surfaceBox(a.x, a.y, a.ex(), a.ey(), fill, true, false);

        if (this.added)
        {
            batcher.outline(a.x, a.y, a.ex(), a.ey(), addedBorderColor(), 2);
        }

        /* Boolean cells delegate label + value entirely to the embedded (locked) toggle. */
        if (this.toggle != null)
        {
            return;
        }

        int textY = a.my(font.getHeight());
        int valueWidth = 0;

        if (this.cell.type == ShaderMenu.CellType.OPTION || this.cell.type == ShaderMenu.CellType.PROFILE)
        {
            valueWidth = Math.max(MIN_VALUE_WIDTH, font.getWidth(this.value) + 8);
            valueWidth = Math.min(valueWidth, a.w - LABEL_PADDING * 2);

            int vx = a.ex() - valueWidth - 2;
            int vy = a.y + 2;
            int vh = a.h - 4;

            /* Numeric sliders read out on a chrome surface box; other string/enum options keep a plain box. */
            int boxColor = this.cell.slider && this.numericValue != null ? BBSSettings.chromeSurface() : Colors.A50;

            batcher.box(vx, vy, vx + valueWidth, vy + vh, boxColor);

            int tx = vx + (valueWidth - font.getWidth(this.value)) / 2;

            batcher.text(this.value, tx, textY, Colors.WHITE, true);
        }
        else if (this.cell.type == ShaderMenu.CellType.LINK)
        {
            String arrow = ">";

            valueWidth = font.getWidth(arrow) + LABEL_PADDING;

            batcher.text(arrow, a.ex() - LABEL_PADDING - font.getWidth(arrow), textY, this.textColor(false), true);
        }

        int labelWidth = a.w - LABEL_PADDING * 2 - valueWidth;

        if (labelWidth > 0)
        {
            boolean nonCurvableOption = this.cell.type == ShaderMenu.CellType.OPTION && !this.cell.curvable;

            batcher.text(font.limitToWidth(this.label, labelWidth), a.x + LABEL_PADDING, textY, this.textColor(nonCurvableOption), true);
        }
    }

    /** Selected/added ring color: the user's BBS primary colour, lightened ~40% toward white. */
    private static int addedBorderColor()
    {
        return Colors.lerp(BBSSettings.primaryColor.get() | Colors.A100, Colors.opaque(Colors.WHITE), ADDED_BORDER_LIGHTEN);
    }

    /**
     * Label/arrow color: white everywhere (the text shadow keeps it legible on the primary-tinted
     * buttons, so it is no longer darkened there), grey for a non-curvable option to mute it.
     */
    private int textColor(boolean nonCurvableOption)
    {
        return nonCurvableOption ? Colors.GRAY : Colors.WHITE;
    }
}
