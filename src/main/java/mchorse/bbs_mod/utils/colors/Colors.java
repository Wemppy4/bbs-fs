package mchorse.bbs_mod.utils.colors;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

public class Colors
{
    public static final int RGB = 0xffffff;

    /* Alpha */
    public static final int A100 = 0xff000000;
    public static final int A90 = 0xee000000;
    public static final int A75 = 0xbb000000;
    public static final int A50 = 0x88000000;
    public static final int A25 = 0x44000000;
    public static final int A12 = 0x22000000;
    public static final int A6 = 0x11000000;

    public static final int WHITE = 0xffffffff;
    public static final int LIGHTEST_GRAY = 0xffcccccc;
    public static final int LIGHTER_GRAY = 0xffaaaaaa;
    public static final int GRAY = 0xff888888;
    public static final int DARKER_GRAY = 0xff444444;
    public static final int DARK_GRAY = 0x676767;
    public static final int DARKEST_GRAY = 0xff222222;
    public static final int RED = 0xff4059;
    public static final int GREEN = 0x59d940;
    public static final int BLUE = 0x4073ff;
    public static final int PLANE_XZ = 0xffd940d9;
    public static final int PLANE_XY = 0xff40a6d9;
    public static final int PLANE_ZY = 0xffd9d940;
    public static final int YELLOW = 0xffff33;
    public static final int CYAN = 0x33ffff;
    public static final int MAGENTA = 0xff66ff;
    public static final int DEEP_PINK = 0xff1493;
    public static final int PINK = 0xff9da1;
    public static final int ORANGE = 0xff8822;

    /* General purpose colors */
    public static final int CONTROL_BAR = 0xff141417;
    public static final int ACTIVE = 0x0088ff;
    public static final int POSITIVE = GREEN;
    public static final int NEGATIVE = RED;
    public static final int INACTIVE = 0xffbb00;
    public static final int HIGHLIGHT = 0xddddff;
    public static final int CURSOR = 0xff57f52a;

    public static final Color COLOR = new Color();

    public static int opaque(int color)
    {
        return A100 | (color & RGB);
    }

    public static int mulRGB(int color, float factor)
    {
        COLOR.set(color);
        COLOR.r *= factor;
        COLOR.g *= factor;
        COLOR.b *= factor;

        return COLOR.getARGBColor();
    }

    public static int lerp(int a, int b, float x)
    {
        x = MathUtils.clamp(x, 0F, 1F);

        Color aColor = new Color().set(a);
        Color bColor = new Color().set(b);

        return COLOR.set(
            Lerps.lerp(aColor.r, bColor.r, x),
            Lerps.lerp(aColor.g, bColor.g, x),
            Lerps.lerp(aColor.b, bColor.b, x),
            Lerps.lerp(aColor.a, bColor.a, x)
        ).getARGBColor();
    }

    public static float getA(int color)
    {
        return ((color >> 24) & 0xff) / 255F;
    }

    public static float getR(int color)
    {
        return ((color >> 16) & 0xff) / 255F;
    }

    public static float getG(int color)
    {
        return ((color >> 8) & 0xff) / 255F;
    }

    public static float getB(int color)
    {
        return (color & 0xff) / 255F;
    }

    public static int setA(int color, float alpha)
    {
        COLOR.set(color);
        COLOR.a = alpha;

        return COLOR.getARGBColor();
    }

    public static int mulA(int color, float factor)
    {
        COLOR.set(color);
        COLOR.a *= factor;

        return COLOR.getARGBColor();
    }

    public static int a(float alpha)
    {
        return setA(0, alpha);
    }

    public static void interpolate(Color target, int a, int b, float x)
    {
        interpolate(target, a, b, x, true);
    }

    public static void interpolate(Color target, int a, int b, float x, boolean alpha)
    {
        target.set(a, alpha);
        COLOR.set(b, alpha);

        target.r = Lerps.lerp(target.r, COLOR.r, x);
        target.g = Lerps.lerp(target.g, COLOR.g, x);
        target.b = Lerps.lerp(target.b, COLOR.b, x);

        if (alpha)
        {
            target.a = Lerps.lerp(target.a, COLOR.a, x);
        }
    }

    public static int parse(String color)
    {
        return parse(color, 0);
    }

    public static int parse(String color, int orDefault)
    {
        try
        {
            return parseWithException(color);
        }
        catch (Exception e)
        {}

        return orDefault;
    }

    public static int parseWithException(String color) throws Exception
    {
        if (color.startsWith("#"))
        {
            color = color.substring(1);
        }

        if (color.length() == 6 || color.length() == 8)
        {
            return StringUtils.parseHex(color);
        }

        throw new Exception("Given color \"" + color + "\" can't be parsed!");
    }

    public static Color HSVtoRGB(float h, float s, float v)
    {
        return HSVtoRGB(new Color(), h, s, v);
    }

    /**
     * Convert HSV to RGB. All input values are expected to be 0..1.
     *
     * @link https://www.rapidtables.com/convert/color/hsv-to-rgb.html
     */
    public static Color HSVtoRGB(Color color, float h, float s, float v)
    {
        h *= 360;
        h %= 360;

        float c = v * s;
        float x = c * (1 - Math.abs((h / 60F) % 2 - 1));
        float m = v - c;

        if (h >= 0 && h < 60)
        {
            color.set(c, x, 0);
        }
        else if (h >= 60 && h < 120)
        {
            color.set(x, c, 0);
        }
        else if (h >= 120 && h < 180)
        {
            color.set(0, c, x);
        }
        else if (h >= 180 && h < 240)
        {
            color.set(0, x, c);
        }
        else if (h >= 240 && h < 300)
        {
            color.set(x, 0, c);
        }
        else
        {
            color.set(c, 0, x);
        }

        color.r += m;
        color.g += m;
        color.b += m;

        return color;
    }

    public static Color RGBtoHSV(float r, float g, float b)
    {
        return RGBtoHSV(new Color(), r, g, b);
    }

    /**
     * Convert RGB to HSV. All input values are expected to be 0..1.
     * The given color will be populated with HSV to red, green and blue
     * respectively in 0..1 value range.
     *
     * @link https://www.rapidtables.com/convert/color/rgb-to-hsv.html
     */
    public static Color RGBtoHSV(Color color, float r, float g, float b)
    {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        /* Hue */
        if (delta == 0)
        {
            color.r = 0;
        }
        else if (max == r)
        {
            color.r = 60F * (((g - b) / delta) % 6F);
        }
        else if (max == g)
        {
            color.r = 60F * (((b - r) / delta) + 2F);
        }
        else if (max == b)
        {
            color.r = 60F * (((r - g) / delta) + 4F);
        }

        color.r /= 360F;

        if (color.r < 0)
        {
            color.r += 1F;
        }

        /* Saturation */
        color.g = max == 0 ? 0 : delta / max;

        /* Value */
        color.b = max;

        return color;
    }
}