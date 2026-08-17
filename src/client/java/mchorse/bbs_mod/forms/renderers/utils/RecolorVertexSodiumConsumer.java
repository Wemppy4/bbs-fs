package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexConsumer;
import org.lwjgl.system.MemoryStack;

/**
 * The Sodium-aware recolor wrapper: Sodium's intrinsic vertex writers bypass the vanilla
 * {@link VertexConsumer} chain entirely (they push whole vertex blocks through
 * {@link VertexBufferWriter}), so a plain wrapper silently drops every intrinsic write.
 * Implementing the interface and forwarding keeps the geometry flowing; the recolor itself
 * still applies on the per-vertex path.
 */
public class RecolorVertexSodiumConsumer extends RecolorVertexConsumer implements VertexBufferWriter
{
    public RecolorVertexSodiumConsumer(VertexConsumer consumer, Color color)
    {
        super(consumer, color);
    }

    @Override
    public void push(MemoryStack memoryStack, long l, int i, VertexFormat vertexFormat)
    {
        if (this.consumer instanceof VertexBufferWriter writer)
        {
            writer.push(memoryStack, l, i, vertexFormat);
        }
    }
}
