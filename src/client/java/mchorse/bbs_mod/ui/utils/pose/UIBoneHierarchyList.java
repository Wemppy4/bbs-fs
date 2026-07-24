package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.forms.renderers.BoneHierarchy;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A string list that keeps stable bone IDs as values while rendering hierarchy labels. */
public class UIBoneHierarchyList extends UIStringList
{
    private Map<String, String> labels = Collections.emptyMap();

    public UIBoneHierarchyList(Consumer<List<String>> callback)
    {
        super(callback);
    }

    public void setLabels(Map<String, String> labels)
    {
        this.labels = labels == null ? Collections.emptyMap() : labels;
    }

    @Override
    protected String elementToString(UIContext context, int i, String element)
    {
        return this.labels.getOrDefault(element, element);
    }
}
