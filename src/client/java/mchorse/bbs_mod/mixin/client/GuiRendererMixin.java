package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.render.special.BbsFormGuiElementRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
}
