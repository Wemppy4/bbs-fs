package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.utils.colors.Color;
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

    public UIColorPalette palette;

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

    /** Place the panel; its size is fixed by the grid, so there is nothing else to say. */
    public void setup(int x, int y)
    {
        this.xy(x, y);
    }

    @Override
    public void resize()
    {
        this.w(COLUMNS * CELL_SIZE + PADDING * 2);
        this.h(ROWS * CELL_SIZE + PADDING * 2);

        if (this.resizer != null)
        {
            this.resizer.apply(this.area);
        }

        this.afterResizeApplied();

        this.palette.set(this.area.x + PADDING, this.area.y + PADDING, COLUMNS * CELL_SIZE, ROWS * CELL_SIZE);
        this.palette.resize();

        if (this.resizer != null)
        {
            this.resizer.postApply(this.area);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
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

        super.render(context);
    }
}
