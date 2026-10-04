package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forms.forms.MobForm;
import java.util.*;

/** The actual native ModelRenderer tree, with the same release-safe names as the CEM runtime. */
public final class NativeMobHierarchy implements IBoneHierarchy
{
    private final List<String> roots;
    private final Map<String, List<String>> children = new LinkedHashMap<>();
    private final Map<String, String> parents = new LinkedHashMap<>();

    public static NativeMobHierarchy create(MobForm form)
    {
        String id = form.mobID.get();
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        if (form.isPlayer()) id = form.slim.get() ? "player_slim" : "player";
        VanillaRigs.Rig rig = VanillaRigs.create(id);
        return rig == null ? null : new NativeMobHierarchy(rig);
    }

    private NativeMobHierarchy(VanillaRigs.Rig rig)
    {
        this.roots = Collections.unmodifiableList(new ArrayList<>(rig.roots));
        for (String key : rig.parts.keySet())
        {
            List<String> direct = new ArrayList<>(rig.children.getOrDefault(key, Collections.emptyList()));
            this.children.put(key, Collections.unmodifiableList(direct));
            for (String child : direct) this.parents.put(child, key);
        }
    }

    @Override public Collection<String> getRootGroupKeys() { return this.roots; }
    @Override public Collection<String> getDirectChildrenKeys(String key) { return this.children.getOrDefault(key, Collections.emptyList()); }
    @Override public String getParentGroupKey(String key) { return this.parents.get(key); }
}
