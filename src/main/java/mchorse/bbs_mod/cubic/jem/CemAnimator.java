package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;

import java.util.Collections;
import java.util.List;

/**
 * The animator stage of an OptiFine CEM model: vanilla's frame, then the model's {@link CemAnimation}
 * program on top of it, with its own {@link CemState}. It sits where the procedural animator does in
 * the channels pipeline (rest &rarr; animator &rarr; default pose &rarr; form pose), so the form's pose
 * and the film's keyframes layer on top of the live animation additively, exactly like on any other
 * model. An animator lives per form renderer, which is what makes the state per instance.
 *
 * <p>The vanilla frame is not decoration: OptiFine evaluates CEM statements over the frame vanilla
 * just posed, so a bone's model variables arrive holding vanilla's angles, positions and flags. Packs
 * rely on it — Fresh Animations reads the parts it leaves empty (the fox's flat body, the hoglin's
 * bowed head, the blaze's orbiting rods), Fresh Moves layers on top and says so outright
 * ({@code varb.use_vanilla_leg_animations}). The frame comes from an {@link ICemVanillaStage}: the
 * game's own model of the entity, posed by the game's own code ({@code CemVanillaStage}). Without one —
 * a probe, or an entity the game has no model for — the program evaluates over the rest pose.</p>
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
    private final CemAnimation program;

    /** The vanilla stage, or null for a program run with no game under it. */
    private final ICemVanillaStage stage;

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

    /**
     * The states the form sets by hand, refilled by the renderer each frame — see {@link CemStatus}.
     * It lives on the animator rather than on the program because a program is shared by every form
     * using that model, while an animator is one form's.
     */
    public final CemStatus status = new CemStatus();

    /** The preview clock, in ticks, off wall time — a preview has no entity age to follow. */
    private double previewTicks;
    private long previewNanos;

    /** The preview tick the stage was last stepped on: a preview has no tick of its own, so the stage gets one per tick of the clock. */
    private int previewTicked = -1;

    public CemAnimator(CemAnimation program, ICemVanillaStage stage)
    {
        this.program = program;
        this.stage = stage;
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
        boolean inGui = entity == null;

        if (inGui)
        {
            /* The preview keeps its own clock, and the frame's place in it is its own too: the game's
             * partial tick belongs to the game's ticks, which the preview's are not in step with, and
             * mixing the two made the preview's time saw back and forth once a tick. */
            entity = this.preview();
            transition = (float) (this.previewTicks - Math.floor(this.previewTicks));
        }

        CemVanillaSeed seed = this.stage == null ? null : this.stage.seed(entity, transition);

        this.program.apply(this.state(entity), entity, transition, inGui, this.status, seed);
    }

    /** The stand-in entity, its clock stepped to now — and the stage stepped with it, once a tick. */
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

        int tick = (int) this.previewTicks;

        this.preview.setAge(tick);

        if (this.stage != null && tick != this.previewTicked)
        {
            this.previewTicked = tick;
            this.stage.tick(this.preview);
        }

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
        if (this.stage != null)
        {
            this.stage.tick(entity);
        }
    }
}
