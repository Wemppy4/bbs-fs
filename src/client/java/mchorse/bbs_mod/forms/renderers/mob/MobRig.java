package mchorse.bbs_mod.forms.renderers.mob;

import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The skeleton of a vanilla entity model, as the mob form sees it.
 *
 * <p>Bones are addressed by the names vanilla itself gave them ({@code head}, {@code left_arm}),
 * read off the {@link ModelPart} tree rather than off the model's Java fields. Field names are
 * remapped in a production jar, so a pose authored in one environment used to be unusable in the
 * other, the list of bones was unreadable in a release, and {@code Pose}'s left/right mirror table
 * (which matches {@code left_something}, not {@code leftSomething}) never fired for a mob at all.
 *
 * <p>The rig also carries an ORDER: {@link #ordered()} is a depth-first walk from the roots, and
 * that position is what the picking pass writes into the lightmap channel for each part. It is
 * rebuilt every session and never persisted — only names are saved — so it only has to agree with
 * itself, which it does by construction (the id-emitting mixin and {@code updateStencilMap} both
 * read this one list).
 */
public class MobRig implements IBoneHierarchy
{
    private final List<ModelPart> ordered = new ArrayList<>();
    private final Map<ModelPart, Integer> indices = new IdentityHashMap<>();
    private final Map<ModelPart, String> names = new IdentityHashMap<>();
    private final Map<String, ModelPart> byName = new LinkedHashMap<>();
    private final Map<String, String> parents = new LinkedHashMap<>();
    private final Map<String, List<String>> children = new LinkedHashMap<>();
    private final List<String> roots = new ArrayList<>();

    /**
     * Canonical name to the Java field name the same part used to be addressed by, in THIS
     * environment. A pose saved before the rig existed is keyed by that field name — dev poses by
     * {@code leftArm}, release poses by {@code field_3398} — and each environment repairs its own
     * data by resolving through this table. The editor only ever writes canonical names, so a pose
     * drifts to them the first time it is touched; nothing is rewritten behind the user's back.
     */
    private final Map<String, String> legacyNames = new LinkedHashMap<>();

    public MobRig(EntityModel model)
    {
        Map<String, ModelPart> scraped = scrape(model);
        Map<ModelPart, String> fieldNames = new IdentityHashMap<>();

        for (Map.Entry<String, ModelPart> entry : scraped.entrySet())
        {
            fieldNames.putIfAbsent(entry.getValue(), entry.getKey());
        }

        for (ModelPart root : roots(scraped.values()))
        {
            this.visit(root, null, fieldNames);
        }

        /* A part that never showed up in anyone's children map - a model that built its
         * ModelParts outside the named-children path. Keep it listed under its field name
         * rather than losing the bone. */
        for (Map.Entry<String, ModelPart> entry : scraped.entrySet())
        {
            if (!this.names.containsKey(entry.getValue()))
            {
                this.visit(entry.getValue(), null, fieldNames);
            }
        }

        for (Map.Entry<String, ModelPart> entry : scraped.entrySet())
        {
            String canonical = this.names.get(entry.getValue());

            if (canonical != null && !canonical.equals(entry.getKey()))
            {
                this.legacyNames.putIfAbsent(canonical, entry.getKey());
            }
        }
    }

    /**
     * Every {@link ModelPart} field up the model's class hierarchy. The rig no longer NAMES bones
     * by these fields, but it still needs them as entry points into the tree (a model keeps its
     * limbs, not the root it was built from) and as the legacy alias table.
     */
    private static Map<String, ModelPart> scrape(EntityModel model)
    {
        Map<String, ModelPart> fields = new LinkedHashMap<>();
        Class<?> aClass = model.getClass();

        while (aClass != null && aClass != Object.class)
        {
            for (Field field : aClass.getDeclaredFields())
            {
                if (!field.getType().equals(ModelPart.class))
                {
                    continue;
                }

                try
                {
                    field.setAccessible(true);

                    ModelPart part = (ModelPart) field.get(model);

                    if (part != null)
                    {
                        fields.putIfAbsent(field.getName(), part);
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            aClass = aClass.getSuperclass();
        }

        return fields;
    }

    /** Climbs from every part the model kept a field for to the top of its tree. */
    private static List<ModelPart> roots(Collection<ModelPart> parts)
    {
        List<ModelPart> roots = new ArrayList<>();
        Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ModelPart part : parts)
        {
            ModelPart root = part;
            ModelPart parent;

            while ((parent = IBBSModelPart.of(root).bbs$getParent()) != null)
            {
                root = parent;
            }

            if (seen.add(root))
            {
                roots.add(root);
            }
        }

        return roots;
    }

    private void visit(ModelPart part, String parentName, Map<ModelPart, String> fieldNames)
    {
        if (this.names.containsKey(part))
        {
            return;
        }

        String name = this.register(part, parentName, fieldNames);

        for (ModelPart child : IBBSModelPart.of(part).bbs$children().values())
        {
            this.visit(child, name, fieldNames);
        }
    }

    private String register(ModelPart part, String parentName, Map<ModelPart, String> fieldNames)
    {
        String base = IBBSModelPart.of(part).bbs$getName();

        if (base == null)
        {
            base = fieldNames.get(part);
        }

        if (base == null)
        {
            base = "root";
        }

        String name = this.unique(base, parentName);

        this.names.put(part, name);
        this.indices.put(part, this.ordered.size());
        this.ordered.add(part);
        this.byName.put(name, part);
        this.children.put(name, new ArrayList<>());

        if (parentName == null)
        {
            this.roots.add(name);
        }
        else
        {
            this.parents.put(name, parentName);
            this.children.get(parentName).add(name);
        }

        return name;
    }

    private String unique(String base, String parentName)
    {
        if (!this.byName.containsKey(base))
        {
            return base;
        }

        if (parentName != null)
        {
            String qualified = parentName + "/" + base;

            if (!this.byName.containsKey(qualified))
            {
                return qualified;
            }
        }

        int index = 2;

        while (this.byName.containsKey(base + "." + index))
        {
            index += 1;
        }

        return base + "." + index;
    }

    /**
     * The pose's entry for a bone, falling back to the name the same bone used to be saved under.
     * The single read every poser goes through, so back-compatibility lives in one place.
     */
    public PoseTransform resolve(Pose pose, String bone)
    {
        PoseTransform transform = pose.transforms.get(bone);

        if (transform != null)
        {
            return transform;
        }

        String legacy = this.legacyNames.get(bone);

        return legacy == null ? null : pose.transforms.get(legacy);
    }

    public List<ModelPart> ordered()
    {
        return this.ordered;
    }

    public boolean isEmpty()
    {
        return this.ordered.isEmpty();
    }

    /** The bone's position in {@link #ordered()}, or -1 when the part is not part of this rig. */
    public int index(ModelPart part)
    {
        Integer index = this.indices.get(part);

        return index == null ? -1 : index;
    }

    public String name(ModelPart part)
    {
        return this.names.get(part);
    }

    public ModelPart part(String bone)
    {
        return this.byName.get(bone);
    }

    @Override
    public Collection<String> getRootGroupKeys()
    {
        return this.roots;
    }

    @Override
    public Collection<String> getDirectChildrenKeys(String key)
    {
        return this.children.getOrDefault(key, Collections.emptyList());
    }

    @Override
    public String getParentGroupKey(String key)
    {
        return this.parents.get(key);
    }

    /** Already the build order: a depth-first walk from the roots. */
    @Override
    public List<String> getGroupKeysInHierarchyOrder()
    {
        return new ArrayList<>(this.byName.keySet());
    }
}
