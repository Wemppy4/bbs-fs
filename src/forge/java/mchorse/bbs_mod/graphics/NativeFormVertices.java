package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Scoped native upload adjustment; cached baked quads/structure arrays are never modified. */
public final class NativeFormVertices implements AutoCloseable
{
    private static final ThreadLocal<NativeFormVertices> CURRENT = new ThreadLocal<>();
    private final NativeFormVertices previous;
    private final Color tint;
    private final int light;
    private final boolean vertexLight;
    private final Runnable beforeDraw;

    public NativeFormVertices(Color tint, int light, boolean vertexLight, Runnable beforeDraw)
    {
        this.previous = CURRENT.get(); this.tint = tint.copy(); this.light = light;
        this.vertexLight = vertexLight; this.beforeDraw = beforeDraw; CURRENT.set(this);
    }

    public static void beforeUpload(BufferBuilder buffer)
    {
        NativeFormVertices draw = CURRENT.get();
        if (draw == null) return;
        if (draw.beforeDraw != null) draw.beforeDraw.run();
        draw.apply(buffer);
    }

    private void apply(BufferBuilder buffer)
    {
        VertexFormat format = buffer.getVertexFormat();
        int stride = format.getSize(), color = -1, lightmap = -1;
        for (int i = 0; i < format.getElementCount(); i++)
        {
            VertexFormatElement element = format.getElement(i);
            if (element.getUsage() == VertexFormatElement.EnumUsage.COLOR
                && element.getType() == VertexFormatElement.EnumType.UBYTE && element.getElementCount() == 4)
                color = format.getOffset(i);
            if (element.getUsage() == VertexFormatElement.EnumUsage.UV && element.getIndex() == 1
                && element.getType() == VertexFormatElement.EnumType.SHORT && element.getElementCount() == 2)
                lightmap = format.getOffset(i);
        }
        ByteBuffer data = buffer.getByteBuffer().duplicate().order(ByteOrder.nativeOrder());
        for (int i = 0; i < buffer.getVertexCount(); i++)
        {
            int offset = i * stride;
            if (color >= 0)
            {
                multiply(data, offset + color, tint.r); multiply(data, offset + color + 1, tint.g);
                multiply(data, offset + color + 2, tint.b); multiply(data, offset + color + 3, tint.a);
            }
            if (lightmap >= 0)
            {
                int block = light & 65535;
                if (vertexLight) block = Math.max(block, data.getShort(offset + lightmap) & 65535);
                data.putShort(offset + lightmap, (short) block);
                data.putShort(offset + lightmap + 2, (short) (light >>> 16));
            }
        }
    }

    private static void multiply(ByteBuffer data, int offset, float value)
    { data.put(offset, (byte) Math.max(0, Math.min(255, Math.round((data.get(offset) & 255) * value)))); }

    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
