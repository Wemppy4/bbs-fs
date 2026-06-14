package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class ModelMesh implements IMapSerializable
{
    public Vector3f origin = new Vector3f();
    public Vector3f rotate = new Vector3f();
    public ModelData baseData = new ModelData();
    public Map<String, ModelData> data = new HashMap<>();

    /**
     * Name of the material this mesh belongs to (OBJ material name, BOBJ mesh name).
     * Empty string means the model's default texture. Drives per-material texture
     * selection at render time.
     */
    public String material = "";

    @Override
    public void fromData(MapType data)
    {
        this.baseData.clear();
        this.data.clear();

        this.origin.set(DataStorageUtils.vector3fFromData(data.getList("origin"), this.origin));
        this.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate"), this.rotate));
        this.material = data.getString("material", "");

        ListType vertices = data.getList("vertices");
        ListType uvs = data.getList("uvs");
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();

        if (vertices.size() / 3 == uvs.size() / 2)
        {
            for (int i = 0, c = vertices.size() / 3; i < c; i++)
            {
                int indexV = i * 3;
                int indexU = i * 2;

                this.baseData.vertices.add(new Vector3f(vertices.getFloat(indexV), vertices.getFloat(indexV + 1), vertices.getFloat(indexV + 2)).add(this.origin));
                this.baseData.uvs.add(new Vector2f(uvs.getFloat(indexU), uvs.getFloat(indexU + 1)));
            }

            for (int i = 0, c = this.baseData.vertices.size() / 3; i < c; i++)
            {
                Vector3f p1 = this.baseData.vertices.get(i * 3);
                Vector3f p2 = this.baseData.vertices.get(i * 3 + 1);
                Vector3f p3 = this.baseData.vertices.get(i * 3 + 2);
                Vector3f normal = new Vector3f();

                a.set(p2).sub(p1);
                b.set(p3).sub(p1);

                a.cross(b, normal);
                normal.normalize();

                this.baseData.normals.add(normal);
                this.baseData.normals.add(normal);
                this.baseData.normals.add(normal);
            }
        }
    }

    @Override
    public void toData(MapType data)
    {
        ListType vertices = new ListType();
        ListType uvs = new ListType();

        for (Vector3f v : this.baseData.vertices)
        {
            vertices.addFloat(v.x);
            vertices.addFloat(v.y);
            vertices.addFloat(v.z);
        }

        for (Vector2f v : this.baseData.uvs)
        {
            uvs.addFloat(v.x);
            uvs.addFloat(v.y);
        }

        data.put("origin", DataStorageUtils.vector3fToData(this.origin));
        data.put("rotate", DataStorageUtils.vector3fToData(this.rotate));
        data.put("vertices", vertices);
        data.put("uvs", uvs);

        if (!this.material.isEmpty())
        {
            data.putString("material", this.material);
        }
    }
}