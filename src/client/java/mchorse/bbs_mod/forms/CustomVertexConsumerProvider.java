package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class CustomVertexConsumerProvider extends VertexConsumerProvider.Immediate
{
    private static Consumer<RenderLayer> runnables;

    private Function<VertexConsumer, VertexConsumer> substitute;
    private Color overlay;
    private boolean ui;

    public static void drawLayer(RenderLayer layer)
    {
        if (runnables != null)
        {
            runnables.accept(layer);
        }
    }

    public static void hijackVertexFormat(Consumer<RenderLayer> runnable)
    {
        runnables = runnable;
    }

    public static void clearRunnables()
    {
        runnables = null;
    }

    public CustomVertexConsumerProvider(BufferBuilder fallback, Map<RenderLayer, BufferBuilder> layers)
    {
        super(fallback, layers);
    }

    public void setSubstitute(Function<VertexConsumer, VertexConsumer> substitute)
    {
        this.substitute = substitute;

        if (this.substitute == null)
        {
            RecolorVertexConsumer.newColor = null;
        }
    }

    /**
     * The color overlay a deferred layer has to be drawn with. A layer that defers is drawn from
     * the translucent queue long after the renderer's per-layer hook unbound the overlay texture,
     * so the color travels with the command instead. Null while nothing sets one.
     */
    public void setOverlay(Color overlay)
    {
        this.overlay = overlay;
    }

    public void setUI(boolean ui)
    {
        this.ui = ui;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer renderLayer)
    {
        VertexConsumer buffer = super.getBuffer(renderLayer);

        if (this.substitute != null)
        {
            VertexConsumer apply = this.substitute.apply(buffer);

            if (apply != null)
            {
                return apply;
            }
        }

        return buffer;
    }

    /**
     * Translucent layers of buffered forms (blocks, items) defer into the frame's sorted
     * translucent queue instead of drawing immediately — otherwise their semi-transparent
     * pixels write depth mid-frame and occlude forms drawn after them. Active only when the
     * current form renderer published its sort origin (never in picking or UI paths).
     */
    @Override
    public void draw(RenderLayer layer)
    {
        Vector3f origin = FormTranslucentQueue.getSortOrigin();

        /* Text layers defer only inside a recorded group (labels), where the group preserves
         * the text-over-background order. */
        boolean textLayer = FormTranslucentQueue.isGroupOpen() && layer.getVertexFormat() == VertexFormats.POSITION_COLOR_TEXTURE_LIGHT;

        if (origin == null || !FormTranslucentQueue.isActive() || !(textLayer || isDeferrableTranslucent(layer)))
        {
            super.draw(layer);

            return;
        }

        BufferBuilder builder = this.layerBuffers.getOrDefault(layer, this.fallbackBuffer);
        boolean current = Objects.equals(this.currentLayer, layer.asOptional());

        if (!current && builder == this.fallbackBuffer)
        {
            return;
        }

        if (!this.activeConsumers.remove(builder))
        {
            return;
        }

        if (builder.isBuilding())
        {
            /* Ending and uploading the layer's buffer here is what the immediate provider's own
             * draw would have done — including the vertex layout Iris pins around it. */
            boolean extended = BBSRendering.beginIrisBufferUpload(builder);
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            try
            {
                buffer.bind();
                buffer.upload(builder.end());
                VertexBuffer.unbind();
            }
            finally
            {
                BBSRendering.endIrisBufferUpload(extended);
            }

            FormTranslucentQueue.add(new FormTranslucentQueue.RenderLayerCommand(layer, buffer, new Matrix4f(RenderSystem.getModelViewMatrix()), new Vector3f(origin)).overlayColor(this.overlay));
        }

        if (current)
        {
            this.currentLayer = java.util.Optional.empty();
        }
    }

    private static boolean isDeferrableTranslucent(RenderLayer layer)
    {
        String name = layer.toString();

        return name.contains("translucent") && !name.contains("glint");
    }

    public void draw()
    {
        super.draw();

        if (this.ui)
        {
            /* Force back the depth func because it seems like stuff rendered by a vertex
             * consumer is resetting the depth func to GL_LESS, and since this vertex consumer
             * is designed  */
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }
}