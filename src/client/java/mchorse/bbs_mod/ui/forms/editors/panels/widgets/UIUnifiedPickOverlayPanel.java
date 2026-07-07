package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
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
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIUnifiedPickOverlayPanel extends UIOverlayPanel
{
    private static final int PADDING = 6;
    private static final int GAP = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final int HOTBAR_HEIGHT = 24;

    private static final List<String> ITEM_IDS = new ArrayList<>();
    private static final List<String> BLOCK_IDS = new ArrayList<>();

    private static final Map<String, String> ITEM_LABEL_CACHE = new HashMap<>();
    private static final Map<String, String> BLOCK_LABEL_CACHE = new HashMap<>();

    /** Row height: two text lines + vertical padding (see {@link RegistryIdList}). */
    private static final int LIST_ROW_HEIGHT = 32;

    /** Slot behind list icon; item drawn at +1 px inset (16×16). */
    private static final int LIST_ICON_SLOT = 18;
    private static final int LIST_ICON_GAP = 4;

    private static final Map<String, ItemStack> PREVIEW_STACK_CACHE = new HashMap<>();

    static
    {
        for (RegistryKey<Item> key : Registries.ITEM.getKeys())
        {
            ITEM_IDS.add(key.getValue().toString());
        }

        for (RegistryKey<Block> key : Registries.BLOCK.getKeys())
        {
            BLOCK_IDS.add(key.getValue().toString());
        }

        ITEM_IDS.sort(String::compareToIgnoreCase);
        BLOCK_IDS.sort(String::compareToIgnoreCase);
    }

    /**
     * Vanilla Minecraft UI language ({@code options.language}, e.g. {@code ru_ru}).
     * Label caches are keyed with this so names update when the player changes language.
     */
    private static String minecraftLanguageKey()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.options == null)
        {
            return "en_us";
        }

        String lang = mc.options.language;

        return lang == null || lang.isEmpty() ? "en_us" : lang;
    }

    private static String itemLabel(String id)
    {
        String cacheKey = minecraftLanguageKey() + "\0" + id;

        return ITEM_LABEL_CACHE.computeIfAbsent(cacheKey, (k) ->
        {
            try
            {
                Item item = Registries.ITEM.get(new Identifier(id));

                return new ItemStack(item).getName().getString();
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
                return Registries.BLOCK.get(new Identifier(id)).getName().getString();
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
                Identifier rid = new Identifier(id);

                if (mode == PickerMode.ITEM)
                {
                    return new ItemStack(Registries.ITEM.get(rid));
                }

                Block block = Registries.BLOCK.get(rid);
                Item item = block.asItem();

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
        BLOCK
    }

    private final PickerMode mode;
    private final Consumer<ItemStack> itemCallback;
    private final Consumer<BlockState> blockCallback;

    private final UISearchList<String> list;
    private final UIElement itemPanel;
    private final UIElement blockPanel;
    private final UIElement blockPropertiesWrap;
    private final UIElement blockProperties;
    private final UIItemHotbar hotbar;
    private final UITrackpad itemCount;
    private final UITextbox itemName;
    private final UITextarea itemNbt;

    private ItemStack itemStack = ItemStack.EMPTY;
    private BlockState blockState = Blocks.AIR.getDefaultState();
    private String selectedId = "";

    public static UIUnifiedPickOverlayPanel forItem(Consumer<ItemStack> callback, ItemStack current)
    {
        return new UIUnifiedPickOverlayPanel(PickerMode.ITEM, callback, null, current == null ? ItemStack.EMPTY : current.copy(), null);
    }

    public static UIUnifiedPickOverlayPanel forBlock(Consumer<BlockState> callback, BlockState current)
    {
        return new UIUnifiedPickOverlayPanel(PickerMode.BLOCK, null, callback, ItemStack.EMPTY, current == null ? Blocks.AIR.getDefaultState() : current);
    }

    private UIUnifiedPickOverlayPanel(PickerMode mode, Consumer<ItemStack> itemCallback, Consumer<BlockState> blockCallback, ItemStack itemStack, BlockState blockState)
    {
        super(mode == PickerMode.ITEM ? UIKeys.ACTIONS_ITEM_STACK : UIKeys.FORMS_EDITORS_BLOCK_TITLE);

        this.mode = mode;
        this.itemCallback = itemCallback;
        this.blockCallback = blockCallback;
        this.itemStack = itemStack;
        this.blockState = blockState;

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

        this.blockPropertiesWrap = new UIElement();
        this.blockPropertiesWrap.relative(this.blockPanel).x(0.5F, GAP).y(0).w(0.5F, -GAP).h(1F);
        this.hotbar = new UIItemHotbar();
        this.hotbar.relative(this.itemPanel).x(0).y(1F, -HOTBAR_HEIGHT).w(1F).h(HOTBAR_HEIGHT);

        this.itemName = new UITextbox(1000, (value) ->
        {
            if (this.mode != PickerMode.ITEM)
            {
                return;
            }

            this.itemStack.setCustomName(value.isEmpty() ? null : Text.literal(value));
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
                NbtCompound nbt = new StringNbtReader(new StringReader(v.toString())).parseCompound();
                ItemStack parsed = ItemStack.fromNbt(nbt);

                this.acceptItem(parsed);

                String id = Registries.ITEM.getId(parsed.getItem()).toString();

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
        else
        {
            this.content.add(this.blockPanel);
        }

        if (mode == PickerMode.ITEM)
        {
            this.selectedId = Registries.ITEM.getId(this.itemStack.getItem()).toString();
            this.itemCount.limit(1, this.itemStack.getMaxCount(), true).setValue(this.itemStack.getCount());
            this.itemName.setText(this.itemStack.getName().getString());
            this.updateItemNbt();
        }
        else
        {
            this.selectedId = Registries.BLOCK.getId(this.blockState.getBlock()).toString();
            this.fillBlockProperties(this.blockState);
        }

        this.refreshEntries();
    }

    private void refreshEntries()
    {
        this.list.list.clear();

        List<String> source = this.mode == PickerMode.ITEM ? ITEM_IDS : BLOCK_IDS;

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
            Item item = Registries.ITEM.get(new Identifier(id));
            ItemStack selected;

            if (this.itemStack != null && !this.itemStack.isEmpty() && this.itemStack.getItem() == item)
            {
                selected = this.itemStack.copy();
            }
            else
            {
                selected = new ItemStack(item);

                selected.setCount(Math.max(1, this.itemStack.getCount()));

                if (this.itemStack.hasCustomName())
                {
                    selected.setCustomName(this.itemStack.getName());
                }
            }

            this.acceptItem(selected);
            this.itemCount.limit(1, selected.getMaxCount(), true).setValue(selected.getCount());
            this.itemName.setText(selected.getName().getString());
            this.updateItemNbt();
        }
        else
        {
            Block block = Registries.BLOCK.get(new Identifier(id));
            BlockState selectedState = block.getDefaultState();

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

    private void acceptBlock(BlockState state)
    {
        this.blockState = state;

        if (this.blockCallback != null)
        {
            this.blockCallback.accept(this.blockState);
        }
    }

    private void updateItemNbt()
    {
        this.itemNbt.setText(ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, this.itemStack).result().map(Object::toString).orElse("{}"));
    }

    private void fillBlockProperties(BlockState state)
    {
        this.blockProperties.removeAll();

        if (state.getProperties().isEmpty())
        {
            this.blockProperties.add(UI.label(IKey.constant("-")));
        }
        else
        {
            for (Property<?> property : state.getProperties())
            {
                final UIButton[] buttonRef = new UIButton[1];
                buttonRef[0] = new UIButton(this.propertyLabel(state, property), (b) -> this.openPropertyContextMenu(buttonRef[0], property));
                UIButton button = buttonRef[0];

                button.tooltip(IKey.constant(property.getName()));
                this.blockProperties.add(button);
            }
        }

        if (this.getRoot() != null)
        {
            this.getRoot().resize();
        }
    }

    private void openPropertyContextMenu(UIButton button, Property<?> property)
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

        for (Object value : property.getValues())
        {
            IKey key = IKey.constant(value.toString());

            menu.actions.add(new ContextAction(Icons.BLOCK, key, () ->
            {
                BlockState nextState = this.blockState.with((Property) property, (Comparable) value);

                this.acceptBlock(nextState);
                this.fillBlockProperties(nextState);
            }));
        }

        this.getContext().replaceContextMenu(menu);
    }

    private IKey propertyLabel(BlockState state, Property<?> property)
    {
        return IKey.constant(property.getName() + ": " + state.get(property));
    }

    private class UIItemHotbar extends UIElement
    {
        private static final int SLOTS = 9;
        private static final int SLOT_SIZE = 20;
        private static final int SLOT_GAP = 2;

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (!this.area.isInside(context) || context.mouseButton != 0)
            {
                return false;
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.player == null)
            {
                return false;
            }

            int contentWidth = SLOTS * SLOT_SIZE + (SLOTS - 1) * SLOT_GAP;
            int startX = this.area.mx(contentWidth);
            int y = this.area.my(SLOT_SIZE);
            int index = (context.mouseX - startX) / (SLOT_SIZE + SLOT_GAP);

            if (index < 0 || index >= SLOTS)
            {
                return false;
            }

            int slotX = startX + index * (SLOT_SIZE + SLOT_GAP);

            if (context.mouseX < slotX || context.mouseX >= slotX + SLOT_SIZE || context.mouseY < y || context.mouseY >= y + SLOT_SIZE)
            {
                return false;
            }

            ItemStack stack = mc.player.getInventory().getStack(index).copy();

            if (stack.isEmpty())
            {
                return true;
            }

            if (UIUnifiedPickOverlayPanel.this.mode == PickerMode.ITEM)
            {
                UIUnifiedPickOverlayPanel.this.acceptItem(stack);
                UIUnifiedPickOverlayPanel.this.selectId(Registries.ITEM.getId(stack.getItem()).toString());
            }
            else if (stack.getItem() instanceof BlockItem blockItem)
            {
                BlockState state = blockItem.getBlock().getDefaultState();

                UIUnifiedPickOverlayPanel.this.acceptBlock(state);
                UIUnifiedPickOverlayPanel.this.selectId(Registries.BLOCK.getId(state.getBlock()).toString());
            }

            return true;
        }

        @Override
        public void render(UIContext context)
        {
            super.render(context);

            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.player == null)
            {
                return;
            }

            PlayerInventory inventory = mc.player.getInventory();
            int contentWidth = SLOTS * SLOT_SIZE + (SLOTS - 1) * SLOT_GAP;
            int startX = this.area.mx(contentWidth);
            int y = this.area.my(SLOT_SIZE);

            for (int i = 0; i < SLOTS; i++)
            {
                int x = startX + i * (SLOT_SIZE + SLOT_GAP);
                ItemStack stack = inventory.getStack(i);
                int border = i == inventory.selectedSlot ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.LIGHTER_GRAY;

                context.batcher.box(x, y, x + SLOT_SIZE, y + SLOT_SIZE, border);
                context.batcher.box(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, Colors.A50);

                if (!stack.isEmpty())
                {
                    MatrixStack matrices = context.batcher.getContext().getMatrices();
                    CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

                    matrices.push();
                    consumers.setUI(true);
                    context.batcher.getContext().drawItem(stack, x + 2, y + 2);
                    context.batcher.getContext().drawItemInSlot(context.batcher.getFont().getRenderer(), stack, x + 2, y + 2);
                    consumers.setUI(false);
                    matrices.pop();
                }

            }
        }
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
            return this.mode == PickerMode.ITEM ? itemLabel(id) : blockLabel(id);
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
                CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

                matrices.push();
                consumers.setUI(true);
                context.batcher.getContext().drawItem(stack, iconLeft + 1, iconTop + 1);
                context.batcher.getContext().drawItemInSlot(context.batcher.getFont().getRenderer(), stack, iconLeft + 1, iconTop + 1);
                consumers.setUI(false);
                matrices.pop();
            }

            int textX = iconLeft + LIST_ICON_SLOT + LIST_ICON_GAP;
            int maxW = this.area.w - (textX - x) - LIST_ICON_GAP;
            if (maxW < 8)
            {
                maxW = 8;
            }

            String title = font.limitToWidth(this.labelFor(element), maxW);
            String idLine = font.limitToWidth(element, maxW);
            int colorTitle = hover ? Colors.HIGHLIGHT : Colors.WHITE;
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
