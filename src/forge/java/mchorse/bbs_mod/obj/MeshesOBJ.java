package mchorse.bbs_mod.obj;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshesOBJ implements IMeshes
{
    public List<MeshOBJ> meshes = new ArrayList<MeshOBJ>();
    public Map<String, List<MeshOBJ>> shapes;

    public void mergeShape(String name, MeshesOBJ shape)
    {
        this.shapes = this.shapes == null ? new HashMap<String, List<MeshOBJ>>() : this.shapes;
        this.shapes.put(name, shape.meshes);
    }

    @Override
    public Vector3f getMin()
    {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

        for (MeshOBJ obj : this.meshes)
        {
            for (int i = 0, c = obj.posData.length / 3; i < c; i++)
            {
                min.x = Math.min(obj.posData[i * 3], min.x);
                min.y = Math.min(obj.posData[i * 3 + 1], min.y);
                min.z = Math.min(obj.posData[i * 3 + 2], min.z);
            }
        }

        return min;
    }

    @Override
    public Vector3f getMax()
    {
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (MeshOBJ obj : this.meshes)
        {
            for (int i = 0, c = obj.posData.length / 3; i < c; i++)
            {
                max.x = Math.max(obj.posData[i * 3], max.x);
                max.y = Math.max(obj.posData[i * 3 + 1], max.y);
                max.z = Math.max(obj.posData[i * 3 + 2], max.z);
            }
        }

        return max;
    }
}
