package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

public class ModelVAORenderer
{
    /**
     * The full model-view a draw issued right now would use. Deferred translucent commands
     * capture it at enqueue time, because the global model-view is different by the time
     * the queue flushes at the end of the frame.
     */
    public static Matrix4f captureModelView(MatrixStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
    }

    public static void render(ShaderProgram shader, IModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        render(shader, modelVAO, captureModelView(stack), stack.peek().getNormalMatrix(), r, g, b, a, light, overlay);
    }

    public static void render(ShaderProgram shader, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        setupUniforms(shader, modelView, normalMat);

        shader.bind();
        modelVAO.render(shader.getFormat(), r, g, b, a, light, overlay);
        shader.unbind();

        GL30.glBindVertexArray(currentVAO);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
    }

    public static void setupUniforms(MatrixStack stack, ShaderProgram shader)
    {
        setupUniforms(shader, captureModelView(stack), stack.peek().getNormalMatrix());
    }

    public static void setupUniforms(ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat)
    {
        for (int i = 0; i < 12; i++)
        {
            shader.addSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }

        if (shader.projectionMat != null)
        {
            shader.projectionMat.set(RenderSystem.getProjectionMatrix());
        }

        if (shader.modelViewMat != null)
        {
            shader.modelViewMat.set(modelView);
        }

        /* NormalMat is present by default in Iris' shaders, but when there is no Iris,
         * the BBS mod's model.json shader is being used instead that provides NormalMat
         * uniform.
         */
        GlUniform normalUniform = shader.getUniform("NormalMat");

        if (normalUniform != null)
        {
            normalUniform.set(normalMat);
        }

        if (shader.viewRotationMat != null)
        {
            shader.viewRotationMat.set(RenderSystem.getInverseViewRotationMatrix());
        }

        if (shader.fogStart != null)
        {
            shader.fogStart.set(RenderSystem.getShaderFogStart());
        }

        if (shader.fogEnd != null)
        {
            shader.fogEnd.set(RenderSystem.getShaderFogEnd());
        }

        if (shader.fogColor != null)
        {
            shader.fogColor.set(RenderSystem.getShaderFogColor());
        }

        if (shader.fogShape != null)
        {
            shader.fogShape.set(RenderSystem.getShaderFogShape().getId());
        }

        if (shader.colorModulator != null)
        {
            shader.colorModulator.set(1F, 1F, 1F, 1F);
        }

        if (shader.gameTime != null)
        {
            shader.gameTime.set(RenderSystem.getShaderGameTime());
        }

        if (shader.textureMat != null)
        {
            shader.textureMat.set(RenderSystem.getTextureMatrix());
        }

        RenderSystem.setupShaderLights(shader);
    }
}
