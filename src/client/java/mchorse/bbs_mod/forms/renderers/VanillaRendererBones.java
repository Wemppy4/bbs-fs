package mchorse.bbs_mod.forms.renderers;

import com.mojang.datafixers.util.Pair;
import mchorse.bbs_mod.mixin.client.LivingEntityRendererAccessor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

            if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
            {
                scanner.scan(livingRenderer.getModel(), true, null);
            }

            scanner.scanFields(renderer, true);

            if (renderer instanceof LivingEntityRendererAccessor accessor)
            {
                for (FeatureRenderer<?, ?> feature : accessor.bbs$getFeatures())
                {
                    scanner.scanFeature(feature);
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
        private static final Discovery EMPTY = new Discovery(
            Collections.emptyList(),
            Collections.emptyList(),
            new IdentityHashMap<>(),
            new IdentityHashMap<>(),
            new IdentityHashMap<>(),
            -1L
        );

        private final List<VanillaBoneHierarchy.Hierarchy> hierarchies;
        private final List<VanillaBoneHierarchy.Hierarchy> runtimeHierarchies;
        private final List<VanillaBoneHierarchy.Hierarchy> legacyHierarchies;
        private final List<String> boneIds;
        private final Map<String, VanillaBoneHierarchy.Bone> bonesById;
        private final IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> canonicalBonesByPart;
        private final IdentityHashMap<VanillaBoneHierarchy.Bone, VanillaBoneHierarchy.Bone> canonicalBones;
        private final Map<String, List<VanillaBoneHierarchy.Bone>> runtimeBonesById;
        private final IdentityHashMap<VanillaBoneHierarchy.Hierarchy, AlternativeRef> variantGroups;
        private final IdentityHashMap<VanillaBoneHierarchy.Hierarchy, List<VanillaBoneHierarchy.Hierarchy>> featureContexts;
        private final Map<String, String> aliases;
        private final BoneHierarchy boneHierarchy;
        private final long hierarchyRevision;

        private Discovery(
            List<VanillaBoneHierarchy.Hierarchy> runtimeHierarchies,
            List<VanillaBoneHierarchy.Hierarchy> legacyHierarchies,
            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, AlternativeRef> alternativeGroups,
            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Boolean> primaryHierarchies,
            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, List<VanillaBoneHierarchy.Hierarchy>> featureContexts,
            long hierarchyRevision
        )
        {
            this.runtimeHierarchies = List.copyOf(runtimeHierarchies);
            this.legacyHierarchies = List.copyOf(legacyHierarchies);
            this.variantGroups = new IdentityHashMap<>(alternativeGroups);
            this.featureContexts = new IdentityHashMap<>();

            for (Map.Entry<VanillaBoneHierarchy.Hierarchy, List<VanillaBoneHierarchy.Hierarchy>> entry : featureContexts.entrySet())
            {
                this.featureContexts.put(entry.getKey(), List.copyOf(entry.getValue()));
            }

            this.hierarchyRevision = hierarchyRevision;

            List<VanillaBoneHierarchy.Hierarchy> canonicalOrder = new ArrayList<>(this.runtimeHierarchies);

            canonicalOrder.sort(Discovery::compareHierarchies);

            Map<StructureKey, VanillaBoneHierarchy.Hierarchy> canonicalByLayer = new LinkedHashMap<>();
            IdentityHashMap<Object, Map<AlternativeKey, AlternativeSet>> alternatives = new IdentityHashMap<>();
            IdentityHashMap<Object, Map<AlternativeBoneKey, AlternativeBoneSet>> alternativeBones = new IdentityHashMap<>();
            IdentityHashMap<VanillaBoneHierarchy.Bone, VanillaBoneHierarchy.Hierarchy> hierarchyByBone = new IdentityHashMap<>();

            for (VanillaBoneHierarchy.Hierarchy hierarchy : canonicalOrder)
            {
                canonicalByLayer.putIfAbsent(StructureKey.of(hierarchy), hierarchy);

                AlternativeRef alternative = alternativeGroups.get(hierarchy);

                for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
                {
                    hierarchyByBone.put(bone, hierarchy);

                    if (alternative != null && alternative.fold)
                    {
                        alternativeBones.computeIfAbsent(alternative.container, (key) -> new LinkedHashMap<>())
                            .computeIfAbsent(
                                new AlternativeBoneKey(bone.getPath(), getLayerRole(hierarchy.getLayerId())),
                                (key) -> new AlternativeBoneSet()
                            )
                            .add(hierarchy, bone, alternative.member);
                    }
                }

                if (alternative != null && alternative.fold && alternative.modelType != null)
                {
                    alternatives.computeIfAbsent(alternative.container, (key) -> new LinkedHashMap<>())
                        .computeIfAbsent(
                            new AlternativeKey(hierarchy.getStructureKey(), alternative.modelType, getLayerRole(hierarchy.getLayerId())),
                            (key) -> new AlternativeSet()
                        )
                        .add(hierarchy, alternative.member);
                }
            }

            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, VanillaBoneHierarchy.Hierarchy> canonicalAlternatives = new IdentityHashMap<>();

            for (Map<AlternativeKey, AlternativeSet> groups : alternatives.values())
            {
                for (AlternativeSet group : groups.values())
                {
                    if (!group.isUnambiguous())
                    {
                        continue;
                    }

                    VanillaBoneHierarchy.Hierarchy canonical = canonicalByLayer.get(StructureKey.of(group.hierarchies.get(0)));

                    for (VanillaBoneHierarchy.Hierarchy hierarchy : group.hierarchies)
                    {
                        canonicalAlternatives.put(hierarchy, canonical);
                    }
                }
            }

            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, VanillaBoneHierarchy.Hierarchy> canonicalByRuntime = new IdentityHashMap<>();

            for (VanillaBoneHierarchy.Hierarchy hierarchy : this.runtimeHierarchies)
            {
                VanillaBoneHierarchy.Hierarchy sameLayer = canonicalByLayer.get(StructureKey.of(hierarchy));
                VanillaBoneHierarchy.Hierarchy canonical = canonicalAlternatives.get(hierarchy);

                if (canonical == null)
                {
                    canonical = canonicalAlternatives.getOrDefault(sameLayer, sameLayer);
                }

                canonicalByRuntime.put(hierarchy, canonical);
            }

            IdentityHashMap<VanillaBoneHierarchy.Bone, VanillaBoneHierarchy.Bone> canonicalAlternativeBones = new IdentityHashMap<>();

            for (Map<AlternativeBoneKey, AlternativeBoneSet> groups : alternativeBones.values())
            {
                for (AlternativeBoneSet group : groups.values())
                {
                    if (!group.isUnambiguous())
                    {
                        continue;
                    }

                    AlternativeBone candidate = group.bones.get(0);
                    VanillaBoneHierarchy.Hierarchy canonicalHierarchy = canonicalByRuntime.get(candidate.hierarchy);
                    VanillaBoneHierarchy.Bone canonicalBone = canonicalHierarchy.resolve(candidate.bone.getPath()).orElse(null);

                    if (canonicalBone == null)
                    {
                        continue;
                    }

                    for (AlternativeBone bone : group.bones)
                    {
                        canonicalAlternativeBones.put(bone.bone, canonicalBone);
                    }
                }
            }

            List<VanillaBoneHierarchy.Hierarchy> hierarchies = new ArrayList<>();
            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Boolean> added = new IdentityHashMap<>();
            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Boolean> canonicalPrimary = new IdentityHashMap<>();

            for (VanillaBoneHierarchy.Hierarchy hierarchy : this.runtimeHierarchies)
            {
                VanillaBoneHierarchy.Hierarchy canonical = canonicalByRuntime.get(hierarchy);

                if (added.put(canonical, Boolean.TRUE) == null)
                {
                    hierarchies.add(canonical);
                }

                if (primaryHierarchies.containsKey(hierarchy))
                {
                    canonicalPrimary.put(canonical, Boolean.TRUE);
                }
            }

            hierarchies.sort((a, b) ->
            {
                int primary = Boolean.compare(canonicalPrimary.containsKey(b), canonicalPrimary.containsKey(a));

                return primary == 0 ? compareHierarchies(a, b) : primary;
            });

            this.hierarchies = List.copyOf(hierarchies);

            Map<String, String> aliases = new LinkedHashMap<>();
            Set<String> ambiguousAliases = new HashSet<>();
            Map<String, Boolean> primaryAliases = new HashMap<>();

            IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> canonicalBonesByPart = new IdentityHashMap<>();
            IdentityHashMap<VanillaBoneHierarchy.Bone, VanillaBoneHierarchy.Bone> canonicalBones = new IdentityHashMap<>();
            IdentityHashMap<VanillaBoneHierarchy.Bone, Boolean> canonicalPrimaryBones = new IdentityHashMap<>();
            Map<String, List<VanillaBoneHierarchy.Bone>> runtimeBonesById = new LinkedHashMap<>();

            for (VanillaBoneHierarchy.Hierarchy hierarchy : this.runtimeHierarchies)
            {
                VanillaBoneHierarchy.Hierarchy canonical = canonicalByRuntime.get(hierarchy);

                for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
                {
                    VanillaBoneHierarchy.Bone canonicalBone = canonical.resolve(bone.getPath()).orElse(null);
                    ModelPart part = bone.getPart();

                    if (canonicalBone == null)
                    {
                        continue;
                    }

                    canonicalBone = canonicalAlternativeBones.getOrDefault(
                        bone,
                        canonicalAlternativeBones.getOrDefault(canonicalBone, canonicalBone)
                    );

                    canonicalBones.put(bone, canonicalBone);

                    if (primaryHierarchies.containsKey(hierarchy))
                    {
                        canonicalPrimaryBones.put(canonicalBone, Boolean.TRUE);
                    }

                    if (part != null)
                    {
                        canonicalBonesByPart.put(part, canonicalBone);
                    }

                    runtimeBonesById.computeIfAbsent(canonicalBone.getId(), (key) -> new ArrayList<>()).add(bone);

                    if (!bone.getId().equals(canonicalBone.getId()))
                    {
                        addAlias(bone.getId(), canonicalBone.getId(), false, aliases, primaryAliases, ambiguousAliases);
                    }

                    String legacyPlayerSlimId = VanillaBoneHierarchy.getLegacyPlayerSlimBoneId(canonicalBone.getId());

                    if (legacyPlayerSlimId != null)
                    {
                        addAlias(
                            legacyPlayerSlimId,
                            canonicalBone.getId(),
                            primaryHierarchies.containsKey(hierarchy),
                            aliases,
                            primaryAliases,
                            ambiguousAliases
                        );
                    }
                }
            }

            this.canonicalBonesByPart = canonicalBonesByPart;
            this.canonicalBones = canonicalBones;

            Map<String, List<VanillaBoneHierarchy.Bone>> immutableRuntimeBones = new LinkedHashMap<>();

            for (Map.Entry<String, List<VanillaBoneHierarchy.Bone>> entry : runtimeBonesById.entrySet())
            {
                immutableRuntimeBones.put(entry.getKey(), List.copyOf(entry.getValue()));
            }

            this.runtimeBonesById = Collections.unmodifiableMap(immutableRuntimeBones);

            List<VanillaBoneHierarchy.Hierarchy> displayOrder = new ArrayList<>(this.runtimeHierarchies);
            List<String> boneIds = new ArrayList<>();
            Map<String, VanillaBoneHierarchy.Bone> bonesById = new LinkedHashMap<>();
            List<BoneHierarchy.Bone> hierarchyBones = new ArrayList<>();
            Set<String> addedBoneIds = new HashSet<>();

            displayOrder.sort((a, b) ->
            {
                int primary = Boolean.compare(primaryHierarchies.containsKey(b), primaryHierarchies.containsKey(a));

                return primary == 0 ? compareHierarchies(a, b) : primary;
            });

            for (VanillaBoneHierarchy.Hierarchy hierarchy : displayOrder)
            {
                for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
                {
                    VanillaBoneHierarchy.Bone canonicalBone = this.canonicalBones.get(bone);

                    if (canonicalBone == null || !addedBoneIds.add(canonicalBone.getId()))
                    {
                        continue;
                    }

                    VanillaBoneHierarchy.Hierarchy canonicalHierarchy = hierarchyByBone.get(canonicalBone);
                    VanillaBoneHierarchy.Bone parent = bone.getParentId() == null
                        ? null
                        : hierarchy.resolve(bone.getParentId()).orElse(null);
                    VanillaBoneHierarchy.Bone canonicalParent = parent == null ? null : this.canonicalBones.get(parent);

                    boneIds.add(canonicalBone.getId());
                    bonesById.put(canonicalBone.getId(), canonicalBone);
                    hierarchyBones.add(new BoneHierarchy.Bone(
                        canonicalBone.getId(),
                        canonicalBone.getName(),
                        canonicalParent == null ? null : canonicalParent.getId(),
                        canonicalBone.getDepth(),
                        canonicalHierarchy == null ? hierarchy.getLayerId() : canonicalHierarchy.getLayerId(),
                        canonicalPrimaryBones.containsKey(canonicalBone)
                    ));
                }
            }

            this.boneIds = Collections.unmodifiableList(boneIds);
            this.bonesById = Collections.unmodifiableMap(bonesById);

            for (VanillaBoneHierarchy.Hierarchy hierarchy : this.legacyHierarchies)
            {
                for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
                {
                    VanillaBoneHierarchy.Bone canonicalBone = this.canonicalBonesByPart.get(bone.getPart());

                    if (canonicalBone != null)
                    {
                        boolean primary = primaryHierarchies.containsKey(hierarchy);

                        addAlias(hierarchy, bone, bone.getPath(), canonicalBone.getId(), primary, aliases, primaryAliases, ambiguousAliases);
                        addAlias(hierarchy, bone, bone.getName(), canonicalBone.getId(), primary, aliases, primaryAliases, ambiguousAliases);
                        addAlias(hierarchy, bone, VanillaBoneHierarchy.toCamelCase(bone.getName()), canonicalBone.getId(), primary, aliases, primaryAliases, ambiguousAliases);
                    }
                }
            }

            this.aliases = Collections.unmodifiableMap(aliases);
            this.boneHierarchy = new BoneHierarchy(hierarchyBones, aliases);
        }

        private static int compareHierarchies(VanillaBoneHierarchy.Hierarchy a, VanillaBoneHierarchy.Hierarchy b)
        {
            int layer = VanillaBoneHierarchy.compareNames(a.getLayerId(), b.getLayerId());

            if (layer != 0)
            {
                return layer;
            }

            List<String> aPaths = a.getStructureKey();
            List<String> bPaths = b.getStructureKey();
            int length = Math.min(aPaths.size(), bPaths.size());

            for (int i = 0; i < length; i++)
            {
                int path = VanillaBoneHierarchy.compareNames(aPaths.get(i), bPaths.get(i));

                if (path != 0)
                {
                    return path;
                }
            }

            return Integer.compare(aPaths.size(), bPaths.size());
        }

        private static String getLayerRole(String layerId)
        {
            int separator = layerId.indexOf('#');

            return separator < 0 ? "" : layerId.substring(separator + 1);
        }

        public List<String> getBoneIds()
        {
            return this.boneIds;
        }

        public List<VanillaBoneHierarchy.Hierarchy> getHierarchies()
        {
            return this.hierarchies;
        }

        public BoneHierarchy getBoneHierarchy()
        {
            return this.boneHierarchy;
        }

        List<VanillaBoneHierarchy.Hierarchy> getRuntimeHierarchies()
        {
            return this.runtimeHierarchies;
        }

        VanillaBoneHierarchy.Bone getCanonicalBone(ModelPart part)
        {
            return this.canonicalBonesByPart.get(part);
        }

        VanillaBoneHierarchy.Bone getCanonicalBone(VanillaBoneHierarchy.Bone bone)
        {
            return this.canonicalBones.get(bone);
        }

        Object getVariantGroup(VanillaBoneHierarchy.Hierarchy hierarchy)
        {
            AlternativeRef group = this.variantGroups.get(hierarchy);

            return group == null ? null : group.container;
        }

        List<VanillaBoneHierarchy.Hierarchy> getFeatureContexts(VanillaBoneHierarchy.Hierarchy hierarchy)
        {
            return this.featureContexts.getOrDefault(hierarchy, Collections.emptyList());
        }

        Class<?> getModelType(VanillaBoneHierarchy.Hierarchy hierarchy)
        {
            AlternativeRef group = this.variantGroups.get(hierarchy);

            return group == null ? null : group.modelType;
        }

        /**
         * Resolves a stable ID or an unambiguous relative/legacy name across all model layers.
         */
        public Optional<VanillaBoneHierarchy.Bone> resolve(String id)
        {
            VanillaBoneHierarchy.Bone exact = this.bonesById.get(id);

            if (exact != null)
            {
                return Optional.of(exact);
            }

            return Optional.ofNullable(this.bonesById.get(this.aliases.get(id)));
        }

        List<VanillaBoneHierarchy.Bone> resolveAll(String id)
        {
            VanillaBoneHierarchy.Bone resolved = this.resolve(id).orElse(null);

            if (resolved == null)
            {
                return Collections.emptyList();
            }

            return this.runtimeBonesById.getOrDefault(resolved.getId(), Collections.emptyList());
        }

        /**
         * Produces mapping-independent lines suitable for comparing dev and remapped clients.
         */
        public List<String> getDiagnosticLines()
        {
            List<String> lines = new ArrayList<>();

            for (BoneHierarchy.Bone bone : this.boneHierarchy.getBones())
            {
                lines.add(bone.id() + "\t" + bone.name() + "\t" +
                    (bone.parentId() == null ? "" : bone.parentId()) + "\t" + bone.depth());
            }

            return Collections.unmodifiableList(lines);
        }

        private static void addAlias(
            VanillaBoneHierarchy.Hierarchy hierarchy,
            VanillaBoneHierarchy.Bone bone,
            String alias,
            String canonicalId,
            boolean primary,
            Map<String, String> aliases,
            Map<String, Boolean> primaryAliases,
            Set<String> ambiguousAliases
        )
        {
            if (alias.equals(canonicalId) || hierarchy.resolve(alias).orElse(null) != bone)
            {
                return;
            }

            addAlias(alias, canonicalId, primary, aliases, primaryAliases, ambiguousAliases);
        }

        private static void addAlias(
            String alias,
            String canonicalId,
            boolean primary,
            Map<String, String> aliases,
            Map<String, Boolean> primaryAliases,
            Set<String> ambiguousAliases
        )
        {
            if (ambiguousAliases.contains(alias))
            {
                return;
            }

            String previous = aliases.putIfAbsent(alias, canonicalId);

            if (previous == null)
            {
                primaryAliases.put(alias, primary);
            }
            else if (!previous.equals(canonicalId))
            {
                boolean previousPrimary = primaryAliases.getOrDefault(alias, false);

                if (primary && !previousPrimary)
                {
                    aliases.put(alias, canonicalId);
                    primaryAliases.put(alias, true);
                }
                else if (primary == previousPrimary)
                {
                    aliases.remove(alias);
                    primaryAliases.remove(alias);
                    ambiguousAliases.add(alias);
                }
            }
        }
    }

    private static final class Scanner
    {
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        private final IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Boolean> visitedHierarchies = new IdentityHashMap<>();
        private final IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Boolean> primaryHierarchies = new IdentityHashMap<>();
        private final IdentityHashMap<VanillaBoneHierarchy.Hierarchy, AlternativeRef> alternativeGroups = new IdentityHashMap<>();
        private final IdentityHashMap<VanillaBoneHierarchy.Hierarchy, List<VanillaBoneHierarchy.Hierarchy>> featureContexts = new IdentityHashMap<>();
        private final Map<String, List<VanillaBoneHierarchy.Hierarchy>> hierarchies = new LinkedHashMap<>();
        private List<VanillaBoneHierarchy.Hierarchy> activeFeatureContexts = Collections.emptyList();

        private void scanFeature(FeatureRenderer<?, ?> feature)
        {
            List<VanillaBoneHierarchy.Hierarchy> previous = this.activeFeatureContexts;

            this.activeFeatureContexts = this.findModelHierarchies(feature.getContextModel());

            try
            {
                this.scanFields(feature, false);
            }
            finally
            {
                this.activeFeatureContexts = previous;
            }
        }

        private void scanFields(Object owner, boolean primary)
        {
            this.scanFields(owner, primary, null);
        }

        private void scanFields(Object owner, boolean primary, AlternativeRef alternativeGroup)
        {
            Class<?> type = owner.getClass();

            while (type != null && type != Object.class)
            {
                for (Field field : type.getDeclaredFields())
                {
                    if (Modifier.isStatic(field.getModifiers()))
                    {
                        continue;
                    }

                    try
                    {
                        field.setAccessible(true);
                        this.scan(field.get(owner), primary, alternativeGroup);
                    }
                    catch (ReflectiveOperationException | RuntimeException ignored)
                    {}
                }

                type = type.getSuperclass();
            }
        }

        private void scan(Object value, boolean primary, AlternativeRef alternativeGroup)
        {
            if (value == null)
            {
                return;
            }

            if (value instanceof ModelPart part)
            {
                VanillaBoneHierarchy.getHierarchy(part).ifPresent((hierarchy) ->
                {
                    if (this.visitedHierarchies.put(hierarchy, Boolean.TRUE) == null)
                    {
                        this.hierarchies.computeIfAbsent(hierarchy.getLayerId(), (key) -> new ArrayList<>()).add(hierarchy);
                    }

                    if (primary)
                    {
                        this.primaryHierarchies.put(hierarchy, Boolean.TRUE);
                    }

                    if (alternativeGroup != null)
                    {
                        this.alternativeGroups.putIfAbsent(hierarchy, alternativeGroup);
                    }

                    if (!primary && !this.activeFeatureContexts.isEmpty() && !this.activeFeatureContexts.contains(hierarchy))
                    {
                        this.featureContexts.putIfAbsent(hierarchy, this.activeFeatureContexts);
                    }
                });

                return;
            }

            if (value instanceof Model model)
            {
                if (this.visited.put(model, Boolean.TRUE) != null)
                {
                    return;
                }

                AlternativeRef modelGroup = alternativeGroup == null
                    ? null
                    : alternativeGroup.withModel(model.getClass());

                if (model instanceof SinglePartEntityModel<?> singlePartModel)
                {
                    this.scan(singlePartModel.getPart(), primary, modelGroup);
                }
                else if (model instanceof CompositeEntityModel<?> compositeModel)
                {
                    this.scan(compositeModel.getParts(), primary, modelGroup);
                }

                /* Dragon and a few other vanilla models expose neither root API. */
                this.scanFields(model, primary, modelGroup);

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
                    this.scan(Array.get(value, i), primary, alternativeGroup);
                }

                return;
            }

            if (value instanceof Map<?, ?> map)
            {
                if (this.visited.put(value, Boolean.TRUE) != null)
                {
                    return;
                }

                Object variantContainer = alternativeGroup == null ? new Object() : null;

                for (Object entryValue : map.values())
                {
                    /* Direct model values represent independent render layers and retain their
                     * hierarchy. Tuple-wrapped models are renderer variants whose equivalent parts
                     * may share a canonical hierarchy. */
                    AlternativeRef group = alternativeGroup == null
                        ? new AlternativeRef(variantContainer, new Object(), entryValue instanceof Pair<?, ?>)
                        : alternativeGroup;

                    this.scan(entryValue, primary, group);
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
                    this.scan(element, primary, alternativeGroup);
                }

                return;
            }

            if (value instanceof Optional<?> optional)
            {
                optional.ifPresent((element) -> this.scan(element, primary, alternativeGroup));

                return;
            }

            /* Tuple wrappers may keep render metadata next to a model. */
            if (value instanceof Pair<?, ?> pair)
            {
                this.scan(pair.getFirst(), primary, alternativeGroup);
                this.scan(pair.getSecond(), primary, alternativeGroup);
            }
        }

        private Discovery createDiscovery(long hierarchyRevision)
        {
            List<VanillaBoneHierarchy.Hierarchy> runtimeHierarchies = new ArrayList<>();
            List<VanillaBoneHierarchy.Hierarchy> legacyHierarchies = new ArrayList<>();

            for (List<VanillaBoneHierarchy.Hierarchy> layerHierarchies : this.hierarchies.values())
            {
                runtimeHierarchies.addAll(layerHierarchies);
            }

            for (VanillaBoneHierarchy.Hierarchy hierarchy : runtimeHierarchies)
            {
                if (this.primaryHierarchies.containsKey(hierarchy))
                {
                    legacyHierarchies.add(hierarchy);
                }
            }

            for (VanillaBoneHierarchy.Hierarchy hierarchy : runtimeHierarchies)
            {
                if (!this.primaryHierarchies.containsKey(hierarchy))
                {
                    legacyHierarchies.add(hierarchy);
                }
            }

            return new Discovery(
                runtimeHierarchies,
                legacyHierarchies,
                this.alternativeGroups,
                this.primaryHierarchies,
                this.featureContexts,
                hierarchyRevision
            );
        }

        private List<VanillaBoneHierarchy.Hierarchy> findModelHierarchies(Model model)
        {
            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Boolean> found = new IdentityHashMap<>();
            IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();

            this.collectModelHierarchies(model, found, visited);

            return List.copyOf(found.keySet());
        }

        private void collectModelHierarchies(
            Object value,
            IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Boolean> found,
            IdentityHashMap<Object, Boolean> visited
        )
        {
            if (value == null)
            {
                return;
            }

            if (value instanceof ModelPart part)
            {
                VanillaBoneHierarchy.getHierarchy(part).ifPresent((hierarchy) -> found.put(hierarchy, Boolean.TRUE));

                return;
            }

            if (visited.put(value, Boolean.TRUE) != null)
            {
                return;
            }

            if (value instanceof SinglePartEntityModel<?> singlePartModel)
            {
                this.collectModelHierarchies(singlePartModel.getPart(), found, visited);
            }
            else if (value instanceof CompositeEntityModel<?> compositeModel)
            {
                this.collectModelHierarchies(compositeModel.getParts(), found, visited);
            }

            if (value instanceof Model)
            {
                Class<?> type = value.getClass();

                while (type != null && type != Object.class)
                {
                    for (Field field : type.getDeclaredFields())
                    {
                        if (Modifier.isStatic(field.getModifiers()))
                        {
                            continue;
                        }

                        try
                        {
                            field.setAccessible(true);
                            this.collectModelHierarchies(field.get(value), found, visited);
                        }
                        catch (ReflectiveOperationException | RuntimeException ignored)
                        {}
                    }

                    type = type.getSuperclass();
                }

                return;
            }

            Class<?> type = value.getClass();

            if (type.isArray())
            {
                int length = Array.getLength(value);

                for (int i = 0; i < length; i++)
                {
                    this.collectModelHierarchies(Array.get(value, i), found, visited);
                }
            }
            else if (value instanceof Iterable<?> iterable)
            {
                for (Object element : iterable)
                {
                    this.collectModelHierarchies(element, found, visited);
                }
            }
            else if (value instanceof Optional<?> optional)
            {
                optional.ifPresent((element) -> this.collectModelHierarchies(element, found, visited));
            }
        }
    }

    /** Tracks a renderer variant entry and whether its hierarchy can share a canonical bone ID. */
    private static final class AlternativeRef
    {
        private final Object container;
        private final Object member;
        private final Class<?> modelType;
        private final boolean fold;

        private AlternativeRef(Object container, Object member, boolean fold)
        {
            this(container, member, null, fold);
        }

        private AlternativeRef(Object container, Object member, Class<?> modelType, boolean fold)
        {
            this.container = container;
            this.member = member;
            this.modelType = modelType;
            this.fold = fold;
        }

        private AlternativeRef withModel(Class<?> modelType)
        {
            return new AlternativeRef(this.container, this.member, modelType, this.fold);
        }
    }

    private record AlternativeKey(List<String> paths, Class<?> modelType, String layerRole)
    {}

    private record AlternativeBoneKey(String path, String layerRole)
    {}

    private record StructureKey(String layerId, List<String> paths)
    {
        private static StructureKey of(VanillaBoneHierarchy.Hierarchy hierarchy)
        {
            return new StructureKey(hierarchy.getLayerId(), hierarchy.getStructureKey());
        }
    }

    /**
     * A structure can only be folded when every candidate came from a different map entry. This
     * conservatively preserves multiple same-shaped models stored together in one entry.
     */
    private static final class AlternativeSet
    {
        private final List<VanillaBoneHierarchy.Hierarchy> hierarchies = new ArrayList<>();
        private final IdentityHashMap<Object, Integer> memberCounts = new IdentityHashMap<>();

        private void add(VanillaBoneHierarchy.Hierarchy hierarchy, Object member)
        {
            this.hierarchies.add(hierarchy);
            this.memberCounts.merge(member, 1, Integer::sum);
        }

        private boolean isUnambiguous()
        {
            return this.hierarchies.size() > 1 && this.hierarchies.size() == this.memberCounts.size();
        }
    }

    /** Shared paths are folded only when each path came from a different variant entry. */
    private static final class AlternativeBoneSet
    {
        private final List<AlternativeBone> bones = new ArrayList<>();
        private final IdentityHashMap<Object, Integer> memberCounts = new IdentityHashMap<>();

        private void add(VanillaBoneHierarchy.Hierarchy hierarchy, VanillaBoneHierarchy.Bone bone, Object member)
        {
            this.bones.add(new AlternativeBone(hierarchy, bone));
            this.memberCounts.merge(member, 1, Integer::sum);
        }

        private boolean isUnambiguous()
        {
            return this.bones.size() > 1 && this.bones.size() == this.memberCounts.size();
        }
    }

    private record AlternativeBone(VanillaBoneHierarchy.Hierarchy hierarchy, VanillaBoneHierarchy.Bone bone)
    {}

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
