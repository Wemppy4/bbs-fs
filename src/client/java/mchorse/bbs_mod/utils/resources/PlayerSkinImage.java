package mchorse.bbs_mod.utils.resources;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Bringing a downloaded player skin into the layout the models expect: 64x64 with an alpha
 * channel. See {@link PlayerSkins} for where the picture comes from.
 *
 * <p>Skins from before 1.8 are 64x32 — they have one arm and one leg, and the game mirrors
 * them into the bottom half when it loads them. A skin that arrived that way would otherwise
 * be a model with two blank limbs, so it is grown here the same way, off the render thread.</p>
 */
public class PlayerSkinImage
{
    /**
     * The skin as 64x64 ARGB: an old 64x32 skin gets its second arm and leg mirrored into the
     * bottom half and its base layer forced opaque (old skins carry junk in the alpha there).
     * Anything already in the modern layout is only copied into an ARGB image.
     */
    public static BufferedImage normalize(BufferedImage image)
    {
        boolean legacy = image.getHeight() * 2 == image.getWidth();
        int height = legacy ? image.getWidth() : image.getHeight();
        BufferedImage result = new BufferedImage(image.getWidth(), height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();

        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        if (legacy)
        {
            remapLegacy(result);
        }

        return result;
    }

    /** Where the game copies the old skin's limbs to, in 64x64 coordinates. */
    private static void remapLegacy(BufferedImage image)
    {
        /* HD skins are the same layout on a bigger grid */
        int scale = Math.max(1, image.getWidth() / 64);

        copyRect(image, scale, 4, 16, 16, 32, 4, 4);
        copyRect(image, scale, 8, 16, 16, 32, 4, 4);
        copyRect(image, scale, 0, 20, 24, 32, 4, 12);
        copyRect(image, scale, 4, 20, 16, 32, 4, 12);
        copyRect(image, scale, 8, 20, 8, 32, 4, 12);
        copyRect(image, scale, 12, 20, 16, 32, 4, 12);
        copyRect(image, scale, 44, 16, -8, 32, 4, 4);
        copyRect(image, scale, 48, 16, -8, 32, 4, 4);
        copyRect(image, scale, 40, 20, 0, 32, 4, 12);
        copyRect(image, scale, 44, 20, -8, 32, 4, 12);
        copyRect(image, scale, 48, 20, -16, 32, 4, 12);
        copyRect(image, scale, 52, 20, -8, 32, 4, 12);

        setOpaque(image, scale, 0, 0, 32, 16);
        clearOpaqueHat(image, scale);
        setOpaque(image, scale, 0, 16, 64, 16);
        setOpaque(image, scale, 16, 48, 32, 16);
    }

    /**
     * Skins from back then had no alpha at all, so a player without a hat still has the hat
     * half of the head painted — leaving it would put a solid block around every old skin's
     * head. Painted means every pixel of it is opaque, which is what makes it safe to drop;
     * a hat that was drawn with transparency is left alone.
     */
    private static void clearOpaqueHat(BufferedImage image, int scale)
    {
        int x = 32 * scale;
        int y = 0;
        int width = 32 * scale;
        int height = 16 * scale;

        for (int j = y; j < y + height; j++)
        {
            for (int i = x; i < x + width; i++)
            {
                if ((image.getRGB(i, j) >> 24 & 0xff) < 128)
                {
                    return;
                }
            }
        }

        for (int j = y; j < y + height; j++)
        {
            for (int i = x; i < x + width; i++)
            {
                image.setRGB(i, j, image.getRGB(i, j) & 0xffffff);
            }
        }
    }

    /** Copies a part of the skin somewhere else, mirrored horizontally the way limbs are. */
    private static void copyRect(BufferedImage image, int scale, int x, int y, int dx, int dy, int width, int height)
    {
        x *= scale;
        y *= scale;
        dx *= scale;
        dy *= scale;
        width *= scale;
        height *= scale;

        int[] pixels = image.getRGB(x, y, width, height, null, 0, width);
        int[] mirrored = new int[pixels.length];

        for (int j = 0; j < height; j++)
        {
            for (int i = 0; i < width; i++)
            {
                mirrored[i + j * width] = pixels[(width - 1 - i) + j * width];
            }
        }

        image.setRGB(x + dx, y + dy, width, height, mirrored, 0, width);
    }

    private static void setOpaque(BufferedImage image, int scale, int x, int y, int width, int height)
    {
        x *= scale;
        y *= scale;
        width *= scale;
        height *= scale;

        for (int j = y; j < y + height; j++)
        {
            for (int i = x; i < x + width; i++)
            {
                image.setRGB(i, j, image.getRGB(i, j) | 0xff000000);
            }
        }
    }
}
