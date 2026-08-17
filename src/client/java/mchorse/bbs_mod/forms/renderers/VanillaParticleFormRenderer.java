package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.particles.vanilla.VanillaParticleScene;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.command.argument.ParticleEffectArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class VanillaParticleFormRenderer extends FormRenderer<VanillaParticleForm> implements ITickable
{
    public static final Link PARTICLE_PREVIEW = new Link("minecraft", "textures/particle/flame.png");

    /**
     * How many ticks a frame keeps counting as "drawn just now". Rendering runs
     * per frame and ticking at 20 per second, so a single tick must never fall
     * between two frames. Read twice: an editor frame suppresses the world's
     * emission for this long, and a world frame keeps {@link #updateFromEntity}
     * out of the way for the same span.
     */
    private static final int RENDER_GRACE_TICKS = 4;

    /**
     * Cap on ticks replayed in one frame, so a stall does not dump a whole
     * backlog of bursts at once. Same bound {@link mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer}
     * puts on its own catch-up.
     */
    private static final int MAX_CATCHUP = 10;

    private Vector3d pos = new Vector3d();
    private Vector3f vel = new Vector3f();
    private Matrix3f rot = new Matrix3f();
    private int tick;

    /**
     * Particles for the editor's 3D preview. They are owned here rather than
     * handed to the world's particle manager, which would draw them in the game
     * behind the panel instead of inside it. Only ever non-null while the form
     * editor is drawing this form &mdash; see {@link #RENDER_GRACE_TICKS}.
     */
    private VanillaParticleScene scene;
    private int previewTicks;
    private int worldRenderTicks;
    private long lastPreviewTick = Long.MIN_VALUE;

    public VanillaParticleFormRenderer(VanillaParticleForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(PARTICLE_PREVIEW);

        float min = Math.min(texture.width, texture.height);
        int ow = (x2 - x1) - 4;
        int oh = (y2 - y1) - 4;

        int w = (int) ((texture.width / min) * ow);
        int h = (int) ((texture.height / min) * ow);

        int x = x1 + (ow - w) / 2 + 2;
        int y = y1 + (oh - h) / 2 + 2;

        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        Matrix4f matrix = new Matrix4f(InverseView.get());

        /* Since 1.21.1 the world render keeps the camera view in RenderSystem's global
         * model-view and gives the stack an identity base, so stack.peek() no longer
         * carries the view rotation that InverseView is meant to cancel. Fold the global
         * model-view back in (identity, hence a no-op, in the form editor where the camera
         * lives in the stack) so the emitter's world position and direction come out right. */
        matrix.mul(RenderSystem.getModelViewMatrix());
        matrix.mul(context.stack.peek().getPositionMatrix());

        Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));

        /* The matrix above is camera-relative, so adding the camera back gives
         * the emitter's place in whichever space is being drawn: the world for
         * the game, the preview's own scene for the editor */
        if (context.modelRenderer)
        {
            translation.add(context.camera.position);
        }
        else
        {
            Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

            translation.add(camera.getCameraPos().x, camera.getCameraPos().y, camera.getCameraPos().z);

            this.worldRenderTicks = RENDER_GRACE_TICKS;
        }

        this.pos.set(translation);
        this.vel.set(0F, 0F, 1F);
        this.rot.set(matrix).transform(this.vel);

        /* The editor draws the form a second time to pick bones out of a
         * stencil buffer. Particles have no business in that pass: they are not
         * pickable, and their shader would trample the picking one */
        if (context.modelRenderer && !context.isPicking())
        {
            this.previewTicks = RENDER_GRACE_TICKS;

            this.updatePreview(context.modelRendererTick);
            this.getScene().render(context.camera, context.getTransition());
        }
    }

    private VanillaParticleScene getScene()
    {
        if (this.scene == null)
        {
            this.scene = new VanillaParticleScene();
        }

        return this.scene;
    }

    /**
     * Run the preview scene off the viewport's clock rather than off
     * {@link #tick(IEntity)}, which the plain form editor never calls: ticking
     * a form there is opt-in and nobody opts in. See
     * {@link FormRenderingContext#modelRenderer(long)}.
     */
    private void updatePreview(long tick)
    {
        VanillaParticleScene scene = this.getScene();

        if (this.lastPreviewTick == Long.MIN_VALUE)
        {
            this.lastPreviewTick = tick;
        }

        long elapsed = tick - this.lastPreviewTick;

        this.lastPreviewTick = tick;

        if (elapsed > MAX_CATCHUP)
        {
            /* Away for a while (tab switched, editor hidden): whatever was in
             * flight is stale, and replaying the gap would dump it in one go */
            scene.clear();

            elapsed = 1;
        }

        for (long i = 0; i < elapsed; i++)
        {
            /* Particles carry on while emission is paused, exactly like the
             * ones already in flight in the world do */
            scene.tick();
            this.emitTick(scene::spawn);
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        if (this.previewTicks > 0)
        {
            /* An editor viewport drew this form just now, so its own clock is
             * running the burst. The world must not get a second copy of it */
            this.previewTicks -= 1;

            return;
        }

        if (this.scene != null)
        {
            this.scene = null;
            this.lastPreviewTick = Long.MIN_VALUE;
        }

        World world = entity.getWorld();

        if (world == null)
        {
            return;
        }

        if (this.worldRenderTicks > 0)
        {
            this.worldRenderTicks -= 1;
        }
        else
        {
            this.updateFromEntity(entity);
        }

        /* 1.21.x: World.addParticle(effect, alwaysSpawn, x..., v...) -> addParticleClient
         * which split the old alwaysSpawn flag into (alwaysSpawn, important/force). Keep
         * alwaysSpawn=true and force=false to match the prior 8-arg behaviour. */
        this.emitTick((effect, x, y, z, velocityX, velocityY, velocityZ) ->
            world.addParticleClient(effect, true, false, x, y, z, velocityX, velocityY, velocityZ));
    }

    /**
     * Place the emitter from the entity when nobody draws the form.
     *
     * <p>The position normally falls out of the render matrix, which only
     * exists while something is being drawn. In first person the player's own
     * model is never drawn, so that matrix stops arriving and the emitter would
     * stay pinned wherever the form was last seen &mdash; particles stop
     * following you. Rebuilding the placement from the entity keeps them with
     * the player. A form nested on a bone lands on its parent's spot rather
     * than the bone's, which is still far better than standing still.
     */
    private void updateFromEntity(IEntity entity)
    {
        Matrix4f matrix = new Matrix4f().rotateY(MathUtils.toRad(-entity.getBodyYaw()));

        matrix.mul(this.createTransform().createMatrix());

        Vector3f translation = matrix.getTranslation(new Vector3f());

        this.pos.set(entity.getX() + translation.x, entity.getY() + translation.y, entity.getZ() + translation.z);
        this.rot.set(matrix);
        this.vel.set(0F, 0F, 1F);
        this.rot.transform(this.vel);
    }

    /**
     * One tick of the emitter's own timing: a burst every {@code frequency} ticks.
     */
    private void emitTick(ParticleSink sink)
    {
        if (this.form.paused.get())
        {
            return;
        }

        if (this.tick <= 0)
        {
            this.emit(sink);

            this.tick = this.form.frequency.get();
        }

        this.tick -= 1;
    }

    /**
     * Scatter one burst of particles, handing each one to whoever keeps them
     * &mdash; the world, or the editor's preview scene.
     */
    private void emit(ParticleSink sink)
    {
        Matrix3f m = Matrices.TEMP_3F;
        Vector3f v = Vectors.TEMP_3F;
        Vector3f temp3f = new Vector3f();

        ParticleEffect effect = this.getEffect();
        float velocity = this.form.velocity.get();
        int count = this.form.count.get();

        for (int i = 0; i < count; i++)
        {
            float velocityX = this.vel.x * velocity;
            float velocityY = this.vel.y * velocity;
            float velocityZ = this.vel.z * velocity;
            float sh = MathUtils.toRad(this.form.scatteringYaw.get()) * (float) (Math.random() - 0.5D);
            float sv = MathUtils.toRad(this.form.scatteringPitch.get()) * (float) (Math.random() - 0.5D);

            m.identity()
                .rotateY(sh)
                .rotateX(sv)
                .transform(v.set(velocityX, velocityY, velocityZ));

            temp3f.set(
                (Math.random() * 2F - 1F) * this.form.offsetX.get(),
                (Math.random() * 2F - 1F) * this.form.offsetY.get(),
                (Math.random() * 2F - 1F) * this.form.offsetZ.get()
            );

            if (this.form.local.get())
            {
                this.rot.transform(temp3f);
            }

            double x = this.pos.x + temp3f.x;
            double y = this.pos.y + temp3f.y;
            double z = this.pos.z + temp3f.z;

            sink.spawn(effect, x, y, z, v.x, v.y, v.z);
        }
    }

    private ParticleEffect getEffect()
    {
        /* The registry manager the parser needs lives on the world. Nobody hands
         * one down here — the editor's preview emits without an entity — and on
         * the client there is only ever the one world, so take it from there */
        return createEffect(this.form.settings.get(), MinecraftClient.getInstance().world);
    }

    /**
     * Builds the particle effect from the form's settings. For the two common parametric
     * particles the arguments string is just a bare id for convenience — {@code dirt} for
     * {@code minecraft:block}, {@code apple} for {@code minecraft:item} — and the rest of the
     * structure is filled in here. Otherwise (or when a {@code {...}} compound is given) it is
     * parsed exactly like the vanilla {@code /particle} command, so every particle type still
     * works. Falls back to flame when nothing parses.
     */
    private static ParticleEffect createEffect(ParticleSettings settings, World world)
    {
        String args = settings.arguments == null ? "" : settings.arguments.trim();

        try
        {
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(settings.particle);
            boolean bareId = !args.isEmpty() && args.charAt(0) != '{';

            if (bareId && type == ParticleTypes.BLOCK)
            {
                return new BlockStateParticleEffect(ParticleTypes.BLOCK, Registries.BLOCK.get(Identifier.of(args)).getDefaultState());
            }

            if (bareId && type == ParticleTypes.ITEM)
            {
                return new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Registries.ITEM.get(Identifier.of(args))));
            }

            StringReader reader = new StringReader(settings.particle.toString() + args);

            return ParticleEffectArgumentType.readParameters(reader, world.getRegistryManager());
        }
        catch (Exception e)
        {
            return ParticleTypes.FLAME;
        }
    }

    private interface ParticleSink
    {
        public void spawn(ParticleEffect effect, double x, double y, double z, double velocityX, double velocityY, double velocityZ);
    }
}
