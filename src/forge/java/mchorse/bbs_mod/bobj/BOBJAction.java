package mchorse.bbs_mod.bobj;

import java.util.HashMap;
import java.util.Map;

public class BOBJAction
{
    public String name;
    public Map<String, BOBJGroup> groups = new HashMap<String, BOBJGroup>();

    public BOBJAction(String name)
    {
        this.name = name;
    }

    public float getDuration()
    {
        float max = 0;

        for (BOBJGroup group : this.groups.values())
        {
            max = Math.max(max, group.getDuration());
        }

        return max;
    }
}