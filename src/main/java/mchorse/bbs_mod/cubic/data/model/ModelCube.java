package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.Quad;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModelCube implements IMapSerializable
{
    public List<ModelQuad> quads = new ArrayList<>();
    public Vector3f origin = new Vector3f();
    public Vector3f size = new Vector3f();
    public Vector3f pivot = new Vector3f();
    public Vector3f rotate = new Vector3f();
    public float inflate;

    /**
     * What the cube is called in the model editor's tree; empty for most cubes, which are named
     * by their place in their group there. Nothing in the model refers to a cube by name, so
     * names may repeat. Written to the file only when given.
     */
    public String name = "";

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

    /**
     * Where the sides that aren't drawn used to be on the sheet: a side taken away keeps its unwrap
     * here, so giving it back puts it where it was. Written apart from {@code uvs}, so whatever reads
     * only those still draws nothing on that side.
     */
    private final Map<CubeFace, ModelUV> hiddenUVs = new EnumMap<>(CubeFace.class);

    /** The unwrap of one of the cube's six sides, or null when that side isn't drawn at all. */
    public ModelUV getUV(CubeFace face)
    {
        switch (face)
        {
            case FRONT: return this.front;
            case BACK: return this.back;
            case RIGHT: return this.right;
            case LEFT: return this.left;
            case TOP: return this.top;
            case BOTTOM: return this.bottom;
            default: throw new IllegalArgumentException("Unknown cube face: " + face);
        }
    }

    /** The unwrap a side that isn't drawn will come back with; null for a drawn side, or one never drawn. */
    public ModelUV getHiddenUV(CubeFace face)
    {
        return this.hiddenUVs.get(face);
    }

    /** Stop drawing a side, keeping its unwrap for when it's drawn again. */
    public void hideUV(CubeFace face)
    {
        ModelUV uv = this.getUV(face);

        if (uv != null)
        {
            this.setUV(face, null);
            this.hiddenUVs.put(face, uv);
        }
    }

    /**
     * Draw a side again with the unwrap it had — or, for a side that was never drawn, over the
     * sheet's corner at the side's own size.
     */
    public void showUV(CubeFace face)
    {
        if (this.getUV(face) != null)
        {
            return;
        }

        ModelUV uv = this.hiddenUVs.get(face);

        if (uv == null)
        {
            Vector2f size = this.faceSize(face);

            uv = ModelUV.fromXY(0F, 0F, size.x, size.y);
        }

        this.setUV(face, uv);
    }

    /** How big a side is on the sheet at a pixel of it per pixel of the model: across, then down, as it faces out. */
    public Vector2f faceSize(CubeFace face)
    {
        switch (face)
        {
            case FRONT: case BACK: return new Vector2f(this.size.x, this.size.y);
            case RIGHT: case LEFT: return new Vector2f(this.size.z, this.size.y);
            case TOP: case BOTTOM: return new Vector2f(this.size.x, this.size.z);
            default: throw new IllegalArgumentException("Unknown cube face: " + face);
        }
    }

    /** Give a side its unwrap, or null to stop drawing it — either way, what it was hidden with is forgotten. */
    public void setUV(CubeFace face, ModelUV uv)
    {
        this.hiddenUVs.remove(face);

        switch (face)
        {
            case FRONT: this.front = uv; break;
            case BACK: this.back = uv; break;
            case RIGHT: this.right = uv; break;
            case LEFT: this.left = uv; break;
            case TOP: this.top = uv; break;
            case BOTTOM: this.bottom = uv; break;
        }
    }

    public void setupBoxUV(Vector2f boxUV, boolean mirror)
    {
        /* All six are laid out anew and drawn, so none has a place of its own left to come back to */
        this.hiddenUVs.clear();

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

    /** Move the cube as a whole: its corner and the pivot it turns about go by the same step. */
    public void shift(Vector3f delta)
    {
        this.origin.add(delta);
        this.pivot.add(delta);
    }

    @Override
    public void toData(MapType data)
    {
        if (!this.name.isEmpty())
        {
            data.putString("name", this.name);
        }

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

        /* Ordered, as the cube's own keys are: a hash map would shuffle the faces on every save. */
        MapType uvs = new MapType(false);

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

        MapType hidden = new MapType(false);

        for (Map.Entry<CubeFace, ModelUV> entry : this.hiddenUVs.entrySet())
        {
            if (this.getUV(entry.getKey()) == null)
            {
                this.saveUVSide(hidden, faceKey(entry.getKey()), entry.getValue());
            }
        }

        if (hidden.size() > 0)
        {
            data.put("hidden_uvs", hidden);
        }
    }

    /** A side's key in the file, where {@code uvs} and {@code hidden_uvs} name it the same way. */
    private static String faceKey(CubeFace face)
    {
        return face.name().toLowerCase(Locale.ROOT);
    }

    private void saveUVSide(MapType data, String key, ModelUV side)
    {
        if (side != null)
        {
            data.put(key, side.toData());
        }
    }

    /**
     * The whole cube from its data: what the data leaves out takes its default, so a cube read
     * twice (the model editor's copies and undo snapshots) never keeps a rotation, an inflate or
     * a face the data no longer has.
     */
    @Override
    public void fromData(MapType data)
    {
        this.name = data.getString("name", "");
        this.origin.set(DataStorageUtils.vector3fFromData(data.getList("from")));
        this.size.set(DataStorageUtils.vector3fFromData(data.getList("size")));
        this.pivot.set(DataStorageUtils.vector3fFromData(data.getList("origin")));
        this.inflate = data.getFloat("offset", 0F);

        if (data.has("rotate"))
        {
            this.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate")));
        }
        else
        {
            this.rotate.set(0F, 0F, 0F);
        }

        this.material = data.getString("material", "");
        this.front = this.back = this.right = this.left = this.top = this.bottom = null;
        this.hiddenUVs.clear();

        if (data.has("uvs"))
        {
            this.parseUV(data.get("uvs"));
        }

        MapType hidden = data.getMap("hidden_uvs");

        for (CubeFace face : CubeFace.values())
        {
            if (hidden.has(faceKey(face)) && this.getUV(face) == null)
            {
                this.hiddenUVs.put(face, this.parseUVSide(hidden, faceKey(face)));
            }
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
