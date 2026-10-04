package mchorse.bbs_mod.ui.utils;

import org.joml.Matrix4f;

/**
 * In-memory, session-only slot holding one world-space matrix captured from a transform editor
 * ("copy world transform"). Kept off the OS/JSON clipboard on purpose — it is a transient, in-world
 * cache used to pin an element to the same world orientation/scale/position at another tick, much
 * like the image clipboard keeps pixels in memory rather than on the system clipboard.
 */
public class WorldTransformClipboard
{
    private static Matrix4f matrix;

    public static void set(Matrix4f matrix)
    {
        WorldTransformClipboard.matrix = matrix == null ? null : new Matrix4f(matrix);
    }

    public static boolean has()
    {
        return WorldTransformClipboard.matrix != null;
    }

    /** A copy of the stored matrix, or {@code null} when nothing has been captured. */
    public static Matrix4f get()
    {
        return WorldTransformClipboard.matrix == null ? null : new Matrix4f(WorldTransformClipboard.matrix);
    }
}
