package mchorse.bbs_mod.forms.renderers.mob;

import net.minecraft.client.render.entity.model.EntityModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The rig cache, keyed by the {@link EntityModel} INSTANCE.
 *
 * <p>Not by entity class, which is what the mob form used to do: a slim and a wide player are both
 * {@code OtherClientPlayerEntity} but render through two different {@code PlayerEntityModel}
 * instances, so whichever drew first won the cache and the pose was then written into the other
 * model's parts. Models are per-renderer singletons, so one entry per model is also the smallest
 * the cache can be.</p>
 */
public class MobRigs
{
    private static final Map<EntityModel, MobRig> CACHE = new WeakHashMap<>();

    public static MobRig of(EntityModel model)
    {
        if (model == null)
        {
            return null;
        }

        return CACHE.computeIfAbsent(model, MobRig::new);
    }

    public static void clear()
    {
        CACHE.clear();
    }
}
