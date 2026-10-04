package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.lwjgl.BufferUtils;

public class Pixels
{
    private ByteBuffer buffer;
    public final int width;
    public final int height;
    public final int bits;

    public Color color = new Color();

    /**
     * Create pixels object from given PNG stream
     */
    public static Pixels fromPNGStream(InputStream stream) throws IOException
    {
        BufferedImage image = ImageIO.read(stream);
        if (image == null) throw new IOException("Unsupported or damaged image");
        Pixels pixels = fromIntArray(image.getWidth(), image.getHeight(),
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
        pixels.rewindBuffer();
        return pixels;
    }

    public static Pixels fromIntArray(int width, int height, int[] data)
    {
        Pixels pixels = fromSize(width, height);

        for (int x = 0; x < width; x++)
        {
            for (int y = 0; y < height; y++)
            {
                int i = x + y * width;

                pixels.setColor(x, y, Colors.COLOR.set(data[i]));
            }
        }

        return pixels;
    }

    public static Pixels fromSize(int w, int h)
    {
        ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);

        buffer.position(0);

        for (int i = 0, c = w * h; i < c; i++)
        {
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            buffer.put((byte) 0);
        }

        return new Pixels(buffer, w, h);
    }

    public Pixels(ByteBuffer buffer, int w, int h)
    {
        this(buffer, w, h, 4);
    }

    public Pixels(ByteBuffer buffer, int w, int h, int bits)
    {
        this.buffer = buffer;
        this.width = w;
        this.height = h;
        this.bits = bits;
    }

    public ByteBuffer getBuffer()
    {
        return this.buffer;
    }

    public int toIndex(int x, int y)
    {
        return x + y * this.width;
    }

    public int toX(int index)
    {
        return index % this.width;
    }

    public int toY(int index)
    {
        return index / this.width;
    }

    public int getCount()
    {
        return this.width * this.height;
    }

    public Color getColor(int index)
    {
        if (index < 0 || index >= this.width * this.height)
        {
            return null;
        }

        this.buffer.position(index * this.bits);
        this.color.r = ((int) this.buffer.get() & 0xff) / 255F;
        this.color.g = ((int) this.buffer.get() & 0xff) / 255F;
        this.color.b = ((int) this.buffer.get() & 0xff) / 255F;
        this.color.a = this.bits == 4 ? ((int) this.buffer.get() & 0xff) / 255F : 1F;

        return this.color;
    }

    public Color getColor(int x, int y)
    {
        return this.getColor(this.toIndex(x, y));
    }

    public void setColor(int index, Color color)
    {
        this.buffer.position(index * this.bits);
        this.buffer.put((byte) (color.r * 0xff));
        this.buffer.put((byte) (color.g * 0xff));
        this.buffer.put((byte) (color.b * 0xff));

        if (this.bits == 4)
        {
            this.buffer.put((byte) (color.a * 0xff));
        }
    }

    public void setColor(int x, int y, Color color)
    {
        this.setColor(this.toIndex(x, y), color);
    }

    public void draw(Pixels pixels, int x, int y)
    {
        this.draw(pixels, x, y, 1.0F);
    }

    public void draw(Pixels pixels, int x, int y, float opacity)
    {
        Color color = new Color();

        for (int i = Math.max(x, 0), ic = Math.min(x + pixels.width, this.width); i < ic; i++)
        {
            for (int j = Math.max(y, 0), jc = Math.min(y + pixels.height, this.height); j < jc; j++)
            {
                int px = i - x;
                int py = j - y;

                Color target = pixels.getColor(px, py);
                Color source = this.getColor(i, j);
                
                float targetAlpha = target.a * opacity;

                color.a = 1 - (1 - targetAlpha) * (1 - source.a);
                if (color.a > 0F)
                {
                    color.r = target.r * targetAlpha / color.a + source.r * source.a * (1 - targetAlpha) / color.a;
                    color.g = target.g * targetAlpha / color.a + source.g * source.a * (1 - targetAlpha) / color.a;
                    color.b = target.b * targetAlpha / color.a + source.b * source.a * (1 - targetAlpha) / color.a;
                }

                this.setColor(i, j, color);
            }
        }
    }

    public void draw(Pixels pixels, int x, int y, int w, int h)
    {
        this.draw(pixels, x, y, w, h, 1.0F);
    }

    public void draw(Pixels pixels, int x, int y, int w, int h, float opacity)
    {
        Color color = new Color();

        for (int i = Math.max(x, 0), ic = Math.min(x + w, this.width); i < ic; i++)
        {
            for (int j = Math.max(y, 0), jc = Math.min(y + h, this.height); j < jc; j++)
            {
                float fx = (i - x) / (float) w;
                float fy = (j - y) / (float) h;
                int px = (int) (pixels.width * fx);
                int py = (int) (pixels.height * fy);

                Color target = pixels.getColor(px, py);
                Color source = this.getColor(i, j);
                
                float targetAlpha = target.a * opacity;

                color.a = 1 - (1 - targetAlpha) * (1 - source.a);
                if (color.a > 0F)
                {
                    color.r = target.r * targetAlpha / color.a + source.r * source.a * (1 - targetAlpha) / color.a;
                    color.g = target.g * targetAlpha / color.a + source.g * source.a * (1 - targetAlpha) / color.a;
                    color.b = target.b * targetAlpha / color.a + source.b * source.a * (1 - targetAlpha) / color.a;
                }

                this.setColor(i, j, color);
            }
        }
    }

    public void drawRect(int x, int y, int w, int h, int c)
    {
        Color color = new Color().set(c);

        for (int i = Math.max(x, 0), ic = Math.min(x + w, this.width); i < ic; i++)
        {
            for (int j = Math.max(y, 0), jc = Math.min(y + h, this.height); j < jc; j++)
            {
                this.setColor(i, j, color);
            }
        }
    }

    /**
     * D = destination (this)
     * S = source
     */
    public void drawPixels(Pixels source, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2)
    {
        int dirX = dx2 - dx1 < 0 ? -1 : 1;
        int dirY = dy2 - dy1 < 0 ? -1 : 1;

        if (dirX < 0)
        {
            dx1 -= 1;
            dx2 -= 1;
        }

        if (dirY < 0)
        {
            dy1 -= 1;
            dy2 -= 1;
        }

        for (int x = dx1; x != dx2; x += dirX)
        {
            for (int y = dy1; y != dy2; y += dirY)
            {
                float fx = (x - dx1) / (float) (dx2 - dx1);
                float fy = (y - dy1) / (float) (dy2 - dy1);

                int xx = (int) Lerps.lerp(sx1, sx2, fx);
                int yy = (int) Lerps.lerp(sy1, sy2, fy);

                Color color = source.getColor(xx, yy);

                if (color != null)
                {
                    this.setColor(x, y, color);
                }
            }
        }
    }

    public int[] getARGB()
    {
        int[] colors = new int[this.width * this.height];

        for (int i = 0, c = this.getCount(); i < c; i++)
        {
            colors[i] = this.getColor(i).getARGBColor();
        }

        return colors;
    }

    /**
     * A copy of the given rectangle, four channels per pixel. Whole rows (a frame cut from a
     * strip, the picture entire) are copied byte for byte: no pixel is walked, and none passes
     * through a float and back.
     */
    public Pixels createCopy(int x, int y, int w, int h)
    {
        Pixels pixels = fromSize(w, h);

        if (x == 0 && w == this.width && this.bits == 4 && y >= 0 && y + h <= this.height)
        {
            long row = (long) w * 4;

            ByteBuffer source = this.buffer.duplicate();
            source.position((int) (y * row));
            source.limit((int) ((y + h) * row));
            pixels.buffer.clear();
            pixels.buffer.put(source);
            pixels.buffer.flip();

            return pixels;
        }

        for (int i = 0; i < w; i++)
        {
            for (int j = 0; j < h; j++)
            {
                Color color = this.getColor(x + i, y + j);

                if (color != null)
                {
                    pixels.setColor(i, j, color);
                }
            }
        }

        return pixels;
    }

    public void rewindBuffer()
    {
        if (this.buffer != null)
        {
            this.buffer.position(0);
            this.buffer.limit(this.buffer.capacity());
        }
    }

    public void delete()
    {
        this.buffer = null;
    }
}