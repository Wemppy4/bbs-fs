package mchorse.bbs_mod.utils.categories;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * One folder of a list that keeps its items in folders.
 *
 * <p>A folder is addressed by its {@link #path} and not by this record's id, because the items
 * store that same path — see {@link CategoryPath}. The record holds what a bare name cannot: the
 * order folders are shown in (the order of the records themselves), whether the folder is open and
 * the colour it marks its items with. A folder that only some item mentions needs no record at
 * all — it is shown all the same, after the recorded ones and open.</p>
 */
public class Category extends ValueGroup
{
    public final ValueString path = new ValueString("path", "");
    public final ValueBoolean expanded = new ValueBoolean("expanded", true);
    /**
     * The stripe its items wear down the left of the list, RGB without alpha like every other
     * authored colour in the tree. Zero is no colour, which is what a folder starts with — and
     * what makes an uncoloured folder inherit the stripe of the folder that holds it.
     */
    public final ValueInt color = new ValueInt("color", 0).color();

    public Category(String id)
    {
        super(id);

        this.add(this.path);
        this.add(this.expanded);
        this.add(this.color);
    }
}
