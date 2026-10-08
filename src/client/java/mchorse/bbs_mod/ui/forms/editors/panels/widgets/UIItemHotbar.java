package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Nine item cells in a row, the way the game lays out its hotbar, with the cell in hand framed
 * in the primary colour. What fills the cells and what a left click on one does is the owner's.
 *
 * <p>The row keeps its natural size and only shrinks into a box too small for it, unless it is
 * told to {@link #fillWidth() fill the width} - then it scales with it, items included, and asks
 * for the height that keeps the cells square.</p>
 */
public class UIItemHotbar extends UIElement
{
    public static final int SLOT_SIZE = 20;
    public static final int SLOT_GAP = 2;
    public static final int WIDTH = ReplayKeyframes.HOTBAR_SIZE * SLOT_SIZE + (ReplayKeyframes.HOTBAR_SIZE - 1) * SLOT_GAP;

    /** Item icons are drawn this big at scale 1, inset from the cell's edge by the rest */
    private static final int ITEM_SIZE = 16;

    private final IntFunction<ItemStack> stacks;
    private final IntSupplier selected;
    private final IntConsumer callback;
    private boolean fill;

    /**
     * @param selected the cell in hand, or -1 for none
     */
    public UIItemHotbar(IntFunction<ItemStack> stacks, IntSupplier selected, IntConsumer callback)
    {
        this.stacks = stacks;
        this.selected = selected;
        this.callback = callback;
    }

    /**
     * Scale with the width a layout hands out. The height follows from it: a column that knows
     * the width it is about to give asks for the height at that width.
     */
    public UIItemHotbar fillWidth()
    {
        this.fill = true;
        this.post(new IResizer()
        {
            @Override
            public int getH(int w)
            {
                return Math.round(w * SLOT_SIZE / (float) WIDTH);
            }
        });

        return this;
    }

    private float getScale()
    {
        float fit = Math.min(this.area.w / (float) WIDTH, this.area.h / (float) SLOT_SIZE);

        return this.fill ? fit : Math.min(1F, fit);
    }

    private static int cellX(int x, int slot, float scale)
    {
        return x + Math.round(slot * (SLOT_SIZE + SLOT_GAP) * scale);
    }

    private static int cellSize(float scale)
    {
        return Math.max(1, Math.round(SLOT_SIZE * scale));
    }

    /** The cell under the mouse, or -1. */
    public int getSlot(UIContext context)
    {
        float scale = this.getScale();
        int size = cellSize(scale);
        int x = this.area.mx(Math.round(WIDTH * scale));
        int y = this.area.my(size);

        if (context.mouseY < y || context.mouseY >= y + size)
        {
            return -1;
        }

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            int cellX = cellX(x, i, scale);

            if (context.mouseX >= cellX && context.mouseX < cellX + size)
            {
                return i;
            }
        }

        return -1;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || context.mouseButton != 0)
        {
            return super.subMouseClicked(context);
        }

        int slot = this.getSlot(context);

        if (slot < 0)
        {
            return false;
        }

        this.callback.accept(slot);

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        float scale = this.getScale();
        int hovered = this.area.isInside(context) ? this.getSlot(context) : -1;

        renderRow(context, this.area.mx(Math.round(WIDTH * scale)), this.area.my(cellSize(scale)), scale, this.stacks, this.selected.getAsInt(), hovered);

        super.render(context);
    }

    /**
     * Draw the row at given scale with its top left corner at given point. Also used where there
     * is no element to own it, such as a hover preview.
     */
    public static void renderRow(UIContext context, int x, int y, float scale, IntFunction<ItemStack> stacks, int selected, int hovered)
    {
        int size = cellSize(scale);
        float inset = (SLOT_SIZE - ITEM_SIZE) / 2F * scale;

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            int cellX = cellX(x, i, scale);
            ItemStack stack = stacks.apply(i);
            int border = i == selected ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.LIGHTER_GRAY;

            context.batcher.box(cellX, y, cellX + size, y + size, border);
            context.batcher.box(cellX + 1, y + 1, cellX + size - 1, y + size - 1, Colors.A50);

            if (i == hovered)
            {
                context.batcher.box(cellX + 1, y + 1, cellX + size - 1, y + size - 1, Colors.setA(Colors.WHITE, 0.15F));
            }

            if (!stack.isEmpty())
            {
                MatrixStack matrices = context.batcher.getContext().getMatrices();
                CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

                /* Scaled on every axis alike, so a block item keeps its depth in proportion too */
                matrices.push();
                matrices.translate(cellX + inset, y + inset, 0F);
                matrices.scale(scale, scale, scale);
                consumers.setUI(true);
                context.batcher.getContext().drawItem(stack, 0, 0);
                context.batcher.getContext().drawItemInSlot(context.batcher.getFont().getRenderer(), stack, 0, 0);
                consumers.setUI(false);
                matrices.pop();
            }
        }
    }
}
