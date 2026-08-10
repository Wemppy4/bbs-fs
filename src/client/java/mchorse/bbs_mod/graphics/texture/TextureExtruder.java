package mchorse.bbs_mod.graphics.texture;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextureExtruder
{
    private Map<Link, ModelVAO> extruded = new HashMap<>();

    /**
     * Fill a quad for {@link net.minecraft.client.render.VertexFormats#POSITION_TEXTURE_COLOR_NORMAL}. Points should
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

    public void delete(Link key)
    {
        ModelVAO remove = this.extruded.remove(key);

        if (remove != null)
        {
            remove.delete();
        }
    }

    public void deleteAll()
    {
        for (ModelVAO value : this.extruded.values())
        {
            value.delete();
        }

        this.extruded.clear();
    }

    public ModelVAO get(Link key)
    {
        if (this.extruded.containsKey(key))
        {
            return this.extruded.get(key);
        }

        Pixels pixels = null;

        try
        {
            pixels = BBSModClient.getTextures().getPixels(key);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (pixels == null)
        {
            this.extruded.put(key, null);

            return null;
        }

        ModelVAO buffer = this.generate(pixels);

        pixels.delete();
        this.extruded.put(key, buffer);

        return buffer;
    }

    private ModelVAO generate(Pixels pixels)
    {
        List<Float> vertices = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();

        float px = 0.5F;
        float py = 0.5F;
        float u1 = 0F;
        float v1 = 0F;
        float u2 = 1F;
        float v2 = 1F;
        float d = 0.5F / 16F;

        if (pixels.width > pixels.height)
        {
            py = pixels.height / (float) pixels.width * 0.5F;
        }
        else if (pixels.height > pixels.width)
        {
            px = pixels.width / (float) pixels.height * 0.5F;
        }

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
            float[] v = CollectionUtils.toArray(vertices);
            float[] n = CollectionUtils.toArray(normals);
            float[] u = CollectionUtils.toArray(uvs);

            return new ModelVAO(new ModelVAOData(v, n, u));
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