package mchorse.bbs_mod.ui.particles;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.InverseView;
import mchorse.bbs_mod.particles.ParticleMaterial;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.expiration.ParticleComponentKillPlane;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class UIParticleSchemeRenderer extends UIModelRenderer
{
    public ParticleEmitter emitter;

    /**
     * A 16x16 fully white texture bound to the particle shader's light map slot
     * (Sampler2) when previewing particles in the editor. See {@link #renderUserModel}.
     */
    private static NativeImageBackedTexture whiteLightmapTexture;

    private Vector3f vector = new Vector3f(0, 0, 0);

    private static NativeImageBackedTexture getWhiteLightmapTexture()
    {
        if (whiteLightmapTexture == null)
        {
            whiteLightmapTexture = new NativeImageBackedTexture("bbs_particle_white_lightmap", 16, 16, false);

            NativeImage image = whiteLightmapTexture.getImage();

            for (int y = 0; y < 16; y++)
            {
                for (int x = 0; x < 16; x++)
                {
                    image.setColor(x, y, -1);
                }
            }

            whiteLightmapTexture.upload();
        }

        return whiteLightmapTexture;
    }

    public UIParticleSchemeRenderer()
    {
        super();

        this.emitter = new ParticleEmitter();
    }

    public void setScheme(ParticleScheme scheme)
    {
        this.emitter = new ParticleEmitter();
        this.emitter.setScheme(scheme);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* Debug readout (particle count and emitter age) in the preview's bottom-right corner. */
        if (this.emitter != null && this.emitter.scheme != null)
        {
            String label = this.emitter.particles.size() + "P - " + this.emitter.age + "A";

            context.batcher.textShadow(label, this.area.ex() - 4 - context.batcher.getFont().getWidth(label), this.area.ey() - 12);
        }
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.emitter != null)
        {
            this.emitter.rotation.identity();
            this.emitter.update();
        }
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.emitter == null || this.emitter.scheme == null)
        {
            return;
        }

        this.emitter.setupCameraProperties(this.camera);
        this.emitter.rotation.identity();

        /* TODO(1.21.11 render): verify at runtime. The GUI matrix stack is now 2D
         * (DrawContext.getMatrices() returns a Matrix3x2fStack), so this 3D particle preview can no
         * longer borrow it. Build a fresh 3D MatrixStack seeded with the inverse view; the actual
         * camera/view wiring for in-GUI 3D previews is part of the UIModelRenderer foundation port. */
        MatrixStack stack = new MatrixStack();

        stack.push();
        stack.loadIdentity();
        stack.multiplyPositionMatrix(new Matrix4f(InverseView.get()).invert());

        /* TODO(1.21.11 render): blend/depth state and the active shader are now encoded in the
         * RenderLayer's RenderPipeline; the removed RenderSystem.enableBlend/enableDepthTest/
         * setShaderColor/setShaderTexture calls are gone. The old workaround bound a 16x16 white
         * texture to the light-map sampler (Sampler2) so the particle shader sampled fullbright
         * instead of black; getWhiteLightmapTexture() still builds that texture, but binding it to
         * the picker-particles layer's sampler must be re-wired in the new pipeline. */
        getWhiteLightmapTexture();

        /* 1.21.11 render: this is a NORMAL (non-picking) preview, so it must not use the picker
         * layer - picker_particles now declares the BBSPicker UBO and cannot be drawn through the
         * immediate RenderLayer path. Route through the proper non-picker particle layer (same
         * POSITION_TEXTURE_COLOR_LIGHT format), blending by the scheme's material as in 1.21.1. */
        RenderLayer layer = BBSShaders.getParticlesLayer(this.emitter.scheme.material == ParticleMaterial.BLEND);

        this.emitter.render(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, layer, stack, OverlayTexture.DEFAULT_UV, context.getTransition());

        stack.pop();

        ParticleComponentKillPlane plane = this.emitter.scheme.get(ParticleComponentKillPlane.class);

        if (plane.a != 0 || plane.b != 0 || plane.c != 0)
        {
            this.renderPlane(context, plane.a, plane.b, plane.c, plane.d);
        }
    }

    private void renderPlane(UIContext context, float a, float b, float c, float d)
    {
        /* TODO(1.21.11 render): verify at runtime. This kill-plane gizmo is a 3D POSITION_COLOR draw
         * that previously shared the model viewport's 3D MatrixStack (now a 2D Matrix3x2fStack) and
         * the removed GameRenderer::getPositionColorProgram. The geometry is still built faithfully
         * against the inverse-view matrix, but there is no 3D POSITION_COLOR RenderLayer wired here
         * yet, so the buffer is closed without a GPU submit until the UIModelRenderer 3D foundation
         * provides one. */
        Matrix4f matrix = new Matrix4f(InverseView.get()).invert();
        final float alpha = 0.5F;

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        this.calculate(0, 0, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha);
        this.calculate(0, 1, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha);
        this.calculate(1, 0, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha);

        this.calculate(1, 0, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha);
        this.calculate(0, 1, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha);
        this.calculate(1, 1, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha);

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            built.close();
        }
    }

    private void calculate(float i, float j, float a, float b, float c, float d)
    {
        final float radius = 5;

        if (b != 0)
        {
            this.vector.x = -radius + radius * 2 * i;
            this.vector.z = -radius + radius * 2 * j;
            this.vector.y = (a * this.vector.x + c * this.vector.z + d) / -b;
        }
        else if (a != 0)
        {
            this.vector.y = -radius + radius * 2 * i;
            this.vector.z = -radius + radius * 2 * j;
            this.vector.x = (b * this.vector.y + c * this.vector.z + d) / -a;
        }
        else if (c != 0)
        {
            this.vector.x = -radius + radius * 2 * i;
            this.vector.y = -radius + radius * 2 * j;
            this.vector.z = (b * this.vector.y + a * this.vector.x + d) / -c;
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        super.renderGrid(context);

        if (UIBaseMenu.shouldRenderAxes())
        {
            /* TODO(1.21.11 render): verify at runtime. coolerAxes is a 3D draw and the GUI matrix
             * stack is now 2D (Matrix3x2fStack), so seed a fresh 3D MatrixStack with the inverse
             * view to match the particle preview's space until the UIModelRenderer 3D foundation
             * exposes the viewport's model-view stack directly. */
            MatrixStack stack = new MatrixStack();

            stack.multiplyPositionMatrix(new Matrix4f(InverseView.get()).invert());
            Draw.coolerAxes(stack, 1F, 0.005F);
        }
    }
}