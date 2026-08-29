package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Two rows of colors worth reaching for, and nothing else.
 *
 * <p>This is what a right click on a color swatch opens: a way past the whole picker for
 * the times a color only has to be roughly right. A hue for every direction along the top
 * row, the neutrals and the earths along the bottom one.</p>
 *
 * <p>A property that has drifted from its declared color adds one more swatch, apart from
 * the grid on the left — see {@link #withDefault}. It is the way back, not a pick, but it
 * is still a color, so it costs the popup a column instead of a row of chrome.</p>
 */
public class UIColorPresets extends UIElement
{
    public static final int CELL_SIZE = 14;
    public static final int COLUMNS = 10;
    public static final int ROWS = 2;

    private static final int PADDING = 4;

    /** Muted rather than pure: a full-blast primary is rarely what a scene wants. */
    private static final int[] PRESETS =
    {
        0xe74c3c, 0xe67e22, 0xf1c40f, 0x8bc34a, 0x2ecc71, 0x1abc9c, 0x3498db, 0x4054b2, 0x9b59b6, 0xe91e63,
        0xffffff, 0xcfd8dc, 0x90a4ae, 0x546e7a, 0x263238, 0x000000, 0xffd7a8, 0xa1887f, 0x795548, 0x33691e
    };

    private static final ValueColors COLORS = buildPresets();

    /** The gap the default swatch stands apart by, divider included. */
    private static final int DEFAULT_GAP = 5;

    public UIColorPalette palette;

    /**
     * The value's own default, standing in its own column to the left. It is a
     * color like the rest of the popup, so it needs no row of chrome of its own
     * — but it is a way back rather than a pick, hence the gap and the divider.
     * Null when the property is already at its default: then there is nothing
     * to go back to and the popup is the plain grid it has always been.
     */
    private Color defaultColor;
    private Runnable onReset;

    private final Area defaultArea = new Area();

    private static ValueColors buildPresets()
    {
        ValueColors colors = new ValueColors("presets");
        List<Color> list = new ArrayList<>();

        for (int preset : PRESETS)
        {
            list.add(Color.rgb(preset));
        }

        colors.setColors(list);

        return colors;
    }

    public UIColorPresets(Consumer<Color> callback)
    {
        super();

        this.palette = new UIColorPalette(COLORS, (color) ->
        {
            callback.accept(color);

            this.removeFromParent();
        });

        this.palette.setCellSize(CELL_SIZE);

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).add(this.palette);
    }

    /**
     * Offer the property's default alongside the presets. A null color leaves
     * the popup exactly as it was.
     */
    public UIColorPresets withDefault(Color defaultColor, Runnable onReset)
    {
        this.defaultColor = defaultColor;
        this.onReset = onReset;

        return this;
    }

    private boolean hasDefault()
    {
        return this.defaultColor != null && this.onReset != null;
    }

    private int defaultWidth()
    {
        return this.hasDefault() ? CELL_SIZE + DEFAULT_GAP : 0;
    }

    /** Place the panel; its size is fixed by the grid, so there is nothing else to say. */
    public void setup(int x, int y)
    {
        this.xy(x, y);
    }

    @Override
    public void resize()
    {
        this.w(COLUMNS * CELL_SIZE + PADDING * 2 + this.defaultWidth());
        this.h(ROWS * CELL_SIZE + PADDING * 2);

        if (this.resizer != null)
        {
            this.resizer.apply(this.area);
        }

        this.afterResizeApplied();

        this.defaultArea.set(this.area.x + PADDING, this.area.y + PADDING, CELL_SIZE, ROWS * CELL_SIZE);
        this.palette.set(this.area.x + PADDING + this.defaultWidth(), this.area.y + PADDING, COLUMNS * CELL_SIZE, ROWS * CELL_SIZE);
        this.palette.resize();

        if (this.resizer != null)
        {
            this.resizer.postApply(this.area);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.hasDefault() && this.defaultArea.isInside(context))
        {
            this.onReset.run();
            this.removeFromParent();

            return true;
        }

        if (!this.area.isInside(context))
        {
            this.removeFromParent();
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.removeFromParent();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());

        this.area.render(context.batcher, BBSSettings.raisedSurface());

        this.renderDefault(context);

        super.render(context);
    }

    /** The default swatch: same cell as the palette's, plus the divider that keeps it apart. */
    private void renderDefault(UIContext context)
    {
        if (!this.hasDefault())
        {
            return;
        }

        int x = this.defaultArea.x;
        int y = this.defaultArea.y;
        int ex = this.defaultArea.ex();
        int ey = this.defaultArea.ey();

        context.batcher.iconArea(Icons.CHECKBOARD, x, y, this.defaultArea.w, this.defaultArea.h);
        UIColorPicker.renderAlphaPreviewQuad(context.batcher, x, y, ex, ey, this.defaultColor);

        int divider = ex + DEFAULT_GAP / 2;

        context.batcher.box(divider, y, divider + 1, ey, Colors.A50);

        if (this.defaultArea.isInside(context))
        {
            context.batcher.outline(x, y, ex, ey, Colors.WHITE);
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            context.batcher.textCard(UIKeys.VALUE_RESET.get(), context.mouseX + 6, context.mouseY + 10);
        }
    }
}
