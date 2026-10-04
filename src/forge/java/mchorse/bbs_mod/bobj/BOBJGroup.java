package mchorse.bbs_mod.bobj;

import java.util.ArrayList;
import java.util.List;

public class BOBJGroup
{
    public String name;
    public List<BOBJChannel> channels = new ArrayList<BOBJChannel>();

    public BOBJGroup(String name)
    {
        this.name = name;
    }

    public float getDuration()
    {
        float max = 0;

        for (BOBJChannel channel : this.channels)
        {
            int size = channel.keyframes.size();

            if (size > 0)
            {
                max = Math.max(max, channel.keyframes.get(size - 1).frame);
            }
        }

        return max;
    }
}