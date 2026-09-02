package mchorse.bbs_mod.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Roll is how far into a glide or a riptide the entity is - vanilla counts it up a tick at a
 * time while flying, and the elytra's dive and the trident's spin are both driven off it.
 *
 * <p>An entity played back from a replay is placed frame by frame rather than flown, so nothing
 * counts for it. The count is recorded and handed back instead, which is also the only version
 * of it that survives scrubbing to an arbitrary tick.</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityRollAccessor
{
    /* The same field yarn has renamed twice: fallFlyingTicks on 1.21.1, glidingTicks since 1.21.11.
     * Aiming at the old name is not a no-op — remapJar warns ("Cannot remap fallFlyingTicks..."),
     * and an accessor whose target does not exist takes the client down at mixin APPLY. */
    @Accessor("glidingTicks")
    public void bbs$setRoll(int roll);
}
