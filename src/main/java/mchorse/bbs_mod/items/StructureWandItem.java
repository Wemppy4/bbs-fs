package mchorse.bbs_mod.items;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The structure wand as an item: nothing but a tooltip. Everything it does lives on the client,
 * next to the selection it drives (see {@code StructureWand}), so there is nothing for the item
 * to hold — the tooltip is the one thing only the item can offer, and a tool that explains itself
 * in the inventory needs no manual.
 */
public class StructureWandItem extends Item
{
    public StructureWandItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context)
    {
        tooltip.add(Text.translatable("item.bbs.structure_wand.tooltip.corners").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.bbs.structure_wand.tooltip.faces").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.bbs.structure_wand.tooltip.save").formatted(Formatting.GRAY));
    }
}
