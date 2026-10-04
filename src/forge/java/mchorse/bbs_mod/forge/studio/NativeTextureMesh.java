package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.utils.CollectionUtils;
import java.util.List;

/** Immutable triangles shared by the Forge texture-form backend. */
public final class NativeTextureMesh
{
    public final float[] positions;
    public final float[] normals;
    public final float[] uvs;

    public NativeTextureMesh(List<Float> positions, List<Float> normals, List<Float> uvs)
    {
        this.positions = CollectionUtils.toArray(positions);
        this.normals = CollectionUtils.toArray(normals);
        this.uvs = CollectionUtils.toArray(uvs);
    }

    public NativeTextureMesh(float[] positions, float[] normals, float[] uvs)
    {
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
    }
}
