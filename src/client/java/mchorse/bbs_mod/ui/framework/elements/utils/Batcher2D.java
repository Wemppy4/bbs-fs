package mchorse.bbs_mod.ui.framework.elements.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.PixelArt;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.function.Supplier;

public class Batcher2D
{
    /** How far a lit edge is pulled towards white, see {@link #surfaceBox}. */
    private static final float HIGHLIGHT_STRENGTH = 0.15F;

    private static FontRenderer fontRenderer = new FontRenderer();

    private DrawContext context;
    private FontRenderer font;

    public static FontRenderer getDefaultTextRenderer()
    {
        fontRenderer.setRenderer(MinecraftClient.getInstance().textRenderer);

        return fontRenderer;
    }

    /**
     * Program for textured UI quads. The pixel art one keeps the seam between
     * texels even when the interface is drawn at a fractional scale, and falls
     * back to vanilla's when it's turned off or failed to compile.
     */
    private static Supplier<ShaderProgram> texturedProgram()
    {
        if (PixelArt.isEnabled() && BBSShaders.getPixelArtProgram() != null)
        {
            return BBSShaders::getPixelArtProgram;
        }

        return GameRenderer::getPositionTexColorProgram;
    }

    /**
     * Same, but a texture the user asked to be filtered linearly or mipmapped
     * (the toggles in the texture picker) keeps GL's own filtering — the pixel
     * art shader reads texels of level 0 directly, which would both render
     * those toggles meaningless and lean on a complete mipmap pyramid.
     */
    private static Supplier<ShaderProgram> texturedProgram(Texture texture)
    {
        if (texture != null && (texture.isLinear() || texture.isMipmap()))
        {
            return GameRenderer::getPositionTexColorProgram;
        }

        return texturedProgram();
    }

    public Batcher2D(DrawContext context)
    {
        this.context = context;
        this.font = getDefaultTextRenderer();
    }

    public DrawContext getContext()
    {
        return this.context;
    }

    public FontRenderer getFont()
    {
        return this.font;
    }

    /* Screen space clipping */

    public void clip(Area area, UIContext context)
    {
        this.clip(area.x, area.y, area.w, area.h, context);
    }

    /**
     * Clip to a rectangle given by its corners, matching how {@link #box} is called. The size-based
     * {@link #clip} right below reads almost identically at the call site, and passing corners to it
     * silently widens the region instead of failing.
     */
    public void clipBox(int x1, int y1, int x2, int y2, UIContext context)
    {
        this.clip(x1, y1, x2 - x1, y2 - y1, context);
    }

    public void clip(int x, int y, int w, int h, UIContext context)
    {
        this.clip(context.globalX(x), context.globalY(y), w, h, context.menu.width, context.menu.height);
    }

    /**
     * Scissor (clip) the screen
     */
    public void clip(int x, int y, int w, int h, int sw, int sh)
    {
        this.context.enableScissor(x, y, x + w, y + h);
    }

    public void unclip(UIContext context)
    {
        this.unclip(context.menu.width, context.menu.height);
    }

    public void unclip(int sw, int sh)
    {
        this.context.disableScissor();
    }

    /* Solid rectangles */

    public void normalizedBox(float x1, float y1, float x2, float y2, int color)
    {
        float temp = x1;

        x1 = Math.min(x1, x2);
        x2 = Math.max(temp, x2);

        temp = y1;

        y1 = Math.min(y1, y2);
        y2 = Math.max(temp, y2);

        this.box(x1, y1, x2, y2, color);
    }

    public void box(float x1, float y1, float x2, float y2, int color)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, color, color, color, color);
    }

    public void box(float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        this.fillRect(builder, matrix4f, x, y, w, h, color1, color2, color3, color4);

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        this.context.draw();
    }

    public void fillRect(BufferBuilder builder, Matrix4f matrix4f, float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        /* c1 ---- c2
         * |        |
         * c3 ---- c4 */
        builder.vertex(matrix4f, x, y, 0).color(color1).next();
        builder.vertex(matrix4f, x, y + h, 0).color(color3).next();
        builder.vertex(matrix4f, x + w, y + h, 0).color(color4).next();
        builder.vertex(matrix4f, x + w, y, 0).color(color2).next();
    }

    public void surfaceBox(int x1, int y1, int x2, int y2, int fill, boolean shadow, boolean border)
    {
        if (border)
        {
            this.box(x1, y1, x2, y2, Colors.A100);

            x1++;
            y1++;
            x2--;
            y2--;
        }

        this.box(x1, y1, x2, y2, fill);

        /* Highlight and shadow are separate settings: the lit edges are the loud
         * half of the old bevel, so they're off by default and weaker than they
         * were — about six steps of the surface ramp instead of thirteen. */
        if (BBSSettings.interfaceHighlights.get())
        {
            int light = Colors.lerp(fill, Colors.WHITE, HIGHLIGHT_STRENGTH);

            this.box(x1, y1, x2, y1 + 1, light);
            this.box(x1, y1, x1 + 1, y2, light);
        }

        if (shadow && BBSSettings.interfaceShadows.get())
        {
            this.box(x1, y2 - 2, x2, y2, Colors.lerp(fill, Colors.A100, 0.4F));
        }
    }

    public void dropShadow(int left, int top, int right, int bottom, int offset, int opaque, int shadow)
    {
        left -= offset;
        top -= offset;
        right += offset;
        bottom += offset;

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        /* Draw opaque part */
        builder.vertex(matrix4f, left + offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f,left + offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, top + offset, 0).color(opaque).next();

        /* Draw top shadow */
        builder.vertex(matrix4f, left, top, 0).color(shadow).next();
        builder.vertex(matrix4f,left + offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right, top, 0).color(shadow).next();

        /* Draw bottom shadow */
        builder.vertex(matrix4f, left + offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f,left, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f, right, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f, right - offset, bottom - offset, 0).color(opaque).next();

        /* Draw left shadow */
        builder.vertex(matrix4f, left, top, 0).color(shadow).next();
        builder.vertex(matrix4f, left, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f, left + offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f,left + offset, top + offset, 0).color(opaque).next();

        /* Draw right shadow */
        builder.vertex(matrix4f, right - offset, top + offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right - offset, bottom - offset, 0).color(opaque).next();
        builder.vertex(matrix4f, right, bottom, 0).color(shadow).next();
        builder.vertex(matrix4f,right, top, 0).color(shadow).next();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /* Gradients */

    /**
     * Draw a selection highlight over an area: a solid bar of the primary color along the
     * {@code edge}, fading into a gradient towards the opposite side. This is what marks the
     * chosen tab, mode or tool everywhere in the UI.
     */
    public void highlight(Area area, Direction edge)
    {
        int color = BBSSettings.primaryColor.get();
        int bar = Colors.A100 | color;
        int near = Colors.A75 | color;
        int far = color;
        int t = 2;

        switch (edge)
        {
            case TOP:
                this.box(area.x, area.y, area.ex(), area.y + t, bar);
                this.gradientVBox(area.x, area.y + t, area.ex(), area.ey(), near, far);
                break;
            case BOTTOM:
                this.box(area.x, area.ey() - t, area.ex(), area.ey(), bar);
                this.gradientVBox(area.x, area.y, area.ex(), area.ey() - t, far, near);
                break;
            case LEFT:
                this.box(area.x, area.y, area.x + t, area.ey(), bar);
                this.gradientHBox(area.x + t, area.y, area.ex(), area.ey(), near, far);
                break;
            case RIGHT:
                this.box(area.ex() - t, area.y, area.ex(), area.ey(), bar);
                this.gradientHBox(area.x, area.y, area.ex() - t, area.ey(), far, near);
                break;
        }
    }

    public void gradientHBox(float x1, float y1, float x2, float y2, int leftColor, int rightColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, leftColor, rightColor, leftColor, rightColor);
    }

    public void gradientVBox(float x1, float y1, float x2, float y2, int topColor, int bottomColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, topColor, topColor, bottomColor, bottomColor);
    }

    public void dropCircleShadow(int x, int y, int radius, int segments, int opaque, int shadow)
    {
        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, x, y, 0F).color(opaque).next();

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (float) (x - Math.cos(a) * radius), (float) (y + Math.sin(a) * radius), 0F).color(shadow).next();
        }
    }

    public void dropCircleShadow(int x, int y, int radius, int offset, int segments, int opaque, int shadow)
    {
        if (offset >= radius)
        {
            this.dropCircleShadow(x, y, radius, segments, opaque, shadow);

            return;
        }

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        /* Draw opaque base */
        builder.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, x, y, 0F).color(opaque).next();

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (int) (x - Math.cos(a) * offset), (int) (y + Math.sin(a) * offset), 0F).color(opaque).next();
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        /* Draw outer shadow */
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segments; i ++)
        {
            double alpha1 = i / (double) segments * Math.PI * 2 - Math.PI / 2;
            double alpha2 = (i + 1) / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (float) (x - Math.cos(alpha2) * offset), (float) (y + Math.sin(alpha2) * offset), 0F).color(opaque).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha1) * offset), (float) (y + Math.sin(alpha1) * offset), 0F).color(opaque).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha1) * radius), (float) (y + Math.sin(alpha1) * radius), 0F).color(shadow).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha2) * offset), (float) (y + Math.sin(alpha2) * offset), 0F).color(opaque).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha1) * radius), (float) (y + Math.sin(alpha1) * radius), 0F).color(shadow).next();
            builder.vertex(matrix4f, (float) (x - Math.cos(alpha2) * radius), (float) (y + Math.sin(alpha2) * radius), 0F).color(shadow).next();
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /* Outline methods */

    public void outlineCenter(float x, float y, float offset, int color)
    {
        this.outlineCenter(x, y, offset, color, 1);
    }

    public void outlineCenter(float x, float y, float offset, int color, int border)
    {
        this.outline(x - offset, y - offset, x + offset, y + offset, color, border);
    }

    public void outline(float x1, float y1, float x2, float y2, int color)
    {
        this.outline(x1, y1, x2, y2, color, 1);
    }

    /**
     * Draw rectangle outline with given border.
     */
    public void outline(float x1, float y1, float x2, float y2, int color, int border)
    {
        this.box(x1, y1, x1 + border, y2, color);
        this.box(x2 - border, y1, x2, y2, color);
        this.box(x1 + border, y1, x2 - border, y1 + border, color);
        this.box(x1 + border, y2 - border, x2 - border, y2, color);
    }

    /* Icon */

    /** In the light theme white foreground (text/icons) becomes black; other colours pass through. */
    private static int darkenWhite(int color)
    {
        return (color & 0xFFFFFF) == 0xFFFFFF ? (color & 0xFF000000) : color;
    }

    public void icon(Icon icon, float x, float y)
    {
        this.icon(icon, Colors.WHITE, x, y);
    }

    public void icon(Icon icon, int color, float x, float y)
    {
        this.icon(icon, color, x, y, 0F, 0F);
    }

    public void icon(Icon icon, float x, float y, float ax, float ay)
    {
        this.icon(icon, Colors.WHITE, x, y, ax, ay);
    }

    public void icon(Icon icon, int color, float x, float y, float ax, float ay)
    {
        if (icon.texture == null)
        {
            return;
        }

        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        x -= icon.w * ax;
        y -= icon.h * ay;

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, icon.w, icon.h, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    public void iconArea(Icon icon, float x, float y, float w, float h)
    {
        this.iconArea(icon, Colors.WHITE, x, y, w, h);
    }

    public void iconArea(Icon icon, int color, float x, float y, float w, float h)
    {
        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        this.texturedArea(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, w, h, icon.x, icon.y, icon.w, icon.h, icon.textureW, icon.textureH);
    }

    public void outlinedIcon(Icon icon, float x, float y, float ax, float ay)
    {
        this.outlinedIcon(icon, x, y, Colors.WHITE, ax, ay);
    }

    /**
     * Draw an icon with a black outline.
     */
    public void outlinedIcon(Icon icon, float x, float y, int color, float ax, float ay)
    {
        this.icon(icon, Colors.A100, x - 1, y, ax, ay);
        this.icon(icon, Colors.A100, x + 1, y, ax, ay);
        this.icon(icon, Colors.A100, x, y - 1, ax, ay);
        this.icon(icon, Colors.A100, x, y + 1, ax, ay);
        this.icon(icon, color, x, y, ax, ay);
    }

    /* Textured box */

    public void fullTexturedBox(Texture texture, float x, float y, float w, float h)
    {
        this.fullTexturedBox(texture, Colors.WHITE, x, y, w, h);
    }

    public void fullTexturedBox(Texture texture, int color, float x, float y, float w, float h)
    {
        this.texturedBox(texture, color, x, y, w, h, 0, 0, w, h, (int) w, (int) h);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2)
    {
        this.texturedBox(texture, color, x, y, w, h, u1, v1, u2, v2, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u, float v)
    {
        this.texturedBox(texture, color, x, y, w, h, u, v, u + w, v + h, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        RenderSystem.setShaderTexture(0, texture.id);

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(texturedProgram(texture));

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
        this.fillTexturedBox(builder, matrix, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void texturedBox(int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.texturedBox(texturedProgram(), texture, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    public void texturedBox(Supplier<ShaderProgram> shader, int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(shader);

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
        this.fillTexturedBox(builder, matrix, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private void fillTexturedBox(BufferBuilder builder, Matrix4f matrix, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        builder.vertex(matrix, x, y + h, 0F).texture(u1 / (float) textureW, v2 / (float) textureH).color(color).next();
        builder.vertex(matrix, x + w, y + h, 0F).texture(u2 / (float) textureW, v2 / (float) textureH).color(color).next();
        builder.vertex(matrix, x + w, y, 0F).texture(u2 / (float) textureW, v1 / (float) textureH).color(color).next();
        builder.vertex(matrix, x, y + h, 0F).texture(u1 / (float) textureW, v2 / (float) textureH).color(color).next();
        builder.vertex(matrix, x + w, y, 0F).texture(u2 / (float) textureW, v1 / (float) textureH).color(color).next();
        builder.vertex(matrix, x, y, 0F).texture(u1 / (float) textureW, v1 / (float) textureH).color(color).next();
    }

    /* Repeatable textured box */

    public void texturedArea(Texture texture, int color, float x, float y, float w, float h, float u, float v, float tileW, float tileH, int tw, int th)
    {
        int countX = (int) (((w - 1) / tileW) + 1);
        int countY = (int) (((h - 1) / tileH) + 1);
        float fillerX = w - (countX - 1) * tileW;
        float fillerY = h - (countY - 1) * tileH;

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(texturedProgram(texture));
        RenderSystem.setShaderTexture(0, texture.id);

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0, c = countX * countY; i < c; i ++)
        {
            float ix = i % countX;
            float iy = i / countX;
            float xx = x + ix * tileW;
            float yy = y + iy * tileH;
            float xw = ix == countX - 1 ? fillerX : tileW;
            float yh = iy == countY - 1 ? fillerY : tileH;

            this.fillTexturedBox(builder, matrix, color, xx, yy, xw, yh, u, v, u + xw, v + yh, tw, th);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /* Text with default font */

    public void text(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, false);
    }

    public void text(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, false);
    }

    public void textShadow(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, true);
    }

    public void textShadow(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, true);
    }

    public void text(String label, float x, float y, int color, boolean shadow)
    {
        if (BBSSettings.isLightTheme())
        {
            shadow = false;
            color = darkenWhite(color);
        }

        this.drawTextDirect(label, x, y, color, shadow);
    }

    /** Actual text draw (theming is applied by the public text() before calling this). */
    private void drawTextDirect(String label, float x, float y, int color, boolean shadow)
    {
        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        this.context.drawText(this.font.getRenderer(), label, (int) x, (int) y, color, shadow);
        this.context.draw();

        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    /* Text helpers */

    public int wallText(String text, int x, int y, int color, int width)
    {
        return this.wallText(text, x, y, color, width, 12);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight)
    {
        return this.wallText(text, x, y, color, width, lineHeight, 0F, 0F);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay)
    {
        return wallText(text, x, y, color, width, lineHeight, ax, ay, true);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay, boolean shadow)
    {
        List<String> list = this.font.wrap(text, width);
        int h = (lineHeight * (list.size() - 1)) + this.font.getHeight();

        y -= h * ay;

        for (String string : list)
        {
            this.text(string.toString(), (int) (x + (width - this.font.getWidth(string)) * ax), y, color, shadow);

            y += lineHeight;
        }

        return h;
    }

    public void textCard(String text, float x, float y)
    {
        this.textCard(text, x, y, Colors.WHITE, Colors.A50);
    }

    /**
     * In this context, text card is a text with some background behind it
     */
    public void textCard(String text, float x, float y, int color, int background)
    {
        this.textCard(text, x, y, color, background, 3);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset)
    {
        this.textCard(text, x, y, color, background, offset, true);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset, boolean shadow)
    {
        int a = background >> 24 & 0xff;

        if (a != 0)
        {
            if (BBSSettings.isLightTheme() && (background & 0xFFFFFF) == 0)
            {
                background = (background & 0xFF000000) | 0xFFFFFF;
            }

            this.box(x - offset, y - offset, x + this.font.getWidth(text) + offset - 1, y + this.font.getHeight() + offset, background);
        }

        this.text(text, x, y, color, shadow);
    }

    public void flush()
    {
        this.context.draw();
    }
}