package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * The form tint: a plain texture multiplier. The old "additive color" brighten mode is gone —
 * its {@code ×(1 + c·a·8)} math clipped to flat white in any 8-bit target (every shader pack),
 * so it never survived shaders. Its niche is covered honestly by the color overlay (a real mix
 * toward the color) and the glow slider; legacy data converts in {@code Form#fromData}.
 */
public class FormColorBlend
{
    public static void blend(Color base, Color overlay)
    {
        if (base == null || overlay == null)
        {
            return;
        }

        base.r *= MathUtils.clamp(overlay.r, 0F, 1F);
        base.g *= MathUtils.clamp(overlay.g, 0F, 1F);
        base.b *= MathUtils.clamp(overlay.b, 0F, 1F);
        base.a *= MathUtils.clamp(overlay.a, 0F, 1F);
    }
}
