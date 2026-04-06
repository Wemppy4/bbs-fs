package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.forms.entities.IEntity;
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

    private static final class ChainState
    {
        public int lastAge = Integer.MIN_VALUE;
        public Vector3f anchor = new Vector3f();
        public Vector3f attach = new Vector3f();
        public Quaternionf attachRotation = new Quaternionf();
        public Vector3f attachVelocity = new Vector3f();
        public Vector3f rootOffsetLocal = new Vector3f();
        public boolean rootOffsetReady;
        public Vector3f[] pos;
        public Vector3f[] prev;
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
        ModelPhysicsCache.invalidate(modelId);

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
        if (entity == null || instance == null || !(instance.model instanceof Model model))
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = ModelPhysicsCache.get(instance.id, model);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, InstanceState> byModel = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byModel.computeIfAbsent(instance.id, (k) -> new InstanceState());

        applyCompiled(entity.getAge(), transition, model, compiled.chains(), state, baseTransform);
    }

    private static void applyCompiled(int age, float transition, Model model, List<ModelPhysicsCache.CompiledChain> compiledChains, InstanceState state, Matrix4f baseTransform)
    {
        Set<String> wanted = new HashSet<>();
        Set<String> chainIds = new HashSet<>();

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            chainIds.add(chain.id());
            wanted.add(chain.attach());
            wanted.addAll(chain.chainRootToEnd());
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
        CubicRenderer.collectPivotFrames(model, wanted, frames, baseTransform);

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            applyChain(age, transition, model, chain, frames, state);
        }
    }

    private static void applyChain(int age, float transition, Model model, ModelPhysicsCache.CompiledChain chain, Map<String, PivotFrame> frames, InstanceState instanceState)
    {
        PivotFrame attachFrame = frames.get(chain.attach());

        if (attachFrame == null)
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

        ChainState state = instanceState.chains.computeIfAbsent(chain.id(), (k) -> new ChainState());

        if (state.pos == null || state.pos.length != pointCount)
        {
            state.pos = new Vector3f[pointCount];
            state.prev = new Vector3f[pointCount];
            state.render = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.render[i] = new Vector3f();
            }

            state.lastAge = Integer.MIN_VALUE;
            state.rootOffsetReady = false;
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

        Quaternionf attachRotation = attachFrame.worldRotation();
        step(age, chain, attachFrame.position(), attachRotation, chainFrames, state);
        Vector3f[] positions = interpolate(state, transition);
        applyRenderAnchorFollow(state, positions, attachFrame.position(), attachRotation, transition);
        CubicRenderer.applyRotations(model, chainFrames.get(0).parentRotation(), ids, positions);
    }

    private static Vector3f[] interpolate(ChainState state, float transition)
    {
        if (state.prev == null || state.pos == null || state.render == null || state.prev.length != state.pos.length || state.render.length != state.pos.length)
        {
            return state.pos;
        }

        float t = transition;

        if (t < 0F)
        {
            t = 0F;
        }
        else if (t > 1F)
        {
            t = 1F;
        }

        for (int i = 0; i < state.pos.length; i++)
        {
            state.render[i].set(state.prev[i]).lerp(state.pos[i], t);
        }

        return state.render;
    }

    private static void applyRenderAnchorFollow(ChainState state, Vector3f[] positions, Vector3f attachPosition, Quaternionf attachRotation, float transition)
    {
        if (state.lastAge == Integer.MIN_VALUE || positions == null || positions.length == 0)
        {
            return;
        }

        float t = transition;

        if (t <= 0F)
        {
            return;
        }
        else if (t > 1F)
        {
            t = 1F;
        }

        Vector3f oldAttach = state.attach;
        Vector3f delta = new Vector3f(attachPosition).sub(oldAttach).mul(t);

        Quaternionf dq = new Quaternionf(attachRotation).mul(new Quaternionf(state.attachRotation).invert()).normalize();
        Quaternionf dqPartial = new Quaternionf().identity().slerp(dq, t);

        if (Math.abs(delta.x) < EPS && Math.abs(delta.y) < EPS && Math.abs(delta.z) < EPS && Math.abs(dqPartial.x) < EPS && Math.abs(dqPartial.y) < EPS && Math.abs(dqPartial.z) < EPS)
        {
            return;
        }

        for (int i = 0; i < positions.length; i++)
        {
            Vector3f rel = new Vector3f(positions[i]).sub(oldAttach);
            dqPartial.transform(rel);
            positions[i].set(oldAttach).add(delta).add(rel);
        }
    }

    private static void step(int age, ModelPhysicsCache.CompiledChain chain, Vector3f attachPosition, Quaternionf attachRotation, List<PivotFrame> chainFrames, ChainState state)
    {
        Vector3f newAttach = attachPosition;
        Quaternionf newAttachRotation = attachRotation;
        float[] lengths = chain.restLengths();

        if (lengths == null || lengths.length != state.pos.length - 1)
        {
            return;
        }

        if (state.lastAge == Integer.MIN_VALUE)
        {
            state.attach.set(newAttach);
            state.attachRotation.set(newAttachRotation);
            state.attachVelocity.set(0F, 0F, 0F);

            Vector3f rootPos = chainFrames.get(0).position();

            if (!state.rootOffsetReady)
            {
                Vector3f offsetWorld = new Vector3f(rootPos).sub(newAttach);
                Quaternionf inv = new Quaternionf(newAttachRotation).invert();
                inv.transform(offsetWorld);
                state.rootOffsetLocal.set(offsetWorld);
                state.rootOffsetReady = true;
            }

            state.anchor.set(rootPos);
            state.pos[0].set(rootPos);
            state.prev[0].set(rootPos);

            for (int i = 1; i < chainFrames.size(); i++)
            {
                Vector3f p = chainFrames.get(i).position();
                state.pos[i].set(p);
                state.prev[i].set(p);
            }

            Vector3f tipDir;

            if (chainFrames.size() >= 2)
            {
                tipDir = new Vector3f(state.pos[chainFrames.size() - 1]).sub(state.pos[chainFrames.size() - 2]);

                if (tipDir.lengthSquared() < EPS * EPS)
                {
                    tipDir.set(0F, -1F, 0F);
                }
                else
                {
                    tipDir.normalize();
                }
            }
            else
            {
                tipDir = new Vector3f(0F, -1F, 0F);
            }

            state.pos[state.pos.length - 1].set(state.pos[chainFrames.size() - 1]).add(tipDir.mul(lengths[lengths.length - 1]));
            state.prev[state.prev.length - 1].set(state.pos[state.pos.length - 1]);

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

        Vector3f oldAttachTick = new Vector3f(state.attach);
        Vector3f velAttach = new Vector3f(newAttach).sub(oldAttachTick).mul(1F / dt);
        Vector3f accelAttach = new Vector3f(velAttach).sub(state.attachVelocity);
        state.attachVelocity.set(velAttach);

        for (int t = 0; t < dt; t++)
        {
            Vector3f oldAttach = new Vector3f(state.attach);
            Quaternionf dq = new Quaternionf(newAttachRotation).mul(new Quaternionf(state.attachRotation).invert()).normalize();
            Quaternionf dqPos = new Quaternionf().identity().slerp(dq, ANCHOR_ROTATION_POS_FOLLOW);
            Quaternionf dqPrev = new Quaternionf().identity().slerp(dq, ANCHOR_ROTATION_PREV_FOLLOW);

            for (int i = 0; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f rel = new Vector3f(p).sub(oldAttach);
                dqPos.transform(rel);
                p.set(newAttach).add(rel);

                Vector3f prev = state.prev[i];
                Vector3f relPrev = new Vector3f(prev).sub(oldAttach);
                dqPrev.transform(relPrev);
                prev.set(newAttach).add(relPrev);
            }

            state.attach.set(newAttach);
            state.attachRotation.set(newAttachRotation);

            Vector3f offsetWorld = new Vector3f(state.rootOffsetLocal);
            new Quaternionf(newAttachRotation).transform(offsetWorld);
            Vector3f anchor = new Vector3f(newAttach).add(offsetWorld);

            state.anchor.set(anchor);
            state.pos[0].set(anchor);
            state.prev[0].set(anchor);

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f prev = state.prev[i];

                Vector3f vel = new Vector3f(p).sub(prev).mul(1F - damping);

                prev.set(p);
                p.add(vel);
                if (t == 0 && ANCHOR_TRANSLATION_INERTIA > 0F)
                {
                    p.x -= accelAttach.x * ANCHOR_TRANSLATION_INERTIA;
                    p.y -= accelAttach.y * ANCHOR_TRANSLATION_INERTIA;
                    p.z -= accelAttach.z * ANCHOR_TRANSLATION_INERTIA;
                }
                p.y -= gravity;
            }

            for (int iter = 0; iter < iterations; iter++)
            {
                for (int i = state.pos.length - 2; i >= 0; i--)
                {
                    Vector3f a = state.pos[i];
                    Vector3f b = state.pos[i + 1];

                    Vector3f dir = new Vector3f(a).sub(b);
                    float lenSq = dir.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    dir.mul((float) (lengths[i] / Math.sqrt(lenSq)));
                    a.set(b).add(dir);
                }

                state.pos[0].set(state.anchor);

                for (int i = 1; i < state.pos.length; i++)
                {
                    Vector3f a = state.pos[i - 1];
                    Vector3f b = state.pos[i];

                    Vector3f dir = new Vector3f(b).sub(a);
                    float lenSq = dir.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    dir.mul((float) (lengths[i - 1] / Math.sqrt(lenSq)));
                    b.set(a).add(dir);
                }
            }

            state.lastAge++;
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
