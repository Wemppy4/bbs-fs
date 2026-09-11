package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
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

        if (!this.checked)
        {
            ParticleScheme scheme = BBSModClient.getParticles().load(this.form.effect.get());

            if (scheme != null)
            {
                this.emitter = new ParticleEmitter();
                this.emitter.setScheme(scheme);
                this.emitter.setWorld(world);
            }

            this.checked = true;
        }

        if (this.emitter != null && !BBSRendering.isIrisShadowPass())
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
        this.ensureEmitter(MinecraftClient.getInstance().world, context.getTransition());

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
            emitter.renderUI(stack, context.getTransition());

            stack.pop();
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.ensureEmitter(MinecraftClient.getInstance().world, context.transition);

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

            Matrix4f matrix = new Matrix4f(InverseView.get());

            /* Since 1.21.1 the world render keeps the camera view in RenderSystem's global
             * model-view and gives the stack an identity base, so stack.peek() no longer
             * carries the view rotation InverseView is meant to cancel. Fold the global
             * model-view in (identity, hence a no-op, in the form editor) so the emitter's
             * world origin and rotation come out right. */
            matrix.mul(RenderSystem.getModelViewMatrix());
            matrix.mul(context.stack.peek().getPositionMatrix());

            Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));
            translation.add(context.camera.position.x, context.camera.position.y, context.camera.position.z);

            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

            gameRenderer.getLightmapTextureManager().enable();
            gameRenderer.getOverlayTexture().setupOverlayColor();

            context.stack.push();
            context.stack.loadIdentity();
            /* The emitter builds its quads in camera-relative world space, so the effective
             * model-view (RenderSystem.getModelViewMatrix() * stack) must be the pure camera
             * view. Since 1.21.1 holds that view in RenderSystem's global model-view (it used
             * to be identity), cancel it here so it isn't applied twice: stack = inv(global) * view.
             * In the form editor the global model-view is identity, so this stays the old `view`. */
            Matrix4f particleView = new Matrix4f(InverseView.get()).invert();
            context.stack.multiplyPositionMatrix(new Matrix4f(RenderSystem.getModelViewMatrix()).invert().mul(particleView));

            emitter.lastGlobal.set(translation);
            emitter.rotation.set(matrix);

            if (!BBSRendering.isIrisShadowPass())
            {
                boolean shadersEnabled = BBSRendering.isIrisShadersEnabled();

                VertexFormat format = shadersEnabled ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR_LIGHT;
                Supplier<ShaderProgram> shader = shadersEnabled
                    ? this.getShader(context, GameRenderer::getRenderTypeEntityTranslucentProgram, BBSShaders::getPickerBillboardProgram)
                    : this.getShader(context, GameRenderer::getParticleProgram, BBSShaders::getPickerParticlesProgram);

                emitter.setupCameraProperties(context.camera);
                emitter.render(format, shader, context.stack, context.overlay, context.getTransition(), !context.isPicking());
            }

            context.stack.pop();

            gameRenderer.getLightmapTextureManager().disable();
            gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
    }

    private void updateTexture(float transition)
    {
        if (this.emitter != null)
        {
            this.emitter.texture = this.form.texture.get();
        }
    }

    @Override
    public void tick(IEntity entity)
    {
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