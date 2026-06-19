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
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

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
        /* List/icon preview: submit a special GUI element so the emitter's preview particle draws off-screen
         * during the GUI prepare phase (two-phase GUI drops a direct immediate draw here). BbsFormGuiElementRenderer
         * calls back into renderUIPreview inside the FBO render pass — same path as ModelForm. */
        this.submitUIPreview(context, x1, y1, x2, y2);
    }

    @Override
    public void renderUIPreview(MatrixStack stack, float angle, float transition, int x1, int y1, int x2, int y2)
    {
        this.ensureEmitter(MinecraftClient.getInstance().world, transition);

        ParticleEmitter emitter = this.emitter;

        if (emitter == null)
        {
            return;
        }

        /* The base renderer pre-translated the stack to the cell at (centre, 0.85*height down) + scale(f,f,-f).
         * The original particle preview placed the emitter at the cell CENTRE with scale (y2-y1)/2, so move the
         * origin up from 0.85*height to the centre (0.5*height -> -0.35*(y2-y1) in base units) then apply that
         * scale. Z handedness is irrelevant: emitter.renderUI builds a screen-facing quad at z=0 and the
         * particle pipeline disables culling. */
        stack.push();
        stack.translate(0F, -0.35F * (y2 - y1), 0F);
        float scale = (y2 - y1) / 2F;
        stack.scale(scale, scale, scale);

        this.updateTexture(transition);
        emitter.lastGlobal.set(new Vector3f(0, 0, 0));
        emitter.rotation.identity();
        emitter.renderUI(stack, transition);

        stack.pop();
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

            /* 1.21.5: LightmapTextureManager.enable()/OverlayTexture.setupOverlayColor() were removed
             * along with the imperative GL texture-unit binding. The lightmap and overlay textures are
             * now bound automatically as samplers by the RenderLayer's pipeline (the BBS billboard
             * layer declares useLightmap()/useOverlay()), so there is nothing to enable/teardown here. */

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

                /* 1.21.5: ParticleEmitter.render now takes the target RenderLayer directly instead of a
                 * Supplier<ShaderProgram> (ShaderProgram + GameRenderer.getXxxProgram() were removed).
                 * Faithful to the original getShader(normal, picker) split: normal rendering uses the
                 * actual particle/entity-translucent layer, the picker layer is ONLY for picking (its
                 * shader writes a Target-index colour, not the texture). The no-shaders normal path uses
                 * the new bbs:core/particles layer (the 1.21.1 GameRenderer::getParticleProgram
                 * equivalent); the shaders path borrows the model layer (entity-translucent equivalent).
                 * TODO(1.21.11 render): the picker branch still needs the per-object Target UBO upload
                 * wired (picker_particles pipeline also still needs its std140 migration); picking is a
                 * no-op until then, but normal in-world particles now render. */
                RenderLayer layer;

                if (context.isPicking())
                {
                    layer = shadersEnabled
                        ? BBSShaders.getPickerBillboardLayer()
                        : BBSShaders.getPickerParticlesLayer();
                }
                else
                {
                    layer = shadersEnabled
                        ? BBSShaders.getModelLayer()
                        : BBSShaders.getParticlesLayer();
                }

                emitter.setupCameraProperties(context.camera);
                emitter.render(format, layer, context.stack, context.overlay, context.getTransition());
            }

            context.stack.pop();
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