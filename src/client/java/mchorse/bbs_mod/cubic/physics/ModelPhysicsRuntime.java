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
    private static final float ANCHOR_ROTATION_POS_FOLLOW = 0.85F;
    private static final float ANCHOR_ROTATION_PREV_FOLLOW = 0.75F;
    private static final float RENDER_SWING_TIP_FOLLOW = 0.7F;
    private static final float ANCHOR_TRANSLATION_INERTIA = 0.5F;
    private static final float ANCHOR_TRANSLATION_DRAG = 0.15F;
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
        public Vector3f anchorVelocity = new Vector3f();
        public float simTime;
        public float accumulator;
        public float renderAlpha;
        public Vector3f[] pos;
        public Vector3f[] prev;
        public Vector3f[] settled;
        public Vector3f[] settledPrev;
        public Vector3f[] render;
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

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.settled[i] = new Vector3f();
                state.settledPrev[i] = new Vector3f();
                state.render[i] = new Vector3f();
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
     * Interpolates the settled chain shape of the two latest simulation ticks and re-roots it onto the
     * live anchor. The chain is rebuilt segment by segment from the anchor outwards: each segment's
     * direction is slerped between the two ticks (so the bone swings along an arc, not a straight chord)
     * and its length is lerped, while the anchor's sub-tick rotation swings the whole chain. This keeps
     * the motion smooth between the 20 Hz simulation steps instead of reading as linear stepping.
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

        int segments = render.length - 1;

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

            /* Soften the rigid swing: the segment nearest the anchor follows the head's sub-tick
             * rotation fully, segments toward the tip follow progressively less so the tip trails
             * the turn instead of snapping rigidly with it. */
            float follow = segments <= 1 ? 1F : 1F - (1F - RENDER_SWING_TIP_FOLLOW) * (i / (float) (segments - 1));
            frac.identity().slerp(swing, follow).transform(dir);
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
        Vector3f V2 = new Vector3f();
        Vector3f V3 = new Vector3f();
        Vector3f V5 = new Vector3f();
        Vector3f V6 = new Vector3f();
        Quaternionf Q1 = new Quaternionf();
        Quaternionf Q2 = new Quaternionf();
        Quaternionf Q3 = new Quaternionf();

        if (state.lastAge == Integer.MIN_VALUE)
        {
            state.anchor.set(newAnchor);
            state.anchorRotation.set(newAnchorRotation);
            state.anchorVelocity.set(0F, 0F, 0F);

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
        float advanced = steps * h; // simulated time consumed this frame, in ticks
        float dampMul = (float) Math.pow(1F - damping, h);
        float gravityScale = h * h;

        computeGravityDirection(chain, parentRotation, gravity, V6);
        float gravityX = V6.x * gravityScale;
        float gravityY = V6.y * gravityScale;
        float gravityZ = V6.z * gravityScale;

        /* Per-step rotation follow, scaled so the cumulative follow over a whole tick stays close to
         * the original 20 Hz tuning no matter how many sub-steps fit into a tick. */
        float followPos = 1F - (float) Math.pow(1F - ANCHOR_ROTATION_POS_FOLLOW, h);
        float followPrev = 1F - (float) Math.pow(1F - ANCHOR_ROTATION_PREV_FOLLOW, h);

        /* Anchor velocity/acceleration over the simulated span this frame (units per tick), for the
         * inertia/drag kick. */
        V1.set(state.anchor); // anchor where the simulation left it
        V2.set(newAnchor).sub(V1).mul(1F / advanced); // velAnchor
        V3.set(V2).sub(state.anchorVelocity); // accelAnchor
        state.anchorVelocity.set(V2);

        Vector3f startAnchor = new Vector3f(state.anchor);
        Quaternionf startAnchorRotation = new Quaternionf(state.anchorRotation);
        Vector3f stepAnchor = new Vector3f();
        Quaternionf stepAnchorRotation = new Quaternionf();

        BlockPos.Mutable mutable = collisions ? new BlockPos.Mutable() : null;

        for (int s = 0; s < steps; s++)
        {
            copyPositions(state.settled, state.settledPrev);

            /* Slide the anchor from where the simulation left it toward the live anchor across the
             * sub-steps of this frame, so the chain sees a smooth anchor trajectory. */
            float progress = (s + 1) / (float) steps;
            stepAnchor.set(startAnchor).lerp(newAnchor, progress);
            stepAnchorRotation.set(startAnchorRotation).slerp(newAnchorRotation, progress);

            V6.set(state.anchor);
            Q1.set(stepAnchorRotation).mul(Q2.set(state.anchorRotation).invert()).normalize();
            Q2.identity().slerp(Q1, followPos);
            Q3.identity().slerp(Q1, followPrev);

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                V5.set(p).sub(V6);
                Q2.transform(V5);
                p.set(stepAnchor).add(V5);

                Vector3f prev = state.prev[i];
                V5.set(prev).sub(V6);
                Q3.transform(V5);
                prev.set(stepAnchor).add(V5);
            }

            state.anchor.set(stepAnchor);
            state.anchorRotation.set(stepAnchorRotation);
            state.pos[0].set(stepAnchor);
            state.prev[0].set(stepAnchor);

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f prev = state.prev[i];

                V5.set(p).sub(prev).mul(dampMul); // vel

                prev.set(p);
                p.add(V5);
                if (s == 0 && ANCHOR_TRANSLATION_INERTIA > 0F)
                {
                    p.x -= V3.x * ANCHOR_TRANSLATION_INERTIA * advanced;
                    p.y -= V3.y * ANCHOR_TRANSLATION_INERTIA * advanced;
                    p.z -= V3.z * ANCHOR_TRANSLATION_INERTIA * advanced;
                }
                if (s == 0 && ANCHOR_TRANSLATION_DRAG > 0F)
                {
                    p.x -= V2.x * ANCHOR_TRANSLATION_DRAG * advanced;
                    p.y -= V2.y * ANCHOR_TRANSLATION_DRAG * advanced;
                    p.z -= V2.z * ANCHOR_TRANSLATION_DRAG * advanced;
                }
                p.x += gravityX;
                p.y += gravityY;
                p.z += gravityZ;

                if (collisions)
                {
                    float dx = p.x - prev.x;
                    float dy = p.y - prev.y;
                    float dz = p.z - prev.z;

                    float maxStep = Math.max(COLLISION_MAX_ANCHOR_STEP, radius * 2F);
                    float maxStepSq = maxStep * maxStep;
                    float lenSq = dx * dx + dy * dy + dz * dz;

                    if (lenSq > maxStepSq)
                    {
                        float minX = Math.min(prev.x, p.x) - radius;
                        float minY = Math.min(prev.y, p.y) - radius;
                        float minZ = Math.min(prev.z, p.z) - radius;
                        float maxX = Math.max(prev.x, p.x) + radius;
                        float maxY = Math.max(prev.y, p.y) + radius;
                        float maxZ = Math.max(prev.z, p.z) + radius;

                        int minBX = MathHelper.floor(minX);
                        int minBY = MathHelper.floor(minY);
                        int minBZ = MathHelper.floor(minZ);
                        int maxBX = MathHelper.floor(maxX);
                        int maxBY = MathHelper.floor(maxY);
                        int maxBZ = MathHelper.floor(maxZ);

                        boolean nearSolids = ModelPhysicsWorldCollisions.hasFullCubeInAabb(world, mutable, minBX, minBY, minBZ, maxBX, maxBY, maxBZ);

                        if (nearSolids)
                        {
                            float inv = maxStep / (float) Math.sqrt(lenSq);
                            p.x = prev.x + dx * inv;
                            p.y = prev.y + dy * inv;
                            p.z = prev.z + dz * inv;
                        }
                    }
                }
            }

            for (int iter = 0; iter < iterations; iter++)
            {
                /* Backward pass (from target to anchor) */
                if (targetPosition != null)
                {
                    state.pos[state.pos.length - 1].set(targetPosition);
                }

                for (int i = state.pos.length - 2; i >= 0; i--)
                {
                    Vector3f a = state.pos[i];
                    Vector3f b = state.pos[i + 1];

                    V5.set(a).sub(b); // dir
                    float lenSq = V5.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    V5.mul((float) (lengths[i] / Math.sqrt(lenSq)));
                    a.set(b).add(V5);
                }

                /* Forward pass (from anchor to target) */
                state.pos[0].set(state.anchor);

                for (int i = 1; i < state.pos.length; i++)
                {
                    Vector3f a = state.pos[i - 1];
                    Vector3f b = state.pos[i];

                    V5.set(b).sub(a); // dir
                    float lenSq = V5.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    V5.mul((float) (lengths[i - 1] / Math.sqrt(lenSq)));
                    b.set(a).add(V5);
                }

                if (targetPosition != null)
                {
                    state.pos[state.pos.length - 1].set(targetPosition);
                }

                if (constraints != null && !constraints.isEmpty() && rig != null)
                {
                    applyAngleConstraints(rig, ids, state.pos, lengths, constraints, chainFrames.get(0).parentRotation());

                    state.pos[0].set(state.anchor);

                    if (targetPosition != null)
                    {
                        state.pos[state.pos.length - 1].set(targetPosition);
                    }
                }

                if (collisions)
                {
                    int from = 1;
                    int to = targetPosition != null ? state.pos.length - 1 : state.pos.length;
                    ModelPhysicsWorldCollisions.resolve(world, state.pos, state.prev, from, to, radius, COLLISION_FRICTION);

                    state.pos[0].set(state.anchor);
                    if (targetPosition != null)
                    {
                        state.pos[state.pos.length - 1].set(targetPosition);
                    }
                }
            }

            copyPositions(state.pos, state.settled);
        }
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
