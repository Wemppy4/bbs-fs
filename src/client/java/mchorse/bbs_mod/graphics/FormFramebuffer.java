package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;

/**
 * The off-screen colour + depth pair a framebuffer form draws itself into, handed out by
 * {@link FramebufferPool}.
 *
 * <p>On 1.21.1 this was a raw-GL framebuffer of BBS's own ({@link Framebuffer}): bind it, draw, bind
 * the previous one back. The 1.21.5 GPU rewrite ended that — a draw now goes through a render pass
 * the backend builds out of {@link GpuTextureView}s, and an FBO bound by hand is never consulted, so
 * the parts would have landed on the screen and the buffer would have stayed empty. The target is
 * therefore a pair of device textures, pointed at through
 * {@link RenderSystem#outputColorTextureOverride} — the same mechanism the in-panel model preview and
 * the form-list thumbnails use.</p>
 *
 * <p>The finished picture is sampled back through {@link #getIdentifier()}: BBS's render layers name
 * their Sampler0 by {@link Identifier}, and {@link AdoptedTexture} bridges the colour texture's GL
 * name into the vanilla texture manager without copying it.</p>
 */
public class FormFramebuffer implements AutoCloseable
{
    /** Drawn into (render attachment) and then sampled by the quad that shows it (texture binding). */
    private static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;

    public final int width;
    public final int height;

    private final GpuTexture color;
    private final GpuTexture depth;
    private final GpuTextureView colorView;
    private final GpuTextureView depthView;

    public FormFramebuffer(int width, int height)
    {
        GpuDevice device = RenderSystem.getDevice();

        this.width = width;
        this.height = height;
        this.color = device.createTexture(() -> "BBS form framebuffer", USAGE, TextureFormat.RGBA8, width, height, 1, 1);
        this.depth = device.createTexture(() -> "BBS form framebuffer depth", GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32, width, height, 1, 1);
        this.colorView = device.createTextureView(this.color);
        this.depthView = device.createTextureView(this.depth);
    }

    public GpuTexture getColor()
    {
        return this.color;
    }

    public GpuTexture getDepth()
    {
        return this.depth;
    }

    public GpuTextureView getColorView()
    {
        return this.colorView;
    }

    public GpuTextureView getDepthView()
    {
        return this.depthView;
    }

    /**
     * The vanilla id the finished picture is sampled by, adopted (zero-copy) from the colour texture's
     * GL name. NEAREST, like the raw-GL buffer this replaces: the picture is drawn at the form's own
     * resolution and is meant to land on the quad texel for texel.
     */
    public Identifier getIdentifier()
    {
        return AdoptedTexture.identifier(((GlTexture) this.color).getGlId(), this.width, this.height, false);
    }

    /** Colour + depth, four bytes each, for the pool's idle budget. */
    public long getBytes()
    {
        return (long) this.width * this.height * 8L;
    }

    @Override
    public void close()
    {
        this.colorView.close();
        this.depthView.close();
        this.color.close();
        this.depth.close();
    }
}
