package mchorse.bbs_mod.cubic.render.vao;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;

/** Render-thread upload buffers for LWJGL2's NIO overloads. */
final class ModelBuffers
{
    private static FloatBuffer floats = BufferUtils.createFloatBuffer(1024);
    private static IntBuffer integers = BufferUtils.createIntBuffer(1024);
    static FloatBuffer wrap(float[] values)
    {
        if (floats.capacity() < values.length) floats = BufferUtils.createFloatBuffer(values.length);
        floats.clear(); floats.put(values); floats.flip();
        return floats;
    }
    static IntBuffer wrap(int[] values)
    {
        if (integers.capacity() < values.length) integers = BufferUtils.createIntBuffer(values.length);
        integers.clear(); integers.put(values); integers.flip();
        return integers;
    }
    private ModelBuffers() {}
}
