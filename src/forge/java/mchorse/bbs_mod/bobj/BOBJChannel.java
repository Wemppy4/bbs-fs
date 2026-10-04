package mchorse.bbs_mod.bobj;

import java.util.ArrayList;
import java.util.List;

public class BOBJChannel
{
    public String path;
    public int index;
    public List<BOBJKeyframe> keyframes = new ArrayList<BOBJKeyframe>();

    public BOBJChannel(String path, int index)
    {
        this.path = path;
        this.index = index;
    }
}