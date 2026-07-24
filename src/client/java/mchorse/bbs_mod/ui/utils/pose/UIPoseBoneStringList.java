package mchorse.bbs_mod.ui.utils.pose;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bone list for {@link UIPoseEditor}: supports multi-selection with the default list behavior
 * (Shift = range selection, Ctrl = toggle).
 */
public class UIPoseBoneStringList extends UIBoneHierarchyList
{
    public UIPoseBoneStringList(Consumer<List<String>> callback)
    {
        super(callback);

        this.multi();
    }
}
