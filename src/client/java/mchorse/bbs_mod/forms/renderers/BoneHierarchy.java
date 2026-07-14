package mchorse.bbs_mod.forms.renderers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A renderer-independent, string-only view of a form's editable bone hierarchy.
 */
public final class BoneHierarchy
{
    public static final BoneHierarchy EMPTY = new BoneHierarchy(Collections.emptyList());

    private final List<Bone> bones;
    private final List<String> boneIds;
    private final Map<String, Bone> bonesById;

    public BoneHierarchy(List<Bone> bones)
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
        return this.bonesById.get(id);
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
        if (this.getBone(id) == null)
        {
            return Collections.emptyList();
        }

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

    public record Bone(String id, String name, String parentId, int depth, String layerId)
    {
        public Bone
        {
            name = name == null ? id : name;
            depth = Math.max(0, depth);
            layerId = layerId == null ? "" : layerId;
        }
    }
}
