package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Item use states of the bodies a film drives, so everything vanilla derives
 * from "this one is using that item" works on them too: the arm pose of the
 * procedural animator, and the model predicates of the held item - a drawn bow
 * bends and shows its arrow, a shield goes into its blocking model, a trident
 * lifts into its throwing model.
 *
 * <p>Film controllers publish each replay's state every render frame, and both
 * {@link ItemUsePose} and {@code ModelFormRenderer.renderItems} look it up.</p>
 */
public class ThirdPersonItemUse
{
    /**
     * Weak keys: the live Minecraft entity outlives the film, the stubs die
     * with their controller - either way stale states can't pile up.
     */
    private static final Map<Object, ItemUsePose.Use[]> STATES = new WeakHashMap<>();

    /**
     * The map key behind an {@link IEntity}: the live Minecraft entity when
     * there is one (world actors get wrapped into throwaway MCEntity shells,
     * so the shell itself can't be the key), the stub itself otherwise.
     */
    public static Object keyOf(IEntity entity)
    {
        return entity instanceof MCEntity mc ? mc.getMcEntity() : entity;
    }

    public static void set(Object holder, ItemUsePose.Use use, ItemUsePose.Use offUse)
    {
        if (holder == null)
        {
            return;
        }

        if (use == null && offUse == null)
        {
            STATES.remove(holder);
        }
        else
        {
            STATES.put(holder, new ItemUsePose.Use[] {use, offUse});
        }
    }

    public static ItemUsePose.Use get(IEntity entity, boolean mainHand)
    {
        ItemUsePose.Use[] states = entity == null ? null : STATES.get(keyOf(entity));

        return states == null ? null : states[mainHand ? 0 : 1];
    }

    public static void clear()
    {
        STATES.clear();
    }
}
