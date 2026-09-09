package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.forms.entities.IEntity;

/**
 * The vanilla stage under a CEM program: whatever poses the entity's vanilla model for the frame and
 * reports how its parts came out, so the program starts from vanilla's frame the way it does in
 * OptiFine. The game's side of it lives with the client ({@code CemVanillaStage}); a program run
 * without one — a probe, a test — starts from the rest pose instead.
 */
public interface ICemVanillaStage
{
    /** Once a tick: keep the stage's entity in step with the actor. */
    public void tick(IEntity entity);

    /** The vanilla frame for this actor and transition, or null when there is no vanilla model to pose. */
    public CemVanillaSeed seed(IEntity entity, float transition);
}
