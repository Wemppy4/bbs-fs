package mchorse.bbs_mod.forms.renderers;

import com.mojang.datafixers.util.Pair;
import mchorse.bbs_mod.mixin.client.LivingEntityRendererAccessor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.CompositeEntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Discovers the baked model hierarchies owned by a vanilla entity renderer.
 *
 * <p>The scanner deliberately only descends into models, model parts and a small set of
 * containers used by vanilla renderers. Living renderer features are obtained through a controlled
 * accessor and scanned as separate owners; arbitrary renderer-owned objects are never traversed.
 * Results are cached by renderer identity and contain only the weak part references supplied by
 * {@link VanillaBoneHierarchy}.</p>
 */
public final class VanillaRendererBones
{
    private static final ReferenceQueue<Object> STALE_RENDERERS = new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, Discovery> CACHE = new HashMap<>();

    private VanillaRendererBones()
    {}

    public static synchronized Discovery discover(Object renderer)
    {
        if (renderer == null)
        {
            return Discovery.EMPTY;
        }

        removeStaleRenderers();

        IdentityWeakReference lookup = new IdentityWeakReference(renderer);
        Discovery discovery = CACHE.get(lookup);
        long hierarchyRevision = VanillaBoneHierarchy.getRevision();

        if (discovery == null || discovery.hierarchyRevision != hierarchyRevision)
        {
            Scanner scanner = new Scanner();

            scanner.scanFields(renderer);

            if (renderer instanceof LivingEntityRendererAccessor accessor)
            {
                for (FeatureRenderer<?, ?> feature : accessor.bbs$getFeatures())
                {
                    scanner.scanFields(feature);
                }
            }

            discovery = scanner.createDiscovery(hierarchyRevision);
            CACHE.put(new IdentityWeakReference(renderer, STALE_RENDERERS), discovery);
        }

        return discovery;
    }

    public static synchronized void clear()
    {
        CACHE.clear();

        while (STALE_RENDERERS.poll() != null)
        {}
    }

    private static void removeStaleRenderers()
    {
        IdentityWeakReference reference;

        while ((reference = (IdentityWeakReference) STALE_RENDERERS.poll()) != null)
        {
            CACHE.remove(reference);
        }
    }

    public static final class Discovery
    {
        private static final Discovery EMPTY = new Discovery(Collections.emptyList(), -1L);

        private final List<VanillaBoneHierarchy.Hierarchy> hierarchies;
        private final List<String> boneIds;
        private final Map<String, VanillaBoneHierarchy.Bone> bonesById;
        private final long hierarchyRevision;

        private Discovery(List<VanillaBoneHierarchy.Hierarchy> hierarchies, long hierarchyRevision)
        {
            this.hierarchies = List.copyOf(hierarchies);
            this.hierarchyRevision = hierarchyRevision;

            List<String> boneIds = new ArrayList<>();
            Map<String, VanillaBoneHierarchy.Bone> bonesById = new LinkedHashMap<>();

            for (VanillaBoneHierarchy.Hierarchy hierarchy : this.hierarchies)
            {
                for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
                {
                    if (!bonesById.containsKey(bone.getId()))
                    {
                        boneIds.add(bone.getId());
                        bonesById.put(bone.getId(), bone);
                    }
                }
            }

            this.boneIds = Collections.unmodifiableList(boneIds);
            this.bonesById = Collections.unmodifiableMap(bonesById);
        }

        public List<String> getBoneIds()
        {
            return this.boneIds;
        }

        public List<VanillaBoneHierarchy.Hierarchy> getHierarchies()
        {
            return this.hierarchies;
        }

        /**
         * Resolves a stable ID or an unambiguous relative/legacy name across all discovered layers.
         */
        public Optional<VanillaBoneHierarchy.Bone> resolve(String id)
        {
            VanillaBoneHierarchy.Bone exact = this.bonesById.get(id);

            if (exact != null)
            {
                return Optional.of(exact);
            }

            VanillaBoneHierarchy.Bone resolved = null;

            for (VanillaBoneHierarchy.Hierarchy hierarchy : this.hierarchies)
            {
                Optional<VanillaBoneHierarchy.Bone> candidate = hierarchy.resolve(id);

                if (candidate.isEmpty())
                {
                    continue;
                }

                if (resolved != null && !resolved.getId().equals(candidate.get().getId()))
                {
                    return Optional.empty();
                }

                resolved = candidate.get();
            }

            return Optional.ofNullable(resolved);
        }

        /**
         * Produces mapping-independent lines suitable for comparing dev and remapped clients.
         */
        public List<String> getDiagnosticLines()
        {
            List<String> lines = new ArrayList<>();

            for (VanillaBoneHierarchy.Hierarchy hierarchy : this.hierarchies)
            {
                for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
                {
                    lines.add(bone.getId() + "\t" + bone.getName() + "\t" +
                        (bone.getParentId() == null ? "" : bone.getParentId()) + "\t" + bone.getDepth());
                }
            }

            return Collections.unmodifiableList(lines);
        }
    }

    private static final class Scanner
    {
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        private final Map<String, VanillaBoneHierarchy.Hierarchy> hierarchies = new TreeMap<>();

        private void scanFields(Object owner)
        {
            Class<?> type = owner.getClass();

            while (type != null && type != Object.class)
            {
                for (Field field : type.getDeclaredFields())
                {
                    if (Modifier.isStatic(field.getModifiers()) || !isSupportedFieldType(field.getType()))
                    {
                        continue;
                    }

                    try
                    {
                        field.setAccessible(true);
                        this.scan(field.get(owner), false);
                    }
                    catch (ReflectiveOperationException | RuntimeException ignored)
                    {}
                }

                type = type.getSuperclass();
            }
        }

        private void scan(Object value, boolean mapValue)
        {
            if (value == null)
            {
                return;
            }

            if (value instanceof ModelPart part)
            {
                VanillaBoneHierarchy.getHierarchy(part).ifPresent((hierarchy) ->
                    this.hierarchies.putIfAbsent(hierarchy.getLayerId(), hierarchy));

                return;
            }

            if (value instanceof Model model)
            {
                if (this.visited.put(model, Boolean.TRUE) != null)
                {
                    return;
                }

                if (model instanceof SinglePartEntityModel<?> singlePartModel)
                {
                    this.scan(singlePartModel.getPart(), false);
                }
                else if (model instanceof CompositeEntityModel<?> compositeModel)
                {
                    this.scan(compositeModel.getParts(), false);
                }

                /* Dragon and a few other vanilla models expose neither root API. */
                this.scanFields(model);

                return;
            }

            Class<?> type = value.getClass();

            if (type.isArray())
            {
                if (this.visited.put(value, Boolean.TRUE) != null)
                {
                    return;
                }

                int length = Array.getLength(value);

                for (int i = 0; i < length; i++)
                {
                    this.scan(Array.get(value, i), false);
                }

                return;
            }

            if (value instanceof Map<?, ?> map)
            {
                if (this.visited.put(value, Boolean.TRUE) != null)
                {
                    return;
                }

                for (Object entryValue : map.values())
                {
                    this.scan(entryValue, true);
                }

                return;
            }

            if (value instanceof Iterable<?> iterable)
            {
                if (this.visited.put(value, Boolean.TRUE) != null)
                {
                    return;
                }

                for (Object element : iterable)
                {
                    this.scan(element, false);
                }

                return;
            }

            /* Boat/Raft renderer maps wrap each model with its texture in a DFU Pair. */
            if (mapValue && value instanceof Pair<?, ?> pair)
            {
                this.scan(pair.getSecond(), false);
            }
        }

        private Discovery createDiscovery(long hierarchyRevision)
        {
            return new Discovery(new ArrayList<>(this.hierarchies.values()), hierarchyRevision);
        }

        private static boolean isSupportedFieldType(Class<?> type)
        {
            return Model.class.isAssignableFrom(type) ||
                ModelPart.class.isAssignableFrom(type) ||
                type.isArray() ||
                Iterable.class.isAssignableFrom(type) ||
                Map.class.isAssignableFrom(type);
        }
    }

    private static final class IdentityWeakReference extends WeakReference<Object>
    {
        private final int hash;

        private IdentityWeakReference(Object renderer)
        {
            super(renderer);
            this.hash = System.identityHashCode(renderer);
        }

        private IdentityWeakReference(Object renderer, ReferenceQueue<Object> queue)
        {
            super(renderer, queue);
            this.hash = System.identityHashCode(renderer);
        }

        @Override
        public int hashCode()
        {
            return this.hash;
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object)
            {
                return true;
            }

            if (!(object instanceof IdentityWeakReference other))
            {
                return false;
            }

            Object renderer = this.get();

            return renderer != null && renderer == other.get();
        }
    }
}
