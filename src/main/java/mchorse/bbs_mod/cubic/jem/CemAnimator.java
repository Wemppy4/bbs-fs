package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Collections;
import java.util.List;

/**
 * The animator stage of an OptiFine CEM model: the vanilla animation, then the model's
 * {@link CemAnimation} program on top of it, with its own {@link CemState}. It sits where
 * {@link ProceduralAnimator} does in the channels pipeline (rest &rarr; animator &rarr; default pose
 * &rarr; form pose), so the form's pose and the film's keyframes layer on top of the live animation
 * additively, exactly like on any other model. An animator lives per form renderer, which is what
 * makes the state per instance.
 *
 * <p>The vanilla stage is not decoration: OptiFine evaluates CEM statements over the frame vanilla
 * just posed, so a bone's model variables arrive holding vanilla's angles. Packs rely on it — Fresh
 * Animations overwrites every rotation channel and never notices, while Fresh Moves layers on top and
 * says so outright ({@code varb.use_vanilla_leg_animations}), leaving a bone at its incoming value
 * where it wants vanilla's. {@link ProceduralAnimator} is BBS's vanilla stage, keyed by the same bone
 * names ({@code head}, {@code body}, {@code right_arm}…) the packs use, so it is the one to run. With
 * no entity there is no vanilla frame either, and the program evaluates over the rest pose.</p>
 *
 * <p>There are no named actions: a .jem carries no keyframe animations.</p>
 */
public class CemAnimator implements IAnimator
{
    private final ProceduralAnimator vanilla = new ProceduralAnimator();

    private final CemAnimation program;
    private final CemState state;

    public CemAnimator(CemAnimation program)
    {
        this.program = program;
        this.state = program.createState();
    }

    @Override
    public List<String> getActions()
    {
        return Collections.emptyList();
    }

    @Override
    public void setup(IModelInstance model, ActionsConfig actionsConfig, boolean fade)
    {
        this.vanilla.setup(model, actionsConfig, fade);
    }

    @Override
    public void applyActions(IEntity entity, IModelInstance cubicModel, float transition)
    {
        this.vanilla.applyActions(entity, cubicModel, transition);
        this.program.apply(this.state, entity, transition);
    }

    @Override
    public void playAnimation(String name)
    {}

    @Override
    public void update(IEntity entity)
    {
        this.vanilla.update(entity);
    }
}
