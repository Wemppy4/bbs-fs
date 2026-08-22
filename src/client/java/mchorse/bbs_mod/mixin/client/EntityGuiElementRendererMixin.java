package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.MorphRenderer;
import net.minecraft.client.gui.render.EntityGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mark the span in which an entity is drawn into the GUI (the inventory's player window and anything
 * else using this renderer), so a morph is drawn there and then instead of being queued for a world
 * pass that will not come — see {@link MorphRenderer#setGuiPass}.
 *
 * <p>The span is closed in a {@code finally}-equivalent pair of injections rather than a single
 * wrapper because vanilla's own render must keep running between them: this hook decides nothing, it
 * only says where we are.</p>
 */
@Mixin(EntityGuiElementRenderer.class)
public class EntityGuiElementRendererMixin
{
    @Inject(
        method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At("HEAD")
    )
    private void bbs$guiEntityBegin(EntityGuiElementRenderState state, MatrixStack matrices, CallbackInfo info)
    {
        MorphRenderer.setGuiPass(true);
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At("RETURN")
    )
    private void bbs$guiEntityEnd(EntityGuiElementRenderState state, MatrixStack matrices, CallbackInfo info)
    {
        MorphRenderer.setGuiPass(false);
    }
}
