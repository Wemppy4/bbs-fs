package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;

import java.io.IOException;

/**
 * Tracked stand-in for a PBR-slider albedo variant (see {@code TextureManager#getVariant}):
 * registered with Iris' texture tracker under the variant's GL id, so {@link IrisPbrConstLoader}
 * receives the slider snapshot when the pack asks for that albedo's normal/specular maps.
 */
public class IrisPbrConstWrapper extends AbstractTexture
{
    public final Link albedo;
    public final int glId;

    public final float smoothness;
    public final float metallic;
    public final float sss;
    public final float emission;
    public final float relief;

    public IrisPbrConstWrapper(Link albedo, int glId, float smoothness, float metallic, float sss, float emission, float relief)
    {
        this.albedo = albedo;
        this.glId = glId;
        this.smoothness = smoothness;
        this.metallic = metallic;
        this.sss = sss;
        this.emission = emission;
        this.relief = relief;
    }

    @Override
    public void load(ResourceManager manager) throws IOException
    {}

    @Override
    public int getGlId()
    {
        return this.glId;
    }

    @Override
    public void close()
    {
        /* The variant texture is owned by the texture manager. */
    }
}
