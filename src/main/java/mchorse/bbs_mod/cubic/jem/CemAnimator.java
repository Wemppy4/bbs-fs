package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;

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
 * <p>The program's {@code var.*}/{@code varb.*} are not the animator's: they belong to the entity, so
 * every CEM model on it reads what the others wrote — that is how a pack's cape follows its body (see
 * {@link CemVariables}). The frame clock in {@link CemState} does stay here, per model, and the state is
 * rebuilt whenever the entity under it changes.</p>
 *
 * <p>There are no named actions: a .jem carries no keyframe animations.</p>
 */
public class CemAnimator implements IAnimator
{
    private final ProceduralAnimator vanilla = new ProceduralAnimator();

    private final CemAnimation program;

    /** The store for an instance with no entity to share one with — a UI preview. */
    private final CemVariables own = new CemVariables();

    private CemState state;
    private CemVariables bound;

    /**
     * The entity a preview stands on. A form editor or a palette icon renders without one, and a CEM pack
     * asked about an entity that is not there reads every parameter as zero: not on the ground, not alive,
     * at the world origin. Fresh Animations' player poses exactly that — arms up, as if falling. This one
     * stands still, alive, on the ground, and its clock follows the preview's own frames so the idle
     * animation still breathes.
     */
    private final StubEntity preview = new StubEntity();

    /** The preview clock, in ticks, off wall time — a preview has no entity age to follow. */
    private double previewTicks;
    private long previewNanos;

    public CemAnimator(CemAnimation program)
    {
        this.program = program;
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
        boolean inGui = entity == null;

        if (inGui)
        {
            entity = this.preview();
        }

        this.vanilla.applyActions(entity, cubicModel, transition);
        this.program.apply(this.state(entity), entity, transition, inGui);
    }

    /** The stand-in entity, its clock stepped to now. */
    private IEntity preview()
    {
        long now = System.nanoTime();

        if (this.previewNanos != 0)
        {
            /* Capped like the animation clock is: a preview that was off screen for a minute should
             * resume, not fast-forward a minute of idle. */
            this.previewTicks += Math.min(CemState.MAX_FRAME_TIME, (now - this.previewNanos) / 1.0e9D) * 20D;
        }

        this.previewNanos = now;
        this.preview.setAge((int) this.previewTicks);

        return this.preview;
    }

    /**
     * This model's clock, bound to the store it draws its variables from: the entity's, or this
     * animator's own without one. A new store means a different entity, and a clock that says nothing
     * about it — so the state starts over rather than carrying a stranger's frame stamp.
     */
    private CemState state(IEntity entity)
    {
        CemVariables variables = entity == null ? null : entity.getCemVariables();

        if (variables == null)
        {
            variables = this.own;
        }

        if (this.state == null || this.bound != variables)
        {
            this.state = new CemState(variables);
            this.bound = variables;
        }

        return this.state;
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
