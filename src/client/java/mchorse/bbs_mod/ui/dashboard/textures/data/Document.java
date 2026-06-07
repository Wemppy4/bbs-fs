package mchorse.bbs_mod.ui.dashboard.textures.data;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.storage.DataFileStorage;
import mchorse.bbs_mod.data.storage.DataGzipStorage;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntArrayType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureEditor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Editable texture document: the model the texture painter edits and persists.
 *
 * <p>It owns the layer stack (CPU {@link Pixels} plus their GPU {@link mchorse.bbs_mod.graphics.texture.Texture}
 * via {@link TextureLayer}), the active layer index and the canvas size. {@link UITextureEditor}
 * operates on a {@code Document} instead of holding the layers itself.</p>
 *
 * <p>A document is persisted next to its texture as {@code NAME_INCLUDING_EXTENSION.dat} (e.g.
 * {@code skin.png.dat}) through the BBS {@link mchorse.bbs_mod.data data library}: layer pixels are
 * stored as ARGB {@link IntArrayType} arrays, the rest as plain map entries. When a texture is
 * opened and no {@code .dat} exists, a single-layer document is built from the texture's pixels.</p>
 */
public class Document implements IMapSerializable
{
    /** Runtime association with the texture file; not serialized (it is implied by the .dat location). */
    public Link link;

    public final List<TextureLayer> layers = new ArrayList<>();
    public int activeLayerIndex = -1;
    public int width;
    public int height;

    /** The {@code .dat} sidecar file for a given texture file (e.g. {@code skin.png} -> {@code skin.png.dat}). */
    public static File datFile(File textureFile)
    {
        return new File(textureFile.getParentFile(), textureFile.getName() + ".dat");
    }

    /** Deserialize a document from its {@code .dat} sidecar, or {@code null} when it can't be read. */
    public static Document read(Link link, File file)
    {
        try
        {
            BaseType data = new DataGzipStorage(new DataFileStorage(file)).read();

            if (data instanceof MapType map)
            {
                Document document = new Document(link);

                document.fromData(map);

                return document.layers.isEmpty() ? null : document;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /** Build a fresh single-layer document from a texture's pixels (used when no {@code .dat} exists). */
    public static Document fromPixels(Link link, Pixels pixels)
    {
        Document document = new Document(link);

        document.width = pixels.width;
        document.height = pixels.height;
        document.layers.add(new TextureLayer(UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format("1").get(), pixels));
        document.activeLayerIndex = 0;

        return document;
    }

    public Document()
    {}

    public Document(Link link)
    {
        this.link = link;
    }

    public TextureLayer getActiveLayer()
    {
        return this.activeLayerIndex >= 0 && this.activeLayerIndex < this.layers.size()
            ? this.layers.get(this.activeLayerIndex)
            : null;
    }

    /** Resize every layer to {@code w}x{@code h}, preserving the existing content in the top-left. */
    public void resize(int w, int h)
    {
        this.width = w;
        this.height = h;

        for (TextureLayer layer : this.layers)
        {
            if (layer.pixels != null && (layer.pixels.width != w || layer.pixels.height != h))
            {
                Pixels newPixels = Pixels.fromSize(w, h);

                newPixels.draw(layer.pixels, 0, 0);
                layer.pixels.delete();
                layer.pixels = newPixels;
                layer.updateTexture();
            }
        }
    }

    /**
     * Composite the colour at a single document pixel by blending every visible
     * layer (respecting its offset and opacity), without allocating a whole
     * flattened canvas. Mirrors {@link Pixels#draw}'s source-over blend, layer by
     * layer from the bottom (index 0) to the top. Returns {@code null} when there
     * are no layers; a pixel no layer covers yields transparent black.
     */
    public Color getColorAt(int x, int y)
    {
        if (this.layers.isEmpty())
        {
            return null;
        }

        float r = 0F;
        float g = 0F;
        float b = 0F;
        float a = 0F;

        for (TextureLayer layer : this.layers)
        {
            if (!layer.visible || layer.pixels == null || layer.opacity <= 0F)
            {
                continue;
            }

            int lx = x - layer.offsetX;
            int ly = y - layer.offsetY;

            if (lx < 0 || ly < 0 || lx >= layer.pixels.width || ly >= layer.pixels.height)
            {
                continue;
            }

            Color source = layer.pixels.getColor(lx, ly);

            if (source == null)
            {
                continue;
            }

            /* This layer draws over what's accumulated below (source-over): the
             * layer is the foreground, the accumulated colour is the background. */
            float sr = source.r;
            float sg = source.g;
            float sb = source.b;
            float sa = source.a * layer.opacity;

            float outA = 1F - (1F - sa) * (1F - a);

            if (outA > 0F)
            {
                r = (sr * sa + r * a * (1F - sa)) / outA;
                g = (sg * sa + g * a * (1F - sa)) / outA;
                b = (sb * sa + b * a * (1F - sa)) / outA;
            }

            a = outA;
        }

        return new Color(r, g, b, a);
    }

    /** Flatten the visible layers (respecting opacity) into a freshly allocated {@link Pixels}. */
    public Pixels flatten()
    {
        if (this.layers.isEmpty())
        {
            return null;
        }

        Pixels output = Pixels.fromSize(this.width, this.height);

        for (TextureLayer layer : this.layers)
        {
            if (layer.visible && layer.pixels != null && layer.opacity > 0F)
            {
                /* Draw at the layer's offset; Pixels.draw clips to the output bounds, so any part
                 * of the layer pushed outside the canvas by the move tool is correctly cropped. */
                output.draw(layer.pixels, layer.offsetX, layer.offsetY, layer.opacity);
            }
        }

        output.rewindBuffer();

        return output;
    }

    /** Free all GPU/CPU resources held by the layers and reset the stack. */
    public void delete()
    {
        for (TextureLayer layer : this.layers)
        {
            layer.delete();
        }

        this.layers.clear();
        this.activeLayerIndex = -1;
    }

    /** Write this document to its {@code .dat} sidecar through the data library. */
    public void write(File file)
    {
        try
        {
            if (file.getParentFile() != null)
            {
                file.getParentFile().mkdirs();
            }

            new DataGzipStorage(new DataFileStorage(file)).write(this.toData());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.putInt("width", this.width);
        data.putInt("height", this.height);
        data.putInt("active", this.activeLayerIndex);

        ListType layersData = new ListType();

        for (TextureLayer layer : this.layers)
        {
            MapType layerData = new MapType();

            layerData.putString("name", layer.name);
            layerData.putFloat("opacity", layer.opacity);
            layerData.putBool("visible", layer.visible);
            layerData.putInt("offsetX", layer.offsetX);
            layerData.putInt("offsetY", layer.offsetY);
            layerData.put("pixels", new IntArrayType(toARGB(layer.pixels)));

            layersData.add(layerData);
        }

        data.put("layers", layersData);
    }

    @Override
    public void fromData(MapType data)
    {
        this.delete();

        this.width = data.getInt("width");
        this.height = data.getInt("height");

        int count = this.width * this.height;
        ListType layersData = data.getList("layers");

        if (layersData != null)
        {
            for (int i = 0; i < layersData.size(); i++)
            {
                MapType layerData = layersData.getMap(i);

                if (layerData == null)
                {
                    continue;
                }

                Pixels pixels = Pixels.fromIntArray(this.width, this.height, readARGB(layerData.get("pixels"), count));
                TextureLayer layer = new TextureLayer(layerData.getString("name", UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format("1").get()), pixels);

                layer.opacity = layerData.getFloat("opacity", 1F);
                layer.visible = layerData.getBool("visible", true);
                layer.offsetX = layerData.getInt("offsetX", 0);
                layer.offsetY = layerData.getInt("offsetY", 0);

                this.layers.add(layer);
            }
        }

        this.activeLayerIndex = this.layers.isEmpty()
            ? -1
            : MathUtils.clamp(data.getInt("active", 0), 0, this.layers.size() - 1);
    }

    private static int[] toARGB(Pixels pixels)
    {
        int count = pixels.getCount();
        int[] argb = new int[count];

        for (int i = 0; i < count; i++)
        {
            Color color = pixels.getColor(i);

            argb[i] = color == null ? 0 : color.getARGBColor();
        }

        return argb;
    }

    private static int[] readARGB(BaseType type, int count)
    {
        if (type instanceof IntArrayType array && array.value.length >= count)
        {
            return array.value;
        }

        return new int[count];
    }
}
