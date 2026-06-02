package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
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
    private static final float ANCHOR_TRANSLATION_INERTIA = 0.5F;
    private static final float ANCHOR_TRANSLATION_DRAG = 0.15F;
    private static final float COLLISION_FRICTION = 0.5F;
    private static final float COLLISION_MAX_ANCHOR_STEP = 0.25F;
    private static final int COLLISION_MAX_SUBSTEPS = 8;
    private static final float COLLISION_EPS = 1.0e-4f;
    private static final float COLLISION_SLEEP_EPS = 0.005f;
    private static final float COLLISION_STATIC_FRICTION_EPS = 0.02f;
    private static final float COLLISION_CONTACT_SLOP = 0.02f;


    private static final class ChainState
    {
        public int lastAge = Integer.MIN_VALUE;
        public Vector3f anchor = new Vector3f();
        public Quaternionf anchorRotation = new Quaternionf();
        public Vector3f anchorVelocity = new Vector3f();
        public Vector3f[] pos;
        public Vector3f[] prev;
        public Vector3f[] settled;
        public Vector3f[] settledPrev;
        public Vector3f[] render;
        public Vector3f[] contactNormals;
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
        if (instance.form instanceof mchorse.bbs_mod.forms.forms.ModelForm modelForm && modelForm.physics.get() instanceof MapType map)
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
            java.util.Iterator<String> it = state.chains.keySet().iterator();

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
            state.contactNormals = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.settled[i] = new Vector3f();
                state.settledPrev[i] = new Vector3f();
                state.render[i] = new Vector3f();
                state.contactNormals[i] = new Vector3f();
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
        if (instance != null && instance.form instanceof mchorse.bbs_mod.forms.forms.ModelForm modelForm)
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

        step(world, age, model, ids, chain, constraints, anchor, anchorRotation, chainFrames.get(0).parentRotation(), target, chainFrames, state);
        Vector3f[] positions = renderInterpolate(state, transition, anchor, anchorRotation, target);
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
     * live anchor. The chain shape is carried relative to its own root so the root stays pinned to the
     * anchor exactly, while the anchor's sub-tick rotation swings the whole chain.
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

        Vector3f rootPrev = settledPrev[0];
        Vector3f rootCurr = settled[0];

        Vector3f V1 = new Vector3f();
        Vector3f V2 = new Vector3f();
        Quaternionf Q1 = new Quaternionf();
        Quaternionf Q2 = new Quaternionf();

        Q1.set(liveAnchorRotation).mul(Q2.set(state.anchorRotation).invert()).normalize(); // anchor sub-tick swing

        for (int i = 0; i < render.length; i++)
        {
            V1.set(settledPrev[i]).sub(rootPrev); // shape last tick
            V2.set(settled[i]).sub(rootCurr); // shape this tick
            V1.lerp(V2, alpha);
            Q1.transform(V1);
            render[i].set(liveAnchor).add(V1);
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

    private static void step(World world, int age, IModel model, List<String> ids, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Vector3f anchorPosition, Quaternionf anchorRotation, Quaternionf parentRotation, Vector3f targetPosition, List<PivotFrame> chainFrames, ChainState state)
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
        Vector3f V4 = new Vector3f();
        Vector3f V5 = new Vector3f();
        Vector3f V6 = new Vector3f();
        Vector3f V7 = new Vector3f();
        Quaternionf Q1 = new Quaternionf();
        Quaternionf Q2 = new Quaternionf();
        Quaternionf Q3 = new Quaternionf();
        Quaternionf Q4 = new Quaternionf();
        Quaternionf Q5 = new Quaternionf();

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
            return;
        }

        int dt = age - state.lastAge;

        if (dt <= 0)
        {
            return;
        }

        if (dt > 10)
        {
            dt = 10;
            state.lastAge = age - dt;
        }

        float gravity = BASE_GRAVITY * chain.gravity();
        float damping = clamp01(chain.damping());
        int iterations = chain.iterations();
        boolean collisions = chain.collisions() && world != null && chain.radius() > 0F;
        float radius = chain.radius();

        computeGravityDirection(chain, parentRotation, gravity, V6);
        float gravityX = V6.x;
        float gravityY = V6.y;
        float gravityZ = V6.z;

        V1.set(state.anchor); // oldAnchorTick
        V2.set(newAnchor).sub(V1).mul(1F / dt); // velAnchor
        V3.set(V2).sub(state.anchorVelocity); // accelAnchor
        state.anchorVelocity.set(V2);

        BlockPos.Mutable mutable = collisions ? new BlockPos.Mutable() : null;

        for (int t = 0; t < dt; t++)
        {
            copyPositions(state.settled, state.settledPrev);

            V4.set(state.anchor);
            Q4.set(state.anchorRotation);

            int substeps = computeSubsteps(V4, newAnchor);

            for (int sub = 0; sub < substeps; sub++)
            {
                float alpha = (sub + 1) / (float) substeps;

                V7.set(V4).lerp(newAnchor, alpha);
                Q5.set(Q4).slerp(newAnchorRotation, alpha);

                V6.set(state.anchor);
                Q1.set(Q5).mul(Q2.set(state.anchorRotation).invert()).normalize();
                Q2.identity().slerp(Q1, ANCHOR_ROTATION_POS_FOLLOW);
                Q3.identity().slerp(Q1, ANCHOR_ROTATION_PREV_FOLLOW);

                for (int i = 1; i < state.pos.length; i++)
                {
                    Vector3f p = state.pos[i];
                    V5.set(p).sub(V6);
                    Q2.transform(V5);
                    p.set(V7).add(V5);

                    Vector3f prev = state.prev[i];
                    V5.set(prev).sub(V6);
                    Q3.transform(V5);
                    prev.set(V7).add(V5);
                }

                state.anchor.set(V7);
                state.anchorRotation.set(Q5);
                state.pos[0].set(V7);
                state.prev[0].set(V7);

                /* Collisions are resolved later during the solver iterations */
            }

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f prev = state.prev[i];

                V5.set(p).sub(prev).mul(1F - damping); // vel

                prev.set(p);
                p.add(V5);
                if (t == 0 && ANCHOR_TRANSLATION_INERTIA > 0F)
                {
                    p.x -= V3.x * ANCHOR_TRANSLATION_INERTIA;
                    p.y -= V3.y * ANCHOR_TRANSLATION_INERTIA;
                    p.z -= V3.z * ANCHOR_TRANSLATION_INERTIA;
                }
                if (t == 0 && ANCHOR_TRANSLATION_DRAG > 0F)
                {
                    p.x -= V2.x * ANCHOR_TRANSLATION_DRAG;
                    p.y -= V2.y * ANCHOR_TRANSLATION_DRAG;
                    p.z -= V2.z * ANCHOR_TRANSLATION_DRAG;
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

                if (constraints != null && !constraints.isEmpty())
                {
                    if (model instanceof Model cubic)
                    {
                        applyAngleConstraints(cubic, ids, state.pos, lengths, constraints, chainFrames.get(0).parentRotation());
                    }
                    else if (model instanceof BOBJModel bobj)
                    {
                        applyAngleConstraintsBobj(bobj, ids, state.pos, lengths, constraints, chainFrames.get(0).parentRotation());
                    }

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
                    ModelPhysicsWorldCollisions.resolve(world, state.pos, state.prev, state.contactNormals, from, to, radius, COLLISION_FRICTION, COLLISION_CONTACT_SLOP, COLLISION_SLEEP_EPS, COLLISION_STATIC_FRICTION_EPS, COLLISION_EPS);

                    state.pos[0].set(state.anchor);
                    if (targetPosition != null)
                    {
                        state.pos[state.pos.length - 1].set(targetPosition);
                    }
                }
            }

            copyPositions(state.pos, state.settled);

            state.lastAge++;
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

    private static int computeSubsteps(Vector3f from, Vector3f to)
    {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        float d2 = dx * dx + dy * dy + dz * dz;

        if (d2 <= COLLISION_MAX_ANCHOR_STEP * COLLISION_MAX_ANCHOR_STEP)
        {
            return 1;
        }

        int steps = (int) Math.ceil(Math.sqrt(d2) / COLLISION_MAX_ANCHOR_STEP);

        if (steps < 1)
        {
            return 1;
        }

        return Math.min(steps, COLLISION_MAX_SUBSTEPS);
    }

    private static void applyAngleConstraints(Model model, List<String> ids, Vector3f[] pos, float[] lengths, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Quaternionf rootParentRotation)
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
            ModelConstraintsConfig.BoneConstraint c = boneId == null ? null : constraints.get(boneId);

            ModelGroup bone = model.getGroup(boneId);
            ModelGroup child = i + 1 < boneCount ? model.getGroup(ids.get(i + 1)) : null;

            if (bone == null)
            {
                return;
            }

            Vector3f restDirLocal;

            if (child != null)
            {
                restDirLocal = new Vector3f(child.initial.translate).sub(bone.initial.translate).mul(1.0f / 16.0f);
            }
            else if (bone.children != null && !bone.children.isEmpty())
            {
                ModelGroup firstChild = bone.children.get(0);
                restDirLocal = new Vector3f(firstChild.initial.translate).sub(bone.initial.translate).mul(1.0f / 16.0f);
            }
            else
            {
                restDirLocal = new Vector3f(0F, -1F, 0F);
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
            Vector3f eulerDeg = Matrices.toEulerZYXDegrees(localRot);

            Quaternionf applied = localRot;

            if (c != null && c.enabled())
            {
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

    private static void applyAngleConstraintsBobj(BOBJModel model, List<String> ids, Vector3f[] pos, float[] lengths, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Quaternionf rootParentRotation)
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
            ModelConstraintsConfig.BoneConstraint c = boneId == null ? null : constraints.get(boneId);

            BOBJBone bone = model.getArmature().bones.get(boneId);
            BOBJBone child = i + 1 < boneCount ? model.getArmature().bones.get(ids.get(i + 1)) : null;

            if (bone == null)
            {
                return;
            }

            Vector3f restDirLocal = getBobjRestDirection(model, bone, child);
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
                Vector3f eulerRad = new Quaternionf(localRot).normalize().getEulerAnglesZYX(new Vector3f());

                float minX = (float) Math.toRadians(c.minX());
                float minY = (float) Math.toRadians(c.minY());
                float minZ = (float) Math.toRadians(c.minZ());
                float maxX = (float) Math.toRadians(c.maxX());
                float maxY = (float) Math.toRadians(c.maxY());
                float maxZ = (float) Math.toRadians(c.maxZ());

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

                eulerRad.x = eulerRad.x < minX ? minX : Math.min(eulerRad.x, maxX);
                eulerRad.y = eulerRad.y < minY ? minY : Math.min(eulerRad.y, maxY);
                eulerRad.z = eulerRad.z < minZ ? minZ : Math.min(eulerRad.z, maxZ);

                applied = new Quaternionf().rotationZYX(eulerRad.z, eulerRad.y, eulerRad.x);

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

    private static Vector3f getBobjRestDirection(BOBJModel model, BOBJBone bone, BOBJBone child)
    {
        if (child != null)
        {
            Vector3f out = child.relBoneMat.getTranslation(new Vector3f());

            if (out.lengthSquared() > EPS * EPS)
            {
                return out;
            }
        }

        for (BOBJBone candidate : model.getArmature().orderedBones)
        {
            if (candidate != null && candidate.parentBone == bone)
            {
                Vector3f out = candidate.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }
        }

        if (bone.parentBone != null)
        {
            Vector3f out = bone.relBoneMat.getTranslation(new Vector3f());

            if (out.lengthSquared() > EPS * EPS)
            {
                return out;
            }
        }

        return new Vector3f(0F, -1F, 0F);
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
