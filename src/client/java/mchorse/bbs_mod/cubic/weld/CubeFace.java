package mchorse.bbs_mod.cubic.weld;

import org.joml.Vector3f;

/**
 * One of the six box faces of a cube, identified by the local normal baked into its quads. Lets a weld
 * name a side ("top", "bottom", ...) and the resolver find the matching quad by comparing normals.
 */
public enum CubeFace
{
    FRONT(0, 0, -1),
    BACK(0, 0, 1),
    RIGHT(1, 0, 0),
    LEFT(-1, 0, 0),
    TOP(0, 1, 0),
    BOTTOM(0, -1, 0);

    public final Vector3f normal;

    CubeFace(float x, float y, float z)
    {
        this.normal = new Vector3f(x, y, z);
    }

    public static CubeFace fromName(String name)
    {
        if (name == null)
        {
            return null;
        }

        try
        {
            return valueOf(name.trim().toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
