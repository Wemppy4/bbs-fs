package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.Quad;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ModelCube implements IMapSerializable
{
    public List<ModelQuad> quads = new ArrayList<>();
    public Vector3f origin = new Vector3f();
    public Vector3f size = new Vector3f();
    public Vector3f pivot = new Vector3f();
    public Vector3f rotate = new Vector3f();
    public float inflate;

    /**
     * The material this cube is drawn with, the same way {@link ModelMesh#material} names a mesh's:
     * empty for the model's default texture, a name for a texture of its own — a layer over the
     * entity, the wool over a sheep, drawn from the same bones as the body. Drives the per-material
     * texture selection at render time.
     */
    public String material = "";

    /* Texture mapping */
    public ModelUV front;
    public ModelUV right;
    public ModelUV back;
    public ModelUV left;
    public ModelUV top;
    public ModelUV bottom;

    public void setupBoxUV(Vector2f boxUV, boolean mirror)
    {
        /* North */
        float w = (float) Math.floor(this.size.x);
        float h = (float) Math.floor(this.size.y);
        float d = (float) Math.floor(this.size.z);
        float tMinX = boxUV.x + d;
        float tMinY = boxUV.y + d;
        float tMaxX = tMinX + w;
        float tMaxY = tMinY + h;

        if (mirror)
        {
            float tmp = tMaxX;

            tMaxX = tMinX;
            tMinX = tmp;
        }

        this.front = ModelUV.fromXY(tMinX, tMinY, tMaxX, tMaxY);

        /* East */
        tMinX = boxUV.x;
        tMinY = boxUV.y + d;
        tMaxX = tMinX + d;
        tMaxY = tMinY + h;

        if (mirror)
        {
            tMinX = boxUV.x + d + w;
            tMinY = boxUV.y + d;
            tMaxX = tMinX + d;
            tMaxY = tMinY + h;

            float tmp = tMinX;

            tMinX = tMaxX;
            tMaxX = tmp;
        }

        this.right = ModelUV.fromXY(tMinX, tMinY, tMaxX, tMaxY);

        /* South */
        tMinX = boxUV.x + d * 2 + w;
        tMinY = boxUV.y + d;
        tMaxX = tMinX + w;
        tMaxY = tMinY + h;

        if (mirror)
        {
            float tmp = tMaxX;

            tMaxX = tMinX;
            tMinX = tmp;
        }

        this.back = ModelUV.fromXY(tMinX, tMinY, tMaxX, tMaxY);

        /* West */
        tMinX = boxUV.x + d + w;
        tMinY = boxUV.y + d;
        tMaxX = tMinX + d;
        tMaxY = tMinY + h;

        if (mirror)
        {
            tMinX = boxUV.x;
            tMinY = boxUV.y + d;
            tMaxX = tMinX + d;
            tMaxY = tMinY + h;

            float tmp = tMinX;

            tMinX = tMaxX;
            tMaxX = tmp;
        }

        this.left = ModelUV.fromXY(tMinX, tMinY, tMaxX, tMaxY);

        /* Up */
        tMinX = boxUV.x + d;
        tMinY = boxUV.y;
        tMaxX = tMinX + w;
        tMaxY = tMinY + d;

        if (mirror)
        {
            float tmp = tMaxX;

            tMaxX = tMinX;
            tMinX = tmp;
        }

        this.top = ModelUV.fromXY(tMaxX, tMaxY, tMinX, tMinY);

        /* Down */
        tMinX = boxUV.x + d + w;
        tMinY = boxUV.y + d;
        tMaxX = tMinX + w;
        tMaxY = boxUV.y;

        if (mirror)
        {
            float tmp = tMaxX;

            tMaxX = tMinX;
            tMinX = tmp;
        }

        this.bottom = ModelUV.fromXY(tMaxX, tMaxY, tMinX, tMinY);
    }

    public void generateQuads(int textureWidth, int textureHeight)
    {
        float tw = 1F / textureWidth;
        float th = 1F / textureHeight;

        float minX = (this.origin.x - this.inflate) / 16F;
        float minY = (this.origin.y - this.inflate) / 16F;
        float minZ = (this.origin.z - this.inflate) / 16F;

        float maxX = (this.origin.x + this.size.x + this.inflate) / 16F;
        float maxY = (this.origin.y + this.size.y + this.inflate) / 16F;
        float maxZ = (this.origin.z + this.size.z + this.inflate) / 16F;

        this.quads.clear();

        if (this.front != null)
        {
            Quad quad = this.front.createQuad();
            
            this.quads.add(new ModelQuad()
                .vertex(maxX, minY, minZ, quad.p4.x * tw, quad.p4.y * th)
                .vertex(minX, minY, minZ, quad.p3.x * tw, quad.p3.y * th)
                .vertex(minX, maxY, minZ, quad.p2.x * tw, quad.p2.y * th)
                .vertex(maxX, maxY, minZ, quad.p1.x * tw, quad.p1.y * th)
                .normal(0, 0, -1));
        }

        if (this.right != null)
        {
            Quad quad = this.right.createQuad();

            this.quads.add(new ModelQuad()
                .vertex(maxX, minY, maxZ, quad.p4.x * tw, quad.p4.y * th)
                .vertex(maxX, minY, minZ, quad.p3.x * tw, quad.p3.y * th)
                .vertex(maxX, maxY, minZ, quad.p2.x * tw, quad.p2.y * th)
                .vertex(maxX, maxY, maxZ, quad.p1.x * tw, quad.p1.y * th)
                .normal(1, 0, 0));
        }

        if (this.back != null)
        {
            Quad quad = this.back.createQuad();

            this.quads.add(new ModelQuad()
                .vertex(minX, minY, maxZ, quad.p4.x * tw, quad.p4.y * th)
                .vertex(maxX, minY, maxZ, quad.p3.x * tw, quad.p3.y * th)
                .vertex(maxX, maxY, maxZ, quad.p2.x * tw, quad.p2.y * th)
                .vertex(minX, maxY, maxZ, quad.p1.x * tw, quad.p1.y * th)
                .normal(0, 0, 1));
        }

        if (this.left != null)
        {
            Quad quad = this.left.createQuad();

            this.quads.add(new ModelQuad()
                .vertex(minX, minY, minZ, quad.p4.x * tw, quad.p4.y * th)
                .vertex(minX, minY, maxZ, quad.p3.x * tw, quad.p3.y * th)
                .vertex(minX, maxY, maxZ, quad.p2.x * tw, quad.p2.y * th)
                .vertex(minX, maxY, minZ, quad.p1.x * tw, quad.p1.y * th)
                .normal(-1, 0, 0));
        }

        if (this.top != null)
        {
            Quad quad = this.top.createQuad();

            this.quads.add(new ModelQuad()
                .vertex(maxX, maxY, minZ, quad.p2.x * tw, quad.p2.y * th)
                .vertex(minX, maxY, minZ, quad.p1.x * tw, quad.p1.y * th)
                .vertex(minX, maxY, maxZ, quad.p4.x * tw, quad.p4.y * th)
                .vertex(maxX, maxY, maxZ, quad.p3.x * tw, quad.p3.y * th)
                .normal(0, 1, 0));
        }

        if (this.bottom != null)
        {
            Quad quad = this.bottom.createQuad();

            this.quads.add(new ModelQuad()
                .vertex(minX, minY, minZ, quad.p4.x * tw, quad.p4.y * th)
                .vertex(maxX, minY, minZ, quad.p3.x * tw, quad.p3.y * th)
                .vertex(maxX, minY, maxZ, quad.p2.x * tw, quad.p2.y * th)
                .vertex(minX, minY, maxZ, quad.p1.x * tw, quad.p1.y * th)
                .normal(0, -1, 0));
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.put("from", DataStorageUtils.vector3fToData(this.origin));
        data.put("size", DataStorageUtils.vector3fToData(this.size));
        data.put("origin", DataStorageUtils.vector3fToData(this.pivot));

        if (this.inflate != 0)
        {
            data.putFloat("offset", this.inflate);
        }

        if (this.rotate.x != 0 || this.rotate.y != 0 || this.rotate.z != 0)
        {
            data.put("rotate", DataStorageUtils.vector3fToData(this.rotate));
        }

        if (!this.material.isEmpty())
        {
            data.putString("material", this.material);
        }

        MapType uvs = new MapType();

        this.saveUVSide(uvs, "front", this.front);
        this.saveUVSide(uvs, "back", this.back);
        this.saveUVSide(uvs, "right", this.right);
        this.saveUVSide(uvs, "left", this.left);
        this.saveUVSide(uvs, "top", this.top);
        this.saveUVSide(uvs, "bottom", this.bottom);

        if (uvs.size() > 0)
        {
            data.put("uvs", uvs);
        }
    }

    private void saveUVSide(MapType data, String key, ModelUV side)
    {
        if (side != null)
        {
            data.put(key, side.toData());
        }
    }

    @Override
    public void fromData(MapType data)
    {
        this.origin.set(DataStorageUtils.vector3fFromData(data.getList("from")));
        this.size.set(DataStorageUtils.vector3fFromData(data.getList("size")));
        this.pivot.set(DataStorageUtils.vector3fFromData(data.getList("origin")));

        if (data.has("offset"))
        {
            this.inflate = data.getFloat("offset");
        }

        if (data.has("rotate"))
        {
            this.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate")));
        }

        this.material = data.getString("material", "");

        if (data.has("uvs"))
        {
            this.parseUV(data.get("uvs"));
        }
    }

    private void parseUV(BaseType data)
    {
        if (data instanceof MapType)
        {
            MapType sides = (MapType) data;

            if (sides.has("front")) this.front = parseUVSide(sides, "front");
            if (sides.has("back")) this.back = parseUVSide(sides, "back");
            if (sides.has("right")) this.right = parseUVSide(sides, "right");
            if (sides.has("left")) this.left = parseUVSide(sides, "left");
            if (sides.has("top")) this.top = parseUVSide(sides, "top");
            if (sides.has("bottom")) this.bottom = parseUVSide(sides, "bottom");
        }
    }

    private ModelUV parseUVSide(MapType uvs, String name)
    {
        ModelUV uv = new ModelUV();

        uv.fromData(uvs.getList(name));

        return uv;
    }
}