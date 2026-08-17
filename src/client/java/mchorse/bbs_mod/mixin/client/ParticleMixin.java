package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.particles.vanilla.VanillaParticleScene;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A particle reads its light out of the world it lives in. The editor's preview
 * scene is parked above the build limit so nothing collides with it, and in the
 * Nether or the End there is no sky light up there &mdash; the particles would
 * come out black. Everything else in the preview is drawn at full light
 * anyway (see UIFormRenderer), so particles match it while the scene is drawing.
 */
@Mixin(Particle.class)
public class ParticleMixin
{
    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private void bbs$fullBrightInPreview(float tint, CallbackInfoReturnable<Integer> cir)
    {
        if (VanillaParticleScene.isRendering())
        {
            cir.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    /** TEMPORARY probe clock (black world particles), see below. Remove with the fix. */
    private static long bbs$lastDarkProbe;

    /**
     * TEMPORARY probe: black quads in the WORLD at particle spawn/despawn. If those are world
     * particles sampling light INSIDE a solid block (the emitter spawning them a block too low
     * would do it), this logs them the moment their brightness comes out black. Remove with the fix.
     */
    @Inject(method = "getBrightness", at = @At("RETURN"))
    private void bbs$probeDarkParticle(float tint, CallbackInfoReturnable<Integer> cir)
    {
        if (VanillaParticleScene.isRendering() || cir.getReturnValue() != 0)
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - bbs$lastDarkProbe > 1000L)
        {
            bbs$lastDarkProbe = now;

            Particle self = (Particle) (Object) this;

            org.slf4j.LoggerFactory.getLogger("bbs-particles-probe").info(
                "PROBE dark world particle: class={} box={}",
                self.getClass().getSimpleName(), self.getBoundingBox());
        }
    }
}
