package mchorse.bbs_mod.particles.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.mixin.client.CameraInvoker;
import mchorse.bbs_mod.mixin.client.ParticleManagerInvoker;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * <p>1.21.11 rewrote particle rendering wholesale: {@code Particle.buildGeometry} is gone and
     * a billboard renders itself into a {@link BillboardParticleSubmittable}, which then submits
     * itself to the frame's command queue as a layered custom. That vanilla tail is useless here —
     * {@code LayeredCustomCommandRenderer} binds the CLIENT framebuffer directly (checked against
     * the bytecode), which would punch the preview onto the screen instead of into the viewport's
     * FBO. So only the head of the path is borrowed: each particle writes its quad into
     * {@link PreviewSubmittable}, which re-routes the vertices straight into a BBS-side
     * {@link RenderLayer} draw — the same immediate idiom every other UI-viewport draw uses.
     *
     * <p>Non-billboard particles (elder-guardian flash, item pickups — the old CUSTOM sheet) are
     * skipped, exactly as they were on 1.21.1.
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

        /* The quads come out camera-relative in world axes (buildGeometry's contract survived the
         * rewrite), so the VIEW transform must ride the global model-view the layer snapshots at
         * draw — exactly the swap the 1.21.1 scene did around its sheets. Dropping it left the
         * particles drawn under whatever matrix the UI had current: nowhere near the viewport. */
        Matrix4f previousModelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        RenderSystem.getModelViewMatrix().set(previewCamera.view);

        rendering = true;

        try
        {
            for (Particle particle : this.particles)
            {
                if (particle instanceof BillboardParticle billboard)
                {
                    try
                    {
                        billboard.render(this.submittable, this.camera, transition);
                    }
                    catch (Exception e)
                    {
                        probeException = e;

                        particle.markDead();
                    }
                }
            }

            this.submittable.draw();
        }
        finally
        {
            rendering = false;

            RenderSystem.getModelViewMatrix().set(previousModelView);
        }

        /* TEMPORARY probe (particles invisible in the editor preview): once a second, the state of
         * the whole chain — how many particles are alive, how many quads the submittable took, how
         * many layer draws went out, and the last exception a particle render threw. Remove with
         * the fix. */
        probeRenders++;

        long now = System.currentTimeMillis();

        if (now - lastProbe > 1000L)
        {
            lastProbe = now;

            org.slf4j.LoggerFactory.getLogger("bbs-particles-probe").info(
                "PROBE scene: renders={} particles={} quads={} draws={} origin={} camPos={} ex={}",
                probeRenders, this.particles.size(), this.submittable.probeQuads, this.submittable.probeDraws,
                this.origin, this.camera.getCameraPos(),
                probeException == null ? "none" : probeException.toString());

            this.submittable.probeQuads = 0;
            this.submittable.probeDraws = 0;
            probeRenders = 0;
            probeException = null;
        }
    }

    /** TEMPORARY probe state, see above. */
    private static long lastProbe;
    private static int probeRenders;
    private static Exception probeException;

    /**
     * The vertex sink for the preview: takes the quads {@link BillboardParticle#render} pushes at
     * it and draws them through BBS render layers instead of the world's layered-custom pass.
     * One layer per {@link BillboardParticle.RenderType}, built from the render type's own
     * pipeline and atlas, so the geometry looks exactly like the world's particles do.
     */
    private static class PreviewSubmittable extends BillboardParticleSubmittable
    {
        private final Map<BillboardParticle.RenderType, RenderLayer> layers = new HashMap<>();
        private final Set<BillboardParticle.RenderType> used = new LinkedHashSet<>();

        /** TEMPORARY probe counters, drained by the scene's once-a-second log. */
        private int probeQuads;
        private int probeDraws;

        @Override
        public void render(BillboardParticle.RenderType renderType, float x, float y, float z, float rotX, float rotY, float rotZ, float rotW, float size, float minU, float maxU, float minV, float maxV, int color, int light)
        {
            VertexConsumer consumer = FormUtilsClient.getProvider().getBuffer(this.layer(renderType));

            this.probeQuads++;
            this.used.add(renderType);
            this.drawFace(consumer, x, y, z, rotX, rotY, rotZ, rotW, size, minU, maxU, minV, maxV, color, light);
        }

        public void draw()
        {
            CustomVertexConsumerProvider provider = FormUtilsClient.getProvider();

            for (BillboardParticle.RenderType type : this.used)
            {
                this.probeDraws++;
                provider.draw(this.layer(type));
            }

            this.used.clear();
        }

        private RenderLayer layer(BillboardParticle.RenderType type)
        {
            return this.layers.computeIfAbsent(type, (key) ->
            {
                /* NOT key.pipeline(): the vanilla particle pipelines CULL, and the preview's
                 * stand-in camera is orbited freely, so quads wind backwards half the time and
                 * the GPU drops them — geometry present, screen empty (the 1.21.1 scene disabled
                 * the global cull for exactly this; probe-verified again on 1.21.11). The BBS
                 * particles pipeline is the migrated clone of the vanilla particle shader with
                 * cull OFF, the same POSITION_TEXTURE_COLOR_LIGHT format and the same
                 * Sampler0/Sampler2 pair — only the atlas differs per render type. */
                RenderSetup.Builder setup = RenderSetup.builder(BBSShaders.getParticlesPipeline())
                    .texture("Sampler0", key.textureAtlasLocation())
                    .useLightmap()
                    .translucent();

                String name = key.textureAtlasLocation().getPath().replace('/', '_') + (key.translucent() ? "_translucent" : "_opaque");

                return RenderLayer.of(BBSMod.MOD_ID + "_preview_particles_" + name, setup.build());
            });
        }
    }

    private final PreviewSubmittable submittable = new PreviewSubmittable();
}
