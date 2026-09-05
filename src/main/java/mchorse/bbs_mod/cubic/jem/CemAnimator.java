package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Collections;
import java.util.List;

/**
 * The animator stage of an OptiFine CEM model: evaluates the model's procedural {@link CemAnimation}
 * program into the bones in place of keyframe actions, with its own {@link CemState}. It sits where
 * {@link mchorse.bbs_mod.cubic.animation.ProceduralAnimator} does in the channels pipeline
 * (rest &rarr; animator &rarr; default pose &rarr; form pose), so the form's pose and the film's keyframes
 * layer on top of the live animation additively, exactly like on any other model. An animator lives
 * per form renderer, which is what makes the state per instance.
 *
 * <p>There are no named actions: a .jem carries no keyframe animations.</p>
 */
public class CemAnimator implements IAnimator
{
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
    {}

    @Override
    public void applyActions(IEntity entity, IModelInstance cubicModel, float transition)
    {
        this.program.apply(this.state, entity, transition);
    }

    @Override
    public void playAnimation(String name)
    {}

    @Override
    public void update(IEntity entity)
    {}
}
