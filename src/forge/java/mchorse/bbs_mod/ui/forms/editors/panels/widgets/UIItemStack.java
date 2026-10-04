package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import java.util.function.Consumer;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class UIItemStack extends UIElement
{
    private static final int HEIGHT = 20;

    private Consumer<ItemStack> callback;
    private ItemStack stack;
    private boolean opened;

    /** Ghost icon shown in the slot square while it is empty, like vanilla armor slots. */
    private Icon placeholder;

    public UIItemStack(Consumer<ItemStack> callback)
    {
        this.stack = ItemStack.EMPTY;
        this.callback = callback;

        this.context((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.ITEM_STACK_CONTEXT_RESET, () ->
            {
                if (this.callback != null)
                {
                    this.callback.accept(ItemStack.EMPTY);
                }

                this.setStack(ItemStack.EMPTY);
            });

            if (!this.stack.isEmpty())
            {
                menu.action(Icons.PLAYER, UIKeys.ITEM_STACK_CONTEXT_GIVE, () -> giveToPlayer(this.stack));
            }
        });

        this.h(HEIGHT);
    }

    public void setStack(ItemStack stack)
    {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public UIItemStack placeholder(Icon icon)
    {
        this.placeholder = icon;

        return this;
    }

    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            this.opened = true;

            UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forItem((i) ->
            {
                if (this.callback != null)
                {
                    this.callback.accept(i);
                }

                this.setStack(i);
            }, this.stack);

            panel.onClose((a) -> this.opened = false);

            UIOverlay.addOverlay(this.getContext(), panel, 0.5F, 0.75F);
            UIUtils.playClick();

            return true;
        } else {
            return super.subMouseClicked(context);
        }
    }

    public void render(UIContext context)
    {
        boolean hover = this.area.isInside(context);
        boolean empty = this.stack == null || this.stack.isEmpty();
        int slot = this.area.h;

        if (hover)
        {
            this.area.render(context.batcher, Colors.A25);
        }

        int border = this.opened ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.LIGHTER_GRAY;

        context.batcher.box(this.area.x, this.area.y, this.area.x + slot, this.area.ey(), border);
        context.batcher.box(this.area.x + 1, this.area.y + 1, this.area.x + slot - 1, this.area.ey() - 1, Colors.A50);

        if (!empty)
        {
            MatrixStack matrices = context.batcher.getContext().getMatrices();

            matrices.push();
            context.batcher.getContext().drawItem(this.stack, this.area.x + (slot - 16) / 2, this.area.my() - 8);
            matrices.pop();
        }
        else if (this.placeholder != null)
        {
            context.batcher.icon(this.placeholder, Colors.A50 | Colors.WHITE, this.area.x + (slot - 16) / 2, this.area.my() - 8);
        }

        FontRenderer font = context.batcher.getFont();
        int tx = this.area.x + slot + 5;
        int ty = this.area.y + (this.area.h - font.getHeight()) / 2;
        int maxW = this.area.ex() - tx - 4;

        if (empty)
        {
            context.batcher.textShadow(font.limitToWidth(UIKeys.FORMS_EDITORS_ITEM_EMPTY.get(), maxW), tx, ty, Colors.GRAY);
        }
        else
        {
            int color = hover ? Colors.HIGHLIGHT : Colors.WHITE;
            String name = this.stack.getDisplayName();

            if (this.stack.getCount() > 1)
            {
                String suffix = " ×" + this.stack.getCount();

                name = font.limitToWidth(name, maxW - font.getWidth(suffix));

                context.batcher.textShadow(name, tx, ty, color);
                context.batcher.textShadow(suffix, tx + font.getWidth(name), ty, Colors.LIGHTER_GRAY);
            }
            else
            {
                context.batcher.textShadow(font.limitToWidth(name, maxW), tx, ty, color);
            }
        }

        super.render(context);
    }

    /**
     * Delivers {@code stack} to the local player via the vanilla {@code /give} command,
     * preserving count and NBT (custom name, enchantments, etc.). Silently ignored if the
     * stack is empty or the player has no active network connection. Requires the player
     * to have sufficient permissions for {@code /give}.
     */
    static void giveToPlayer(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.player == null || mc.player.connection == null)
        {
            return;
        }

        ResourceLocation id = stack.getItem().getRegistryName();
        StringBuilder command = new StringBuilder("/give @s ").append(id)
            .append(' ').append(stack.getCount()).append(' ').append(stack.getMetadata());
        NBTTagCompound tag = stack.getTagCompound();

        if (tag != null && !tag.isEmpty())
        {
            command.append(' ').append(tag);
        }

        mc.player.sendChatMessage(command.toString());
    }
}
