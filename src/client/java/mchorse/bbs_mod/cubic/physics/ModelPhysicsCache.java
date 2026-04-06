package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModelPhysicsCache
{
    public static final class CompiledChain
    {
        private final String id;
        private final String attach;
        private final List<String> chainRootToEnd;
        private final float[] restLengths;
        private final float gravity;
        private final float damping;
        private final int iterations;

        public CompiledChain(String id, String attach, List<String> chainRootToEnd, float[] restLengths, float gravity, float damping, int iterations)
        {
            this.id = id;
            this.attach = attach;
            this.chainRootToEnd = chainRootToEnd;
            this.restLengths = restLengths;
            this.gravity = gravity;
            this.damping = damping;
            this.iterations = iterations;
        }

        public String id()
        {
            return this.id;
        }

        public String attach()
        {
            return this.attach;
        }

        public List<String> chainRootToEnd()
        {
            return this.chainRootToEnd;
        }

        public float[] restLengths()
        {
            return this.restLengths;
        }

        public float gravity()
        {
            return this.gravity;
        }

        public float damping()
        {
            return this.damping;
        }

        public int iterations()
        {
            return this.iterations;
        }
    }

    public record Compiled(File file, long lastModified, List<CompiledChain> chains)
    {
    }

    private static final Map<String, Compiled> CACHE = new HashMap<>();

    private ModelPhysicsCache()
    {
    }

    public static void clear()
    {
        CACHE.clear();
    }

    public static void invalidate(String modelId)
    {
        if (modelId != null && !modelId.isEmpty())
        {
            CACHE.remove(modelId);
        }
    }

    public static Compiled get(String modelId, Model model)
    {
        if (modelId == null || modelId.isEmpty() || model == null)
        {
            return null;
        }

        File file = ModelPhysicsIO.getFile(modelId);
        long lm = file != null && file.exists() ? file.lastModified() : -1L;

        Compiled cached = CACHE.get(modelId);

        if (cached != null && cached.lastModified == lm)
        {
            return cached;
        }

        ModelPhysicsConfig config = ModelPhysicsIO.read(modelId);
        List<CompiledChain> compiled = compile(model, config);

        Compiled next = new Compiled(file, lm, compiled);
        CACHE.put(modelId, next);

        return next;
    }

    private static List<CompiledChain> compile(Model model, ModelPhysicsConfig config)
    {
        if (config == null || config.chains() == null || config.chains().isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        List<CompiledChain> out = new ArrayList<>();

        for (int i = 0; i < config.chains().size(); i++)
        {
            ModelPhysicsConfig.Chain chain = config.chains().get(i);

            if (chain == null)
            {
                continue;
            }

            String rootId = chain.root();
            String endId = chain.end();

            ModelGroup root = model.getGroup(rootId);
            ModelGroup end = model.getGroup(endId);

            if (root == null || end == null)
            {
                continue;
            }

            List<String> ids = buildChainIds(end, root);

            if (ids.isEmpty())
            {
                continue;
            }

            float[] lengths = computeRestLengths(model, ids);

            if (lengths == null)
            {
                continue;
            }

            String attach = chain.attach();

            if (attach == null || attach.isEmpty())
            {
                attach = rootId;
            }

            String id = i + ":" + attach + ":" + rootId + ":" + endId;
            out.add(new CompiledChain(id, attach, ids, lengths, chain.gravity(), chain.damping(), chain.iterations()));
        }

        return out;
    }

    private static List<String> buildChainIds(ModelGroup end, ModelGroup root)
    {
        List<String> list = new ArrayList<>();
        ModelGroup group = end;

        while (group != null)
        {
            list.add(group.id);

            if (group == root)
            {
                java.util.Collections.reverse(list);
                return list;
            }

            group = group.parent;
        }

        return java.util.Collections.emptyList();
    }

    private static float[] computeRestLengths(Model model, List<String> ids)
    {
        int n = ids.size();

        float[] lengths = new float[n];

        if (n == 1)
        {
            ModelGroup bone = model.getGroup(ids.get(0));

            if (bone == null)
            {
                return null;
            }

            float len = 0.25F;

            if (bone.children != null && !bone.children.isEmpty())
            {
                ModelGroup child = bone.children.get(0);
                float dx = (child.initial.translate.x - bone.initial.translate.x) / 16F;
                float dy = (child.initial.translate.y - bone.initial.translate.y) / 16F;
                float dz = (child.initial.translate.z - bone.initial.translate.z) / 16F;
                len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }

            if (len <= 1.0e-6f)
            {
                len = 1.0e-6f;
            }

            lengths[0] = len;

            return lengths;
        }

        for (int i = 0; i < n - 1; i++)
        {
            ModelGroup a = model.getGroup(ids.get(i));
            ModelGroup b = model.getGroup(ids.get(i + 1));

            if (a == null || b == null)
            {
                return null;
            }

            float dx = (b.initial.translate.x - a.initial.translate.x) / 16F;
            float dy = (b.initial.translate.y - a.initial.translate.y) / 16F;
            float dz = (b.initial.translate.z - a.initial.translate.z) / 16F;

            lengths[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (lengths[i] <= 1.0e-6f)
            {
                lengths[i] = 1.0e-6f;
            }
        }

        lengths[n - 1] = lengths[n - 2];

        return lengths;
    }
}
