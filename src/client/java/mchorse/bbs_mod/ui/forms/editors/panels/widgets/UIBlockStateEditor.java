package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.function.Consumer;

public class UIBlockStateEditor extends UIElement
{
    private static final int HEIGHT = 20;

    private final Consumer<BlockState> callback;
    private BlockState blockState;
    private boolean opened;

    public UIBlockStateEditor(Consumer<BlockState> callback)
    {
        this.callback = callback;
        this.blockState = Blocks.AIR.getDefaultState();

        this.context((menu) ->
        {
            Item item = this.blockState.getBlock().asItem();

            if (item != Items.AIR)
            {
                menu.action(Icons.PLAYER, UIKeys.ITEM_STACK_CONTEXT_GIVE, () -> UIItemStack.giveToPlayer(new ItemStack(item)));
            }
        });

        this.h(HEIGHT);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            this.opened = true;

            UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forBlock((state) ->
            {
                this.acceptBlockState(state);
            }, this.blockState);

            panel.onClose((a) -> this.opened = false);
            UIOverlay.addOverlay(this.getContext(), panel, 0.5F, 0.75F);
            UIUtils.playClick();

            return true;
        }

        return super.subMouseClicked(context);
    }

    public void setBlockState(BlockState blockState)
    {
        this.blockState = blockState == null ? Blocks.AIR.getDefaultState() : blockState;
    }

    private void acceptBlockState(BlockState blockState)
    {
        this.blockState = blockState == null ? Blocks.AIR.getDefaultState() : blockState;

        if (this.callback != null)
        {
            this.callback.accept(this.blockState);
        }
    }

    @Override
    public void render(UIContext context)
    {
        boolean hover = this.area.isInside(context);
        boolean empty = this.blockState == null || this.blockState.isAir();
        int slot = this.area.h;

        if (hover)
        {
            this.area.render(context.batcher, Colors.A25);
        }

        int border = this.opened ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.LIGHTER_GRAY;

        context.batcher.box(this.area.x, this.area.y, this.area.x + slot, this.area.ey(), border);
        context.batcher.box(this.area.x + 1, this.area.y + 1, this.area.x + slot - 1, this.area.ey() - 1, Colors.A50);

        ItemStack stack = empty ? ItemStack.EMPTY : new ItemStack(this.blockState.getBlock().asItem());

        if (!stack.isEmpty())
        {
            MatrixStack matrices = context.batcher.getContext().getMatrices();
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            matrices.push();
            consumers.setUI(true);
            context.batcher.getContext().drawItem(stack, this.area.x + (slot - 16) / 2, this.area.my() - 8);
            consumers.setUI(false);
            matrices.pop();
        }

        FontRenderer font = context.batcher.getFont();
        int tx = this.area.x + slot + 5;
        int ty = this.area.y + (this.area.h - font.getHeight()) / 2;
        int maxW = this.area.ex() - tx - 4;
        String label = empty ? UIKeys.FORMS_EDITORS_BLOCK_EMPTY.get() : this.blockState.getBlock().getName().getString();
        int color = empty ? Colors.GRAY : (hover ? Colors.HIGHLIGHT : Colors.WHITE);

        context.batcher.textShadow(font.limitToWidth(label, maxW), tx, ty, color);

        super.render(context);
    }
}