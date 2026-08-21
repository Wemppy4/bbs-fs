package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.client.PixelArt;
import net.minecraft.client.gui.render.state.GlyphGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where a glyph of BBS's interface gets the seam-smoothing shader instead of vanilla's.
 *
 * <p>This is the only seam left on the two-phase GUI. The 1.21.1 hook — handing a program to
 * {@code GameRenderer.getRenderTypeText*Program} — has no counterpart: a glyph's pipeline is asked for
 * once, here, when the recorded GUI state is composited. The state itself is built by vanilla during
 * that composite ({@code GuiRenderer}), so there is nothing earlier to mark, which is why
 * {@link PixelArt#getTextPipeline} judges by the open screen rather than by a flag.</p>
 */
@Mixin(GlyphGuiElementRenderState.class)
public class GlyphGuiElementRenderStateMixin
{
    @Inject(method = "pipeline", at = @At("RETURN"), cancellable = true)
    private void bbs$pixelArtText(CallbackInfoReturnable<RenderPipeline> info)
    {
        RenderPipeline pipeline = PixelArt.getTextPipeline(info.getReturnValue());

        if (pipeline != null)
        {
            info.setReturnValue(pipeline);
        }
    }
}
