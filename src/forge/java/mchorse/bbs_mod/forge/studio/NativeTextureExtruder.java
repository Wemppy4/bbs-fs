package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;

public class NativeTextureExtruder
{
    private final Map<Texture, NativeTextureMesh> extruded = new WeakHashMap<>();
    private final mchorse.bbs_mod.graphics.texture.TextureManager textures;

    public NativeTextureExtruder() { this(null); }
    public NativeTextureExtruder(mchorse.bbs_mod.graphics.texture.TextureManager textures) { this.textures = textures; }

    public void delete(Texture texture) { this.extruded.remove(texture); }
    public void delete() { this.extruded.clear(); }

    /** Remove the image and every animation frame before their Texture objects are disposed. */
    public void delete(mchorse.bbs_mod.resources.Link link)
    {
        if (this.textures == null) throw new IllegalStateException("This extrusion cache has no texture manager");
        this.delete(this.textures.textures.get(link));
        mchorse.bbs_mod.graphics.texture.AnimatedTexture animation = this.textures.animatedTextures.get(link);
        if (animation != null) for (Texture frame : animation.textures) this.delete(frame);
        this.extruded.keySet().removeIf(texture -> !texture.isValid());
    }


    /** Front/back quads enclose all generated pixel edges. */
    public static mchorse.bbs_mod.utils.AABB getBounds(int width, int height)
    {
        float x = 0.5F * Math.min(1F, width / (float) Math.max(1, height));
        float y = 0.5F * Math.min(1F, height / (float) Math.max(1, width));
        return new mchorse.bbs_mod.utils.AABB(-x, -y, -0.5F / 16F, x * 2, y * 2, 1F / 16F);
    }

    /**
     * Fill a quad for the native texture-form triangles. Points should
     * be supplied in this order:
     *
     *     3 -------> 4
     *     ^
     *     |
     *     |
     *     2 <------- 1
     *
     * I.e. bottom left, bottom right, top left, top right, where left is -X and right is +X,
     * in case of a quad on fixed on Z axis.
     */
    public static void fillTexturedNormalQuad(List<Float> vertices, List<Float> normals, List<Float> uvs, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float nx, float ny, float nz)
    {
        /* 1 - BL, 2 - BR, 3 - TR, 4 - TL */
        vertices.add(x2);
        vertices.add(y2);
        vertices.add(z2);
        uvs.add(u1);
        uvs.add(v2);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);

        vertices.add(x1);
        vertices.add(y1);
        vertices.add(z1);
        uvs.add(u2);
        uvs.add(v2);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);

        vertices.add(x4);
        vertices.add(y4);
        vertices.add(z4);
        uvs.add(u2);
        uvs.add(v1);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);

        vertices.add(x2);
        vertices.add(y2);
        vertices.add(z2);
        uvs.add(u1);
        uvs.add(v2);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);

        vertices.add(x4);
        vertices.add(y4);
        vertices.add(z4);
        uvs.add(u2);
        uvs.add(v1);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);

        vertices.add(x3);
        vertices.add(y3);
        vertices.add(z3);
        uvs.add(u1);
        uvs.add(v1);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);
    }

    /** A new animation frame or a reloaded asset owns a different Texture and mesh. */
    public NativeTextureMesh get(Texture texture)
    {
        this.extruded.keySet().removeIf(key -> !key.isValid());
        if (texture == null || !texture.isValid()) return null;
        if (this.extruded.containsKey(texture)) return this.extruded.get(texture);

        Pixels pixels = Texture.pixelsFromTexture(texture);
        if (pixels == null) return null;
        try
        {
            NativeTextureMesh mesh = this.generate(pixels);
            this.extruded.put(texture, mesh);
            return mesh;
        }
        finally
        {
            pixels.delete();
        }
    }

    public NativeTextureMesh generate(Pixels pixels)
    {
        List<Float> vertices = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();

        mchorse.bbs_mod.utils.AABB bounds = getBounds(pixels.width, pixels.height);
        float px = (float) bounds.maxX();
        float py = (float) bounds.maxY();
        float u1 = 0F;
        float v1 = 0F;
        float u2 = 1F;
        float v2 = 1F;
        float d = (float) bounds.maxZ();

        float nx = -px;
        float ny = -py;

        fillTexturedNormalQuad(vertices, normals, uvs,
            px, ny, d,
            nx, ny, d,
            nx, py, d,
            px, py, d,
            u1, v1, u2, v2,
            0F, 0F, 1F
        );

        fillTexturedNormalQuad(vertices, normals, uvs,
            nx, ny, -d,
            px, ny, -d,
            px, py, -d,
            nx, py, -d,
            u2, v1, u1, v2,
            0F, 0F, -1F
        );

        for (int i = 0; i < pixels.width; i++)
        {
            for (int j = 0; j < pixels.height; j++)
            {
                if (this.hasPixel(pixels, i, j))
                {
                    this.generateNeighbors(pixels, vertices, normals, uvs, px, py, i, j, d);
                }
            }
        }

        if (!vertices.isEmpty())
        {
            return new NativeTextureMesh(vertices, normals, uvs);
        }

        return null;
    }

    private void generateNeighbors(Pixels pixels, List<Float> vertices, List<Float> normals, List<Float> uvs, float px, float py, int x, int y, float d)
    {
        float w = pixels.width;
        float h = pixels.height;
        float sx = 1 / w * (px / 0.5F);
        float sy = 1 / h * (py / 0.5F);
        float u = (x + 0.5F) / w;
        float v = (y + 0.5F) / h;

        if (!this.hasPixel(pixels, x - 1, y) || x == 0)
        {
            fillTexturedNormalQuad(vertices, normals, uvs,
                x * sx - px, -(y + 1) * sy + py, -d,
                x * sx - px, -y * sy + py, -d,
                x * sx - px, -y * sy + py, d,
                x * sx - px, -(y + 1) * sy + py, d,
                u, v, u, v,
                -1F, 0F, 0F
            );
        }

        if (!this.hasPixel(pixels, x + 1, y) || x == w - 1)
        {
            fillTexturedNormalQuad(vertices, normals, uvs,
                (x + 1) * sx - px, -(y + 1) * sy + py, d,
                (x + 1) * sx - px, -y * sy + py, d,
                (x + 1) * sx - px, -y * sy + py, -d,
                (x + 1) * sx - px, -(y + 1) * sy + py, -d,
                u, v, u, v,
                1F, 0F, 0F
            );
        }

        if (!this.hasPixel(pixels, x, y - 1) || y == 0)
        {
            fillTexturedNormalQuad(vertices, normals, uvs,
                (x + 1) * sx - px, -y * sy + py, d,
                x * sx - px, -y * sy + py, d,
                x * sx - px, -y * sy + py, -d,
                (x + 1) * sx - px, -y * sy + py, -d,
                u, v, u, v,

                0F, 1F, 0F
            );
        }

        if (!this.hasPixel(pixels, x, y + 1) || y == h - 1)
        {
            fillTexturedNormalQuad(vertices, normals, uvs,
                (x + 1) * sx - px, -(y + 1) * sy + py, -d,
                x * sx - px, -(y + 1) * sy + py, -d,
                x * sx - px, -(y + 1) * sy + py, d,
                (x + 1) * sx - px, -(y + 1) * sy + py, d,
                u, v, u, v,
                0F, -1F, 0F
            );
        }
    }

    private boolean hasPixel(Pixels pixels, int x, int y)
    {
        Color pixel = pixels.getColor(x, y);

        return pixel != null && pixel.a >= 1;
    }
}
