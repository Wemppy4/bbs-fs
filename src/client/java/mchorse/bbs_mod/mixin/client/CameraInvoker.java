package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link net.minecraft.client.particle.Particle#buildGeometry} takes a vanilla
 * camera and nothing else &mdash; it subtracts {@link Camera#getPos()} from the
 * particle's position and billboards by {@link Camera#getRotation()}. To draw
 * particles inside a UI viewport we hand it a stand-in camera placed where the
 * preview camera stands, which means writing pos/rotation from the outside.
 *
 * <p>An invoker rather than an access widener on purpose: {@link CameraMixin}
 * shadows both methods as {@code protected}, and widening them to public would
 * make that shadow illegal.
 */
@Mixin(Camera.class)
public interface CameraInvoker
{
    @Invoker("setPos")
    public void bbs$setPos(double x, double y, double z);

    @Invoker("setRotation")
    public void bbs$setRotation(float yaw, float pitch);
}
