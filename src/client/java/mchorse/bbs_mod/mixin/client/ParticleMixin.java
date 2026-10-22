package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.particles.vanilla.HeldParticles;
import mchorse.bbs_mod.particles.vanilla.IHeldParticle;
import mchorse.bbs_mod.particles.vanilla.VanillaParticleScene;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A particle reads its light out of the world it lives in. The editor's preview
 * scene is parked above the build limit so nothing collides with it, and in the
 * Nether or the End there is no sky light up there &mdash; the particles would
 * come out black. Everything else in the preview is drawn at full light
 * anyway (see UIFormRenderer), so particles match it while the scene is drawing.
 *
 * <p>Every particle also carries the form that let it into the world, if any
 * (see {@link HeldParticles}).
 */
@Mixin(Particle.class)
public class ParticleMixin implements IHeldParticle
{
    @Unique
    private HeldParticles bbs$holder;

    @Override
    public HeldParticles bbs$getHolder()
    {
        return this.bbs$holder;
    }

    @Override
    public void bbs$setHolder(HeldParticles holder)
    {
        this.bbs$holder = holder;
    }

    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private void bbs$fullBrightInPreview(float tint, CallbackInfoReturnable<Integer> cir)
    {
        if (VanillaParticleScene.isRendering())
        {
            cir.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }
}
