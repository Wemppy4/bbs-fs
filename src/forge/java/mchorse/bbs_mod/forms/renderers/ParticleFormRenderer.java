package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ParticleFormRenderer extends FormRenderer<ParticleForm> implements ITickable
{
    public static long lastUpdate = 0L;

    private ParticleEmitter emitter;
    private boolean checked;
    private String effect;
    private long previewTick = Long.MIN_VALUE;
    private int previewGrace;
    private boolean restart;
    private long lastParticleUpdate = lastUpdate;

    public ParticleFormRenderer(ParticleForm form)
    {
        super(form);
    }

    public ParticleEmitter getEmitter()
    {
        return this.emitter;
    }

    public void ensureEmitter(World world, float transition)
    {
        if (this.lastParticleUpdate < lastUpdate)
        {
            this.lastParticleUpdate = lastUpdate;
            this.checked = false;
        }

        if (!java.util.Objects.equals(this.effect, this.form.effect.get())) this.checked = false;
        if (!this.checked)
        {
            this.effect = this.form.effect.get();
            this.emitter = null;
            ParticleScheme scheme = BBSModClient.getParticles().load(this.form.effect.get());

            if (scheme != null)
            {
                this.emitter = new ParticleEmitter();
                this.emitter.setScheme(scheme);
                this.emitter.setWorld(world);
            }

            this.checked = true;
        }

        if (this.emitter != null && !mchorse.bbs_mod.graphics.OptiFineShaders.isShadowPass())
        {
            boolean lastPaused = this.emitter.paused;

            this.emitter.paused = this.form.paused.get();

            if (lastPaused != this.emitter.paused && !this.emitter.paused && this.emitter.age > 0 && !this.restart)
            {
                this.restart = true;
            }
        }
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEmitter(Minecraft.getMinecraft().world, context.getTransition());

        ParticleEmitter emitter = this.emitter;

        if (emitter != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();
            int scale = (y2 - y1) / 2;

            stack.push();
            stack.translate((x2 + x1) / 2, (y2 + y1) / 2, 40);
            MatrixStackUtils.scaleStack(stack, scale, scale, scale);

            this.updateTexture(context.getTransition());
            emitter.lastGlobal.set(new Vector3f(0, 0, 0));
            emitter.rotation.identity();
            context.batcher.flush();
            try (mchorse.bbs_mod.particles.ParticleRenderPass pass = new mchorse.bbs_mod.particles.ParticleRenderPass(false))
            { emitter.renderUI(stack, context.getTransition()); }

            stack.pop();
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.ensureEmitter(Minecraft.getMinecraft().world, context.transition);

        ParticleEmitter emitter = this.emitter;

        if (emitter != null)
        {
            emitter.setUserVariables(
                this.form.user1.get(),
                this.form.user2.get(),
                this.form.user3.get(),
                this.form.user4.get(),
                this.form.user5.get(),
                this.form.user6.get()
            );

            this.updateTexture(context.getTransition());

            Matrix4f matrix = new Matrix4f(context.camera.view).invert();

            matrix.mul(context.stack.peek().getPositionMatrix());
            RepeatedFormRender repeated = RepeatedFormRender.current();
            Matrix4f displacement = repeated == null ? null : repeated.displacement(this.form, matrix);
            if (repeated != null) matrix.set(repeated.source(this.form, matrix));

            Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));
            translation.add(context.camera.position.x, context.camera.position.y, context.camera.position.z);

            context.stack.push();
            try
            {
                context.stack.loadIdentity();
                context.stack.multiplyPositionMatrix(context.camera.view);
                if (displacement != null) context.stack.multiplyPositionMatrix(displacement);
                emitter.lastGlobal.set(translation);
                emitter.rotation.set(matrix);
                if (!context.isPicking() && !mchorse.bbs_mod.graphics.OptiFineShaders.isShadowPass())
                {
                    if (context.modelRenderer) this.updatePreview(context.modelRendererTick);
                    emitter.setupCameraProperties(context.camera);
                    try (mchorse.bbs_mod.particles.ParticleRenderPass pass = new mchorse.bbs_mod.particles.ParticleRenderPass(!context.modelRenderer))
                    {
                        emitter.render(context.stack, context.overlay, context.getTransition(), true);
                    }
                }
            }
            finally { context.stack.pop(); }
        }
    }

    private void updateTexture(float transition)
    {
        if (this.emitter != null)
        {
            this.emitter.texture = this.form.texture.get();
        }
    }

    private void updatePreview(long tick)
    {
        this.previewGrace = 4;
        long elapsed = this.previewTick == Long.MIN_VALUE ? 1 : tick - this.previewTick;
        this.previewTick = tick;
        if (elapsed < 0 || elapsed > 10) { this.emitter.particles.clear(); elapsed = 1; }
        for (long i=0; i<elapsed; i++) this.emitter.update();
    }

    @Override
    public void tick(IEntity entity)
    {
        if (this.previewGrace > 0) { this.previewGrace--; return; }
        this.ensureEmitter(entity.getWorld(), 0F);

        if (this.emitter != null)
        {
            /* Rewind the emitter if it was paused and resumed in order to make
             * particle effects with once emitter */
            if (this.restart)
            {
                this.emitter.stop();
                this.emitter.start();

                this.restart = false;
            }

            this.emitter.update();
        }
    }
}
