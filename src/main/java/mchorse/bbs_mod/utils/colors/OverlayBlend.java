package mchorse.bbs_mod.utils.colors;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

/**
 * Color overlay math (the "Наложение" material property): RGB is the overlay color, A is its
 * strength. Rendering applies it as the vanilla overlay-texture mix — {@code mix(overlay.rgb,
 * color.rgb, 1 - strength)} — which both vanilla entity shaders and Iris-patched pack shaders
 * already implement, so the effect survives shader packs.
 *
 * <p>Two stacked overlays collapse into one exactly:
 * {@code k = 1-(1-k1)(1-k2)}, {@code c = (c1*k1*(1-k2) + c2*k2) / k} — which is what
 * {@link #stack} does, so form &rarr; material &rarr; bone levels combine into a single color
 * and a single 1&times;1 texture per draw.</p>
 */
public class OverlayBlend
{
    /**
     * Stack {@code top} over {@code base}, writing the collapsed overlay into {@code base}.
     * Both are (RGB = color, A = strength).
     */
    public static void stack(Color base, Color top)
    {
        float k1 = MathUtils.clamp(base.a, 0F, 1F);
        float k2 = MathUtils.clamp(top.a, 0F, 1F);
        float k = 1F - (1F - k1) * (1F - k2);

        if (k <= 0F)
        {
            base.a = 0F;

            return;
        }

        base.r = (base.r * k1 * (1F - k2) + top.r * k2) / k;
        base.g = (base.g * k1 * (1F - k2) + top.g * k2) / k;
        base.b = (base.b * k1 * (1F - k2) + top.b * k2) / k;
        base.a = k;
    }

    /** Whether the overlay changes anything (strength above zero). */
    public static boolean isActive(Color overlay)
    {
        return overlay != null && overlay.a > 0F;
    }

    /**
     * Apply the overlay to a flat color on the CPU — exact for solid fills (label text), where
     * the texture mix and this mix are the same operation.
     */
    public static void apply(Color color, Color overlay)
    {
        if (!isActive(overlay))
        {
            return;
        }

        float k = MathUtils.clamp(overlay.a, 0F, 1F);

        color.r = Lerps.lerp(color.r, overlay.r, k);
        color.g = Lerps.lerp(color.g, overlay.g, k);
        color.b = Lerps.lerp(color.b, overlay.b, k);
    }

    /**
     * The overlay packed the way the overlay texture stores it: RGB = color,
     * A = {@code 1 - strength} (vanilla's mix convention). 0 when inactive.
     */
    public static int toTexturePixel(Color overlay)
    {
        if (!isActive(overlay))
        {
            return 0;
        }

        Color pixel = new Color(overlay.r, overlay.g, overlay.b, 1F - MathUtils.clamp(overlay.a, 0F, 1F));

        return pixel.getARGBColor();
    }
}
