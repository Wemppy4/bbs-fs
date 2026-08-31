package mchorse.bbs_mod.cubic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A skeleton's shape: which bones there are and which hangs off which.
 *
 * <p>Split out of {@link IModel} because it is all the bone-tree widgets ever wanted — the tree
 * list, the pose editor's bone column, the bone picker menus. Everything else {@code IModel}
 * carries (posing, shape keys, animation application, the two flavours of bone object) is about
 * DRIVING a model, which a vanilla entity model does through its own code and cannot supply.
 * Asking only for the shape is what lets a mob form's rig sit in exactly the same widgets.</p>
 *
 * <p>Only three of these are real questions; the rest are the walks every caller was writing by
 * hand on top of them. A skeleton with a faster answer of its own is free to override them.</p>
 */
public interface IBoneHierarchy
{
    public Collection<String> getRootGroupKeys();

    public Collection<String> getDirectChildrenKeys(String key);

    public String getParentGroupKey(String key);

    /** Every descendant of a bone, parents before children, not counting the bone itself. */
    public default Collection<String> getAllChildrenKeys(String key)
    {
        List<String> out = new ArrayList<>();

        for (String child : this.getDirectChildrenKeys(key))
        {
            this.collectGroupAndDescendants(child, out);
        }

        return out;
    }

    /** The bone's siblings, itself included — what a "pick a neighbour" menu offers. */
    public default Collection<String> getAdjacentGroups(String groupName)
    {
        String parent = this.getParentGroupKey(groupName);

        return parent == null ? this.getRootGroupKeys() : this.getDirectChildrenKeys(parent);
    }

    /** The bone and its ancestors, closest first — what a "pick a parent" menu offers. */
    public default Collection<String> getHierarchyGroups(String groupName)
    {
        List<String> groups = new ArrayList<>();
        String bone = groupName;

        while (bone != null)
        {
            groups.add(bone);

            bone = this.getParentGroupKey(bone);
        }

        return groups;
    }

    public default List<String> getGroupKeysInHierarchyOrder()
    {
        List<String> out = new ArrayList<>();

        for (String root : this.getRootGroupKeys())
        {
            this.collectGroupAndDescendants(root, out);
        }

        return out;
    }

    public default void collectGroupAndDescendants(String name, List<String> out)
    {
        out.add(name);

        for (String child : this.getDirectChildrenKeys(name))
        {
            this.collectGroupAndDescendants(child, out);
        }
    }
}
