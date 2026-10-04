package mchorse.bbs_mod.actions;

import net.minecraft.entity.EntityLivingBase;
/** The real actor responsible for a fake player's item action, including nested uses. */
public final class ActionActorContext implements AutoCloseable
{
    private static final ThreadLocal<EntityLivingBase> ACTOR = new ThreadLocal<>();
    private final EntityLivingBase previous;
    private ActionActorContext(EntityLivingBase actor) { previous = ACTOR.get(); ACTOR.set(actor); }
    public static EntityLivingBase getActor() { return ACTOR.get(); }
    public static ActionActorContext enter(EntityLivingBase actor) { return new ActionActorContext(actor); }
    @Override public void close() { if (previous == null) ACTOR.remove(); else ACTOR.set(previous); }
}
