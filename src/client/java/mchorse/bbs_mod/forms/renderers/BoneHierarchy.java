package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A renderer-independent, string-only view of a form's editable bone hierarchy.
 */
public final class BoneHierarchy
{
    public static final BoneHierarchy EMPTY = new BoneHierarchy(Collections.emptyList());

    private final List<Bone> bones;
    private final List<String> boneIds;
    private final Map<String, Bone> bonesById;
    private final Map<String, String> aliases;

    public BoneHierarchy(List<Bone> bones)
    {
        this(bones, Collections.emptyMap());
    }

    BoneHierarchy(List<Bone> bones, Map<String, String> aliases)
    {
        List<Bone> uniqueBones = new ArrayList<>(bones.size());
        List<String> boneIds = new ArrayList<>(bones.size());
        Map<String, Bone> bonesById = new LinkedHashMap<>();

        for (Bone bone : bones)
        {
            if (bone == null || bone.id() == null || bonesById.putIfAbsent(bone.id(), bone) != null)
            {
                continue;
            }

            uniqueBones.add(bone);
            boneIds.add(bone.id());
        }

        this.bones = Collections.unmodifiableList(uniqueBones);
        this.boneIds = Collections.unmodifiableList(boneIds);
        this.bonesById = Collections.unmodifiableMap(bonesById);
        this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }

    public List<Bone> getBones()
    {
        return this.bones;
    }

    public List<String> getBoneIds()
    {
        return this.boneIds;
    }

    public Bone getBone(String id)
    {
        String resolved = this.resolveId(id);

        return resolved == null ? null : this.bonesById.get(resolved);
    }

    /** Resolves a stable ID or an unambiguous legacy alias without guessing. */
    public String resolveId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        return this.bonesById.containsKey(id) ? id : this.aliases.get(id);
    }

    /**
     * Builds stable editor labels while retaining mapping-independent IDs as list values. Main
     * model fields use lower camel case; feature model fields use a snake-case layer namespace and
     * retain lower camel case for the Java-style field suffix (for example inner_armor_rightArm).
     */
    public Map<String, String> getLabels(boolean indent)
    {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Integer> suffixes = new HashMap<>();
        Set<String> usedLabels = new HashSet<>();

        for (Bone bone : this.bones)
        {
            String label = getDisplayName(bone);

            if (usedLabels.contains(label))
            {
                label = this.getQualifiedName(bone);

                if (usedLabels.contains(label))
                {
                    label = getLayerResource(bone.layerId()) + "_" + label;
                }
            }

            label = makeUnique(label, suffixes, usedLabels);

            if (indent)
            {
                label = "  ".repeat(bone.depth()) + label;
            }

            labels.put(bone.id(), label);
        }

        return Collections.unmodifiableMap(labels);
    }

    private static String makeUnique(String label, Map<String, Integer> suffixes, Set<String> usedLabels)
    {
        if (usedLabels.add(label))
        {
            return label;
        }

        int suffix = suffixes.getOrDefault(label, 2);
        String candidate;

        do
        {
            candidate = label + "_" + suffix;
            suffix++;
        }
        while (!usedLabels.add(candidate));

        suffixes.put(label, suffix);

        return candidate;
    }

    /** Replaces legacy bone names with their stable IDs, preserving an existing stable-ID edit. */
    public void migratePose(Pose pose)
    {
        if (pose == null || this.aliases.isEmpty())
        {
            return;
        }

        for (String alias : new ArrayList<>(pose.transforms.keySet()))
        {
            String id = this.resolveId(alias);

            if (id == null || id.equals(alias))
            {
                continue;
            }

            PoseTransform transform = pose.transforms.remove(alias);

            pose.transforms.putIfAbsent(id, transform);
        }
    }

    public boolean needsMigration(Pose pose)
    {
        if (pose == null || this.aliases.isEmpty())
        {
            return false;
        }

        for (String alias : pose.transforms.keySet())
        {
            String id = this.resolveId(alias);

            if (id != null && !id.equals(alias))
            {
                return true;
            }
        }

        return false;
    }

    public List<Bone> getAdjacent(String id)
    {
        Bone selected = this.getBone(id);

        if (selected == null)
        {
            return Collections.emptyList();
        }

        List<Bone> adjacent = new ArrayList<>();

        for (Bone bone : this.bones)
        {
            if (bone.layerId().equals(selected.layerId()) && sameParent(bone.parentId(), selected.parentId()))
            {
                adjacent.add(bone);
            }
        }

        return adjacent;
    }

    /** Returns every descendant of the selected bone in hierarchy order, excluding itself. */
    public List<Bone> getDescendants(String id)
    {
        Bone selected = this.getBone(id);

        if (selected == null)
        {
            return Collections.emptyList();
        }

        id = selected.id();

        List<Bone> descendants = new ArrayList<>();

        for (Bone candidate : this.bones)
        {
            Bone parent = candidate.parentId() == null ? null : this.getBone(candidate.parentId());

            while (parent != null)
            {
                if (id.equals(parent.id()))
                {
                    descendants.add(candidate);
                    break;
                }

                parent = parent.parentId() == null ? null : this.getBone(parent.parentId());
            }
        }

        return descendants;
    }

    /** Returns the ancestry path from the root down to the selected bone. */
    public List<Bone> getAncestors(String id)
    {
        List<Bone> ancestors = new ArrayList<>();
        Bone bone = this.getBone(id);

        while (bone != null)
        {
            ancestors.add(bone);
            bone = bone.parentId() == null || bone.parentId().isEmpty() ? null : this.getBone(bone.parentId());
        }

        Collections.reverse(ancestors);

        return ancestors;
    }

    private static boolean sameParent(String a, String b)
    {
        return a == null ? b == null : a.equals(b);
    }

    private static String getDisplayName(Bone bone)
    {
        if (bone.layerId().isEmpty())
        {
            return bone.name();
        }

        String namespace = getLayerNamespace(bone);
        String name = VanillaBoneHierarchy.toCamelCase(bone.name());

        return combineNamespace(namespace, name);
    }

    private static String getLayerNamespace(Bone bone)
    {
        int separator = bone.layerId().indexOf('#');
        String namespace = separator < 0 ? "" : bone.layerId().substring(separator + 1);

        if (!namespace.equals("main"))
        {
            return namespace;
        }

        return bone.primary() ? "" : getLayerResource(bone.layerId());
    }

    private static String combineNamespace(String namespace, String name)
    {
        if (namespace.isEmpty())
        {
            return name;
        }

        return namespace.equals(name) || namespace.endsWith("_" + name) ? namespace : namespace + "_" + name;
    }

    private static String getLayerResource(String layerId)
    {
        int separator = layerId.indexOf('#');
        String resource = separator < 0 ? layerId : layerId.substring(0, separator);

        if (resource.startsWith("minecraft:"))
        {
            resource = resource.substring("minecraft:".length());
        }

        return resource.replace(':', '_').replace('/', '_');
    }

    private String getQualifiedName(Bone bone)
    {
        StringBuilder name = new StringBuilder();
        String namespace = getLayerNamespace(bone);

        if (!namespace.isEmpty())
        {
            name.append(namespace);
        }

        boolean first = true;

        for (Bone ancestor : this.getAncestors(bone.id()))
        {
            String segment = ancestor.layerId().isEmpty()
                ? ancestor.name()
                : VanillaBoneHierarchy.toCamelCase(ancestor.name());

            if (first && !name.isEmpty() && (name.toString().equals(segment) || name.toString().endsWith("_" + segment)))
            {
                first = false;
                continue;
            }

            if (!name.isEmpty())
            {
                name.append('_');
            }

            name.append(segment);
            first = false;
        }

        return name.toString();
    }

    public record Bone(String id, String name, String parentId, int depth, String layerId, boolean primary)
    {
        public Bone(String id, String name, String parentId, int depth, String layerId)
        {
            this(id, name, parentId, depth, layerId, true);
        }

        public Bone
        {
            name = name == null ? id : name;
            depth = Math.max(0, depth);
            layerId = layerId == null ? "" : layerId;
        }
    }
}
