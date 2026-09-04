package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CollectionUtils;
import net.minecraft.client.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;

/**
 * A BBS texture, by its link, dressed as a vanilla texture for Iris.
 *
 * <p>This is what BBS registers with Iris' texture tracker for every texture it binds, and what
 * {@link IrisTextureWrapperLoader} hands back for the {@code _n} / {@code _s} maps next to it. The
 * link is resolved on every hand-over, so a texture that is still loading, or an animated one whose
 * frames are separate GL names, resolves to whatever is current — {@code index} picks the frame.</p>
 *
 * <p>A link that resolves to nothing falls back to the texture it was given (the pack's flat default
 * normal or specular), which is how a model with no {@code _n} file beside it still renders.</p>
 */
public class IrisTextureWrapper extends IrisPbrTexture
{
    public final Link texture;
    public final AbstractTexture fallback;
    public final int index;

    private int width = 1;
    private int height = 1;

    public IrisTextureWrapper(Link texture, int index)
    {
        this(texture, null, index);
    }

    public IrisTextureWrapper(Link texture, AbstractTexture fallback, int index)
    {
        this.texture = texture;
        this.fallback = fallback;
        this.index = index;
    }

    @Override
    public GpuTexture getGlTexture()
    {
        GpuTexture resolved = super.getGlTexture();

        return resolved == null && this.fallback != null ? this.fallback.getGlTexture() : resolved;
    }

    @Override
    protected int glId()
    {
        Texture texture = BBSModClient.getTextures().getTexture(this.texture, GL11.GL_NEAREST, true);

        if (texture == null || texture == BBSModClient.getTextures().getError())
        {
            return -1;
        }

        if (this.index >= 0 && texture.getParent() != null)
        {
            Texture frame = CollectionUtils.getSafe(texture.getParent().textures, this.index);

            if (frame != null)
            {
                texture = frame;
            }
        }

        this.width = texture.width;
        this.height = texture.height;

        return texture.id;
    }

    @Override
    protected int width()
    {
        return this.width;
    }

    @Override
    protected int height()
    {
        return this.height;
    }

    @Override
    public void close()
    {
        BBSModClient.getTextures().delete(this.texture);
    }
}
