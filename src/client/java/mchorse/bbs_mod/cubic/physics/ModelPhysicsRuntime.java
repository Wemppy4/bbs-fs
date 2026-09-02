package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Orchestrates bone physics: owns the per-entity simulation state and feeds each chain to the
 * {@link ChainSolver}. The solver itself ({@link ChainSolver}, {@link ChainState}, {@link PhysicsForces})
 * holds the maths.
 */
public final class ModelPhysicsRuntime
{
    static final class InstanceState
    {
        public final Map<String, ChainState> chains = new HashMap<>();

        /**
         * The model the chains were last simulated against. States are keyed by form, not by model, so a
         * form swapping its model has to drop the sim built on the old skeleton instead of reusing it.
         */
        public String modelId;

        public InstanceState copy()
        {
            InstanceState copy = new InstanceState();

            copy.modelId = this.modelId;

            for (Map.Entry<String, ChainState> entry : this.chains.entrySet())
            {
                copy.chains.put(entry.getKey(), entry.getValue().copy());
            }

            return copy;
        }
    }

    /**
     * Simulation state per form, per entity. The form is identified by its path in the entity's form tree
     * ({@link FormUtils#getPath}, the same identity the film's physics tracks are keyed by), because a
     * {@link ModelInstance} is shared by every form using that model asset — keying by it collapsed body
     * parts that mirror their target and share a model (paired wings, twin braids) onto one state, where
     * the first one rendered simulated and the rest silently rendered its chains.
     */
    private static final WeakHashMap<IEntity, Map<String, InstanceState>> STATES = new WeakHashMap<>();

    /**
     * The simulation as it stood when the running authoring gesture began, or {@code null} when no
     * gesture is armed. See {@link #checkpoint()}.
     */
    private static WeakHashMap<IEntity, Map<String, InstanceState>> CHECKPOINT;

    private ModelPhysicsRuntime()
    {
    }

    public static void clearCache()
    {
        STATES.clear();
        CHECKPOINT = null;
    }

    /**
     * Snapshots the whole simulation so a rejected authoring gesture can put it back.
     *
     * <p>A transform gesture writes the edited pose live, frame by frame, and the sim follows it —
     * spinning a bone really does swing its chains and really does build up their velocity. Rejecting
     * the gesture teleports the pose back in a single frame, and without this the chain stays where the
     * drag flung it: the length constraints then yank it home over one tick and Verlet reads that yank
     * as a huge velocity, so the whole accumulated drag comes back out as one lash. Rejecting is meant
     * to mean "nothing happened", and that has to include the simulation the gesture drove.
     *
     * <p>Scoped globally rather than to the edited form because the gesture layer has no form identity
     * — and it costs nothing, since a chain that did not simulate during the gesture is restored to the
     * state it is already in (an editor with the film paused never advances an actor's age at all).
     */
    public static void checkpoint()
    {
        WeakHashMap<IEntity, Map<String, InstanceState>> snapshot = new WeakHashMap<>();

        for (Map.Entry<IEntity, Map<String, InstanceState>> entry : STATES.entrySet())
        {
            Map<String, InstanceState> byForm = entry.getValue();

            if (byForm == null)
            {
                continue;
            }

            Map<String, InstanceState> copy = new HashMap<>();

            for (Map.Entry<String, InstanceState> formEntry : byForm.entrySet())
            {
                copy.put(formEntry.getKey(), formEntry.getValue().copy());
            }

            snapshot.put(entry.getKey(), copy);
        }

        CHECKPOINT = snapshot;
    }

    /** Forgets the armed checkpoint — the gesture was accepted, or ended without one. */
    public static void dropCheckpoint()
    {
        CHECKPOINT = null;
    }

    /**
     * Puts the simulation back to the armed {@link #checkpoint()} and forgets it. Chains that only
     * appeared during the gesture are dropped, so they re-seed at the pose on their next frame.
     */
    public static void rewindToCheckpoint()
    {
        WeakHashMap<IEntity, Map<String, InstanceState>> snapshot = CHECKPOINT;

        CHECKPOINT = null;

        if (snapshot == null)
        {
            return;
        }

        STATES.clear();
        STATES.putAll(snapshot);
    }

    public static void invalidate(String modelId)
    {
        /* The skeleton the checkpoint was taken against is gone, so putting it back would resurrect a
         * sim built on it. Cheap to drop: a rewind then simply leaves the chains to re-seed. */
        CHECKPOINT = null;

        for (Map<String, InstanceState> byForm : STATES.values())
        {
            if (byForm != null)
            {
                byForm.values().removeIf((state) -> Objects.equals(state.modelId, modelId));
            }
        }
    }

    public static void apply(IEntity entity, ModelInstance instance, float transition, Matrix4f baseTransform)
    {
        if (entity == null || instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;

        if (!(instance.form instanceof ModelForm form))
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = ModelPhysicsCache.compile(model, form);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, BoneConstraint> constraints = ModelConstraintsRuntime.getBones(instance);

        Map<String, InstanceState> byForm = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byForm.computeIfAbsent(FormUtils.getPath(form), (k) -> new InstanceState());

        if (!Objects.equals(state.modelId, instance.id))
        {
            state.chains.clear();
            state.modelId = instance.id;
        }

        /* The wind is the form's own `wind` property now: its static value, or the wind track
         * driving it (the property's runtime value) — one read, nothing to layer. */
        WindControl liveWind = form.wind.get();
        ModelPhysicsConfig.Wind wind = new ModelPhysicsConfig.Wind(liveWind.strength, liveWind.x, liveWind.y, liveWind.z, liveWind.turbulence, liveWind.turbulenceSpeed, liveWind.turbulenceScale, liveWind.local);

        wind = resolveWindDirection(wind, baseTransform);

        applyCompiled(entity.getWorld(), entity.getAge(), transition, model, instance, compiled.chains(), wind, constraints, state, baseTransform);
    }

    /**
     * When the wind direction is local to the model, rotates it by the model's world orientation (the
     * rotation baked into {@code baseTransform}, the same transform the chain positions live in), so the
     * wind follows the model as it turns. The solver only ever sees a plain world-space direction. A
     * world-space or inactive wind is returned unchanged.
     */
    private static ModelPhysicsConfig.Wind resolveWindDirection(ModelPhysicsConfig.Wind wind, Matrix4f baseTransform)
    {
        if (wind == null || !wind.local() || !wind.active() || baseTransform == null)
        {
            return wind;
        }

        Vector3f dir = new Vector3f(wind.x(), wind.y(), wind.z());

        baseTransform.transformDirection(dir);

        return new ModelPhysicsConfig.Wind(wind.strength(), dir.x, dir.y, dir.z, wind.turbulence(), wind.turbulenceSpeed(), wind.turbulenceScale(), false);
    }

    private static void applyCompiled(World world, int age, float transition, IModel model, ModelInstance instance, List<ModelPhysicsCache.CompiledChain> compiledChains, ModelPhysicsConfig.Wind wind, Map<String, BoneConstraint> constraints, InstanceState state, Matrix4f baseTransform)
    {
        Set<String> wanted = new HashSet<>();
        Set<String> chainIds = new HashSet<>();

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            chainIds.add(chain.id());
            wanted.addAll(chain.chainRootToEnd());

            if (chain.targetBone() != null && !chain.targetBone().isEmpty())
            {
                wanted.add(chain.targetBone());
            }
        }

        if (!state.chains.isEmpty())
        {
            Iterator<String> it = state.chains.keySet().iterator();

            while (it.hasNext())
            {
                if (!chainIds.contains(it.next()))
                {
                    it.remove();
                }
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames, baseTransform);

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            applyChain(world, age, transition, model, instance, chain, wind, constraints, frames, state);
        }
    }

    private static void applyChain(World world, int age, float transition, IModel model, ModelInstance instance, ModelPhysicsCache.CompiledChain chain, ModelPhysicsConfig.Wind wind, Map<String, BoneConstraint> constraints, Map<String, PivotFrame> frames, InstanceState instanceState)
    {
        List<String> ids = chain.chainRootToEnd();
        int pivotCount = ids.size();
        int pointCount = pivotCount + 1;

        if (pivotCount < 1)
        {
            return;
        }

        /* The chain's animatable scalars, read live from the root bone's `physics` property:
         * the form's static setting, or the film's track when one drives the bone. */
        PhysicsControl control = PhysicsControl.DEFAULT;

        if (instance != null && instance.form instanceof ModelForm modelForm)
        {
            FormBone bone = modelForm.bones.getBone(ids.get(0));

            if (bone != null)
            {
                control = bone.physics.get();
            }
        }

        if (!control.enabled)
        {
            return;
        }

        float weight = control.weight;

        if (weight <= 0F)
        {
            return;
        }

        float gravity = control.gravity;
        float damping = control.damping;
        float stiffness = control.stiffness;

        ChainState state = instanceState.chains.computeIfAbsent(chain.id(), (k) -> new ChainState());

        if (state.pos == null || state.pos.length != pointCount)
        {
            state.pos = new Vector3f[pointCount];
            state.prev = new Vector3f[pointCount];
            state.settledLocal = new Vector3f[pointCount];
            state.settledPrevLocal = new Vector3f[pointCount];
            state.render = new Vector3f[pointCount];
            state.poseLocal = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.settledLocal[i] = new Vector3f();
                state.settledPrevLocal[i] = new Vector3f();
                state.render[i] = new Vector3f();
                state.poseLocal[i] = new Vector3f();
            }

            state.lastAge = Integer.MIN_VALUE;
        }

        List<PivotFrame> chainFrames = new ArrayList<>(pivotCount);

        for (int i = 0; i < pivotCount; i++)
        {
            PivotFrame frame = frames.get(ids.get(i));

            if (frame == null)
            {
                return;
            }

            chainFrames.add(frame);
        }

        PivotFrame rootFrame = chainFrames.get(0);
        Vector3f anchor = rootFrame.position();
        Quaternionf anchorRotation = rootFrame.worldRotation();

        Vector3f target = null;
        if (instance != null && instance.form instanceof ModelForm modelForm)
        {
            String rootBone = ids.get(0);
            Vector3f worldPos = modelForm.physicsTargetOverrides.get(rootBone);

            if (worldPos != null)
            {
                float targetWeight = modelForm.physicsTargetWeights.getOrDefault(rootBone, 1F);

                if (targetWeight >= 1F)
                {
                    target = new Vector3f(worldPos);
                }
                else if (targetWeight > 0F)
                {
                    /* The binding is fading in or out (it crossed a no-target keyframe). Easing the pin point
                     * from the chain's current tip toward the full target by the fade amount lets the chain
                     * travel there smoothly instead of snapping, with no soft-target mode in the solver. */
                    Vector3f tip = state.pos[state.pos.length - 1];

                    target = state.lastAge == Integer.MIN_VALUE
                        ? new Vector3f(worldPos)
                        : new Vector3f(tip).lerp(worldPos, targetWeight);
                }
                /* targetWeight <= 0: fully faded out — leave the chain free this frame. */
            }
        }

        if (target != null)
        {
            if (state.lastAge == Integer.MIN_VALUE)
            {
                state.pos[state.pos.length - 1].set(target);
                state.prev[state.pos.length - 1].set(target);
            }
        }
        else if (chain.targetBone() != null && !chain.targetBone().isEmpty())
        {
            PivotFrame targetFrame = frames.get(chain.targetBone());
            if (targetFrame != null)
            {
                target = targetFrame.position();
                if (state.lastAge == Integer.MIN_VALUE)
                {
                    state.pos[state.pos.length - 1].set(target);
                    state.prev[state.pos.length - 1].set(target);
                }
            }
        }

        ChainSolver.computePoseTargets(model, ids, chainFrames, chain.restLengths(), anchor, anchorRotation, target != null, state);
        ChainSolver.step(world, age, transition, model, ids, chain, gravity, damping, stiffness, wind, constraints, anchor, anchorRotation, chainFrames.get(0).parentRotation(), target, chainFrames, state);

        Vector3f[] positions = ChainSolver.renderInterpolate(state, state.renderAlpha, anchor, anchorRotation, target);
        ModelRotationBlender.applyWeightedRotations(model, chainFrames.get(0).parentRotation(), ids, positions, weight);
    }
}
