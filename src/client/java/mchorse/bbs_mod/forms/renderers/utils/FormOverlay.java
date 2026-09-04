package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The colour overlay's render plumbing. The overlay rides the vanilla overlay-texture channel (the
 * UV1 vertex attribute and the texture it addresses): the fragment shader — vanilla's, the BBS model
 * shader's and an Iris pack's patched one alike — computes {@code mix(overlay.rgb, colour.rgb,
 * overlay.a)}, so a texture holding (colour, 1 - strength) tints the draw, and the effect SURVIVES
 * shader packs, which no uniform of ours can.
 *
 * <p>The swatch is not BOUND anywhere: 1.21.11 has no texture units to bind behind a draw's back, a
 * render pass binds exactly the textures its {@link RenderLayer} names. So a tinted draw is given a
 * TWIN of its layer — the same layer with this swatch named as its overlay texture instead of
 * vanilla's hurt-flash atlas (see {@link #withOverlay}). One swatch serves every twin, because the
 * colour lives in the texture rather than in the layer.</p>
 *
 * <p>A draw can carry several overlays at once. The swatch is 16&times;16, and a model's geometry is
 * one buffer per material with every bone in it, so each bone's collapsed colour claims a texel of
 * its own ({@link #slot}) and the vertices address it — that is how the form, the material and the
 * bone levels all land in a single draw. A draw with one colour simply fills the swatch with it.</p>
 *
 * <p>When the context already carries a hurt flash (overlay UV differs from the default), the flash
 * wins and the colour overlay stands down for that draw.</p>
 */
public class FormOverlay
{
    /**
     * Side of the overlay texture, matching vanilla's own overlay atlas. A single-colour draw fills
     * it, so it does not matter which texel anyone addresses — BBS' own draws emit (0, 0), while
     * vanilla renderers replayed inside a form (the block entities of a structure) emit
     * {@link OverlayTexture#DEFAULT_UV}, which is (0, 10) — and a palette draw has 256 texels to
     * hand out, far past the number of overlays an author sets up on one model.
     */
    private static final int SIZE = 16;

    /**
     * The id every overlay layer names its overlay texture by. Fixed, because a layer is built once
     * and long before any colour is put into the swatch — the id is resolved through the vanilla
     * texture manager at each draw, so registering it late is fine.
     */
    public static final Identifier SWATCH = Identifier.of(BBSMod.MOD_ID, "overlay_swatch");

    /** The texel every untinted vertex of a tinted draw addresses: alpha 1 is "leave the colour alone". */
    private static final int NEUTRAL = 0xFFFFFFFF;

    private static final int NEUTRAL_UV = OverlayTexture.packUv(0, 0);

    private static Texture texture;

    /** The GL name registered under {@link #SWATCH}, so a re-made texture re-registers. */
    private static int registered = -1;

    /** The swatch as pixels, and the copy last handed to the GPU — an unchanged palette costs nothing. */
    private static final int[] pixels = new int[SIZE * SIZE];
    private static final int[] uploadedPixels = new int[SIZE * SIZE];

    /** Texel claimed per colour in the palette being built, by that colour's packed pixel. */
    private static final Map<Integer, Integer> palette = new HashMap<>();

    /** Overlay twins, by the layer they stand in for. */
    private static final Map<RenderLayer, RenderLayer> TWINS = new HashMap<>();

    private static boolean uploaded;

    private static final Color combined = new Color();

    /**
     * Collapse the form &rarr; material &rarr; bone overlay stack into one colour. Returns null when
     * the result is neutral (nothing to tint). The returned instance is a shared buffer — consume it
     * before the next call.
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
     * Put {@code overlay} into the swatch, for the draws that follow — the whole swatch, so it does
     * not matter which texel a draw addresses.
     */
    public static void swatch(Color overlay)
    {
        palette.clear();
        Arrays.fill(pixels, OverlayBlend.toTexturePixel(overlay));

        upload();
    }

    /**
     * Start a palette: from here on, colours are claimed one texel at a time with {@link #slot} and
     * a draw picks between them per vertex. That is how one draw carries several overlays at once —
     * a model's bones and materials each have their own, and the geometry is a single buffer.
     */
    public static void beginPalette()
    {
        palette.clear();
        Arrays.fill(pixels, NEUTRAL);
    }

    /**
     * The overlay UV a vertex takes to be tinted by {@code overlay}: a texel of its own in the
     * palette, or the neutral one when there is no overlay (or the palette is full — 256 distinct
     * overlays in one draw is far past anything an author sets up).
     */
    public static int slot(Color overlay)
    {
        if (!OverlayBlend.isActive(overlay))
        {
            return NEUTRAL_UV;
        }

        int pixel = OverlayBlend.toTexturePixel(overlay);
        Integer claimed = palette.get(pixel);

        if (claimed != null)
        {
            return claimed;
        }

        int index = palette.size() + 1;

        if (index >= SIZE * SIZE)
        {
            return NEUTRAL_UV;
        }

        pixels[index] = pixel;

        int uv = OverlayTexture.packUv(index % SIZE, index / SIZE);

        palette.put(pixel, uv);

        return uv;
    }

    /** Whether anything claimed a texel since {@link #beginPalette} — i.e. whether the draw is tinted. */
    public static boolean hasPalette()
    {
        return !palette.isEmpty();
    }

    /**
     * A copy of the swatch as it stands, for a draw that is not going to happen until later: the
     * deferred pass replays long after the swatch has been rewritten by everything drawn since.
     */
    public static int[] snapshot()
    {
        return pixels.clone();
    }

    /** Put a {@link #snapshot()} back, for the draw it was taken for. */
    public static void restore(int[] snapshot)
    {
        System.arraycopy(snapshot, 0, pixels, 0, pixels.length);

        upload();
    }

    /** Hand the claimed palette to the GPU. Cheap when nothing moved since the last upload. */
    public static void commitPalette()
    {
        upload();
    }

    /**
     * The same layer, drawing through the colour overlay: a twin whose overlay texture is our
     * swatch instead of vanilla's hurt-flash atlas. Everything else about it is copied, textures
     * included, so the geometry draws exactly as it would have.
     *
     * <p>This is what replaced binding a texture unit under the draw's back — 1.21.11 binds exactly
     * the textures a layer names, and nothing else. Twins are memoised per layer, and the colour
     * lives in the swatch, so one twin serves every colour.</p>
     */
    /** {@link #withOverlay(RenderLayer)} when {@code tinted}, the layer itself otherwise. */
    public static RenderLayer withOverlay(RenderLayer layer, boolean tinted)
    {
        return tinted ? withOverlay(layer) : layer;
    }

    public static RenderLayer withOverlay(RenderLayer layer)
    {
        return TWINS.computeIfAbsent(layer, (original) ->
        {
            RenderSetup setup = original.renderSetup;

            if (!setup.useOverlay)
            {
                /* Its shader has no overlay to read — the glint layers, the terrain ones. Nothing to
                 * tint through, and naming a sampler the pipeline never declared is not free. */
                return original;
            }

            RenderSetup.Builder builder = RenderSetup.builder(setup.pipeline)
                .expectedBufferSize(setup.expectedBufferSize)
                .layeringTransform(setup.layeringTransform)
                .outputTarget(setup.outputTarget)
                .textureTransform(setup.textureTransform)
                .outlineMode(setup.outlineMode);

            if (setup.useLightmap)
            {
                builder.useLightmap();
            }

            if (setup.hasCrumbling)
            {
                builder.crumbling();
            }

            if (setup.translucent)
            {
                builder.translucent();
            }

            for (Map.Entry<String, RenderSetup.TextureSpec> entry : setup.textures.entrySet())
            {
                RenderSetup.TextureSpec spec = entry.getValue();

                builder.texture(entry.getKey(), spec.location(), spec.sampler());
            }

            /* Named last, so it wins over both useOverlay() and any Sampler1 the original declared. */
            builder.texture("Sampler1", SWATCH);

            return RenderLayer.of(original + "_bbs_overlay", builder.build());
        });
    }

    private static void upload()
    {
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

        if (!uploaded || !Arrays.equals(pixels, uploadedPixels))
        {
            /* A fresh Pixels per re-upload: uploadTexture takes ownership and frees the buffer,
             * so a persistent Pixels here would die after the first upload. */
            Pixels data = Pixels.fromSize(SIZE, SIZE);
            Color color = new Color();

            for (int i = 0, c = SIZE * SIZE; i < c; i++)
            {
                data.setColor(i, color.set(pixels[i]));
            }

            data.rewindBuffer();

            texture.bind();
            texture.uploadTexture(data);
            texture.unbind();

            System.arraycopy(pixels, 0, uploadedPixels, 0, pixels.length);
            uploaded = true;
        }

        if (registered != texture.id)
        {
            registered = texture.id;

            AdoptedTexture.register(SWATCH, texture);
        }
    }
}
