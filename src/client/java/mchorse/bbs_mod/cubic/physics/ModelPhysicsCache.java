package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ModelPhysicsCache
{
    public static final class CompiledChain
    {
        private final String id;
        private final String attach;
        private final String targetBone;
        private final List<String> chainRootToEnd;
        private final float[] restLengths;
        private final int iterations;
        private final boolean relativeGravity;
        private final boolean hasGravityRotation;
        private final Quaternionf gravityRotation;
        private final boolean collisions;
        private final float radius;

        public CompiledChain(String id, String attach, String targetBone, List<String> chainRootToEnd, float[] restLengths, FormBone bone)
        {
            this.id = id;
            this.attach = attach;
            this.targetBone = targetBone;
            this.chainRootToEnd = chainRootToEnd;
            this.restLengths = restLengths;
            this.iterations = bone.physicsIterations.get();
            this.relativeGravity = bone.physicsRelativeGravity.get();
            this.hasGravityRotation = bone.physicsGravityRotateX.get() != 0F || bone.physicsGravityRotateY.get() != 0F || bone.physicsGravityRotateZ.get() != 0F;
            this.gravityRotation = this.hasGravityRotation
                ? Matrices.toQuaternionZYXDegrees(bone.physicsGravityRotateX.get(), bone.physicsGravityRotateY.get(), bone.physicsGravityRotateZ.get())
                : new Quaternionf();
            this.collisions = bone.physicsCollisions.get();
            this.radius = bone.physicsRadius.get();
        }

        public String id()
        {
            return this.id;
        }

        public String attach()
        {
            return this.attach;
        }

        public String targetBone()
        {
            return this.targetBone;
        }

        public List<String> chainRootToEnd()
        {
            return this.chainRootToEnd;
        }

        public float[] restLengths()
        {
            return this.restLengths;
        }

        public int iterations()
        {
            return this.iterations;
        }

        public boolean relativeGravity()
        {
            return this.relativeGravity;
        }

        public boolean hasGravityRotation()
        {
            return this.hasGravityRotation;
        }

        public void applyGravityRotation(Vector3f direction)
        {
            if (this.hasGravityRotation)
            {
                this.gravityRotation.transform(direction);
            }
        }

        public boolean collisions()
        {
            return this.collisions;
        }

        public float radius()
        {
            return this.radius;
        }

    }

    public record Compiled(List<CompiledChain> chains)
    {
    }

    private ModelPhysicsCache()
    {
    }

    /**
     * Compiles the form's physics chain topology against a model. Only the structure and the
     * simulation's configuration are compiled — the animatable scalars are read live from the
     * bone's {@code physics} property at solve time, so a film track or an edit shows up without
     * any cache to invalidate.
     */
    /* One-slot per-frame memo - see ModelIKCache: the walk used to repeat per render pass. */
    private static IModel lastModel;
    private static ModelForm lastForm;
    private static long lastEpoch;
    private static Compiled lastCompiled;

    public static Compiled compile(IModel model, ModelForm form)
    {
        if (model == null || form == null)
        {
            return null;
        }

        if (RenderFrame.isEnabled() && lastModel == model && lastForm == form && lastEpoch == RenderFrame.getEpoch())
        {
            return lastCompiled;
        }

        Compiled compiled = compileFresh(model, form);

        lastModel = model;
        lastForm = form;
        lastEpoch = RenderFrame.getEpoch();
        lastCompiled = compiled;

        return compiled;
    }

    private static Compiled compileFresh(IModel model, ModelForm form)
    {
        List<CompiledChain> out = null;

        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone) || !bone.hasPhysicsChain())
            {
                continue;
            }

            String rootId = bone.getId();
            String endId = bone.physicsEnd.get();

            if (!model.getAllGroupKeys().contains(rootId) || !model.getAllGroupKeys().contains(endId))
            {
                continue;
            }

            List<String> ids = buildChainIds(model, endId, rootId);

            if (ids.isEmpty())
            {
                continue;
            }

            float[] lengths = computeRestLengths(model, ids);

            if (lengths == null)
            {
                continue;
            }

            if (out == null)
            {
                out = new ArrayList<>();
            }

            out.add(new CompiledChain(rootId + ":" + endId, rootId, bone.physicsTargetBone.get(), ids, lengths, bone));
        }

        if (out == null)
        {
            return null;
        }

        /* Chains ordered by their root bone — the order the old config map iterated in. */
        out.sort((a, b) -> a.id().compareTo(b.id()));

        return new Compiled(out);
    }

    private static List<String> buildChainIds(IModel model, String endId, String rootId)
    {
        List<String> list = new ArrayList<>();
        String group = endId;

        while (group != null && !group.isEmpty())
        {
            list.add(group);

            if (group.equals(rootId))
            {
                Collections.reverse(list);
                return list;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        return Collections.emptyList();
    }

    private static float[] computeRestLengths(IModel model, List<String> ids)
    {
        PhysicsRig rig = PhysicsRig.of(model);

        if (rig == null)
        {
            return null;
        }

        int n = ids.size();
        float[] lengths = new float[n];

        if (n == 1)
        {
            float len = rig.restLength(ids.get(0), null);

            if (len < 0F)
            {
                return null;
            }

            lengths[0] = len;

            return lengths;
        }

        for (int i = 0; i < n - 1; i++)
        {
            float len = rig.restLength(ids.get(i), ids.get(i + 1));

            if (len < 0F)
            {
                return null;
            }

            lengths[i] = len;
        }

        lengths[n - 1] = lengths[n - 2];

        return lengths;
    }
}
