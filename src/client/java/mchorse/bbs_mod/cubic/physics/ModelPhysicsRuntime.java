package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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
import java.util.Set;
import java.util.WeakHashMap;

public final class ModelPhysicsRuntime
{
    private static final float BASE_GRAVITY = 0.08F;
    private static final float EPS = 1.0e-6f;

    /**
     * Stiffness falloff along the chain: the tip is left this fraction as stiff as the root, so the
     * base of the chain springs back to the pose firmly while the end stays loose and trails. A flat
     * stiffness reads stiff and lifeless; the gradient is what gives the chain a living, whip-like tail.
     */
    private static final float TIP_STIFFNESS_SCALE = 0.4F;

    private static final float COLLISION_FRICTION = 0.5F;
    private static final float COLLISION_MAX_ANCHOR_STEP = 0.25F;

    /**
     * Fixed simulation sub-step, in game ticks. The solver is integrated at this constant step off a
     * real-time accumulator (see {@link #step}) instead of once per 20 Hz game tick, so the physics
     * produces a fresh chain shape several times per tick. That is what keeps fast anchor motion (e.g.
     * sharp head turns) smooth: the in-between shapes are actually simulated rather than interpolated
     * from two coarse 20 Hz snapshots. 1/3 tick ≈ 60 Hz output. Smaller = smoother but more solver work.
     */
    private static final float PHYSICS_STEP = 1F / 3F;
    private static final int PHYSICS_MAX_STEPS = 30;


    private static final class ChainState
    {
        public int lastAge = Integer.MIN_VALUE;
        public Vector3f anchor = new Vector3f();
        public Quaternionf anchorRotation = new Quaternionf();
        public float simTime;
        public float accumulator;
        public float renderAlpha;
        public Vector3f[] pos;
        public Vector3f[] prev;
        public Vector3f[] settled;
        public Vector3f[] settledPrev;
        public Vector3f[] render;

        /** The animated pose the chain springs toward, stored relative to the live anchor frame. */
        public Vector3f[] poseLocal;
    }

    private static final class InstanceState
    {
        public final Map<String, ChainState> chains = new HashMap<>();
    }

    private static final WeakHashMap<IEntity, Map<String, InstanceState>> STATES = new WeakHashMap<>();

    private ModelPhysicsRuntime()
    {
    }

    public static void clearCache()
    {
        ModelPhysicsCache.clear();
        STATES.clear();
    }

    public static void invalidate(String modelId)
    {
        for (Map<String, InstanceState> byModel : STATES.values())
        {
            if (byModel != null)
            {
                byModel.remove(modelId);
            }
        }
    }

    public static void apply(IEntity entity, ModelInstance instance, float transition, Matrix4f baseTransform)
    {
        apply(entity, instance, transition, baseTransform, null);
    }

    public static void apply(IEntity entity, ModelInstance instance, float transition, Matrix4f baseTransform, Map<String, Float> poseFixByBone)
    {
        if (entity == null || instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;

        ModelPhysicsCache.Compiled compiled = null;
        if (instance.form instanceof ModelForm modelForm && modelForm.physics.get() instanceof MapType map)
        {
            compiled = ModelPhysicsCache.getFromData(model, map);
        }

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, ModelConstraintsConfig.BoneConstraint> constraints = ModelConstraintsRuntime.getBones(instance);

        Map<String, InstanceState> byModel = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byModel.computeIfAbsent(instance.id, (k) -> new InstanceState());

        applyCompiled(entity.getWorld(), entity.getAge(), transition, model, instance, compiled.chains(), constraints, state, baseTransform, poseFixByBone);
    }

    private static void applyCompiled(World world, int age, float transition, IModel model, ModelInstance instance, List<ModelPhysicsCache.CompiledChain> compiledChains, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, InstanceState state, Matrix4f baseTransform, Map<String, Float> poseFixByBone)
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
            applyChain(world, age, transition, model, instance, chain, constraints, frames, state, poseFixByBone);
        }
    }

    private static void applyChain(World world, int age, float transition, IModel model, ModelInstance instance, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Map<String, PivotFrame> frames, InstanceState instanceState, Map<String, Float> poseFixByBone)
    {
        float weight = chain.weight();

        if (weight <= 0F)
        {
            return;
        }

        List<String> ids = chain.chainRootToEnd();
        int pivotCount = ids.size();
        int pointCount = pivotCount + 1;

        if (pivotCount < 1)
        {
            return;
        }

        float poseFix = getChainPoseFix(ids, chain.targetBone(), poseFixByBone);
        weight *= (1F - poseFix);

        if (weight <= EPS)
        {
            return;
        }

        ChainState state = instanceState.chains.computeIfAbsent(chain.id(), (k) -> new ChainState());

        if (state.pos == null || state.pos.length != pointCount)
        {
            state.pos = new Vector3f[pointCount];
            state.prev = new Vector3f[pointCount];
            state.settled = new Vector3f[pointCount];
            state.settledPrev = new Vector3f[pointCount];
            state.render = new Vector3f[pointCount];
            state.poseLocal = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.settled[i] = new Vector3f();
                state.settledPrev[i] = new Vector3f();
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
                target = new Vector3f(worldPos);
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

        computePoseTargets(model, ids, chainFrames, chain.restLengths(), anchor, anchorRotation, target != null, state);
        step(world, age, transition, model, ids, chain, constraints, anchor, anchorRotation, chainFrames.get(0).parentRotation(), target, chainFrames, state);
        Vector3f[] positions = renderInterpolate(state, state.renderAlpha, anchor, anchorRotation, target);
        ModelRotationBlender.applyWeightedRotations(model, chainFrames.get(0).parentRotation(), ids, positions, weight);
    }

    private static float getChainPoseFix(List<String> ids, String targetBone, Map<String, Float> poseFixByBone)
    {
        if (poseFixByBone == null || poseFixByBone.isEmpty() || ids == null || ids.isEmpty())
        {
            return 0F;
        }

        float maxFix = 0F;

        for (String id : ids)
        {
            maxFix = Math.max(maxFix, getFix(poseFixByBone, id));

            if (maxFix >= 1F)
            {
                return 1F;
            }
        }

        if (targetBone != null && !targetBone.isEmpty())
        {
            maxFix = Math.max(maxFix, getFix(poseFixByBone, targetBone));
        }

        return maxFix;
    }

    private static float getFix(Map<String, Float> poseFixByBone, String bone)
    {
        Float value = poseFixByBone.get(bone);

        if (value == null)
        {
            return 0F;
        }

        if (value <= 0F)
        {
            return 0F;
        }

        return Math.min(value, 1F);
    }

    /**
     * Captures the animated pose the chain should spring back toward, stored relative to the live anchor
     * frame so the sub-step solver can carry it rigidly with the sliding anchor. The real joints take
     * their own animated world positions; the virtual tip past the last bone is reconstructed from the
     * last bone's animated rotation and rest direction (the same convention the rotation appliers use),
     * unless the tip is hard-pinned to a target, in which case its pose slot is left unused.
     */
    private static void computePoseTargets(IModel model, List<String> ids, List<PivotFrame> chainFrames, float[] lengths, Vector3f anchor, Quaternionf anchorRotation, boolean hardTarget, ChainState state)
    {
        Vector3f[] poseLocal = state.poseLocal;
        int pivotCount = chainFrames.size();

        if (poseLocal == null || poseLocal.length != pivotCount + 1)
        {
            return;
        }

        Quaternionf invAnchor = new Quaternionf(anchorRotation).invert();
        Vector3f tmp = new Vector3f();

        poseLocal[0].set(0F, 0F, 0F);

        for (int i = 1; i < pivotCount; i++)
        {
            tmp.set(chainFrames.get(i).position()).sub(anchor);
            poseLocal[i].set(invAnchor.transform(tmp));
        }

        int tip = poseLocal.length - 1;

        if (hardTarget)
        {
            poseLocal[tip].set(poseLocal[pivotCount - 1]);
            return;
        }

        Vector3f tipDir = lengths != null && lengths.length >= pivotCount ? PhysicsRig.tipRestDirectionLocal(model, ids) : null;

        if (tipDir == null || tipDir.lengthSquared() < EPS * EPS)
        {
            poseLocal[tip].set(poseLocal[pivotCount - 1]);
            return;
        }

        PivotFrame lastFrame = chainFrames.get(pivotCount - 1);
        new Quaternionf(lastFrame.worldRotation()).transform(tipDir.normalize()).mul(lengths[pivotCount - 1]);
        tmp.set(lastFrame.position()).add(tipDir).sub(anchor);
        poseLocal[tip].set(invAnchor.transform(tmp));
    }

    /**
     * Interpolates the settled chain shape of the two latest simulation sub-steps and re-roots it onto
     * the live anchor. The chain is rebuilt segment by segment from the anchor outwards: each segment's
     * direction is slerped between the two sub-steps (so the bone swings along an arc, not a straight
     * chord) and its length is lerped, while the anchor's leftover sub-tick rotation carries the whole
     * chain. This keeps the motion smooth between simulation sub-steps instead of reading as stepping.
     */
    private static Vector3f[] renderInterpolate(ChainState state, float transition, Vector3f liveAnchor, Quaternionf liveAnchorRotation, Vector3f target)
    {
        Vector3f[] render = state.render;
        Vector3f[] settled = state.settled;
        Vector3f[] settledPrev = state.settledPrev;

        if (render == null || settled == null || settledPrev == null || render.length != settled.length || settledPrev.length != settled.length)
        {
            return state.pos;
        }

        float alpha = clamp01(transition);

        Vector3f dir = new Vector3f();
        Vector3f dirCurr = new Vector3f();
        Quaternionf swing = new Quaternionf();
        Quaternionf segRot = new Quaternionf();
        Quaternionf frac = new Quaternionf();

        swing.set(liveAnchorRotation).mul(segRot.set(state.anchorRotation).invert()).normalize(); // anchor sub-tick swing

        /* Root point is pinned to the live anchor, the rest is rebuilt outwards from it */
        render[0].set(liveAnchor);

        for (int i = 0; i + 1 < render.length; i++)
        {
            dir.set(settledPrev[i + 1]).sub(settledPrev[i]); // segment last tick
            dirCurr.set(settled[i + 1]).sub(settled[i]); // segment this tick

            float lenPrev = dir.length();
            float lenCurr = dirCurr.length();
            float len = lenPrev + (lenCurr - lenPrev) * alpha;

            boolean okPrev = lenPrev > EPS;
            boolean okCurr = lenCurr > EPS;

            if (okPrev && okCurr)
            {
                dir.div(lenPrev);
                dirCurr.div(lenCurr);
                segRot.rotationTo(dir, dirCurr); // full swing of this segment over the tick
                frac.identity().slerp(segRot, alpha).transform(dir); // dir = direction at sub-tick alpha
            }
            else if (okCurr)
            {
                dir.set(dirCurr).div(lenCurr);
            }
            else if (okPrev)
            {
                dir.div(lenPrev);
            }
            else
            {
                render[i + 1].set(render[i]);
                continue;
            }

            /* Carry the chain by the anchor's leftover sub-tick rotation. The lag of the tip during a
             * turn is produced by the simulation now, so the render carries every segment equally
             * instead of faking the trail with a per-segment falloff. */
            swing.transform(dir);
            render[i + 1].set(render[i]).add(dir.mul(len));
        }

        if (target != null)
        {
            render[render.length - 1].set(target);
        }

        return render;
    }

    private static void copyPositions(Vector3f[] src, Vector3f[] dst)
    {
        for (int i = 0; i < src.length; i++)
        {
            dst[i].set(src[i]);
        }
    }

    private static void step(World world, int age, float transition, IModel model, List<String> ids, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Vector3f anchorPosition, Quaternionf anchorRotation, Quaternionf parentRotation, Vector3f targetPosition, List<PivotFrame> chainFrames, ChainState state)
    {
        Vector3f newAnchor = anchorPosition;
        Quaternionf newAnchorRotation = anchorRotation;
        float[] lengths = chain.restLengths();

        if (lengths == null || lengths.length != state.pos.length - 1)
        {
            return;
        }

        Vector3f V1 = new Vector3f();

        if (state.lastAge == Integer.MIN_VALUE)
        {
            state.anchor.set(newAnchor);
            state.anchorRotation.set(newAnchorRotation);

            state.pos[0].set(newAnchor);
            state.prev[0].set(newAnchor);

            for (int i = 1; i < chainFrames.size(); i++)
            {
                Vector3f p = chainFrames.get(i).position();
                state.pos[i].set(p);
                state.prev[i].set(p);
            }

            if (chainFrames.size() >= 2)
            {
                V1.set(state.pos[chainFrames.size() - 1]).sub(state.pos[chainFrames.size() - 2]); // tipDir

                if (V1.lengthSquared() < EPS * EPS)
                {
                    V1.set(0F, -1F, 0F);
                }
                else
                {
                    V1.normalize();
                }
            }
            else
            {
                V1.set(0F, -1F, 0F);
            }

            state.pos[state.pos.length - 1].set(state.pos[chainFrames.size() - 1]).add(V1.mul(lengths[lengths.length - 1]));
            state.prev[state.prev.length - 1].set(state.pos[state.pos.length - 1]);

            copyPositions(state.pos, state.settled);
            copyPositions(state.pos, state.settledPrev);

            state.lastAge = age;
            state.simTime = age + transition;
            state.accumulator = 0F;
            state.renderAlpha = 0F;
            return;
        }

        float gravity = BASE_GRAVITY * chain.gravity();
        float damping = clamp01(chain.damping());
        int iterations = chain.iterations();
        boolean collisions = chain.collisions() && world != null && chain.radius() > 0F;
        float radius = chain.radius();
        PhysicsRig rig = PhysicsRig.of(model);
        boolean hardTarget = targetPosition != null;
        int last = state.pos.length - 1;

        /* Real-time fixed-step accumulator: integrate the solver at PHYSICS_STEP regardless of the
         * render frame rate or the 20 Hz game tick, so a fresh chain shape is produced several times
         * per tick. age + transition is a continuous tick-clock; the leftover fraction (renderAlpha)
         * interpolates the last two sub-steps in renderInterpolate. */
        float now = age + transition;
        float frameDt = now - state.simTime;

        if (frameDt <= 0F)
        {
            return; // paused or scrubbed backwards — keep the last settled shape, render re-roots it
        }

        if (frameDt > 10F)
        {
            frameDt = 10F;
        }

        state.simTime = now;
        state.accumulator += frameDt;

        int steps = (int) (state.accumulator / PHYSICS_STEP);

        if (steps > PHYSICS_MAX_STEPS)
        {
            steps = PHYSICS_MAX_STEPS;
            state.accumulator = steps * PHYSICS_STEP;
        }

        state.accumulator -= steps * PHYSICS_STEP;
        state.renderAlpha = clamp01(state.accumulator / PHYSICS_STEP);

        if (steps <= 0)
        {
            return; // not enough time accrued for a sub-step yet; render interpolates the leftover
        }

        float h = PHYSICS_STEP;
        float dampMul = (float) Math.pow(1F - damping, h);
        float gravityScale = h * h;

        Vector3f gravityVec = new Vector3f();
        computeGravityDirection(chain, parentRotation, gravity, gravityVec);
        float gravityX = gravityVec.x * gravityScale;
        float gravityY = gravityVec.y * gravityScale;
        float gravityZ = gravityVec.z * gravityScale;

        /* Per-point spring-back fraction toward the animated pose, applied once per sub-step. The base
         * stiffness falls off toward the tip and is converted to a per-sub-step fraction so the pull
         * over a whole tick stays the same no matter how many sub-steps run. */
        float[] stiffStep = computeStiffnessSteps(clamp01(chain.stiffness()), state.pos.length, h);

        Vector3f startAnchor = new Vector3f(state.anchor);
        Quaternionf startAnchorRotation = new Quaternionf(state.anchorRotation);
        Vector3f stepAnchor = new Vector3f();
        Quaternionf stepAnchorRotation = new Quaternionf();
        Vector3f vel = new Vector3f();
        Vector3f poseTarget = new Vector3f();
        Vector3f dir = new Vector3f();

        BlockPos.Mutable mutable = collisions ? new BlockPos.Mutable() : null;

        for (int s = 0; s < steps; s++)
        {
            copyPositions(state.settled, state.settledPrev);

            /* Slide the anchor from where the simulation left it toward the live anchor across the
             * sub-steps of this frame, so the chain sees a smooth anchor trajectory. */
            float progress = (s + 1) / (float) steps;
            stepAnchor.set(startAnchor).lerp(newAnchor, progress);
            stepAnchorRotation.set(startAnchorRotation).slerp(newAnchorRotation, progress);

            state.anchor.set(stepAnchor);
            state.anchorRotation.set(stepAnchorRotation);
            state.pos[0].set(stepAnchor);
            state.prev[0].set(stepAnchor);

            /* Verlet integration of the free points: inertia carried from the previous step plus
             * gravity. The anchor's motion never forces the points — it reaches the chain only through
             * the pose spring and the length constraints below, so lag and whip arise honestly. */
            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f prev = state.prev[i];

                vel.set(p).sub(prev).mul(dampMul);
                prev.set(p);
                p.add(vel);
                p.x += gravityX;
                p.y += gravityY;
                p.z += gravityZ;

                if (collisions)
                {
                    clampTunnelStep(world, mutable, p, prev, radius);
                }
            }

            /* Spring back toward the animated pose, carried rigidly by the sliding anchor frame. */
            for (int i = 1; i < state.pos.length; i++)
            {
                float k = stiffStep[i];

                if (k <= 0F || (hardTarget && i == last))
                {
                    continue;
                }

                poseTarget.set(state.poseLocal[i]);
                stepAnchorRotation.transform(poseTarget).add(stepAnchor);
                state.pos[i].lerp(poseTarget, k);
            }

            for (int iter = 0; iter < iterations; iter++)
            {
                /* Backward pass (from tip to anchor) */
                if (hardTarget)
                {
                    state.pos[last].set(targetPosition);
                }

                for (int i = last - 1; i >= 0; i--)
                {
                    Vector3f a = state.pos[i];
                    Vector3f b = state.pos[i + 1];

                    dir.set(a).sub(b);
                    float lenSq = dir.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    dir.mul((float) (lengths[i] / Math.sqrt(lenSq)));
                    a.set(b).add(dir);
                }

                /* Forward pass (from anchor to tip) */
                state.pos[0].set(state.anchor);

                for (int i = 1; i < state.pos.length; i++)
                {
                    Vector3f a = state.pos[i - 1];
                    Vector3f b = state.pos[i];

                    dir.set(b).sub(a);
                    float lenSq = dir.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    dir.mul((float) (lengths[i - 1] / Math.sqrt(lenSq)));
                    b.set(a).add(dir);
                }

                if (hardTarget)
                {
                    state.pos[last].set(targetPosition);
                }

                if (collisions)
                {
                    resolveCollisions(world, state.pos, state.prev, state.anchor, targetPosition, last, radius);
                }
            }

            /* Angle limits run once, after the length solver has converged, so the clamp and the length
             * constraints don't fight iteration to iteration. Re-pin the ends and let one last
             * depenetration settle any point the clamp pushed into geometry. */
            if (constraints != null && !constraints.isEmpty() && rig != null)
            {
                applyAngleConstraints(rig, ids, state.pos, lengths, constraints, chainFrames.get(0).parentRotation());
                pinEnds(state.pos, state.anchor, targetPosition, last);

                if (collisions)
                {
                    resolveCollisions(world, state.pos, state.prev, state.anchor, targetPosition, last, radius);
                }
            }

            copyPositions(state.pos, state.settled);
        }
    }

    /** Re-fixes the chain endpoints after a constraint pass: the root onto the anchor and, when the tip is hard-pinned, onto its target. */
    private static void pinEnds(Vector3f[] pos, Vector3f anchor, Vector3f target, int last)
    {
        pos[0].set(anchor);

        if (target != null)
        {
            pos[last].set(target);
        }
    }

    /** Depenetrates the chain against the world, then re-pins the endpoints. The tip is excluded from the sweep when it is hard-pinned. */
    private static void resolveCollisions(World world, Vector3f[] pos, Vector3f[] prev, Vector3f anchor, Vector3f target, int last, float radius)
    {
        int to = target != null ? last : pos.length;

        ModelPhysicsWorldCollisions.resolve(world, pos, prev, 1, to, radius, COLLISION_FRICTION);
        pinEnds(pos, anchor, target, last);
    }

    /**
     * Per-point spring-back fractions toward the animated pose for a single sub-step. The base
     * stiffness falls off linearly from the root to {@link #TIP_STIFFNESS_SCALE} at the tip, then each
     * factor is converted from a per-tick pull to a per-sub-step pull so the result is independent of
     * how many sub-steps run. Index 0 (the anchor) and the unused slots stay zero.
     */
    private static float[] computeStiffnessSteps(float baseStiffness, int pointCount, float h)
    {
        float[] out = new float[pointCount];

        if (baseStiffness <= 0F || pointCount <= 1)
        {
            return out;
        }

        int freeCount = pointCount - 1;

        for (int i = 1; i < pointCount; i++)
        {
            float t = freeCount <= 1 ? 0F : (i - 1) / (float) (freeCount - 1);
            float falloff = 1F - (1F - TIP_STIFFNESS_SCALE) * t;
            float perTick = baseStiffness * falloff;

            out[i] = 1F - (float) Math.pow(1F - perTick, h);
        }

        return out;
    }

    /**
     * Anti-tunnelling guard: when a particle would travel further than {@link #COLLISION_MAX_ANCHOR_STEP}
     * (or its own diameter) in one sub-step and there are solid blocks in its swept volume, clamp the
     * step length so the depenetration pass can still catch it instead of passing through thin geometry.
     */
    private static void clampTunnelStep(World world, BlockPos.Mutable mutable, Vector3f p, Vector3f prev, float radius)
    {
        float dx = p.x - prev.x;
        float dy = p.y - prev.y;
        float dz = p.z - prev.z;

        float maxStep = Math.max(COLLISION_MAX_ANCHOR_STEP, radius * 2F);
        float lenSq = dx * dx + dy * dy + dz * dz;

        if (lenSq <= maxStep * maxStep)
        {
            return;
        }

        int minBX = MathHelper.floor(Math.min(prev.x, p.x) - radius);
        int minBY = MathHelper.floor(Math.min(prev.y, p.y) - radius);
        int minBZ = MathHelper.floor(Math.min(prev.z, p.z) - radius);
        int maxBX = MathHelper.floor(Math.max(prev.x, p.x) + radius);
        int maxBY = MathHelper.floor(Math.max(prev.y, p.y) + radius);
        int maxBZ = MathHelper.floor(Math.max(prev.z, p.z) + radius);

        if (!ModelPhysicsWorldCollisions.hasFullCubeInAabb(world, mutable, minBX, minBY, minBZ, maxBX, maxBY, maxBZ))
        {
            return;
        }

        float inv = maxStep / (float) Math.sqrt(lenSq);
        p.x = prev.x + dx * inv;
        p.y = prev.y + dy * inv;
        p.z = prev.z + dz * inv;
    }

    private static void computeGravityDirection(ModelPhysicsCache.CompiledChain chain, Quaternionf parentRotation, float gravity, Vector3f out)
    {
        out.set(0F, -1F, 0F);

        if (chain.relativeGravity() && parentRotation != null)
        {
            /* Model bone forward axis is -Y in this rig convention. */
            parentRotation.transform(out);
        }

        if (chain.hasGravityRotation())
        {
            if (parentRotation != null)
            {
                /* Apply user rotation in chain local space, then convert back to world space. */
                Quaternionf inverseParent = new Quaternionf(parentRotation).invert();
                inverseParent.transform(out);
                chain.applyGravityRotation(out);
                parentRotation.transform(out);
            }
            else
            {
                chain.applyGravityRotation(out);
            }
        }

        if (out.lengthSquared() < EPS * EPS)
        {
            out.set(0F, -1F, 0F);
        }

        out.normalize().mul(gravity);
    }

    private static void applyAngleConstraints(PhysicsRig rig, List<String> ids, Vector3f[] pos, float[] lengths, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Quaternionf rootParentRotation)
    {
        int boneCount = ids.size();

        if (boneCount == 0 || pos == null || pos.length < 2 || lengths == null || lengths.length < 1 || rootParentRotation == null)
        {
            return;
        }

        Quaternionf parentWorld = new Quaternionf(rootParentRotation);

        for (int i = 0; i < boneCount; i++)
        {
            String boneId = ids.get(i);
            String childId = i + 1 < boneCount ? ids.get(i + 1) : null;
            ModelConstraintsConfig.BoneConstraint c = boneId == null ? null : constraints.get(boneId);

            Vector3f restDirLocal = rig.restDirectionLocal(boneId, childId);

            if (restDirLocal == null)
            {
                return;
            }

            Vector3f desiredDirWorld = new Vector3f(pos[i + 1]).sub(pos[i]);

            if (restDirLocal.lengthSquared() < EPS * EPS || desiredDirWorld.lengthSquared() < EPS * EPS)
            {
                continue;
            }

            restDirLocal.normalize();
            desiredDirWorld.normalize();

            Quaternionf invParent = new Quaternionf(parentWorld).invert();
            Vector3f desiredDirLocal = new Vector3f(desiredDirWorld);
            invParent.transform(desiredDirLocal);

            if (desiredDirLocal.lengthSquared() < EPS * EPS)
            {
                continue;
            }

            desiredDirLocal.normalize();

            Quaternionf localRot = Matrices.fromToMirroredX(restDirLocal, desiredDirLocal);
            Quaternionf applied = localRot;

            if (c != null && c.enabled())
            {
                Vector3f eulerDeg = Matrices.toEulerZYXDegrees(localRot);

                float minX = c.minX();
                float minY = c.minY();
                float minZ = c.minZ();
                float maxX = c.maxX();
                float maxY = c.maxY();
                float maxZ = c.maxZ();

                if (minX > maxX)
                {
                    float t = minX;
                    minX = maxX;
                    maxX = t;
                }

                if (minY > maxY)
                {
                    float t = minY;
                    minY = maxY;
                    maxY = t;
                }

                if (minZ > maxZ)
                {
                    float t = minZ;
                    minZ = maxZ;
                    maxZ = t;
                }

                eulerDeg.x = eulerDeg.x < minX ? minX : Math.min(eulerDeg.x, maxX);
                eulerDeg.y = eulerDeg.y < minY ? minY : Math.min(eulerDeg.y, maxY);
                eulerDeg.z = eulerDeg.z < minZ ? minZ : Math.min(eulerDeg.z, maxZ);

                applied = Matrices.toQuaternionZYXDegrees(eulerDeg.x, eulerDeg.y, eulerDeg.z);
                Vector3f dirLocal = new Vector3f(restDirLocal);
                applied.transform(dirLocal);
                parentWorld.transform(dirLocal);

                if (dirLocal.lengthSquared() >= EPS * EPS)
                {
                    dirLocal.normalize().mul(lengths[i]);
                    pos[i + 1].set(pos[i]).add(dirLocal);
                }
            }

            parentWorld.mul(applied);
        }
    }

    private static float clamp01(float v)
    {
        if (v < 0F)
        {
            return 0F;
        }

        return v > 1F ? 1F : v;
    }
}
