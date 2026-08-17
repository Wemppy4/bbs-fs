package mchorse.bbs_mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.QueueDispatch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;

import java.util.List;

/**
 * A tiny standalone GUI pass for the two overlays that draw OUTSIDE the vanilla GUI phase:
 * the recording indicator (drawn after the export snapshot so it shows on screen but never
 * lands in the file) and the playback subtitles (drawn at world-render end so a world export
 * captures them).
 *
 * <p>On 1.21.1 both just drew immediately into whatever framebuffer was bound. The 1.21.6
 * two-phase GUI made {@code DrawContext} a recorder: whoever holds the {@link GuiRenderState}
 * must also render it, and these call sites recorded into a throwaway state nobody rendered —
 * the indicator and export subtitles silently vanished. This helper owns a private state and a
 * private {@link GuiRenderer} (over the BBS provider and {@link QueueDispatch}'s dispatcher)
 * and flushes the recording on {@link #end()} — into {@code MinecraftClient.getFramebuffer()},
 * which is exactly right for both callers: during an export snapshot-restore that is the
 * screen framebuffer (operator UI), and at world-render end it is the export framebuffer
 * (subtitles belong in the film).
 */
public class ImmediateGui
{
    private static GuiRenderState state;
    private static GuiRenderer renderer;

    /** Begin recording; draw into the returned context, then call {@link #end()}. */
    public static DrawContext begin()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (renderer == null)
        {
            state = new GuiRenderState();
            renderer = new GuiRenderer(state, FormUtilsClient.getProvider(), QueueDispatch.queue(), QueueDispatch.dispatcher(), List.of());
        }

        return new DrawContext(mc, state, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }

    /** Render everything recorded since {@link #begin()} right now. */
    public static void end()
    {
        renderer.incrementFrame();
        renderer.render(RenderSystem.getShaderFog());
        state.clear();
    }
}
