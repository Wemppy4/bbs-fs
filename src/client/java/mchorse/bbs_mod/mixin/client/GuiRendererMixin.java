package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.render.special.BbsFormGuiElementRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Inject BBS's own {@link SpecialGuiElementRenderer} into the otherwise-CLOSED special-element registry.
 * Vanilla freezes the {@code List<SpecialGuiElementRenderer<?>>} constructor argument into an ImmutableMap
 * keyed by {@code getElementClass()} ({@code GuiRenderer.<init>}), with no Fabric hook. We widen the
 * (immutable) {@code List.of(...)} into a mutable copy at HEAD of the constructor and append our renderer,
 * built from the shared {@code VertexConsumerProvider.Immediate} — the same instance vanilla passes to every
 * built-in renderer (GameRenderer uses {@code buffers.getEntityVertexConsumers()}; we fetch the identical
 * object via {@code client.getBufferBuilders().getEntityVertexConsumers()}).
 */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin
{
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<SpecialGuiElementRenderer<?>> bbs$addBbsRenderers(List<SpecialGuiElementRenderer<?>> original)
    {
        VertexConsumerProvider.Immediate immediate =
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        List<SpecialGuiElementRenderer<?>> list = new ArrayList<>(original);

        list.add(new BbsFormGuiElementRenderer(immediate));

        return list;
    }

    /**
     * How much of the framebuffer one GUI unit covers, while BBS's UI is on screen.
     *
     * <p>Vanilla asks the window for an {@code int} here — the two places below are where that rounding
     * would otherwise undo BBS's fractional ui_scale: the projection decides how many GUI units the
     * screen is wide, the scissor decides where a clipped element's edges land in pixels. They have to
     * agree with the scaled size {@code WindowMixin} wrote, or the interface is laid out at one scale
     * and drawn at another.</p>
     *
     * @return the fractional scale, or 0 when BBS is not driving it (leave vanilla alone).
     */
    private static float bbs$scale()
    {
        return BBSModClient.getCustomGUIScale() > 0F ? BBSModClient.getGUIScale() : 0F;
    }

    @Redirect(
        method = "renderPreparedDraws",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ProjectionMatrix2;set(FF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;")
    )
    private GpuBufferSlice bbs$guiProjection(ProjectionMatrix2 matrix, float width, float height)
    {
        float scale = bbs$scale();

        if (scale <= 0F)
        {
            return matrix.set(width, height);
        }

        Window window = MinecraftClient.getInstance().getWindow();

        return matrix.set(window.getFramebufferWidth() / scale, window.getFramebufferHeight() / scale);
    }

    @Inject(method = "enableScissor", at = @At("HEAD"), cancellable = true)
    private void bbs$fractionalScissor(ScreenRect rect, RenderPass pass, CallbackInfo info)
    {
        float scale = bbs$scale();

        if (scale <= 0F)
        {
            return;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        int height = window.getFramebufferHeight();

        /* Vanilla's own arithmetic with a float scale: bottom-left origin, and a rect that rounds down
         * to nothing still clamps to zero rather than going negative. */
        pass.enableScissor(
            (int) (rect.getLeft() * scale),
            (int) (height - rect.getBottom() * scale),
            Math.max(0, (int) (rect.width() * scale)),
            Math.max(0, (int) (rect.height() * scale)));

        info.cancel();
    }
}
