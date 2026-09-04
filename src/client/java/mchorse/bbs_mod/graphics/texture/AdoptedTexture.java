package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.GlTextureView;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bridges a BBS raw-GL {@link Texture} (its {@code int} GL texture id) into the vanilla two-phase GUI
 * (1.21.6+) so {@link net.minecraft.client.gui.DrawContext#drawTexture} can sample it.
 *
 * <p>Zero-copy: the existing GL id is ADOPTED by subclassing {@link GlTexture}/{@link GlTextureView}
 * (their constructors are {@code protected}, so reachable from subclasses without reflection) and
 * pointing the {@code glId} field at the BBS texture. Because we wrap the live GL object, dynamic BBS
 * textures (re-uploaded into the same id, e.g. animated frames or framebuffer previews) are reflected
 * automatically — no per-frame copy.</p>
 *
 * <p>BBS owns the GL id lifecycle ({@link Texture#delete()}); this wrapper therefore must NEVER delete
 * it, so {@link #close()} and the adopted {@link GlTexture#close()} are no-ops.</p>
 *
 * <p>Each adopted texture is registered once in the vanilla {@link net.minecraft.client.texture.TextureManager}
 * under a unique {@link Identifier}; the mapping is cached weakly by BBS {@link Texture} identity.</p>
 *
 * TODO(1.21.11 render): registered wrappers are never removed from the vanilla TextureManager (small,
 * bounded leak across the bounded BBS texture set). Add explicit deregistration if texture churn grows.
 */
public final class AdoptedTexture extends AbstractTexture
{
    /* Sampling-only usage; the GUI textured pipeline binds the texture to a sampler slot. */
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING;

    private static final Map<Texture, Identifier> REGISTRY = new WeakHashMap<>();

    /* Raw-GL-id cache (e.g. framebuffer-preview color textures handed to the int / Supplier overloads).
     * Keyed by glId rather than identity: ids are reused/bounded, so a plain map is fine. Unlike the
     * Texture-keyed cache this cannot be weak (the key is a primitive box), so this map retains its
     * wrappers — see the bounded-leak TODO in the class javadoc. */
    private static final Map<Integer, Identifier> GLID_REGISTRY = new HashMap<>();

    /** Filter each adopted Texture was last registered with, so a change forces a re-adopt. */
    private static final Map<Texture, Boolean> LINEAR = new HashMap<>();

    /** Size+filter each adopted GL id was last registered with (ids get recycled by the driver). */
    private static final Map<Integer, Long> GLID_STAMP = new HashMap<>();
    private static int counter;

    /**
     * Return (registering on first use) the vanilla {@link Identifier} that resolves to a wrapper of
     * the given BBS texture. Returns {@code null} if the texture is not valid yet.
     */
    public static Identifier identifier(Texture texture)
    {
        if (texture == null || !texture.isValid())
        {
            return null;
        }

        Identifier id = REGISTRY.get(texture);
        boolean linear = texture.isLinear();

        /* The sampler is baked into the wrapper at adoption time, so a cached entry keeps whatever filter
         * the texture had the FIRST time it was drawn. Textures change theirs afterwards (a billboard's
         * linear/mipmap flags, an editor toggling one), and the stale wrapper then sampled a pixel-art
         * texture blurred — permanently, since nothing ever re-registered it. Re-adopt when it changes. */
        if (id != null && !Boolean.valueOf(linear).equals(LINEAR.get(texture)))
        {
            id = null;
        }

        if (id == null)
        {
            id = REGISTRY.get(texture);

            if (id == null)
            {
                id = Identifier.of(BBSMod.MOD_ID, "adopted/" + (counter++));
                REGISTRY.put(texture, id);
            }

            MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                new AdoptedTexture(texture.id, "bbs_adopted_" + texture.id,
                    texture.width, texture.height, linear));
            LINEAR.put(texture, linear);
        }

        return id;
    }

    /**
     * By-GL-id variant of {@link #identifier(Texture)}: adopt (zero-copy) an existing raw GL texture id
     * directly. Used by the {@code texturedBox(int,...)} / {@code texturedBox(Supplier,...)} bridges,
     * whose callers hand us a framebuffer-preview color texture as a bare {@code glId}.
     *
     * <p>{@code linear} selects the sampler filter (NEAREST for pixel UI, LINEAR for FBO previews).</p>
     *
     * TODO(1.21.11 render): verify at runtime. Caches by glId; the same id is assumed to keep its
     * dimensions/filter intent across frames (FBO previews resize by reallocating the same id, so the
     * cached wrapper's static width/height may go stale — re-register if preview resizing misbehaves).
     */
    public static Identifier identifier(int glId, int width, int height, boolean linear)
    {
        if (glId < 0)
        {
            return null;
        }

        Identifier id = GLID_REGISTRY.get(glId);
        long stamp = (linear ? 1L : 0L) | ((long) width << 1) | ((long) height << 33);

        /* Keyed by a bare GL id, which the driver RECYCLES: once an id is freed, the next texture to take
         * it inherited this cached wrapper — its size and, worse, its filter. That is how unrelated
         * textures started sampling linear and never went back. Re-adopt whenever the id is presented with
         * a different size or filter. */
        if (id != null && !Long.valueOf(stamp).equals(GLID_STAMP.get(glId)))
        {
            id = null;
        }

        if (id == null)
        {
            id = GLID_REGISTRY.get(glId);

            if (id == null)
            {
                id = Identifier.of(BBSMod.MOD_ID, "adopted/glid_" + glId);
                GLID_REGISTRY.put(glId, id);
            }

            MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                new AdoptedTexture(glId, "bbs_adopted_glid_" + glId, width, height, linear));
            GLID_STAMP.put(glId, stamp);
        }

        return id;
    }

    /**
     * Adopt a raw GL id as a vanilla GPU texture, registering nothing: for handing a BBS texture to
     * an API that speaks {@link GpuTexture} rather than drawing it (Iris' PBR maps, which it reads
     * the GL name back out of). BBS still owns the id — the wrapper never frees it.
     */
    public static GpuTexture adopt(int glId, String label, int width, int height)
    {
        return new AdoptedGlTexture(glId, label, width, height);
    }

    /**
     * Shared constructor for both entry points: adopt the existing GL id {@code glId} (zero-copy) into
     * a vanilla {@link GlTexture}/{@link GlTextureView} pair with a clamping sampler.
     */
    private AdoptedTexture(int glId, String label, int width, int height, boolean linear)
    {
        AdoptedGlTexture glTexture = new AdoptedGlTexture(glId, label, width, height);

        this.glTexture = glTexture;
        this.glTextureView = new AdoptedGlTextureView(glTexture);

        FilterMode filter = linear ? FilterMode.LINEAR : FilterMode.NEAREST;

        this.sampler = RenderSystem.getSamplerCache().get(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, filter, filter, false);
    }

    @Override
    public void close()
    {
        /* BBS owns the underlying GL id; never delete it here. */
    }

    /** {@link GlTexture} that adopts an existing GL id instead of allocating a new GL texture. */
    private static final class AdoptedGlTexture extends GlTexture
    {
        private AdoptedGlTexture(int glId, String label, int width, int height)
        {
            super(USAGE, label, TextureFormat.RGBA8,
                Math.max(1, width), Math.max(1, height), 1, 1, glId);
        }

        @Override
        public void close()
        {
            /* BBS owns the GL id; do not free it. */
        }
    }

    /** View over an {@link AdoptedGlTexture} (single mip level). */
    private static final class AdoptedGlTextureView extends GlTextureView
    {
        private AdoptedGlTextureView(AdoptedGlTexture texture)
        {
            super(texture, 0, 1);
        }

        @Override
        public void close()
        {
            /* Underlying texture not owned; nothing to release. */
        }
    }
}
