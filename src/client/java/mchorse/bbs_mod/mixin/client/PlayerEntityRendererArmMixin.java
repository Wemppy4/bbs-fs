package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person arm of a morph: while the player is morphed, the hand they see is the form's own,
 * not their skin's.
 *
 * <p>1.21.1 hooked the single {@code renderArm(matrices, consumers, light, player, arm, sleeve)} and
 * told the hands apart by comparing the {@link net.minecraft.client.model.ModelPart} against the
 * model's right arm. That signature is gone: 1.21.11 renders through a command queue and dropped the
 * player parameter, but it kept the two public entry points {@code renderRightArm}/{@code renderLeftArm},
 * which name the hand themselves — so hooking those needs no model comparison at all. The player comes
 * from the client instead of the arguments, which is exact here: these two methods draw the local
 * player's own arm.
 *
 * <p>The form decides whether it has an arm to show — {@code ModelFormRenderer#renderArm} returns false
 * unless the model defines a first-person slot for that hand — and a false answer leaves the vanilla
 * arm alone.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererArmMixin
{
    @Inject(method = "renderRightArm", at = @At("HEAD"), cancellable = true)
    private void bbs$onRenderRightArm(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Identifier skinTexture, boolean sleeveVisible, CallbackInfo info)
    {
        if (bbs$renderMorphArm(matrices, light, Hand.MAIN_HAND))
        {
            info.cancel();
        }
    }

    @Inject(method = "renderLeftArm", at = @At("HEAD"), cancellable = true)
    private void bbs$onRenderLeftArm(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Identifier skinTexture, boolean sleeveVisible, CallbackInfo info)
    {
        if (bbs$renderMorphArm(matrices, light, Hand.OFF_HAND))
        {
            info.cancel();
        }
    }

    @Unique
    private static boolean bbs$renderMorphArm(MatrixStack matrices, int light, Hand hand)
    {
        AbstractClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return false;
        }

        Morph morph = Morph.getMorph(player);

        if (morph == null)
        {
            return false;
        }

        Form form = morph.getForm();

        if (form == null)
        {
            return false;
        }

        FormRenderer renderer = FormUtilsClient.getRenderer(form);

        if (renderer == null)
        {
            return false;
        }

        /* The arm belongs to the world's frame as much as any other form draw, so it goes inside the
         * world-forms span — and under a shaderpack that is what makes it solid instead of see-through.
         * Outside the span the draw takes the shared pipeline, which carries no program assignment, so
         * the pack leaves it to BBS's own shader and the arm comes out ghosted. Inside it, the draw
         * takes the world variant, which mirrors vanilla's cutout entity pipeline — and Iris resolves
         * that mirror per phase: while the hand renders, its entity entry hands back a HAND program
         * rather than an entity one (IrisPipelines#getCutout, gated on HandRenderer#isActive). So the
         * arm is drawn by the pack's own hand shader, exactly like a vanilla arm. */
        boolean prevWorldForms = BBSRendering.beginWorldForms();

        try
        {
            return renderer.renderArm(matrices, light, player, hand);
        }
        finally
        {
            BBSRendering.endWorldForms(prevWorldForms);
        }
    }
}
