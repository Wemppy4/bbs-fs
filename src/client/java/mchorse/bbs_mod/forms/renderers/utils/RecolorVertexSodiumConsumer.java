package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.render.VertexConsumer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The Sodium-aware recolor wrapper: Sodium's intrinsic vertex writers bypass the vanilla
 * {@link VertexConsumer} chain entirely (they push whole vertex blocks through
 * {@link VertexBufferWriter}), so a plain wrapper silently drops every intrinsic write.
 *
 * <p>Forwarding alone is not enough — the tint has to be applied to the block on the way through,
 * because the per-vertex {@code color} calls it would otherwise ride on are exactly what the
 * intrinsic path skips. On 1.21.1 that was done from the far end, by mixing into Sodium's
 * {@code ColorAttribute#set}; the block is patched here instead, which needs no mixin and so does
 * not care which Sodium version is installed (only the public writer API and the vertex format's
 * own element offsets are used).</p>
 */
public class RecolorVertexSodiumConsumer extends RecolorVertexConsumer implements VertexBufferWriter
{
    public RecolorVertexSodiumConsumer(VertexConsumer consumer, Color color)
    {
        super(consumer, color);
    }

    @Override
    public void push(MemoryStack memoryStack, long pointer, int count, VertexFormat vertexFormat)
    {
        if (!(this.consumer instanceof VertexBufferWriter writer))
        {
            return;
        }

        int offset = vertexFormat.getOffset(VertexFormatElement.COLOR);

        /* A form with no colour set is the common case and multiplies by one: hand the caller's own
         * block straight on rather than copying every vertex to change nothing. */
        if (offset < 0 || isNeutral(this.color))
        {
            writer.push(memoryStack, pointer, count, vertexFormat);

            return;
        }

        int stride = vertexFormat.getVertexSize();

        /* The pushed block belongs to the caller and may be handed on to other writers, so the
         * tint goes onto a copy taken on a frame of the caller's own stack. */
        try (MemoryStack stack = memoryStack.push())
        {
            long copy = stack.nmalloc(Integer.BYTES, count * stride);

            MemoryUtil.memCopy(pointer, copy, (long) count * stride);

            for (int i = 0; i < count; i++)
            {
                long at = copy + (long) i * stride + offset;

                MemoryUtil.memPutInt(at, tintPackedABGR(MemoryUtil.memGetInt(at), this.color));
            }

            writer.push(stack, copy, count, vertexFormat);
        }
    }

    private static boolean isNeutral(Color color)
    {
        return color == null || (color.r == 1F && color.g == 1F && color.b == 1F && color.a == 1F);
    }
}
