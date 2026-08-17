package mchorse.bbs_mod.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.textures.TextureFormat;

/**
 * A reusable device-owned colour target for custom-shader GUI effects (marching ants, pixelate/
 * erase, subtitle blur). The two-phase GUI composites recorded elements at the END of the frame,
 * so an immediate custom pass drawn mid-record would be overpainted; the working pattern (proven
 * by the stencil hover highlight) is: render the effect into an off-screen texture with a manual
 * pass, then blit that texture back through the RECORDED {@code texturedBox(int glId, ...)} path,
 * which composites at the right spot in the element order.
 *
 * <p>Extracted from {@code StencilFormFramebuffer.ensureHighlightTarget} so every effect doesn't
 * re-spell the ensure/release dance. One instance per viewport/effect — a shared static target
 * breaks as soon as two live viewports record blits of it in the same frame (the second write
 * wins for both, composite happens later).
 */
public class OffscreenTarget
{
    private final String label;

    private GpuTexture texture;
    private GpuTextureView view;
    private int width = -1;
    private int height = -1;

    public OffscreenTarget(String label)
    {
        this.label = label;
    }

    /** (Re)build the target at {@code w}x{@code h}. Cheap no-op while the size is unchanged. */
    public GpuTextureView ensure(int w, int h)
    {
        if (this.view != null && this.width == w && this.height == h)
        {
            return this.view;
        }

        this.release();

        this.texture = RenderSystem.getDevice().createTexture(this.label,
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC,
            TextureFormat.RGBA8, w, h, 1, 1);
        this.view = RenderSystem.getDevice().createTextureView(this.texture);

        this.width = w;
        this.height = h;

        return this.view;
    }

    public GpuTextureView getView()
    {
        return this.view;
    }

    /** Raw GL id, for the recorded {@code texturedBox(int, ...)} blit back over the viewport. */
    public int getGlId()
    {
        return this.texture == null ? -1 : ((GlTexture) this.texture).getGlId();
    }

    public int getWidth()
    {
        return this.width;
    }

    public int getHeight()
    {
        return this.height;
    }

    public void release()
    {
        if (this.view != null)
        {
            this.view.close();
            this.view = null;
        }

        if (this.texture != null)
        {
            this.texture.close();
            this.texture = null;
        }

        this.width = -1;
        this.height = -1;
    }
}
