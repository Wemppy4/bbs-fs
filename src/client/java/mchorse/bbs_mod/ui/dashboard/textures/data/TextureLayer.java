package mchorse.bbs_mod.ui.dashboard.textures.data;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * One layer of a texture project: its pixels, and the same pixels on the video card.
 *
 * <p>A picture may be taller than a card takes one texture — the strip of an animation of a few
 * thousand frames is. Such a picture goes up in bands instead, and {@link #draw} puts a piece of
 * it on screen out of whichever bands that piece falls into; everything else works in the
 * picture's own pixels and doesn't know the difference.</p>
 */
public class TextureLayer
{
    public String name;
    public Pixels pixels;

    /** The picture as one texture on the card; null when it didn't fit and lives in {@link #bands}. */
    public Texture texture;

    public boolean visible = true;
    public float opacity = 1.0F;

    /** Pixel offset of this layer within the document, applied by the move tool and baked in on flatten. */
    public int offsetX;
    public int offsetY;

    /** The picture in bands of {@link #bandHeight} rows; one band, the same object as {@link #texture}, when it fits. */
    private final List<Texture> bands = new ArrayList<>();
    private int bandHeight;

    /** What the card said, asked once. */
    private static int maxBandHeight;

    /**
     * How tall a band may be: what the card takes for one texture, halved the way
     * {@link mchorse.bbs_mod.audio.Waveform} halves it — drivers don't always hand out the
     * whole limit in one allocation.
     */
    public static int maxBandHeight()
    {
        if (maxBandHeight <= 0)
        {
            maxBandHeight = Math.max(1, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) / 2);
        }

        return maxBandHeight;
    }

    public TextureLayer(String name, Pixels pixels)
    {
        this.name = name;
        this.pixels = pixels;

        this.updateTexture();
    }

    /** The picture's size, whatever it takes on the card. */
    public int width()
    {
        return this.pixels == null ? 0 : this.pixels.width;
    }

    public int height()
    {
        return this.pixels == null ? 0 : this.pixels.height;
    }

    /** Put the pixels on the card, in as many bands as their height takes. */
    public void updateTexture()
    {
        if (this.pixels == null)
        {
            return;
        }

        int height = Math.min(this.pixels.height, maxBandHeight());
        int count = Math.max(1, (this.pixels.height + height - 1) / height);

        this.bandHeight = height;

        while (this.bands.size() > count)
        {
            this.bands.remove(this.bands.size() - 1).delete();
        }

        while (this.bands.size() < count)
        {
            Texture band = new Texture();

            band.setFilter(GL11.GL_NEAREST);
            this.bands.add(band);
        }

        ByteBuffer buffer = this.pixels.getBuffer();

        for (int i = 0; i < count; i++)
        {
            Texture band = this.bands.get(i);
            int top = i * height;
            int rows = Math.min(height, this.pixels.height - top);

            /* The rows of a band lie together in the buffer, so the band is uploaded straight
             * out of it — glTexImage2D reads from wherever the buffer stands */
            buffer.limit(buffer.capacity());
            buffer.position(top * this.pixels.width * this.pixels.bits);

            band.bind();
            band.uploadTexture(band.target, 0, this.pixels.width, rows, buffer);
        }

        this.pixels.rewindBuffer();
        this.texture = count == 1 ? this.bands.get(0) : null;
    }

    /**
     * Draw a piece of the layer, given in the layer's own pixels, into a rectangle on screen.
     * Only the piece: a band wraps, so a quad reaching past its edge would show the rows on the
     * other side of it. A piece crossing bands is drawn band by band, each getting the share of
     * the rectangle its rows take, so the two stay the same scale.
     */
    public void draw(Batcher2D batcher, int color, float x, float y, float w, float h, int x1, int y1, int x2, int y2)
    {
        if (this.bands.isEmpty() || x2 <= x1 || y2 <= y1)
        {
            return;
        }

        float scale = h / (float) (y2 - y1);
        int last = Math.min(this.bands.size() - 1, (y2 - 1) / this.bandHeight);

        for (int i = Math.max(0, y1 / this.bandHeight); i <= last; i++)
        {
            Texture band = this.bands.get(i);
            int top = i * this.bandHeight;
            int by1 = Math.max(y1, top);
            int by2 = Math.min(y2, top + band.height);

            if (by2 <= by1)
            {
                continue;
            }

            batcher.texturedBox(band, color, x, y + (by1 - y1) * scale, w, (by2 - by1) * scale, x1, by1 - top, x2, by2 - top, band.width, band.height);
        }
    }

    public void delete()
    {
        if (this.pixels != null)
        {
            this.pixels.delete();
        }

        for (Texture band : this.bands)
        {
            band.delete();
        }

        this.bands.clear();
        this.texture = null;
    }
}
