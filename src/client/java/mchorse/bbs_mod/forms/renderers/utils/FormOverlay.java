package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.render.OverlayTexture;
import org.lwjgl.opengl.GL11;

/**
 * The color overlay's render plumbing. The overlay rides the vanilla overlay-texture channel
 * (texture unit 1 + the UV1 vertex attribute): the fragment shader — vanilla's, the BBS model
 * shader's and an Iris pack's patched one alike — computes {@code mix(overlay.rgb, color.rgb,
 * overlay.a)}, so a 1&times;1 texture holding (color, 1 - strength) tints the whole draw and
 * the effect SURVIVES shader packs, which no uniform of ours can.
 *
 * <p>One persistent texture is re-uploaded whenever the requested pixel changes (cheap — a
 * kilobyte) and bound over unit 1 for the draw; the previous binding is restored right after,
 * because vanilla's hurt-flash overlay lives in that unit. When the context already carries a
 * hurt flash (overlay UV differs from the default), the flash wins and the color overlay skips
 * the draw entirely.</p>
 */
public class FormOverlay
{
    /**
     * Side of the overlay texture. It holds the same pixel everywhere, so it does not matter
     * which texel a draw addresses: BBS' own draws emit (0, 0), while vanilla renderers replayed
     * inside a form — the block entities of a structure — emit {@link OverlayTexture#DEFAULT_UV},
     * which is (0, 10). Matching vanilla's 16&times;16 overlay atlas covers both without a vertex
     * consumer wrapper rewriting anyone's overlay UV (Sodium replaces those wrappers anyway).
     */
    private static final int SIZE = 16;

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
     * Bind the overlay's texture into unit 1 for the upcoming draw. Returns the unit's previous
     * texture id to pass to {@link #unbind(int)} after the draw.
     */
    public static int bind(Color overlay)
    {
        int pixel = OverlayBlend.toTexturePixel(overlay);
        int previous = RenderSystem.getShaderTexture(1);

        if (texture == null)
        {
            texture = new Texture();

            /* GL's default minification filter is NEAREST_MIPMAP_LINEAR, and only level 0 is ever
             * uploaded here — at any size past 1x1 that leaves the texture mipmap-incomplete, and
             * every fetch from an incomplete texture comes back (0, 0, 0, 1). The overlay would
             * then read as "no overlay" while the draw it rides on is paid for anyway. The
             * constructor leaves the texture bound, so the filter lands on this one. */
            texture.setFilter(GL11.GL_NEAREST);
            texture.unbind();
        }

        if (pixel != lastPixel || !uploaded)
        {
            /* A fresh Pixels per re-upload: uploadTexture takes ownership and frees the buffer,
             * so a persistent Pixels here would die after the first upload. */
            Pixels pixels = Pixels.fromSize(SIZE, SIZE);
            Color color = new Color().set(pixel);

            for (int i = 0, c = SIZE * SIZE; i < c; i++)
            {
                pixels.setColor(i, color);
            }

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
