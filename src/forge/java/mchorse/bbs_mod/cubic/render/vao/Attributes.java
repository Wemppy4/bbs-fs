package mchorse.bbs_mod.cubic.render.vao;

/**
 * Вершинные атрибуты (могут измениться в зависимости от версии игры или Iris, см. индексы в {@link net.irisshaders.iris.vertices.IrisVertexFormats})
 */
public class Attributes
{
    /* Vanilla attributes */
    public static final int POSITION = 0;
    public static final int COLOR = 1;
    public static final int TEXTURE_UV = 2;
    public static final int OVERLAY_UV = 3;
    public static final int LIGHTMAP_UV = 4;
    public static final int NORMAL = 5;

    /* Iris attributes:
     * 6 - Padding (Not used)
     * 7 - Entity ID (Not used)
     */
    public static final int MID_TEXTURE_UV = 8;
    public static final int TANGENTS = 9;
}
