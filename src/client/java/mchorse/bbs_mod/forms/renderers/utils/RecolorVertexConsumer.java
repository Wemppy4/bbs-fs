package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

public class RecolorVertexConsumer implements VertexConsumer
{
    public static Color newColor;

    /**
     * Apply {@link #newColor} to one packed vertex color for the writers that bypass this
     * consumer's own {@link #color(int, int, int, int)} — Sodium's, and Iris' Sodium-compatible
     * entity format — where the tint has to be folded into the packed int the writer is about to
     * store. Returns it untouched while no tint is set.
     *
     * <p>That int is <b>ABGR</b>, not the ARGB every other packed color in BBS is: the attribute
     * is four bytes in RGBA order, so reading them back as one little-endian int puts red in the
     * lowest byte. Running it through {@code Color#set(int)} instead read the tint's red onto blue
     * and back — which mirrors a tint across the hue wheel (yellow painting cyan, orange painting
     * azure) while leaving green and magenta looking right.</p>
     */
    public static int tintPackedABGR(int color)
    {
        Color tint = newColor;

        if (tint == null)
        {
            return color;
        }

        int r = MathUtils.clamp((int) (tint.r * (color & 0xFF)), 0, 255);
        int g = MathUtils.clamp((int) (tint.g * (color >> 8 & 0xFF)), 0, 255);
        int b = MathUtils.clamp((int) (tint.b * (color >> 16 & 0xFF)), 0, 255);
        int a = MathUtils.clamp((int) (tint.a * (color >> 24 & 0xFF)), 0, 255);

        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    protected VertexConsumer consumer;
    protected Color color;

    public RecolorVertexConsumer(VertexConsumer consumer, Color color)
    {
        this.consumer = consumer;
        this.color = color;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        return this.consumer.vertex(x, y, z);
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z)
    {
        return this.consumer.vertex(matrix, x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        red = MathUtils.clamp((int) (this.color.r * red), 0, 255);
        green = MathUtils.clamp((int) (this.color.g * green), 0, 255);
        blue = MathUtils.clamp((int) (this.color.b * blue), 0, 255);
        alpha = MathUtils.clamp((int) (this.color.a * alpha), 0, 255);

        return this.consumer.color(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        return this.consumer.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this.consumer.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        return this.consumer.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this.consumer.normal(x, y, z);
    }
}
