package mchorse.bbs_mod.morphing;

import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.LivingEntity;

/**
 * The hitbox a morphed entity gets from its form, for the mixins that intercept
 * {@code getBaseDimensions}.
 *
 * <p>There is more than one of them because vanilla's own override chain has more than one link:
 * {@link LivingEntity} answers for mobs, and since 1.21.9 {@code PlayerLikeEntity} sits between it
 * and {@code PlayerEntity} with an override of its own that never calls {@code super} — so a hook
 * on {@link LivingEntity} alone silently misses every player, which is the only entity most people
 * ever morph.</p>
 */
public class MorphHitbox
{
    /**
     * The dimensions the entity's morph asks for, or null when it asks for nothing — no morph, no
     * form, or a form that leaves the hitbox alone.
     *
     * @param dimensions what vanilla was about to return; only its {@link EntityDimensions#fixed()}
     *                   flag survives, so the override stays the same KIND of hitbox.
     */
    public static EntityDimensions override(LivingEntity entity, EntityDimensions dimensions)
    {
        if (!(entity instanceof IMorphProvider provider))
        {
            return null;
        }

        Form form = provider.getMorph().getForm();

        if (form == null || !form.hitbox.get())
        {
            return null;
        }

        float width = form.hitboxWidth.get();
        float height = form.hitboxHeight.get() * (entity.isSneaking() ? form.hitboxSneakMultiplier.get() : 1F);

        return dimensions.fixed()
            ? EntityDimensions.fixed(width, height)
            : EntityDimensions.changing(width, height);
    }
}
