package mchorse.bbs_mod.particles;
import mchorse.bbs_mod.graphics.OptiFineShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
/** Restore shared GL state after native particles, including UI shaders and OptiFine's program cache. */
public final class ParticleRenderPass implements AutoCloseable {
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int vbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int texture;
    private final boolean textured;
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    private final int source = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destination = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int sourceA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
    private final float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int shade = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
    private final OptiFineShaders.EntityPass shader;
    private final boolean lightmap;
    public ParticleRenderPass(boolean worldLighting) {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        if (worldLighting) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        shader = worldLighting && OptiFineShaders.isWorldPass() ? OptiFineShaders.particles() : null;
        if (shader == null) GL20.glUseProgram(0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.color(1,1,1,1);
    }
    @Override public void close(){
        if (shader != null) shader.close(); else GL20.glUseProgram(program);
        if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        GlStateManager.alphaFunc(alphaFunction, alphaReference);
        GlStateManager.tryBlendFuncSeparate(source,destination,sourceA,destinationA);
        GlStateManager.depthMask(depthMask);
        GlStateManager.shadeModel(shade);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (lightmap) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(texture);
        if (textured) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(activeTexture);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
        GlStateManager.color(1,1,1,1);
    }
}
