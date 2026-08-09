package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading films written before the hotbar became nine channels.
 *
 * Those films stored the player's inventory once for the whole film, plus a single "item in
 * the main hand" channel per replay. Playback laid the inventory out at the start and then,
 * every tick, wrote the hand into whichever slot was selected at that moment - which is why
 * a hand item could end up smeared across cells it was never meant to touch.
 *
 * Rather than guess how those two sources meant to combine, this walks the old logic tick by
 * tick and writes down what it saw. The result plays back exactly like the old film did,
 * including the cases where the old rules were surprising - a hand key placed at tick 50, for
 * instance, was visible from the very first frame, since a channel outside its own range
 * answers with the nearest key.
 */
public class FilmLegacy
{
    public static final String LEGACY_MAIN_HAND = "item_main_hand";
    public static final String LEGACY_INVENTORY = "inventory";

    /**
     * @param film loaded film
     * @param data the data it was loaded from, which still holds the legacy fields - the film
     *             itself has no place to put them anymore
     */
    public static void migrateHotbar(Film film, BaseType data)
    {
        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();
        ListType replaysData = map.getList("replays");
        List<ItemStack> inventory = readInventory(map.get(LEGACY_INVENTORY));
        List<Replay> replays = film.replays.getList();

        for (int i = 0; i < replays.size() && i < replaysData.size(); i++)
        {
            Replay replay = replays.get(i);

            if (hasHotbar(replay.keyframes))
            {
                continue;
            }

            KeyframeChannel<ItemStack> hand = readLegacyHand(replaysData.get(i));
            /* The film's inventory was only ever handed to the first person player; actors
             * were given their main hand and nothing else. */
            List<ItemStack> start = replay.fp.get() ? inventory : null;

            if ((hand == null || hand.isEmpty()) && (start == null || start.isEmpty()))
            {
                continue;
            }

            migrate(replay.keyframes, hand, start);
            migrateWorn(replay.keyframes, start);
        }
    }

    private static void migrate(ReplayKeyframes keyframes, KeyframeChannel<ItemStack> hand, List<ItemStack> inventory)
    {
        ItemStack[] hotbar = new ItemStack[ReplayKeyframes.HOTBAR_SIZE];

        for (int i = 0; i < hotbar.length; i++)
        {
            ItemStack stack = inventory == null || i >= inventory.size() ? ItemStack.EMPTY : inventory.get(i);

            hotbar[i] = stack;

            if (!stack.isEmpty())
            {
                keyframes.hotbar.get(i).insert(0, stack.copy());
            }
        }

        /* A replay with no hand channel of its own was never dressed by one - it only ever
         * showed the inventory. Walking the old logic here would write the empty hand into the
         * selected slot on every tick, which is the very emptying this change is undoing. */
        if (hand == null || hand.isEmpty())
        {
            return;
        }

        int last = lastTick(hand, keyframes.selectedSlot);

        for (int tick = 0; tick <= last; tick++)
        {
            int slot = keyframes.getSelectedSlot(tick);
            ItemStack stack = hand.interpolate(tick, ItemStack.EMPTY);

            if (!ItemStack.areEqual(hotbar[slot], stack))
            {
                keyframes.hotbar.get(slot).insert(tick, stack.copy());

                hotbar[slot] = stack;
            }
        }
    }

    /**
     * The old film inventory covered the whole of the player's, so armour and the off hand
     * came out of it too - and a replay assembled by hand could have them there and nowhere
     * else. Where the replay's own channel has nothing to say, the inventory's is taken as its
     * starting key.
     */
    private static void migrateWorn(ReplayKeyframes keyframes, List<ItemStack> inventory)
    {
        if (inventory == null)
        {
            return;
        }

        for (EquipmentSlot slot : ReplayKeyframes.DRESS_SLOTS)
        {
            KeyframeChannel<ItemStack> channel = keyframes.getEquipmentChannel(slot);

            if (!channel.isEmpty())
            {
                continue;
            }

            int index = slot == EquipmentSlot.OFFHAND ? PlayerInventory.OFF_HAND_SLOT : PlayerInventory.MAIN_SIZE + slot.getEntitySlotId();
            ItemStack stack = index < inventory.size() ? inventory.get(index) : ItemStack.EMPTY;

            if (!stack.isEmpty())
            {
                channel.insert(0, stack.copy());
            }
        }
    }

    private static boolean hasHotbar(ReplayKeyframes keyframes)
    {
        for (KeyframeChannel<ItemStack> slot : keyframes.hotbar)
        {
            if (!slot.isEmpty())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Past the last key of both channels nothing changes anymore - a channel outside its range
     * keeps answering with its nearest key - so the walk can stop there.
     */
    private static int lastTick(KeyframeChannel<ItemStack> hand, KeyframeChannel<Integer> selectedSlot)
    {
        int last = 0;

        for (Keyframe<?> keyframe : hand.getKeyframes())
        {
            last = Math.max(last, (int) Math.ceil(keyframe.getTick()));
        }

        for (Keyframe<?> keyframe : selectedSlot.getKeyframes())
        {
            last = Math.max(last, (int) Math.ceil(keyframe.getTick()));
        }

        return last;
    }

    private static KeyframeChannel<ItemStack> readLegacyHand(BaseType replayData)
    {
        if (replayData == null || !replayData.isMap())
        {
            return null;
        }

        MapType keyframes = replayData.asMap().getMap("keyframes");

        if (!keyframes.has(LEGACY_MAIN_HAND))
        {
            return null;
        }

        KeyframeChannel<ItemStack> hand = new KeyframeChannel<>(LEGACY_MAIN_HAND, KeyframeFactories.ITEM_STACK);

        hand.fromData(keyframes.get(LEGACY_MAIN_HAND));

        return hand;
    }

    private static List<ItemStack> readInventory(BaseType data)
    {
        List<ItemStack> stacks = new ArrayList<>();

        if (data != null && data.isList())
        {
            for (BaseType type : data.asList())
            {
                ItemStack stack = KeyframeFactories.ITEM_STACK.fromData(type);

                stacks.add(stack == null ? ItemStack.EMPTY : stack);
            }
        }

        return stacks;
    }
}
