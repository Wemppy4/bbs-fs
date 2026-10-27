package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionPlayback;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.math.molang.MolangParser;

/** Standalone checks that walk cycles go by the ground covered; no Minecraft client required. */
public class AnimatorPaceTest
{
    /* Vanilla's ground friction: what's left of a body's speed each tick once the keys are let go */
    private static final double FRICTION = 0.546D;
    private static final double WALK = 0.21586D;
    private static final double SPRINT = 0.28061D;

    private static double x;

    public static void main(String[] args)
    {
        walkingPlaysAsBefore();
        stoppingDoesNotStep();
        sprintStopKeepsTheStep();

        System.out.println("Animator pace checks passed");
    }

    private static void walkingPlaysAsBefore()
    {
        double walking = steadyAdvance(WALK, false);
        double sprinting = steadyAdvance(SPRINT, true);
        double slow = steadyAdvance(WALK / 2D, false);

        check(Math.abs(walking - 1D) < 0.001D, "Walking speed plays the cycle at its own pace: " + walking);
        check(Math.abs(sprinting - 1D) < 0.001D, "Sprinting speed plays the cycle at its own pace: " + sprinting);
        check(Math.abs(slow - 0.5D) < 0.001D, "Half the speed, half the steps: " + slow);
    }

    /** Ticks of the cycle one tick of steady movement plays */
    private static double steadyAdvance(double speed, boolean sprinting)
    {
        Animator animator = animator();
        StubEntity entity = entity();

        walk(animator, entity, speed, sprinting, 30);

        ActionPlayback action = animator.active;
        double before = action.getTick(0F);

        walk(animator, entity, speed, sprinting, 1);

        return wrap(action.getTick(0F) - before);
    }

    private static void stoppingDoesNotStep()
    {
        Animator animator = animator();
        StubEntity entity = entity();

        walk(animator, entity, WALK, false, 30);

        /* Where the legs are drawn at the end of the last walking tick */
        double before = animator.running.getTick(1F);
        double speed = WALK;
        double last = before;

        for (int i = 0; i < 20; i++)
        {
            speed *= FRICTION;
            walk(animator, entity, speed, false, 1);

            double now = animator.running.getTick(0F);

            check(Math.abs(wrap(now - last)) < 0.001D, "No pop between ticks while coasting: " + last + " -> " + now);
            last = animator.running.getTick(1F);
        }

        check(animator.active == animator.idle, "Coasting ends in idle");

        double after = wrap(animator.running.getTick(0F) - before);

        /* The body coasts ~0.26 blocks, about 1.2 ticks of the cycle; by the clock it was ~10 */
        check(after < 1.5D, "The legs stop with the body: " + after + " ticks of the cycle after letting go");
    }

    private static void sprintStopKeepsTheStep()
    {
        Animator animator = animator();
        StubEntity entity = entity();

        walk(animator, entity, SPRINT, true, 33);

        double phase = animator.sprinting.getTick(0F) / 20D;

        walk(animator, entity, SPRINT * FRICTION, false, 1);

        check(animator.active == animator.running, "Letting go of sprint while coasting runs");

        double running = animator.running.getTick(0F) / 20D;

        check(running > 0.05D && Math.abs(running - phase) < 0.1D, "Running picks up the sprint's step: " + phase + " -> " + running);
    }

    private static Animator animator()
    {
        Animator animator = new Animator();

        animator.idle = playback();
        animator.running = playback();
        animator.sprinting = playback();
        animator.crouching = playback();
        animator.crouchingIdle = playback();

        animator.setActiveAction(animator.idle);

        return animator;
    }

    private static ActionPlayback playback()
    {
        Animation animation = new Animation("cycle", new MolangParser());

        animation.setLength(1D);

        return new ActionPlayback(animation, new ActionConfig("cycle"), true);
    }

    private static StubEntity entity()
    {
        /* Without a world and the item stacks the default one fills, which need the registries */
        StubEntity entity = new StubEntity(null);

        x = 0D;
        entity.setOnGround(true);
        entity.setPosition(0D, 0D, 0D);

        return entity;
    }

    private static void walk(Animator animator, StubEntity entity, double speed, boolean sprinting, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            x += speed;
            entity.setSprinting(sprinting);
            entity.setPosition(x, 0D, 0D);
            animator.update(entity);
        }
    }

    private static double wrap(double ticks)
    {
        return ((ticks % 20D) + 20D) % 20D;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
