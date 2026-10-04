package mchorse.bbs_mod.ui.particles;

import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.expiration.ParticleComponentKillPlane;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import org.lwjgl.opengl.GL11;
import org.joml.Vector3f;

public class UIParticleSchemeRenderer extends UIModelRenderer
{
    public ParticleEmitter emitter;

    private Vector3f vector = new Vector3f();

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

        MatrixStack stack = context.batcher.getContext().getMatrices();

        stack.push();
        stack.loadIdentity();
        stack.multiplyPositionMatrix(this.camera.view);

        GlStateManager.enableDepth();
        try (mchorse.bbs_mod.particles.ParticleRenderPass pass = new mchorse.bbs_mod.particles.ParticleRenderPass(false))
        {
            this.emitter.render(stack, 0, context.getTransition(), true);
        }
        GlStateManager.disableDepth();

        stack.pop();

        ParticleComponentKillPlane plane = this.emitter.scheme.get(ParticleComponentKillPlane.class);

        if (plane != null && (plane.a != 0 || plane.b != 0 || plane.c != 0))
        {
            this.renderPlane(context, plane.a, plane.b, plane.c, plane.d);
        }
    }

    private void renderPlane(UIContext context, float a, float b, float c, float d)
    {
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        UIVertexBuffer builder = UIVertexBuffer.immediate();
        final float alpha = 0.5F;

        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        this.calculate(0, 0, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha).next();
        this.calculate(0, 1, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha).next();
        this.calculate(1, 0, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha).next();

        this.calculate(1, 0, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha).next();
        this.calculate(0, 1, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha).next();
        this.calculate(1, 1, a, b, c, d);
        builder.vertex(matrix, this.vector.x, this.vector.y, this.vector.z).color(0, 1, 0, alpha).next();

        int previousProgram = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        org.lwjgl.opengl.GL20.glUseProgram(0);
        GlStateManager.disableCull();
        builder.draw();
        org.lwjgl.opengl.GL20.glUseProgram(previousProgram);
        GlStateManager.enableCull();
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
            Draw.coolerAxes(context.batcher.getContext().getMatrices(), 1F, 0.005F);
        }
    }
}