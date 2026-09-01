package mchorse.bbs_mod.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's entity flags are tracked data, which is what makes a client act on them - it's the
 * reason a played back actor sprints with particles rather than just moving fast. Gliding has
 * no public setter, only a flag, so a replay needs the raw one to spread an actor's wings.
 *
 * <p>An interface mixin may hold nothing but methods - a constant is a field, and mixin refuses
 * the whole class over one - so the flag's index lives on
 * {@link mchorse.bbs_mod.forms.entities.EntityState} instead.</p>
 */
@Mixin(Entity.class)
public interface EntityInvoker
{
    @Invoker("setFlag")
    public void bbs$setFlag(int index, boolean value);
}
