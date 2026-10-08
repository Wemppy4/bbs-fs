package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.Hotbar;
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
import java.util.TreeSet;

/**
 * Reading films written before the hotbar became one channel of whole-row keys.
 *
 * The oldest of them stored the player's inventory once for the whole film, plus a single "item in
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
    /** Move the old root-form value and track once; explicit replay data always wins. */
    public static void migrateAnchor(Replay replay, BaseType data)
    {
        if (!data.isMap()) return;
        MapType map = data.asMap();
        if (!map.has("anchor") && map.getMap("form").has("anchor"))
        {
            replay.anchor.fromData(map.getMap("form").get("anchor"));
        }

        var id = mchorse.bbs_mod.film.replays.tracks.TrackId.property("", "anchor");
        KeyframeChannel<?> legacy = replay.properties.get(id);
        if (legacy != null)
        {
            if (!map.getMap("keyframes").has("anchor") && legacy.getFactory() == KeyframeFactories.ANCHOR)
            {
                replay.keyframes.anchor.fromData(legacy.toData());
            }
            replay.properties.remove(id);
        }
    }

    public static final String LEGACY_MAIN_HAND = "item_main_hand";
    public static final String LEGACY_INVENTORY = "inventory";
    public static final String LEGACY_SLOT = "item_slot_";

    /**
     * Films written while the hotbar was nine channels, one per cell. Their keys become whole-row
     * keys, one at each tick where any cell had a key, and each cell of a row holds what its
     * channel played back at that tick - before its first key, a channel answered with that key -
     * so the film plays back as it did. A cell with no keys at all used to be left to the world;
     * in a row it is empty.
     */
    public static void migrateHotbarSlots(Replay replay, BaseType data)
    {
        if (!data.isMap() || !replay.keyframes.hotbar.isEmpty())
        {
            return;
        }

        MapType keyframes = data.asMap().getMap("keyframes");
        List<KeyframeChannel<ItemStack>> slots = new ArrayList<>();
        TreeSet<Float> ticks = new TreeSet<>();

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            KeyframeChannel<ItemStack> slot = new KeyframeChannel<>(LEGACY_SLOT + i, KeyframeFactories.ITEM_STACK);

            if (keyframes.has(slot.getId()))
            {
                slot.fromData(keyframes.get(slot.getId()));
            }

            for (Keyframe<ItemStack> keyframe : slot.getKeyframes())
            {
                if (keyframe.isEnabled())
                {
                    ticks.add(keyframe.getTick());
                }
            }

            slots.add(slot);
        }

        for (float tick : ticks)
        {
            replay.keyframes.hotbar.insert(tick, Hotbar.of((i) -> slots.get(i).interpolate(tick, ItemStack.EMPTY)));
        }
    }

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

            if (!replay.keyframes.hotbar.isEmpty())
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
        Hotbar hotbar = Hotbar.of((i) -> inventory == null || i >= inventory.size() ? ItemStack.EMPTY : inventory.get(i));

        if (!hotbar.isEmpty())
        {
            keyframes.hotbar.insert(0, hotbar.copy());
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

            if (!ItemStack.areEqual(hotbar.get(slot), stack))
            {
                hotbar.set(slot, stack);
                keyframes.hotbar.insert(tick, hotbar.copy());
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
