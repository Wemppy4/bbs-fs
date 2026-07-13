package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModelLayer;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Associates baked vanilla model parts with mapping-independent names from their children tree.
 */
public final class VanillaBoneHierarchy
{
    private static final ReferenceQueue<ModelPart> STALE_PARTS = new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, RegisteredNode> NODES = new HashMap<>();
    private static final Map<String, Structure> STRUCTURES = new HashMap<>();

    private VanillaBoneHierarchy()
    {}

    public static synchronized Hierarchy register(EntityModelLayer layer, ModelPart root)
    {
        return register(toLayerId(layer), root);
    }

    /**
     * Registers a tree that was not baked by EntityModelLoader. The caller must supply a stable,
     * mapping-independent layer ID; Java class and field names are not valid layer IDs.
     */
    public static synchronized Hierarchy register(String layerId, ModelPart root)
    {
        if (layerId == null || layerId.isEmpty())
        {
            throw new IllegalArgumentException("A stable model layer ID is required");
        }

        if (root == null)
        {
            throw new IllegalArgumentException("A model root is required");
        }

        removeStaleParts();

        List<PartDescriptor> descriptors = new ArrayList<>();
        IdentityHashMap<ModelPart, PartDescriptor> parts = new IdentityHashMap<>();
        IdentityHashMap<ModelPart, Boolean> visited = new IdentityHashMap<>();

        collectChildren(layerId, root, "", null, 0, descriptors, parts, visited);

        List<Descriptor> structureDescriptors = descriptors.stream()
            .map(PartDescriptor::descriptor)
            .toList();
        Structure structure = STRUCTURES.computeIfAbsent(layerId, (key) -> new Structure(key, structureDescriptors));

        if (!structure.descriptors.equals(structureDescriptors))
        {
            /* A layer can be rebuilt after packs change without waiting for a full client reload. */
            structure = new Structure(layerId, structureDescriptors);
            STRUCTURES.put(layerId, structure);
        }

        List<Bone> bones = new ArrayList<>();
        Map<String, Bone> byId = new LinkedHashMap<>();
        Map<String, Bone> byPath = new HashMap<>();
        Map<String, Bone> legacyAliases = new HashMap<>();
        Set<String> ambiguousAliases = new HashSet<>();

        for (int i = 0; i < descriptors.size(); i++)
        {
            PartDescriptor partDescriptor = descriptors.get(i);
            Descriptor descriptor = structure.descriptors.get(i);
            Bone bone = new Bone(descriptor, partDescriptor.part());

            bones.add(bone);
            byId.put(descriptor.id(), bone);
            byPath.put(descriptor.path(), bone);
            addLegacyAlias(descriptor.name(), bone, legacyAliases, ambiguousAliases);

            String camelCaseName = toCamelCase(descriptor.name());

            if (!camelCaseName.equals(descriptor.name()))
            {
                addLegacyAlias(camelCaseName, bone, legacyAliases, ambiguousAliases);
            }
        }

        Hierarchy hierarchy = new Hierarchy(
            structure,
            Collections.unmodifiableList(bones),
            Collections.unmodifiableMap(byId),
            Collections.unmodifiableMap(byPath),
            Collections.unmodifiableMap(legacyAliases)
        );

        put(root, new RegisteredNode(hierarchy, null));

        for (Map.Entry<ModelPart, PartDescriptor> entry : parts.entrySet())
        {
            put(entry.getKey(), new RegisteredNode(hierarchy, byId.get(entry.getValue().descriptor().id())));
        }

        return hierarchy;
    }

    public static synchronized Optional<Hierarchy> getHierarchy(ModelPart part)
    {
        RegisteredNode node = get(part);

        return node == null ? Optional.empty() : Optional.of(node.hierarchy());
    }

    public static synchronized Optional<Bone> getBone(ModelPart part)
    {
        RegisteredNode node = get(part);

        return node == null ? Optional.empty() : Optional.ofNullable(node.bone());
    }

    public static synchronized void clear()
    {
        NODES.clear();
        STRUCTURES.clear();

        while (STALE_PARTS.poll() != null)
        {}
    }

    public static String toLayerId(EntityModelLayer layer)
    {
        return layer.getId() + "#" + escapeSegment(layer.getName());
    }

    private static void collectChildren(
        String layerId,
        ModelPart parent,
        String parentPath,
        String parentId,
        int depth,
        List<PartDescriptor> descriptors,
        IdentityHashMap<ModelPart, PartDescriptor> parts,
        IdentityHashMap<ModelPart, Boolean> visited
    )
    {
        if (visited.put(parent, Boolean.TRUE) != null)
        {
            return;
        }

        List<Map.Entry<String, ModelPart>> children = new ArrayList<>(((ModelPartAccessor) (Object) parent).bbs$getChildren().entrySet());

        children.sort(Comparator.comparing(Map.Entry::getKey));

        for (Map.Entry<String, ModelPart> child : children)
        {
            if (visited.containsKey(child.getValue()))
            {
                continue;
            }

            String name = child.getKey();
            String segment = escapeSegment(name);
            String path = parentPath.isEmpty() ? segment : parentPath + "/" + segment;
            String id = layerId + "/" + path;
            Descriptor descriptor = new Descriptor(id, name, parentId, depth, path);
            PartDescriptor partDescriptor = new PartDescriptor(child.getValue(), descriptor);

            descriptors.add(partDescriptor);
            parts.put(child.getValue(), partDescriptor);
            collectChildren(layerId, child.getValue(), path, id, depth + 1, descriptors, parts, visited);
        }
    }

    private static String escapeSegment(String value)
    {
        return value.replace("%", "%25").replace("#", "%23").replace("/", "%2F");
    }

    private static String toCamelCase(String value)
    {
        StringBuilder builder = new StringBuilder(value.length());
        boolean uppercase = false;

        for (int i = 0; i < value.length(); i++)
        {
            char character = value.charAt(i);

            if (character == '_')
            {
                uppercase = true;
            }
            else if (uppercase)
            {
                builder.append(Character.toUpperCase(character));
                uppercase = false;
            }
            else
            {
                builder.append(character);
            }
        }

        return builder.toString();
    }

    private static void addLegacyAlias(String alias, Bone bone, Map<String, Bone> aliases, Set<String> ambiguousAliases)
    {
        if (ambiguousAliases.contains(alias))
        {
            return;
        }

        Bone previous = aliases.putIfAbsent(alias, bone);

        if (previous != null && previous != bone)
        {
            aliases.remove(alias);
            ambiguousAliases.add(alias);
        }
    }

    private static RegisteredNode get(ModelPart part)
    {
        removeStaleParts();

        return NODES.get(new IdentityWeakReference(part));
    }

    private static void put(ModelPart part, RegisteredNode node)
    {
        NODES.put(new IdentityWeakReference(part, STALE_PARTS), node);
    }

    private static void removeStaleParts()
    {
        IdentityWeakReference reference;

        while ((reference = (IdentityWeakReference) STALE_PARTS.poll()) != null)
        {
            NODES.remove(reference);
        }
    }

    public static final class Hierarchy
    {
        private final Structure structure;
        private final List<Bone> bones;
        private final Map<String, Bone> byId;
        private final Map<String, Bone> byPath;
        private final Map<String, Bone> legacyAliases;

        private Hierarchy(Structure structure, List<Bone> bones, Map<String, Bone> byId, Map<String, Bone> byPath, Map<String, Bone> legacyAliases)
        {
            this.structure = structure;
            this.bones = bones;
            this.byId = byId;
            this.byPath = byPath;
            this.legacyAliases = legacyAliases;
        }

        public String getLayerId()
        {
            return this.structure.layerId;
        }

        public List<Bone> getBones()
        {
            return this.bones;
        }

        /**
         * Resolves stable IDs first, then relative paths and unique legacy single-name aliases.
         */
        public Optional<Bone> resolve(String id)
        {
            if (id == null || id.isEmpty())
            {
                return Optional.empty();
            }

            Bone bone = this.byId.get(id);

            if (bone == null)
            {
                bone = this.byPath.get(id);
            }

            if (bone == null && id.indexOf('/') < 0 && id.indexOf('#') < 0)
            {
                bone = this.legacyAliases.get(id);
            }

            return Optional.ofNullable(bone);
        }
    }

    public static final class Bone
    {
        private final Descriptor descriptor;
        private final WeakReference<ModelPart> part;

        private Bone(Descriptor descriptor, ModelPart part)
        {
            this.descriptor = descriptor;
            this.part = new WeakReference<>(part);
        }

        public String getId()
        {
            return this.descriptor.id();
        }

        public String getName()
        {
            return this.descriptor.name();
        }

        public String getParentId()
        {
            return this.descriptor.parentId();
        }

        public int getDepth()
        {
            return this.descriptor.depth();
        }

        public ModelPart getPart()
        {
            return this.part.get();
        }
    }

    private static final class Structure
    {
        private final String layerId;
        private final List<Descriptor> descriptors;

        private Structure(String layerId, List<Descriptor> descriptors)
        {
            this.descriptors = List.copyOf(descriptors);
            this.layerId = layerId;
        }
    }

    private record Descriptor(String id, String name, String parentId, int depth, String path)
    {}

    private record PartDescriptor(ModelPart part, Descriptor descriptor)
    {}

    private record RegisteredNode(Hierarchy hierarchy, Bone bone)
    {}

    private static final class IdentityWeakReference extends WeakReference<ModelPart>
    {
        private final int hash;

        private IdentityWeakReference(ModelPart part)
        {
            super(part);
            this.hash = System.identityHashCode(part);
        }

        private IdentityWeakReference(ModelPart part, ReferenceQueue<ModelPart> queue)
        {
            super(part, queue);
            this.hash = System.identityHashCode(part);
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

            ModelPart part = this.get();

            return part != null && part == other.get();
        }
    }
}
