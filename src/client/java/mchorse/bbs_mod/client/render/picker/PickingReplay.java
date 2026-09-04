package mchorse.bbs_mod.client.render.picker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormRenderCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

/**
 * Picking for the vanilla-rendered forms (item, block, mob).
 *
 * <p>1.21.1 picked them by swapping the GLOBAL shader for the picker program while the vanilla
 * renderer drew — the 1.21.5 pipeline system has no global program to swap, which is why these
 * forms went unpickable in the port. What it does have is {@link FormRenderCapture}: the form's
 * vanilla-layer geometry is captured (fully transformed CPU copies), and this class replays every
 * captured buffer through the {@code picker_models} pipeline into the picking stencil via
 * {@link BBSPickerRenderer} — the same manual pass the cubic/BOBJ models already pick with.
 *
 * <p>The captured layers come in whatever vertex formats the vanilla renderers used (full entity
 * format for item quads, POSITION_TEXTURE for glint, ...), while the picker pipeline wants the
 * entity format. Each vertex is decoded by element usage with defaults for anything the source
 * lacks, so every layer replays: position is what matters, colour is overwritten by the picker
 * anyway, UV0 feeds the alpha cutout and UV2 carries the bone id a mob form wrote there.
 *
 * <p>Replayed one layer at a time, each with the texture that layer draws with: the cutout has to
 * be judged against the same picture the visible pass used, or a mob's UVs read against the block
 * atlas punch holes through its silhouette. A layer without a Sampler0 of its own falls back to
 * the block atlas, where item and block geometry points anyway.
 */
public class PickingReplay
{
    /**
     * Replay captured geometry into the picking target. The picking index must already be
     * recorded (the renderer's {@code setupTarget}); the model-view is the global one — the
     * capture bakes the form's stack into the vertices, same as the cubic picking draw.
     */
    public static void draw(Map<RenderLayer, List<FormRenderCapture.Captured>> captured)
    {
        if (captured == null || captured.isEmpty())
        {
            return;
        }

        for (Map.Entry<RenderLayer, List<FormRenderCapture.Captured>> entry : captured.entrySet())
        {
            RenderSetup.Texture texture = sampler0(entry.getKey());

            BBSPickerRenderer.setSampler0(texture.textureView(), texture.sampler());

            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

            for (FormRenderCapture.Captured single : entry.getValue())
            {
                emit(single, builder);
            }

            BuiltBuffer built = builder.endNullable();

            if (built != null)
            {
                BBSPickerRenderer.draw(BBSShaders.getPickerModelsProgram(), built, RenderSystem.getModelViewMatrix());
            }
        }
    }

    /**
     * What the layer's own Sampler0 is, or the block atlas when it has none — item and block
     * geometry points there, and a layer that samples nothing of its own (glint) only duplicates
     * geometry the base layer has already written.
     */
    private static RenderSetup.Texture sampler0(RenderLayer layer)
    {
        RenderSetup.Texture texture = layer.renderSetup.resolveTextures().get("Sampler0");

        if (texture != null)
        {
            return texture;
        }

        AbstractTexture atlas = MinecraftClient.getInstance().getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

        return new RenderSetup.Texture(atlas.getGlTextureView(), atlas.getSampler());
    }

    /** Re-emit one captured buffer as QUADS, doubling the last vertex of each triangle. */
    private static void emit(FormRenderCapture.Captured captured, VertexConsumer consumer)
    {
        VertexFormat.DrawMode mode = captured.params().mode();
        int count = captured.params().vertexCount();

        if (mode == VertexFormat.DrawMode.QUADS)
        {
            for (int v = 0; v < count; v++)
            {
                emitVertex(captured, v, consumer);
            }
        }
        else if (mode == VertexFormat.DrawMode.TRIANGLES)
        {
            for (int v = 0; v + 2 < count; v += 3)
            {
                emitVertex(captured, v, consumer);
                emitVertex(captured, v + 1, consumer);
                emitVertex(captured, v + 2, consumer);
                emitVertex(captured, v + 2, consumer);
            }
        }
    }

    /**
     * Decode one vertex by element usage and write the FULL entity-format vertex, defaulting
     * whatever the source format lacks (unlike {@code FormRenderCapture.emitVertex}, which writes
     * only the source's elements and would underfill the picker's format).
     */
    private static void emitVertex(FormRenderCapture.Captured captured, int index, VertexConsumer consumer)
    {
        VertexFormat format = captured.params().format();
        int base = index * format.getVertexSize();
        ByteBuffer data = captured.data().duplicate().order(ByteOrder.nativeOrder());

        float x = 0F, y = 0F, z = 0F;
        float u = 0F, v = 0F;
        int overlay = OverlayTexture.DEFAULT_UV;
        int light = 0;

        for (VertexFormatElement element : format.getElements())
        {
            int offset = base + format.getOffset(element);

            switch (element.usage())
            {
                case POSITION ->
                {
                    x = data.getFloat(offset);
                    y = data.getFloat(offset + 4);
                    z = data.getFloat(offset + 8);
                }
                case UV ->
                {
                    if (element.index() == 0)
                    {
                        u = data.getFloat(offset);
                        v = data.getFloat(offset + 4);
                    }
                    else if (element.index() == 2)
                    {
                        /* The light channel, where a mob form's parts wrote their bone ids (see
                         * MobRenderContext.partLight) — dropping it would flatten every bone back
                         * onto the form's own id. */
                        light = Short.toUnsignedInt(data.getShort(offset)) | Short.toUnsignedInt(data.getShort(offset + 2)) << 16;
                    }
                }
                default ->
                {}
            }
        }

        /* Colour is replaced by the picker's Target index in the shader; light.x carries the
         * per-vertex bone sub-index, which stays 0 for everything without a rig. */
        consumer.vertex(x, y, z)
            .color(255, 255, 255, 255)
            .texture(u, v)
            .overlay(overlay)
            .light(light)
            .normal(0F, 1F, 0F);
    }
}
