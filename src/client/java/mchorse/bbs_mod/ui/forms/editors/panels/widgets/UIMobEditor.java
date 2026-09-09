package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.BiConsumer;

/**
 * The mob a mob form wears, shown the way an item form shows its stack: a slot with the
 * creature's spawn egg and its name, and the picking itself (the registry list and the
 * spawn NBT) behind a click, in {@link UIUnifiedPickOverlayPanel}.
 */
public class UIMobEditor extends UIElement
{
    private static final int HEIGHT = 20;

    private final BiConsumer<String, String> callback;

    private String mobID = "";
    private String mobNBT = "";
    private boolean opened;

    private Runnable onClose;

    public UIMobEditor(BiConsumer<String, String> callback)
    {
        this.callback = callback;

        this.h(HEIGHT);
    }

    /** Ran once the picker is closed, for whatever depends on WHICH mob it is — the bone list. */
    public UIMobEditor onClose(Runnable onClose)
    {
        this.onClose = onClose;

        return this;
    }

    public void set(String mobID, String mobNBT)
    {
        this.mobID = mobID == null ? "" : mobID;
        this.mobNBT = mobNBT == null ? "" : mobNBT;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            this.opened = true;

            UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forMob(this::accept, this.mobID, this.mobNBT);

            panel.onClose((a) ->
            {
                this.opened = false;

                if (this.onClose != null)
                {
                    this.onClose.run();
                }
            });

            UIOverlay.addOverlay(this.getContext(), panel, 0.5F, 0.75F);
            UIUtils.playClick();

            return true;
        }

        return super.subMouseClicked(context);
    }

    private void accept(String mobID, String mobNBT)
    {
        this.set(mobID, mobNBT);

        if (this.callback != null)
        {
            this.callback.accept(this.mobID, this.mobNBT);
        }
    }

    private ItemStack getSpawnEgg()
    {
        try
        {
            EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(this.mobID));
            SpawnEggItem egg = SpawnEggItem.forEntity(type);

            return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
        }
        catch (Exception e)
        {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void render(UIContext context)
    {
        boolean hover = this.area.isInside(context);
        int slot = this.area.h;

        if (hover)
        {
            this.area.render(context.batcher, Colors.A25);
        }

        int border = this.opened ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.LIGHTER_GRAY;

        context.batcher.box(this.area.x, this.area.y, this.area.x + slot, this.area.ey(), border);
        context.batcher.box(this.area.x + 1, this.area.y + 1, this.area.x + slot - 1, this.area.ey() - 1, Colors.A50);

        ItemStack stack = this.getSpawnEgg();

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
        else
        {
            context.batcher.icon(Icons.MORPH, Colors.A50 | Colors.WHITE, this.area.x + (slot - 16) / 2, this.area.my() - 8);
        }

        FontRenderer font = context.batcher.getFont();
        int tx = this.area.x + slot + 5;
        int ty = this.area.y + (this.area.h - font.getHeight()) / 2;
        int maxW = this.area.ex() - tx - 4;
        String label = UIUnifiedPickOverlayPanel.mobLabel(this.mobID);
        int color = hover ? Colors.HIGHLIGHT : Colors.WHITE;

        context.batcher.textShadow(font.limitToWidth(label, maxW), tx, ty, color);

        super.render(context);
    }
}
