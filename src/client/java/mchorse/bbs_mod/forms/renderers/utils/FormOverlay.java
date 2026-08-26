package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.utils.resources.Pixels;

/**
 * The color overlay's render plumbing. The overlay rides the vanilla overlay-texture channel
 * (texture unit 1 + the UV1 vertex attribute): the fragment shader — vanilla's, the BBS model
 * shader's and an Iris pack's patched one alike — computes {@code mix(overlay.rgb, color.rgb,
 * overlay.a)}, so a 1&times;1 texture holding (color, 1 - strength) tints the whole draw and
 * the effect SURVIVES shader packs, which no uniform of ours can.
 *
 * <p>One persistent 1&times;1 texture is re-uploaded whenever the requested pixel changes
 * (cheap — four bytes) and bound over unit 1 for the draw; the previous binding is restored
 * right after, because vanilla's hurt-flash overlay lives in that unit. When the context
 * already carries a hurt flash (overlay UV differs from the default), the flash wins and the
 * color overlay skips the draw entirely.</p>
 */
public class FormOverlay
{
    private static Texture texture;
    private static int lastPixel;
    private static boolean uploaded;

    private static final Color combined = new Color();

    /**
     * Collapse the form &rarr; material &rarr; bone overlay stack for one draw. Returns null when
     * the result is neutral (nothing to bind). The returned instance is a shared buffer — consume
     * it before the next call.
     */
    public static Color combine(ModelForm form, String material, ModelGroup group)
    {
        combined.set(1F, 1F, 1F, 0F);

        if (form != null)
        {
            Color formOverlay = form.overlayColor.get();

            if (formOverlay != null)
            {
                OverlayBlend.stack(combined, formOverlay);
            }

            Color materialOverlay = materialOverlay(form, material);

            if (materialOverlay != null)
            {
                OverlayBlend.stack(combined, materialOverlay);
            }
        }

        if (group != null)
        {
            OverlayBlend.stack(combined, group.overlay);
        }

        return combined.a > 0F ? combined : null;
    }

    /** The material's overlay: the animation track's override first, then the static material value. */
    public static Color materialOverlay(ModelForm form, String material)
    {
        if (material == null || material.isEmpty())
        {
            return null;
        }

        Color override = form.materialOverlayOverrides.get(material);

        if (override != null)
        {
            return override;
        }

        FormMaterial formMaterial = form.materials.getMaterial(material);

        return formMaterial == null ? null : formMaterial.overlayColor.get();
    }

    /**
     * Bind the overlay's 1&times;1 texture into unit 1 for the upcoming draw. Returns the unit's
     * previous texture id to pass to {@link #unbind(int)} after the draw. The draw's overlay UV
     * must be (0, 0) — the texture is a single texel.
     */
    public static int bind(Color overlay)
    {
        int pixel = OverlayBlend.toTexturePixel(overlay);
        int previous = RenderSystem.getShaderTexture(1);

        if (texture == null)
        {
            texture = new Texture();
        }

        if (pixel != lastPixel || !uploaded)
        {
            /* A fresh 1x1 Pixels per re-upload: uploadTexture takes ownership and frees the
             * buffer, so a persistent Pixels here would die after the first upload. */
            Pixels pixels = Pixels.fromSize(1, 1);

            pixels.setColor(0, 0, new Color().set(pixel));
            pixels.rewindBuffer();

            texture.bind();
            texture.uploadTexture(pixels);
            texture.unbind();

            lastPixel = pixel;
            uploaded = true;
        }

        RenderSystem.setShaderTexture(1, texture.id);

        return previous;
    }

    /** Restore unit 1 to what it held before {@link #bind(Color)} (vanilla's real overlay texture). */
    public static void unbind(int previous)
    {
        RenderSystem.setShaderTexture(1, previous);
    }
}
