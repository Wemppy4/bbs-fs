package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;

import java.util.ArrayList;
import java.util.List;

/**
 * The item use running in a replay's hand at a given tick, read off its "use
 * item" action clips.
 *
 * <p>A live player has an item use state vanilla ticks; a take has clips. The
 * server side of such a clip fires {@code stack.use} once - here only WHEN it
 * fired and for how long is read, never that path, so everything driven by it
 * (the arm pose, the item's own model, the crumbs of a meal) scrubs with the
 * cursor like the rest of the film.</p>
 */
public class ReplayItemUse
{
    /**
     * Which animation: the item actually displayed in the hand decides, and the
     * clip's own stack is the fallback (a vanilla item wrapped into a form has no
     * use action of its own). How long: the clip's duration when the author set
     * one, otherwise the item's natural vanilla time - the animation itself always
     * runs at vanilla speed and simply holds its end pose while the clip lasts,
     * exactly like holding a drawn bow.
     */
    public static ItemUsePose.Use compute(Replay replay, float tick, boolean mainHand, LivingEntity user)
    {
        ItemStack displayed = mainHand
            ? itemAt(replay.keyframes.hotbar.get(replay.keyframes.getSelectedSlot(tick)), tick)
            : itemAt(replay.keyframes.offHand, tick);

        ItemUsePose.Use state = null;
        float latest = Float.NEGATIVE_INFINITY;
        List<Float> fires = new ArrayList<>();

        for (Clip clip : replay.actions.get())
        {
            if (!(clip instanceof UseItemActionClip useClip) || useClip.hand.get() != mainHand)
            {
                continue;
            }

            ItemStack clipStack = useClip.itemStack.get();
            UseAction action = displayed.getUseAction() != UseAction.NONE ? displayed.getUseAction() : clipStack.getUseAction();

            if (action == UseAction.NONE)
            {
                continue;
            }

            ItemStack timing = displayed.getUseAction() != UseAction.NONE ? displayed : clipStack;
            float window = useClip.duration.get() > 1 ? useClip.duration.get() : naturalWindow(action, timing, user);

            fires.clear();
            collectFires(useClip, tick, fires);

            for (float fire : fires)
            {
                if (tick < fire + window && fire > latest)
                {
                    latest = fire;
                    state = new ItemUsePose.Use(action, tick - fire, timing, window, user);
                }
            }
        }

        return state;
    }

    /** The stack a slot channel holds at given tick - a slot never fades, it switches. */
    public static ItemStack itemAt(KeyframeChannel<ItemStack> channel, float tick)
    {
        KeyframeSegment<ItemStack> segment = channel.find(tick);

        return segment == null ? ItemStack.EMPTY : segment.a.getValue();
    }

    /**
     * How long an untouched use clip animates: each item's own vanilla
     * saturation point - a bite takes its eating time, a bow reaches full pull
     * in 20 ticks, a crossbow in its pull time, a trident charges in 10.
     */
    private static float naturalWindow(UseAction action, ItemStack stack, LivingEntity user)
    {
        switch (action)
        {
            case EAT:
            case DRINK:
                int eating = ItemUsePose.maxUseTime(stack, user);

                return eating > 0 ? eating : 32F;
            case CROSSBOW:
                return Math.max(1, ItemUsePose.pullTime(stack, user));
            case SPEAR:
                return 10F;
            default:
                return 20F;
        }
    }

    /**
     * Every tick at or before {@code tick} where an action clip fires: its own
     * start, plus the repeats its frequency schedules inside its duration
     * (the same rule {@link ActionClip#apply} plays them by). Disabled clips
     * fire never.
     */
    private static void collectFires(ActionClip clip, float tick, List<Float> fires)
    {
        if (!clip.enabled.get())
        {
            return;
        }

        int start = clip.tick.get();

        if (tick < start)
        {
            return;
        }

        int frequency = clip.frequency.get();

        if (frequency <= 0)
        {
            fires.add((float) start);
        }
        else
        {
            float inside = Math.min(tick, start + clip.duration.get() - 1) - start;

            for (int k = 0; k * frequency <= inside; k++)
            {
                fires.add((float) (start + k * frequency));
            }
        }
    }
}
