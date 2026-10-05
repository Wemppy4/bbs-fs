package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/** Rendering boundary for the original BBS widgets, including nested clipping. */
public class UIDrawContext
{
    private final MatrixStack matrices = new MatrixStack();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Deque<int[]> scissors = new ArrayDeque<>();
    private final float scale;

    public UIDrawContext(float scale) { this.scale = scale; }
    public MatrixStack getMatrices() { return matrices; }

    /** Native widget draws are immediate; there is no retained vanilla consumer buffer. */
    public void draw() {}

    public void drawItem(ItemStack stack, int x, int y)
    {
        if (stack == null || stack.isEmpty()) return;
        this.drawItemScoped(() -> Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y), true);
    }

    public void drawItemInSlot(FontRenderer font, ItemStack stack, int x, int y)
    {
        if (stack == null || stack.isEmpty()) return;
        this.drawItemScoped(() -> Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(font, stack, x, y, null), false);
    }

    private void drawItemScoped(Runnable draw, boolean lightItem)
    {
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean light0 = GL11.glIsEnabled(GL11.GL_LIGHT0), light1 = GL11.glIsEnabled(GL11.GL_LIGHT1);
        boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        int shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int materialFace = GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_FACE), materialMode = GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_PARAMETER);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        FloatBuffer rgba = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, rgba);
        GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
        try
        {
            this.matrices.peek().getPositionMatrix().get(this.matrixBuffer);
            GlStateManager.multMatrix(this.matrixBuffer);
            GL20.glUseProgram(0);
            GlStateManager.enableTexture2D(); GlStateManager.enableDepth(); GlStateManager.enableAlpha();
            /* BBS text uses ALWAYS. Vanilla's GUI item renderer assumes LEQUAL and
             * writable depth, otherwise the back faces overwrite the front faces. */
            GlStateManager.depthFunc(GL11.GL_LEQUAL); GlStateManager.depthMask(true);
            GlStateManager.enableBlend(); GlStateManager.enableRescaleNormal();
            GlStateManager.color(1F, 1F, 1F, 1F);
            if (lightItem) RenderHelper.enableGUIStandardItemLighting();
            draw.run();
        }
        finally
        {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(mode);
            GL11.glPopAttrib();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            /* Pop restores light parameters; these calls also reconcile Minecraft's enable cache. */
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (light0) GlStateManager.enableLight(0); else GlStateManager.disableLight(0);
            if (light1) GlStateManager.enableLight(1); else GlStateManager.disableLight(1);
            if (colorMaterial) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
            GlStateManager.colorMaterial(materialFace, materialMode); GlStateManager.shadeModel(shadeModel);
            if (textured) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.depthMask(depthMask); GlStateManager.depthFunc(depthFunc); GlStateManager.alphaFunc(alphaFunc, alphaRef);
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            GlStateManager.color(rgba.get(0), rgba.get(1), rgba.get(2), rgba.get(3));
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit); GlStateManager.bindTexture(texture);
            GlStateManager.setActiveTexture(active); GL20.glUseProgram(program);
        }
    }

    public void enableScissor(int x1, int y1, int x2, int y2)
    {
        if (!scissors.isEmpty())
        {
            int[] parent = scissors.peek();
            x1 = Math.max(x1, parent[0]); y1 = Math.max(y1, parent[1]);
            x2 = Math.min(x2, parent[2]); y2 = Math.min(y2, parent[3]);
        }
        scissors.push(new int[] {x1, y1, Math.max(x1, x2), Math.max(y1, y2)});
        applyScissor();
    }

    public void disableScissor()
    {
        if (!scissors.isEmpty()) scissors.pop();
        applyScissor();
    }

    private void applyScissor()
    {
        if (scissors.isEmpty()) { GL11.glDisable(GL11.GL_SCISSOR_TEST); return; }
        int[] box = scissors.peek();
        int left = (int) Math.floor(box[0] * scale), top = (int) Math.floor(box[1] * scale);
        int right = (int) Math.ceil(box[2] * scale), bottom = (int) Math.ceil(box[3] * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, Minecraft.getMinecraft().displayHeight - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public void drawText(FontRenderer font, String text, int x, int y, int color, boolean shadow)
    {
        GlStateManager.pushMatrix();
        matrices.peek().getPositionMatrix().get(matrixBuffer);
        GlStateManager.multMatrix(matrixBuffer);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        font.drawString(text, x, y, color, shadow);
        GlStateManager.popMatrix();
    }
}
