package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Animator class
 * 
 * This class is responsible for applying currently running actions onto 
 * form (more specifically onto an armature).
 */
public class Animator implements IAnimator
{
    /* Actions */
    public ActionPlayback idle;
    public ActionPlayback running;
    public ActionPlayback sprinting;
    public ActionPlayback crouching;
    public ActionPlayback crouchingIdle;
    public ActionPlayback dying;
    public ActionPlayback falling;

    public ActionPlayback swimming;
    public ActionPlayback swimmingIdle;
    public ActionPlayback riding;
    public ActionPlayback ridingIdle;
    public ActionPlayback flying;
    public ActionPlayback flyingIdle;

    public ActionPlayback jump1;
    public ActionPlayback jump2;
    public ActionPlayback swipe;
    public ActionPlayback hurt;
    public ActionPlayback land;
    public ActionPlayback shoot;
    public ActionPlayback consume;

    public ActionPlayback basePre;
    public ActionPlayback basePost;

    /* Action pipeline properties */
    public ActionPlayback active;
    public ActionPlayback lastActive;
    public List<ActionPlayback> actions = new ArrayList<>();

    public double prevX = Float.MAX_VALUE;
    public double prevZ = Float.MAX_VALUE;
    public double prevMY;
    public float prevHandSwing;

    /* States */
    public boolean wasOnGround = true;
    public int jumpingCounter;

    private IModelInstance model;

    @Override
    public List<String> getActions()
    {
        return Arrays.asList(
            "idle", "running", "sprinting", "crouching", "crouching_idle", "dying", "falling",
            "swimming", "swimming_idle", "riding", "riding_idle", "flying", "flying_idle",
            "swipe", "jump", "hurt", "land", "shoot", "consume", "base_pre", "base_post"
        );
    }

    @Override
    public void setup(IModelInstance model, ActionsConfig actions, boolean fade)
    {
        this.model = model;

        this.idle = this.createAction(this.idle, actions.getConfig("idle"), true);
        this.running = this.createAction(this.running, actions.getConfig("running"), true);
        this.sprinting = this.createAction(this.sprinting, actions.getConfig("sprinting"), true);
        this.crouching = this.createAction(this.crouching, actions.getConfig("crouching"), true);
        this.crouchingIdle = this.createAction(this.crouchingIdle, actions.getConfig("crouching_idle"), true);
        this.dying = this.createAction(this.dying, actions.getConfig("dying"), false);
        this.falling = this.createAction(this.falling, actions.getConfig("falling"), true);

        this.swimming = this.createAction(this.swimming, actions.getConfig("swimming"), true);
        this.swimmingIdle = this.createAction(this.swimmingIdle, actions.getConfig("swimming_idle"), true);
        this.riding = this.createAction(this.riding, actions.getConfig("riding"), true);
        this.ridingIdle = this.createAction(this.ridingIdle, actions.getConfig("riding_idle"), true);
        this.flying = this.createAction(this.flying, actions.getConfig("flying"), true);
        this.flyingIdle = this.createAction(this.flyingIdle, actions.getConfig("flying_idle"), true);

        this.swipe = this.createAction(this.swipe, actions.getConfig("swipe"), false);
        this.jump1 = this.createAction(this.jump1, actions.getConfig("jump"), false, 2);
        this.jump2 = this.createAction(this.jump2, actions.getConfig("jump_alt"), false, 2);
        this.hurt = this.createAction(this.hurt, actions.getConfig("hurt"), false, 3);
        this.land = this.createAction(this.land, actions.getConfig("land"), false);
        this.shoot = this.createAction(this.shoot, actions.getConfig("shoot"), true);
        this.consume = this.createAction(this.consume, actions.getConfig("consume"), true);

        this.basePre = this.createAction(this.basePre, actions.getConfig("base_pre"), true);
        this.basePost = this.createAction(this.basePost, actions.getConfig("base_post"), true);

        if (!fade)
        {
            this.setActiveAction(this.idle);

            if (this.idle != null)
            {
                this.idle.resetFade();
            }
        }
    }

    /**
     * Create an action with default priority
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping)
    {
        return this.createAction(old, config, looping, 1);
    }

    /**
     * Create an action playback based on given arguments. This method
     * is used for creating actions so it was easier to tell which
     * actions are missing. Beside that, you can pass an old action so
     * in form merging situation it wouldn't interrupt animation.
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping, int priority)
    {
        Animations animations = this.model == null ? null : this.model.getAnimations();

        if (animations == null)
        {
            return null;
        }

        Animation action = animations.get(config.name);

        /* If given action is missing, then omit creation of ActionPlayback */
        if (action == null)
        {
            return null;
        }

        /* If old is the same, then there is no point creating a new one */
        if (old != null && old.action == action)
        {
            old.config = config;
            old.setSpeed(1);

            return old;
        }

        return new ActionPlayback(action, config, looping, priority);
    }

    /**
     * Update animator. This method is responsible for updating action 
     * pipeline and also change current actions based on entity's state.
     */
    @Override
    public void update(IEntity target)
    {
        /* Fix issue with forms sudden running action */
        if (this.prevX == Float.MAX_VALUE)
        {
            this.prevX = target.getX();
            this.prevZ = target.getZ();
        }

        this.controlActions(target);

        /* Update primary actions */
        if (this.basePre != null)
        {
            this.basePre.update();
        }

        if (this.basePost != null)
        {
            this.basePost.update();
        }

        if (this.active != null)
        {
            this.active.update();
        }

        if (this.lastActive != null)
        {
            this.lastActive.update();
        }

        /* Update secondary actions */
        Iterator<ActionPlayback> it = this.actions.iterator();

        while (it.hasNext())
        {
            ActionPlayback action = it.next();

            action.update();

            if (action.finishedFading() && action.isFadingModeOut())
            {
                action.stopFade();
                it.remove();
            }
        }
    }

    /**
     * The action for a body that is riding, swimming or flying - the states that replace walking
     * outright rather than colour it - or null when the model has nothing to say about any of
     * them, in which case the walk chain gets the entity.
     *
     * <p>Every branch is conditional on the animation actually existing, so a model authored
     * before these states were recorded behaves exactly as it did: no animation, no takeover.
     * A model that defines only one half of a pair uses it for both, since a swimmer holding
     * still still reads better as a swimmer than as someone standing in water.</p>
     *
     * <p>Riding asks the entity's own displacement rather than the vehicle's - a rider is
     * carried along with what it rides, so its own movement already says whether the thing under
     * it is going anywhere.</p>
     */
    protected ActionPlayback pickState(IEntity target, boolean moves)
    {
        if (target.isRiding())
        {
            return either(moves ? this.riding : this.ridingIdle, moves ? this.ridingIdle : this.riding);
        }
        else if (target.isSwimming())
        {
            return either(moves ? this.swimming : this.swimmingIdle, moves ? this.swimmingIdle : this.swimming);
        }
        else if (target.isFlying() || target.isFallFlying())
        {
            return either(moves ? this.flying : this.flyingIdle, moves ? this.flyingIdle : this.flying);
        }

        return null;
    }

    private static ActionPlayback either(ActionPlayback preferred, ActionPlayback fallback)
    {
        return preferred == null ? fallback : preferred;
    }

    /**
     * This method is designed specifically to isolate any controlling
     * code (i.e. the ones that is responsible for switching between
     * actions).
     */
    protected void controlActions(IEntity target)
    {
        Vec3d velocity = target.getVelocity();
        double dx = target.getX() - this.prevX;
        double dz = target.getZ() - this.prevZ;
        final float threshold = 0.01F;
        boolean moves = Math.abs(dx) > threshold || Math.abs(dz) > threshold;

        ActionPlayback state = this.pickState(target, moves);

        if (state != null)
        {
            this.setActiveAction(state);
        }
        else
        {
            if (target.isSneaking())
            {
                this.setActiveAction(!moves ? this.crouchingIdle : this.crouching);
            }
            else if (!target.isOnGround() && velocity.y < 0 && target.getFallDistance() > 1.25
                && !target.isFlying() && !target.isFallFlying())
            {
                this.setActiveAction(this.falling);
            }
            else if (target.isSprinting() && this.sprinting != null)
            {
                this.setActiveAction(this.sprinting);
            }
            else
            {
                this.setActiveAction(!moves ? this.idle : this.running);
            }

            if (target.isOnGround() && !this.wasOnGround && /* !target.isSprinting() && */ this.prevMY < -0.5)
            {
                this.addAction(this.land);
            }
        }

        if (!target.isOnGround() && this.wasOnGround && Math.abs(velocity.y) > 0.2F)
        {
            ActionPlayback jump = this.jump1;

            if (this.jumpingCounter % 2 == 0 && this.jump2 != null)
            {
                jump = this.jump2;
            }

            this.addAction(jump);
            this.wasOnGround = false;

            this.jumpingCounter += 1;
        }

        float handSwingProgress = target.getHandSwingProgress(0F);

        if (handSwingProgress < this.prevHandSwing)
        {
            this.prevHandSwing = 0;
        }

        if (handSwingProgress > 0 && this.prevHandSwing == 0)
        {
            this.addAction(this.swipe);
        }

        this.prevX = target.getX();
        this.prevZ = target.getZ();
        this.prevMY = velocity.y;
        this.prevHandSwing = handSwingProgress;

        this.wasOnGround = target.isOnGround();
    }

    /**
     * Set current active (primary) action 
     */
    public void setActiveAction(ActionPlayback action)
    {
        if (this.active == action || action == null)
        {
            return;
        }

        if (this.active != null && action.priority < this.active.priority)
        {
            return;
        }

        if (this.active != null)
        {
            this.lastActive = this.active;
        }

        this.active = action;
        this.active.rewind();
        this.active.fadeIn();
    }

    public void addAction(ActionPlayback action)
    {
        this.addAction(action, true);
    }

    /**
     * Add an additional secondary action to the playback 
     */
    public void addAction(ActionPlayback action, boolean rewind)
    {
        if (action == null)
        {
            return;
        }

        if (this.actions.contains(action))
        {
            if (rewind)
            {
                action.rewind();
            }

            return;
        }

        action.rewind();
        action.fadeIn();
        this.actions.add(action);
    }

    /**
     * Apply currently running action pipeline onto given armature
     */
    @Override
    public void applyActions(IEntity target, IModelInstance armature, float transition)
    {
        if (this.basePre != null)
        {
            this.basePre.apply(target, armature.getModel(), transition, 1F, false);
        }

        if (this.lastActive != null && this.active.isFading())
        {
            this.lastActive.apply(target, armature.getModel(), transition, 1F, false);
        }

        if (this.active != null)
        {
            float fade = this.active.isFading() ? this.active.getFadeFactor(transition) : 1F;

            this.active.apply(target, armature.getModel(), transition, fade, false);
        }

        if (this.basePost != null)
        {
            this.basePost.apply(target, armature.getModel(), transition, 1F, false);
        }

        for (ActionPlayback action : this.actions)
        {
            if (action.isFading())
            {
                action.apply(target, armature.getModel(), transition, action.getFadeFactor(transition), true);
            }
            else
            {
                action.apply(target, armature.getModel(), transition, 1F, true);
            }
        }
    }

    @Override
    public void playAnimation(String name)
    {
        Animation animation = this.model.getAnimations().get(name);

        this.addAction(new ActionPlayback(animation, new ActionConfig(), false, -1));
    }
}