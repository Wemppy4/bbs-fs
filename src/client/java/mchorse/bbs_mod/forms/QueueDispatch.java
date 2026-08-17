package mchorse.bbs_mod.forms;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * BBS's own command queue + flusher for drawing vanilla-rendered things (entities, their held
 * items, name-tag text, fire) OUTSIDE the world's entity pass — in the film's form pass and in
 * UI viewports.
 *
 * <p>The 1.21.2 render-state split removed every immediate entity-drawing API: renderers only
 * <em>submit commands</em> into an {@link OrderedRenderCommandQueue}, and the queue is flushed
 * once per frame by the world's {@link RenderDispatcher}. BBS draws forms at its own moments
 * (AFTER_ENTITIES, editor FBO passes), so it owns a private queue and flushes it synchronously:
 * submit through {@link #queue()}, then {@link #flush()} right after. The dispatcher is built
 * over {@link FormUtilsClient#getProvider()} — the BBS recolor/substitution provider — so every
 * command family renders through the same {@code VertexConsumerProvider} the rest of the form
 * pipeline uses (and the substitution hooks keep working).
 *
 * <p>Layered customs (the new particle path) are deliberately NOT flushed through the vanilla
 * {@code LayeredCustomCommandRenderer}: it binds the client framebuffer directly (checked against
 * the 1.21.11 bytecode), which would punch UI-viewport draws onto the screen. Nothing BBS submits
 * uses them; the world's own particles go through the world's dispatcher as always.
 *
 * <p>Render thread only, non-reentrant by design (a flush must complete before the next submit
 * cycle; nested form-in-form draws all land in the same cycle before the single flush).
 */
public class QueueDispatch
{
    private static RenderDispatcher dispatcher;

    private static RenderDispatcher get()
    {
        if (dispatcher == null)
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            dispatcher = new RenderDispatcher(
                new net.minecraft.client.render.command.OrderedRenderCommandQueueImpl(),
                mc.getBlockRenderManager(),
                FormUtilsClient.getProvider(),
                mc.getAtlasManager(),
                mc.getBufferBuilders().getOutlineVertexConsumers(),
                mc.getBufferBuilders().getEffectVertexConsumers(),
                mc.textRenderer
            );
        }

        return dispatcher;
    }

    public static OrderedRenderCommandQueue queue()
    {
        return get().getQueue();
    }

    /**
     * Render everything submitted since the last flush through the BBS provider. The caller still
     * owns the provider's {@code draw()} (this only replays commands into it), matching how the
     * other form renderers batch-then-draw.
     */
    public static void flush()
    {
        get().render();
    }

    /**
     * A camera render state for the active vanilla camera — fire billboards read its orientation.
     */
    public static CameraRenderState cameraState()
    {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        CameraRenderState state = new CameraRenderState();
        Vec3d pos = camera.getCameraPos();

        state.initialized = true;
        state.pos = pos;
        state.entityPos = pos;
        state.blockPos = camera.getBlockPos();
        state.orientation = new Quaternionf(camera.getRotation());

        return state;
    }
}
