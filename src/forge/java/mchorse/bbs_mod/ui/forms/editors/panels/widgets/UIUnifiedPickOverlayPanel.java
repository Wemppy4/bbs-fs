package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraft.nbt.JsonToNBT;


import net.minecraft.block.properties.IProperty;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UIUnifiedPickOverlayPanel extends UIOverlayPanel
{
    private static final int PADDING = 6;
    private static final int GAP = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final int HOTBAR_HEIGHT = 24;

    private static final List<String> ITEM_IDS = new ArrayList<>();
    private static final List<String> BLOCK_IDS = new ArrayList<>();
    private static final List<String> MOB_IDS = new ArrayList<>();

    private static final Map<String, String> ITEM_LABEL_CACHE = new HashMap<>();
    private static final Map<String, String> BLOCK_LABEL_CACHE = new HashMap<>();
    private static final Map<String, String> MOB_LABEL_CACHE = new HashMap<>();

    /** Row height: two text lines + vertical padding (see {@link RegistryIdList}). */
    private static final int LIST_ROW_HEIGHT = 32;

    /** Slot behind list icon; item drawn at +1 px inset (16×16). */
    private static final int LIST_ICON_SLOT = 18;
    private static final int LIST_ICON_GAP = 4;

    private static final Map<String, ItemStack> PREVIEW_STACK_CACHE = new HashMap<>();

    static
    {
        for (ResourceLocation key : Item.REGISTRY.getKeys())
        {
            ITEM_IDS.add(key.toString());
        }

        for (ResourceLocation key : Block.REGISTRY.getKeys())
        {
            BLOCK_IDS.add(key.toString());
        }

        for (ResourceLocation key : EntityList.getEntityNameList())
        {
            MOB_IDS.add(key.toString());
        }

        ITEM_IDS.sort(String::compareToIgnoreCase);
        BLOCK_IDS.sort(String::compareToIgnoreCase);
        MOB_IDS.sort(String::compareToIgnoreCase);
    }

    /**
     * Vanilla Minecraft UI language ({@code options.language}, e.g. {@code ru_ru}).
     * Label caches are keyed with this so names update when the player changes language.
     */
    private static String minecraftLanguageKey()
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.gameSettings == null)
        {
            return "en_us";
        }

        String lang = mc.gameSettings.language;

        return lang == null || lang.isEmpty() ? "en_us" : lang;
    }

    private static String itemLabel(String id)
    {
        String cacheKey = minecraftLanguageKey() + "\0" + id;

        return ITEM_LABEL_CACHE.computeIfAbsent(cacheKey, (k) ->
        {
            try
            {
                Item item = Item.REGISTRY.getObject(new ResourceLocation(id));

                return new ItemStack(item).getDisplayName();
            }
            catch (Exception e)
            {
                return id;
            }
        });
    }

    private static String blockLabel(String id)
    {
        String cacheKey = minecraftLanguageKey() + "\0" + id;

        return BLOCK_LABEL_CACHE.computeIfAbsent(cacheKey, (k) ->
        {
            try
            {
                return Block.REGISTRY.getObject(new ResourceLocation(id)).getLocalizedName();
            }
            catch (Exception e)
            {
                return id;
            }
        });
    }

    /** The entity type's own translated name, the same one a spawn egg or a name tag shows. */
    public static String mobLabel(String id)
    {
        String cacheKey = minecraftLanguageKey() + "\0" + id;

        return MOB_LABEL_CACHE.computeIfAbsent(cacheKey, (k) ->
        {
            try
            {
                String name = EntityList.getTranslationName(new ResourceLocation(id));
                return name == null ? id : I18n.format("entity." + name + ".name");
            }
            catch (Exception e)
            {
                return id;
            }
        });
    }

    private static ItemStack previewStackFor(PickerMode mode, String id)
    {
        return PREVIEW_STACK_CACHE.computeIfAbsent(mode.name() + "\0" + id, (k) ->
        {
            try
            {
                ResourceLocation rid = new ResourceLocation(id);

                if (mode == PickerMode.ITEM)
                {
                    return new ItemStack(Item.REGISTRY.getObject(rid));
                }

                if (mode == PickerMode.MOB)
                {
                    /* Only the spawnable ones have an egg; arrows, boats and the player fall back
                     * to the morph icon in the list (see RegistryIdList). */
                    if (!EntityList.ENTITY_EGGS.containsKey(rid)) return ItemStack.EMPTY;
                    ItemStack egg = new ItemStack(Items.SPAWN_EGG);
                    ItemMonsterPlacer.applyEntityIdToItemStack(egg, rid);
                    return egg;
                }

                Block block = Block.REGISTRY.getObject(rid);
                Item item = Item.getItemFromBlock(block);

                return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            }
            catch (Exception e)
            {
                return ItemStack.EMPTY;
            }
        });
    }

    public enum PickerMode
    {
        ITEM,
        BLOCK,
        MOB
    }

    private static IKey titleFor(PickerMode mode)
    {
        if (mode == PickerMode.ITEM)
        {
            return UIKeys.ACTIONS_ITEM_STACK;
        }

        return mode == PickerMode.MOB ? UIKeys.FORMS_EDITORS_MOB_TITLE : UIKeys.FORMS_EDITORS_BLOCK_TITLE;
    }

    private final PickerMode mode;
    private final Consumer<ItemStack> itemCallback;
    private final Consumer<IBlockState> blockCallback;
    private final BiConsumer<String, String> mobCallback;

    private final UISearchList<String> list;
    private final UIElement itemPanel;
    private final UIElement blockPanel;
    private final UIElement blockPropertiesWrap;
    private final UIElement blockProperties;
    private final UIElement mobPanel;
    private final UIItemHotbar hotbar;
    private final UITrackpad itemCount;
    private final UITextbox itemName;
    private final UITextarea itemNbt;
    private final UITextarea<TextLine> mobNbt;

    private ItemStack itemStack = ItemStack.EMPTY;
    private IBlockState blockState = Blocks.AIR.getDefaultState();
    private String mobNbtText = "";
    private String selectedId = "";

    public static UIUnifiedPickOverlayPanel forItem(Consumer<ItemStack> callback, ItemStack current)
    {
        return new UIUnifiedPickOverlayPanel(PickerMode.ITEM, callback, null, null, current == null ? ItemStack.EMPTY : current.copy(), null, "", "");
    }

    public static UIUnifiedPickOverlayPanel forBlock(Consumer<IBlockState> callback, IBlockState current)
    {
        return new UIUnifiedPickOverlayPanel(PickerMode.BLOCK, null, callback, null, ItemStack.EMPTY, current == null ? Blocks.AIR.getDefaultState() : current, "", "");
    }

    /**
     * Mob picker: the entity type on the left, its spawn NBT on the right. Unlike an item's,
     * that NBT is authored by hand rather than derived from the pick, so it survives switching
     * between types.
     */
    public static UIUnifiedPickOverlayPanel forMob(BiConsumer<String, String> callback, String mobID, String mobNBT)
    {
        return new UIUnifiedPickOverlayPanel(PickerMode.MOB, null, null, callback, ItemStack.EMPTY, Blocks.AIR.getDefaultState(), mobID == null ? "" : mobID, mobNBT == null ? "" : mobNBT);
    }

    private UIUnifiedPickOverlayPanel(PickerMode mode, Consumer<ItemStack> itemCallback, Consumer<IBlockState> blockCallback, BiConsumer<String, String> mobCallback, ItemStack itemStack, IBlockState blockState, String mobID, String mobNbt)
    {
        super(titleFor(mode));

        this.mode = mode;
        this.itemCallback = itemCallback;
        this.blockCallback = blockCallback;
        this.mobCallback = mobCallback;
        this.itemStack = itemStack;
        this.blockState = blockState;
        this.mobNbtText = mobNbt;

        this.list = new UISearchList<>(new RegistryIdList((values) ->
        {
            if (values.isEmpty())
            {
                return;
            }

            this.selectId(values.get(0));
        }, mode));
        this.list.label(UIKeys.GENERAL_SEARCH);
        this.list.list.background();

        this.itemPanel = new UIElement();
        this.itemPanel.relative(this.content).xy(PADDING, PADDING).w(1F, -PADDING * 2).h(1F, -PADDING * 2);
        this.itemPanel.setVisible(mode == PickerMode.ITEM);

        this.blockPanel = new UIElement();
        this.blockPanel.relative(this.content).xy(PADDING, PADDING).w(1F, -PADDING * 2).h(1F, -PADDING * 2);
        this.blockPanel.setVisible(mode == PickerMode.BLOCK);

        this.mobPanel = new UIElement();
        this.mobPanel.relative(this.content).xy(PADDING, PADDING).w(1F, -PADDING * 2).h(1F, -PADDING * 2);
        this.mobPanel.setVisible(mode == PickerMode.MOB);

        this.blockPropertiesWrap = new UIElement();
        this.blockPropertiesWrap.relative(this.blockPanel).x(0.5F, GAP).y(0).w(0.5F, -GAP).h(1F);
        this.hotbar = new UIItemHotbar(UIUnifiedPickOverlayPanel::playerHotbarStack, UIUnifiedPickOverlayPanel::playerSelectedSlot, this::pickFromHotbar);
        this.hotbar.relative(this.itemPanel).x(0).y(1F, -HOTBAR_HEIGHT).w(1F).h(HOTBAR_HEIGHT);

        this.itemName = new UITextbox(1000, (value) ->
        {
            if (this.mode != PickerMode.ITEM)
            {
                return;
            }

            if (value.isEmpty()) this.itemStack.clearCustomName();
            else this.itemStack.setStackDisplayName(value);
            this.acceptItem(this.itemStack.copy());
            this.updateItemNbt();
        });
        this.itemCount = new UITrackpad((v) ->
        {
            if (this.mode != PickerMode.ITEM)
            {
                return;
            }

            this.itemStack.setCount(v.intValue());
            this.acceptItem(this.itemStack.copy());
            this.updateItemNbt();
        });
        this.itemCount.integer().limit(1, 64, true);
        this.itemNbt = new UITextarea((v) ->
        {
            if (this.mode != PickerMode.ITEM)
            {
                return;
            }

            try
            {
                NBTTagCompound nbt = JsonToNBT.getTagFromJson(v.toString());
                ItemStack parsed = new ItemStack(nbt);

                this.acceptItem(parsed);

                String id = parsed.getItem().getRegistryName().toString();

                if (!id.equals(this.selectedId))
                {
                    this.selectedId = id;
                    this.list.list.setCurrentScroll(id);
                }
            }
            catch (Exception e)
            {}
        }).background();
        this.itemNbt.wrap();

        this.mobNbt = new UITextarea<>((v) ->
        {
            if (this.mode != PickerMode.MOB)
            {
                return;
            }

            this.mobNbtText = v;
            this.acceptMob(this.selectedId, this.mobNbtText);
        });
        this.mobNbt.background().wrap();

        this.blockProperties = UI.scrollView(4, 0);
        this.blockProperties.relative(this.blockPropertiesWrap).xy(0, HEADER_HEIGHT).w(1F).h(1F, -HEADER_HEIGHT);

        if (mode == PickerMode.ITEM)
        {
            UIElement fields = UI.column(5, 6, this.itemName, this.itemCount);

            fields.relative(this.itemPanel).y(1F).w(0.5F, -GAP).anchorY(1F);
            this.hotbar.relative(this.itemPanel).x(0.5F, GAP).y(1F, -HOTBAR_HEIGHT).w(0.5F, -GAP).h(HOTBAR_HEIGHT);
            this.itemNbt.relative(this.itemPanel).x(0.5F, GAP).y(0).w(0.5F, -GAP).h(1F, -HOTBAR_HEIGHT - GAP);
            this.list.relative(this.itemPanel).xy(0, 0).w(0.5F, -GAP).hTo(fields.area, 0F, -GAP);

            this.itemPanel.add(this.itemNbt, fields, this.list, this.hotbar);
        }
        else if (mode == PickerMode.MOB)
        {
            UIElement nbtWrap = new UIElement();

            nbtWrap.relative(this.mobPanel).x(0.5F, GAP).y(0).w(0.5F, -GAP).h(1F);
            nbtWrap.add(UI.label(UIKeys.FORMS_EDITORS_MOB_NBT).relative(nbtWrap).xy(0, 0).w(1F).h(HEADER_HEIGHT));

            this.mobNbt.relative(nbtWrap).xy(0, HEADER_HEIGHT).w(1F).h(1F, -HEADER_HEIGHT);
            nbtWrap.add(this.mobNbt);

            this.list.relative(this.mobPanel).xy(0, 0).w(0.5F, -GAP).h(1F);
            this.mobPanel.add(this.list, nbtWrap);
        }
        else
        {
            this.hotbar.relative(this.blockPropertiesWrap).x(0).y(1F, -HOTBAR_HEIGHT).w(1F).h(HOTBAR_HEIGHT);
            this.list.relative(this.blockPanel).xy(0, 0).w(0.5F, -GAP).h(1F);
            this.blockPropertiesWrap.add(UI.label(UIKeys.FORMS_EDITORS_BLOCK_PROPERTIES).relative(this.blockPropertiesWrap).xy(0, 0).w(1F).h(HEADER_HEIGHT));
            this.blockPropertiesWrap.add(this.blockProperties);
            this.blockProperties.h(1F, -HEADER_HEIGHT - HOTBAR_HEIGHT - GAP);
            this.blockPanel.add(this.list, this.blockPropertiesWrap, this.hotbar);
        }

        if (mode == PickerMode.ITEM)
        {
            this.content.add(this.itemPanel);
        }
        else if (mode == PickerMode.MOB)
        {
            this.content.add(this.mobPanel);
        }
        else
        {
            this.content.add(this.blockPanel);
        }

        if (mode == PickerMode.ITEM)
        {
            this.selectedId = this.itemStack.getItem().getRegistryName().toString();
            this.itemCount.limit(1, this.itemStack.getMaxStackSize(), true).setValue(this.itemStack.getCount());
            this.itemName.setText(this.itemStack.getDisplayName());
            this.updateItemNbt();
        }
        else if (mode == PickerMode.MOB)
        {
            this.selectedId = mobID;
            this.mobNbt.setText(this.mobNbtText);
        }
        else
        {
            this.selectedId = this.blockState.getBlock().getRegistryName().toString();
            this.fillBlockProperties(this.blockState);
        }

        this.refreshEntries();
    }

    private void refreshEntries()
    {
        this.list.list.clear();

        List<String> source = this.mode == PickerMode.ITEM ? ITEM_IDS : (this.mode == PickerMode.MOB ? MOB_IDS : BLOCK_IDS);

        for (String id : source)
        {
            this.list.list.add(id);
        }

        if (!this.selectedId.isEmpty())
        {
            this.list.list.setCurrentScroll(this.selectedId);
        }
    }

    private void selectId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return;
        }

        this.selectedId = id;

        if (this.mode == PickerMode.ITEM)
        {
            Item item = Item.REGISTRY.getObject(new ResourceLocation(id));
            ItemStack selected;

            if (this.itemStack != null && !this.itemStack.isEmpty() && this.itemStack.getItem() == item)
            {
                selected = this.itemStack.copy();
            }
            else
            {
                selected = new ItemStack(item);

                selected.setCount(Math.max(1, this.itemStack.getCount()));

                if (this.itemStack.hasDisplayName())
                {
                    selected.setStackDisplayName(this.itemStack.getDisplayName());
                }
            }

            this.acceptItem(selected);
            this.itemCount.limit(1, selected.getMaxStackSize(), true).setValue(selected.getCount());
            this.itemName.setText(selected.getDisplayName());
            this.updateItemNbt();
        }
        else if (this.mode == PickerMode.MOB)
        {
            this.acceptMob(id, this.mobNbtText);
        }
        else
        {
            Block block = Block.REGISTRY.getObject(new ResourceLocation(id));
            IBlockState selectedState = block.getDefaultState();

            if (this.blockState != null && this.blockState.getBlock() == block)
            {
                selectedState = this.blockState;
            }

            this.acceptBlock(selectedState);
            this.fillBlockProperties(this.blockState);
        }
    }

    private void acceptItem(ItemStack stack)
    {
        this.itemStack = stack.copy();

        if (this.itemCallback != null)
        {
            this.itemCallback.accept(this.itemStack.copy());
        }
    }

    private void acceptBlock(IBlockState state)
    {
        this.blockState = state;

        if (this.blockCallback != null)
        {
            this.blockCallback.accept(this.blockState);
        }
    }

    private void acceptMob(String id, String nbt)
    {
        this.selectedId = id;
        this.mobNbtText = nbt;

        if (this.mobCallback != null)
        {
            this.mobCallback.accept(this.selectedId, this.mobNbtText);
        }
    }

    private void updateItemNbt()
    {
        this.itemNbt.setText(this.itemStack.writeToNBT(new NBTTagCompound()).toString());
    }

    private void fillBlockProperties(IBlockState state)
    {
        this.blockProperties.removeAll();

        if (state.getProperties().isEmpty())
        {
            this.blockProperties.add(UI.label(IKey.constant("-")));
        }
        else
        {
            for (IProperty<?> property : state.getPropertyKeys())
            {
                final UIButton[] buttonRef = new UIButton[1];
                buttonRef[0] = new UIButton(this.propertyLabel(state, property), (b) -> this.openPropertyContextMenu(buttonRef[0], property));
                UIButton button = buttonRef[0];

                button.tooltip(IKey.constant(property.getName()));
                this.blockProperties.add(button);
            }
        }

        /* Only the property list changed; its own flex is fixed, so the whole screen needn't relayout */
        this.blockProperties.invalidateLayout();
    }

    private void openPropertyContextMenu(UIButton button, IProperty<?> property)
    {
        UISimpleContextMenu menu = new UISimpleContextMenu()
        {
            @Override
            public void setMouse(UIContext context)
            {
                int w = 100;

                for (ContextAction action : this.actions.getList())
                {
                    w = Math.max(action.getWidth(context.batcher.getFont()), w);
                }

                int x = button.area.ex() + 2;
                int y = button.area.y;

                this.set(x, y, w, 0)
                    .h(this.actions.scroll.scrollSize)
                    .maxH(context.menu.height - 10)
                    .bounds(context.menu.overlay, 5);
            }
        };

        for (Object value : property.getAllowedValues())
        {
            IKey key = IKey.constant(value.toString());

            menu.actions.add(new ContextAction(Icons.BLOCK, key, () ->
            {
                IBlockState nextState = this.blockState.withProperty((IProperty) property, (Comparable) value);

                this.acceptBlock(nextState);
                this.fillBlockProperties(nextState);
            }));
        }

        this.getContext().replaceContextMenu(menu);
    }

    private IKey propertyLabel(IBlockState state, IProperty<?> property)
    {
        return IKey.constant(property.getName() + ": " + state.getValue(property));
    }

    /** A cell of the player's hotbar becomes the pick: the item itself, or the block it places. */
    private void pickFromHotbar(int slot)
    {
        ItemStack stack = playerHotbarStack(slot).copy();

        if (stack.isEmpty())
        {
            return;
        }

        if (this.mode == PickerMode.ITEM)
        {
            this.acceptItem(stack);
            this.selectId(stack.getItem().getRegistryName().toString());
        }
        else if (stack.getItem() instanceof ItemBlock blockItem)
        {
            IBlockState state = blockItem.getBlock().getStateFromMeta(stack.getMetadata());

            this.acceptBlock(state);
            this.selectId(state.getBlock().getRegistryName().toString());
        }
    }

    private static ItemStack playerHotbarStack(int slot)
    {
        Minecraft mc = Minecraft.getMinecraft();

        return mc.player == null ? ItemStack.EMPTY : mc.player.inventory.getStackInSlot(slot);
    }

    private static int playerSelectedSlot()
    {
        Minecraft mc = Minecraft.getMinecraft();

        return mc.player == null ? -1 : mc.player.inventory.currentItem;
    }

    /**
     * Registry id list with display name on the first line and id on the second (muted).
     * {@link UIList#filter(String)} matches both label and id via {@link #elementToString}.
     */
    private static final class RegistryIdList extends UIStringList
    {
        private final PickerMode mode;

        RegistryIdList(Consumer<List<String>> callback, PickerMode mode)
        {
            super(callback);

            this.mode = mode;
            this.scroll.scrollItemSize = LIST_ROW_HEIGHT;
        }

        private String labelFor(String id)
        {
            if (this.mode == PickerMode.ITEM)
            {
                return itemLabel(id);
            }

            return this.mode == PickerMode.MOB ? mobLabel(id) : blockLabel(id);
        }

        @Override
        protected String elementToString(UIContext context, int i, String element)
        {
            return this.labelFor(element) + " " + element;
        }

        @Override
        protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
        {
            var font = context.batcher.getFont();
            int lineH = font.getHeight();

            int iconLeft = x + LIST_ICON_GAP;
            int iconTop = y + (this.scroll.scrollItemSize - LIST_ICON_SLOT) / 2;

            context.batcher.box(iconLeft, iconTop, iconLeft + LIST_ICON_SLOT, iconTop + LIST_ICON_SLOT, Colors.A25);
            context.batcher.box(iconLeft + 1, iconTop + 1, iconLeft + LIST_ICON_SLOT - 1, iconTop + LIST_ICON_SLOT - 1, Colors.A12);

            ItemStack stack = previewStackFor(this.mode, element);

            if (!stack.isEmpty())
            {
                MatrixStack matrices = context.batcher.getContext().getMatrices();


                matrices.push();

                context.batcher.getContext().drawItem(stack, iconLeft + 1, iconTop + 1);
                context.batcher.getContext().drawItemInSlot(context.batcher.getFont().getRenderer(), stack, iconLeft + 1, iconTop + 1);

                matrices.pop();
            }
            else if (this.mode == PickerMode.MOB)
            {
                context.batcher.icon(Icons.MORPH, Colors.A50 | Colors.WHITE, iconLeft + 1, iconTop + 1);
            }

            int textX = iconLeft + LIST_ICON_SLOT + LIST_ICON_GAP;
            int maxW = this.area.w - (textX - x) - LIST_ICON_GAP;
            if (maxW < 8)
            {
                maxW = 8;
            }

            String title = font.limitToWidth(this.labelFor(element), maxW);
            String idLine = font.limitToWidth(element, maxW);
            int colorTitle = RowStyle.textColor(hover || selected);
            int colorId = hover ? Colors.LIGHTER_GRAY : Colors.GRAY;

            int padY = (this.scroll.scrollItemSize - (lineH * 2 + 2)) / 2;
            if (padY < 2)
            {
                padY = 2;
            }

            context.batcher.textShadow(title, textX, y + padY, colorTitle);
            context.batcher.textShadow(idLine, textX, y + padY + lineH + 1, colorId);
        }
    }
}
