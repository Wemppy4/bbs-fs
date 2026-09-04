package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import net.minecraft.client.texture.AbstractTexture;

/**
 * A PBR map BBS hands to Iris, over one of its own raw GL textures.
 *
 * <p>Iris speaks vanilla {@code AbstractTexture} (its tracker, its loaders and its holders are all
 * typed on it) and reads the id out of the {@code GpuTexture} behind it. BBS textures are raw GL
 * names of its own making, so each one is adopted into a vanilla GPU texture on the way out — the
 * same zero-copy bridge the interface uses to draw them, see {@link AdoptedTexture}.</p>
 *
 * <p>The id is asked for on every hand-over rather than kept, because these stand for BBS textures
 * that are re-made behind their name: an animated frame, a reloaded file, a slider variant baked
 * again. A new id simply gets adopted again.</p>
 */
public abstract class IrisPbrTexture extends AbstractTexture
{
    /** The id the current {@link #glTexture} was adopted from, so an unchanged one is not re-adopted. */
    private int adopted = -1;

    @Override
    public GpuTexture getGlTexture()
    {
        int id = this.glId();

        if (id != this.adopted)
        {
            this.adopted = id;
            this.glTexture = id < 0 ? null : AdoptedTexture.adopt(id, "bbs_pbr_" + id, this.width(), this.height());
        }

        return this.glTexture;
    }

    /** The GL name this map stands for right now, or -1 when there is nothing to hand over. */
    protected abstract int glId();

    protected abstract int width();

    protected abstract int height();
}
