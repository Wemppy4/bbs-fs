package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import java.util.function.Consumer;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class UIItemStack extends UIElement
{
    private static final int HEIGHT = 20;

    private Consumer<ItemStack> callback;
    private ItemStack stack;
    private boolean opened;

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
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            matrices.push();
            consumers.setUI(true);
            context.batcher.getContext().drawItem(this.stack, this.area.x + (slot - 16) / 2, this.area.my() - 8);
            consumers.setUI(false);
            matrices.pop();
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
            String name = this.stack.getName().getString();

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

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.player.networkHandler == null)
        {
            return;
        }

        Identifier id = Registries.ITEM.getId(stack.getItem());
        StringBuilder command = new StringBuilder("give @s ").append(id);
        NbtCompound tag = stack.getNbt();

        if (tag != null && !tag.isEmpty())
        {
            command.append(tag);
        }

        command.append(' ').append(stack.getCount());

        mc.player.networkHandler.sendChatCommand(command.toString());
    }
}