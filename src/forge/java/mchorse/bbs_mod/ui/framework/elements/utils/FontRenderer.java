package mchorse.bbs_mod.ui.framework.elements.utils;





import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FontRenderer
{
    /** What the interface has always stepped lines by with the default font. */
    public static final int DEFAULT_LINE_HEIGHT = 12;

    private net.minecraft.client.gui.FontRenderer renderer;

    /**
     * {@link net.minecraft.client.gui.FontRenderer#fontHeight} is a constant 9 whichever font is drawing, so
     * a custom font has to bring its own metrics along - see
     * {@link mchorse.bbs_mod.fonts.FontManager}.
     */
    private int height;
    private int lineHeight = DEFAULT_LINE_HEIGHT;

    public static List<String> wrap(net.minecraft.client.gui.FontRenderer renderer, String string, int width)
    {
        return renderer.listFormattedStringToWidth(string, width);
    }

    public void setRenderer(net.minecraft.client.gui.FontRenderer renderer)
    {
        this.setRenderer(renderer, renderer.FONT_HEIGHT - 2, DEFAULT_LINE_HEIGHT);
    }

    public void setRenderer(net.minecraft.client.gui.FontRenderer renderer, int height, int lineHeight)
    {
        this.renderer = renderer;
        this.height = height;
        this.lineHeight = lineHeight;
    }

    public net.minecraft.client.gui.FontRenderer getRenderer()
    {
        return this.renderer;
    }

    public int getWidth(String string)
    {
        return this.renderer.getStringWidth(string);
    }

    public int getHeight()
    {
        return this.height;
    }

    /** How far apart the baselines of two lines of this font sit. */
    public int getLineHeight()
    {
        return this.lineHeight;
    }

    public List<String> wrap(String string, int width)
    {
        return wrap(this.renderer, string, width);
    }

    public String limitToWidth(String str, int width)
    {
        return limitToWidth(str, "...", width);
    }

    public String limitToWidth(String str, String suffix, int width)
    {
        if (str.isEmpty())
        {
            return str;
        }

        int w = this.renderer.getStringWidth(str);

        if (w < width)
        {
            return str;
        }

        int sw = this.renderer.getStringWidth(suffix);
        int i = str.length() - 1;

        while (w + sw >= width && i > 0)
        {
            w -= this.renderer.getStringWidth(String.valueOf(str.charAt(i)));
            i -= 1;
        }

        str = str.substring(0, i);

        return str.isEmpty() ? str : str + suffix;
    }

}
