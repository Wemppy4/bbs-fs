package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionPlayback;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.math.molang.MolangParser;

/** Standalone checks of how the walk cycles switch; no Minecraft client required. */
public class AnimatorWalkPhaseTest
{
    /* Vanilla's ground friction: what's left of a body's speed each tick once the keys are let go */
    private static final double FRICTION = 0.546D;
    private static final double WALK = 0.21586D;
    private static final double SPRINT = 0.28061D;

    private static double x;

    public static void main(String[] args)
    {
        slowedBodyKeepsThePace();
        sprintStopKeepsTheStep();

        System.out.println("Animator walk phase checks passed");
    }

    private static void slowedBodyKeepsThePace()
    {
        Animator animator = animator();
        StubEntity entity = entity();

        walk(animator, entity, WALK / 2D, false, 30);

        ActionPlayback action = animator.active;
        double before = action.getTick(0F);

        walk(animator, entity, WALK / 2D, false, 1);

        double advance = wrap(action.getTick(0F) - before);

        check(Math.abs(advance - 1D) < 0.001D, "A slowed body still steps at the cycle's own pace: " + advance);
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
