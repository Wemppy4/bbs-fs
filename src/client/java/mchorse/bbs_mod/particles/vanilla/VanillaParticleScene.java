package mchorse.bbs_mod.particles.vanilla;

import mchorse.bbs_mod.mixin.client.CameraInvoker;
import mchorse.bbs_mod.mixin.client.ParticleManagerInvoker;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A pocket of vanilla particles that lives inside a UI viewport.
 *
 * <p>Vanilla's own {@link net.minecraft.client.particle.ParticleManager} cannot
 * be used for this: everything handed to it is drawn by the world pass, so a
 * preview would spray particles into the game behind the interface instead of
 * into the panel. What this class borrows is only the factory &mdash; the
 * particle is created through {@link ParticleManagerInvoker}, then owned,
 * ticked and drawn here.
 *
 * <p>A particle still reads the world for collisions and physics, so the scene
 * is anchored above the build limit ({@link #ORIGIN_HEIGHT}), where the chunk is
 * loaded but no block can get in the way. Light is forced to full by
 * {@code ParticleMixin} while {@link #render} runs, matching the rest of the
 * preview.
 */
public class VanillaParticleScene
{
    /** How far above the world's ceiling the scene is parked. */
    private static final int ORIGIN_HEIGHT = 32;

    /** A runaway emitter (count 100 at frequency 1) must not eat the client. */
    private static final int MAX_PARTICLES = 4096;

    private static boolean rendering;

    private final List<Particle> particles = new ArrayList<>();
    private final Camera camera = new Camera();
    private final Vector3d origin = new Vector3d();

    /**
     * Whether a preview scene is drawing right now &mdash; the one thing the
     * particles themselves need to know, since their light comes from a world
     * they are only nominally in.
     */
    public static boolean isRendering()
    {
        return rendering;
    }

    public void clear()
    {
        this.particles.clear();
    }

    /**
     * Spawn a particle at a point given in the preview's own space.
     */
    public void spawn(ParticleEffect effect, double x, double y, double z, double velocityX, double velocityY, double velocityZ)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        if (world == null || this.particles.size() >= MAX_PARTICLES)
        {
            return;
        }

        /* Re-anchoring while particles are alive would leave them behind the
         * camera, so the origin only follows the player between bursts */
        if (this.particles.isEmpty())
        {
            this.updateOrigin(mc, world);
        }

        Particle particle = ((ParticleManagerInvoker) mc.particleManager).bbs$createParticle(effect,
            this.origin.x + x, this.origin.y + y, this.origin.z + z,
            velocityX, velocityY, velocityZ
        );

        if (particle != null)
        {
            this.particles.add(particle);
        }
    }

    private void updateOrigin(MinecraftClient mc, ClientWorld world)
    {
        Entity anchor = mc.getCameraEntity();
        double x = anchor == null ? 0D : anchor.getX();
        double z = anchor == null ? 0D : anchor.getZ();

        /* Above the ceiling there are no blocks to collide with, while the
         * chunk underneath is still loaded, so the particles tick normally.
         * 1.21.9: World.getTopY() (exclusive ceiling) -> HeightLimitView.getTopYInclusive(). */
        this.origin.set(x, world.getTopYInclusive() + 1 + ORIGIN_HEIGHT, z);
    }

    public void tick()
    {
        Iterator<Particle> it = this.particles.iterator();

        while (it.hasNext())
        {
            Particle particle = it.next();

            try
            {
                particle.tick();
            }
            catch (Exception e)
            {
                /* A particle that throws is dropped rather than taken to the
                 * whole editor. The effect is visible (it disappears), so this
                 * is not a silent failure */
                particle.markDead();
            }

            if (!particle.isAlive())
            {
                it.remove();
            }
        }
    }

    /**
     * Draw the scene through the preview's camera.
     *
     * <p>Only the stand-in camera is placed here for now &mdash; see the TODO
     * below for why nothing is drawn on this branch.
     */
    public void render(mchorse.bbs_mod.camera.Camera previewCamera, float transition)
    {
        if (this.particles.isEmpty())
        {
            return;
        }

        CameraInvoker standIn = (CameraInvoker) this.camera;

        standIn.bbs$setPos(
            this.origin.x + previewCamera.position.x,
            this.origin.y + previewCamera.position.y,
            this.origin.z + previewCamera.position.z
        );
        standIn.bbs$setRotation(MathUtils.toDeg(previewCamera.rotation.y), -MathUtils.toDeg(previewCamera.rotation.x));

        /* TODO(1.21.11 render): the preview's particle pass is not drawn.
         *
         * On 1.21.1 this built the geometry by hand: Particle#buildGeometry(BufferBuilder, Camera,
         * transition) per ParticleTextureSheet, with RenderSystem.setShader(getParticleProgram) +
         * setShaderTexture + BufferRenderer.drawWithGlobalProgram, the global model-view swapped to
         * the preview's view matrix, and the cull state saved through GlStateManager.CULL (a
         * stand-in camera the user orbits freely winds billboards either way).
         *
         * 1.21.11 rewrote particle rendering wholesale: buildGeometry is gone, the sheets are
         * SINGLE_QUADS/ITEM_PICKUP/ELDER_GUARDIANS/NO_RENDER, and drawing goes
         * BillboardParticle#render(BillboardParticleSubmittable, Camera, tickDelta) ->
         * Submittable#submit(OrderedRenderCommandQueue, CameraRenderState). A UI viewport has no
         * command queue to submit into on this branch — the same missing foundation that keeps
         * MobFormRenderer stubbed — so the preview is deferred until that lands.
         *
         * Everything else about the scene is live: particles are created, owned and ticked here,
         * so wiring the draw is all that is left. Emission into the WORLD is unaffected. */
        rendering = true;

        try
        {
            /* The draw goes here */
        }
        finally
        {
            rendering = false;
        }
    }
}
