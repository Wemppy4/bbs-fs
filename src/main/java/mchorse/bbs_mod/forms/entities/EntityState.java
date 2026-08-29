package mchorse.bbs_mod.forms.entities;

import net.minecraft.entity.EntityPose;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A boolean state an entity can be in, together with the way to read it off one and put it
 * back on another.
 *
 * <p>These used to be spelled out one by one everywhere they were needed - a keyframe channel,
 * the recorder, the playback, {@link IEntity#copy(IEntity)}, the timeline's icon table - so a
 * new state cost the same edit in eight files and was only as complete as the person adding it
 * was thorough. The table is the list; whoever needs all the states walks it.</p>
 *
 * <p>The state belongs to the entity rather than to the replay: a replay merely writes it down
 * and hands it back. That's why this lives beside {@link IEntity} and not next to the keyframe
 * channels that store it.</p>
 */
public enum EntityState
{
    SNEAKING("sneaking", IEntity::isSneaking, IEntity::setSneaking),
    SPRINTING("sprinting", IEntity::isSprinting, IEntity::setSprinting),
    GROUNDED("grounded", IEntity::isOnGround, IEntity::setOnGround),
    SWIMMING("swimming", IEntity::isSwimming, IEntity::setSwimming),
    RIDING("riding", IEntity::isRiding, IEntity::setRiding),
    FLYING("flying", IEntity::isFlying, IEntity::setFlying),
    GLIDING("gliding", IEntity::isFallFlying, IEntity::setFallFlying);

    /**
     * {@code Entity.FALL_FLYING_FLAG_INDEX} - the tracked bit {@code isFallFlying()} reads, and
     * the only way to tell a client to spread an entity's wings. Vanilla keeps it protected, and
     * the invoker that writes it is an interface mixin, which may not hold a constant at all, so
     * it sits here with the rest of what a state is.
     */
    public static final int FALL_FLYING_FLAG = 7;

    /** Name of the state, and the id of the keyframe channel a replay stores it in. */
    public final String id;

    private final Predicate<IEntity> getter;
    private final BiConsumer<IEntity, Boolean> setter;

    private EntityState(String id, Predicate<IEntity> getter, BiConsumer<IEntity, Boolean> setter)
    {
        this.id = id;
        this.getter = getter;
        this.setter = setter;
    }

    public boolean get(IEntity entity)
    {
        return this.getter.test(entity);
    }

    public void set(IEntity entity, boolean value)
    {
        this.setter.accept(entity, value);
    }

    /**
     * Whether a recorded state reads as on.
     *
     * <p>A state is stored as a number so it can live in a keyframe channel, which means a
     * hand-edited or eased channel can hand back anything at all - including values below zero,
     * where a bezier undershoots between two keys that are both off. Everything that plays a
     * replay back asks this rather than comparing on its own, so the preview stub, the actor and
     * the first person player can't disagree about what a frame says.</p>
     */
    public static boolean isOn(double value)
    {
        return value > 0D;
    }

    /**
     * The vanilla pose the states add up to.
     *
     * <p>One reading, so the preview stub, the actor being played back and the mob a mob form
     * renders through can't end up in three different poses from the same frame. The order is
     * vanilla's own: lying down beats standing tall, and a crouch is what's left.</p>
     */
    public static EntityPose pose(IEntity entity)
    {
        return pose(entity.isFallFlying(), entity.isSwimming(), entity.isSneaking());
    }

    /** For places that hold the three states loose rather than an entity - a replay's frame. */
    public static EntityPose pose(boolean gliding, boolean swimming, boolean sneaking)
    {
        if (gliding)
        {
            return EntityPose.FALL_FLYING;
        }
        else if (swimming)
        {
            return EntityPose.SWIMMING;
        }
        else if (sneaking)
        {
            return EntityPose.CROUCHING;
        }

        return EntityPose.STANDING;
    }
}
