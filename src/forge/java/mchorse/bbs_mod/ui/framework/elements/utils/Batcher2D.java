package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.ui.framework.UIDrawContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.util.List;
import java.util.function.Supplier;

public class Batcher2D
{
    /** How far a lit edge is pulled towards white, see {@link #surfaceBox}. */
    private static final float HIGHLIGHT_STRENGTH = 0.15F;

    private static FontRenderer fontRenderer = new FontRenderer();

    private UIDrawContext context;
    private FontRenderer font;

    public static FontRenderer getDefaultTextRenderer()
    {
        fontRenderer.setRenderer(mchorse.bbs_mod.fonts.nativefonts.NativeDefaultFont.renderer());

        return fontRenderer;
    }

    /* Quad batching. A scope opened with beginBatch() collects every solid quad (box, outline,
     * surfaceBox and friends all funnel into box) into one dedicated buffer and draws it once at
     * endBatch() - instead of a begin/setShader/draw/flush per rectangle. Order stays exact
     * because only homogeneous solid quads batch: every other primitive (textures, text, clip)
     * flushes the pending quads first. The buffer is our own, not the shared Tessellator one,
     * so code that builds on the Tessellator directly can never collide with an open batch.
     *
     * The buffer is shared by all batchers: the HUD creates a new batcher every frame, and a
     * UIVertexBuffer's native memory is never freed, so a buffer per batcher leaked 1.5 MB each
     * time until the game crashed with OutOfMemoryError. Only one batcher holds it at a time -
     * starting a batch flushes the previous holder first, so the draw order stays the same. */
    private static final UIVertexBuffer immediateBuilder = new UIVertexBuffer(262144);
    private static UIVertexBuffer batchBuilder;
    private static Batcher2D batchOwner;
    private boolean batching;

    public Batcher2D(UIDrawContext context)
    {
        this.context = context;
        this.font = getDefaultTextRenderer();
    }

    public boolean isBatching()
    {
        return this.batching;
    }

    /** Open a quad batch. Nested calls are folded into the outermost scope. */
    public void beginBatch()
    {
        this.batching = true;
    }

    /** Close the scope opened by {@link #beginBatch()} and draw the collected quads. */
    public void endBatch()
    {
        this.batching = false;
        this.flushBatch();
    }

    private void flushBatch()
    {
        if (batchOwner != this)
        {
            return;
        }

        batchOwner = null;

        GlStateManager.enableBlend();
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        batchBuilder.draw();

    }

    public UIDrawContext getContext()
    {
        return this.context;
    }

    public FontRenderer getFont()
    {
        return this.font;
    }

    /**
     * Swap the font every text call of this batcher goes through, handing back the
     * previous one so the caller can put it back. A null restores the default one.
     */
    public FontRenderer setFont(FontRenderer font)
    {
        FontRenderer previous = this.font;

        this.font = font == null ? getDefaultTextRenderer() : font;

        return previous;
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
        this.flushBatch();
        this.context.enableScissor(x, y, x + w, y + h);
    }

    public void unclip(UIContext context)
    {
        this.unclip(context.menu.width, context.menu.height);
    }

    public void unclip(int sw, int sh)
    {
        this.flushBatch();
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

        /* The matrix bakes into the vertices right here, so quads from different matrix
         * contexts share one batch safely. */
        if (this.batching)
        {
            if (batchOwner != this)
            {
                if (batchOwner != null)
                {
                    batchOwner.flushBatch();
                }

                if (batchBuilder == null)
                {
                    batchBuilder = new UIVertexBuffer(262144);
                }

                batchBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                batchOwner = this;
            }

            this.fillRect(batchBuilder, matrix4f, x, y, w, h, color1, color2, color3, color4);

            return;
        }

        UIVertexBuffer builder = immediateBuilder;

        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        this.fillRect(builder, matrix4f, x, y, w, h, color1, color2, color3, color4);

        GlStateManager.enableBlend();
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();

    }

    /**
     * A rectangle cut along its top-left to bottom-right diagonal, a color to each half.
     * A color with an alpha channel is shown this way — its opaque half beside its real one,
     * both over a checkboard — so how transparent it is reads at a glance.
     */
    public void splitBox(float x1, float y1, float x2, float y2, int topLeft, int bottomRight)
    {
        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        UIVertexBuffer builder = immediateBuilder;

        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        builder.vertex(matrix4f, x1, y1, 0F).color(topLeft).next();
        builder.vertex(matrix4f, x1, y2, 0F).color(topLeft).next();
        builder.vertex(matrix4f, x2, y1, 0F).color(topLeft).next();
        builder.vertex(matrix4f, x2, y1, 0F).color(bottomRight).next();
        builder.vertex(matrix4f, x1, y2, 0F).color(bottomRight).next();
        builder.vertex(matrix4f, x2, y2, 0F).color(bottomRight).next();

        GlStateManager.enableBlend();
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();

    }

    public void fillRect(UIVertexBuffer builder, Matrix4f matrix4f, float x, float y, float w, float h, int color1, int color2, int color3, int color4)
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

    /**
     * A soft glow radiating out of a rectangle, with the rectangle itself filled
     * by the opaque colour. Every glow in the interface comes through here, so
     * the one toggle that turns them off is read here rather than at each
     * caller: every caller paints its own background over this rectangle right
     * after, which is what makes skipping the whole thing safe.
     */
    public void dropShadow(int left, int top, int right, int bottom, int offset, int opaque, int shadow)
    {
        if (!BBSSettings.hasInterfaceGlow())
        {
            return;
        }

        this.flushBatch();

        left -= offset;
        top -= offset;
        right += offset;
        bottom += offset;

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        UIVertexBuffer builder = immediateBuilder;

        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

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

        GlStateManager.enableBlend();
        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();
    }

    /* Gradients */

    /**
     * Draw a selection highlight over an area: a solid bar of the primary color along the
     * {@code edge}, fading into a gradient towards the opposite side. This is what marks the
     * chosen tab, mode or tool everywhere in the UI.
     */
    public void highlight(Area area, Direction edge)
    {
        this.highlight(area, edge, BBSSettings.primaryColor.get());
    }

    /**
     * The same mark in a colour of its own — what a destructive button wears, so that "this one
     * is not like the others" is said the same way as "this one is the active one".
     */
    public void highlight(Area area, Direction edge, int color)
    {
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
        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();
        UIVertexBuffer builder = immediateBuilder;

        builder.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, x, y, 0F).color(opaque).next();

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (float) (x - Math.cos(a) * radius), (float) (y + Math.sin(a) * radius), 0F).color(shadow).next();
        }
        builder.draw();
    }

    public void dropCircleShadow(int x, int y, int radius, int offset, int segments, int opaque, int shadow)
    {
        if (offset >= radius)
        {
            this.dropCircleShadow(x, y, radius, segments, opaque, shadow);

            return;
        }

        this.flushBatch();

        Matrix4f matrix4f = this.context.getMatrices().peek().getPositionMatrix();

        UIVertexBuffer builder = immediateBuilder;

        GlStateManager.enableBlend();

        /* Draw opaque base */
        builder.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, x, y, 0F).color(opaque).next();

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            builder.vertex(matrix4f, (int) (x - Math.cos(a) * offset), (int) (y + Math.sin(a) * offset), 0F).color(opaque).next();
        }

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();

        /* Draw outer shadow */
        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

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

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();
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

        if (BBSSettings.lightSurfaces())
        {
            color = darkenWhite(color);
        }

        x -= icon.w * ax;
        y -= icon.h * ay;

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, icon.w, icon.h, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    /**
     * An icon scaled to a square of {@code size}, for the few places where an icon stands in
     * for a picture and grows with its cell (a folder in a texture grid). Buttons never come
     * through here — their icons keep their own size.
     */
    public void scaledIcon(Icon icon, int color, float x, float y, float size)
    {
        if (icon.texture == null)
        {
            return;
        }

        if (BBSSettings.lightSurfaces())
        {
            color = darkenWhite(color);
        }

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, size, size, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    public void iconArea(Icon icon, float x, float y, float w, float h)
    {
        this.iconArea(icon, Colors.WHITE, x, y, w, h);
    }

    public void iconArea(Icon icon, int color, float x, float y, float w, float h)
    {
        if (BBSSettings.lightSurfaces())
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
        this.flushBatch();

        GlStateManager.bindTexture(texture.id);

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        UIVertexBuffer builder = immediateBuilder;

        /* The colour carries an alpha and the caller means it, so blending is turned on here
         * rather than borrowed from whatever was drawn before - see the note above box() */
        GlStateManager.enableBlend();

        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
        this.fillTexturedBox(builder, matrix, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();
    }

    public void texturedBox(int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.flushBatch();

        GlStateManager.bindTexture(texture);

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        UIVertexBuffer builder = immediateBuilder;

        GlStateManager.enableBlend();

        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
        this.fillTexturedBox(builder, matrix, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();
    }

    private static mchorse.bbs_mod.graphics.render.VertexBuffer shaderBuffer;
    private static final mchorse.bbs_mod.graphics.render.BufferBuilder shaderBuilder = new mchorse.bbs_mod.graphics.render.BufferBuilder();

    /** Original custom-shader quad path, backed by the native BBS VAO/VBO renderer. */
    public void texturedBox(Supplier<mchorse.bbs_mod.graphics.shader.ShaderProgram> program, int texture, int color,
        float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.flushBatch();
        if (shaderBuffer == null) shaderBuffer = new mchorse.bbs_mod.graphics.render.VertexBuffer(mchorse.bbs_mod.graphics.render.VertexBuffer.Usage.DYNAMIC);
        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        shaderBuilder.begin(mchorse.bbs_mod.graphics.render.VertexFormat.DrawMode.TRIANGLES, mchorse.bbs_mod.graphics.render.VertexFormats.POSITION_TEXTURE_COLOR);
        shaderVertex(matrix, x, y+h, u1/textureW, v2/textureH, color);
        shaderVertex(matrix, x+w, y+h, u2/textureW, v2/textureH, color);
        shaderVertex(matrix, x+w, y, u2/textureW, v1/textureH, color);
        shaderVertex(matrix, x, y+h, u1/textureW, v2/textureH, color);
        shaderVertex(matrix, x+w, y, u2/textureW, v1/textureH, color);
        shaderVertex(matrix, x, y, u1/textureW, v1/textureH, color);
        shaderBuffer.upload(shaderBuilder.end());
        int active = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        try
        {
            shaderBuffer.draw(mchorse.bbs_mod.graphics.render.RenderSystem.getModelViewMatrix(),
                mchorse.bbs_mod.graphics.render.RenderSystem.getProjectionMatrix(), program.get());
            BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        }
        finally
        {
            GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(previous);
            GlStateManager.setActiveTexture(active);
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        }
    }

    private static void shaderVertex(Matrix4f matrix, float x, float y, float u, float v, int color)
    {
        shaderBuilder.vertex(matrix.m00()*x + matrix.m10()*y + matrix.m30(),
            matrix.m01()*x + matrix.m11()*y + matrix.m31(), matrix.m02()*x + matrix.m12()*y + matrix.m32())
            .texture(u, v).color((color>>16&255)/255F, (color>>8&255)/255F, (color&255)/255F, (color>>>24)/255F).next();
    }

    private void fillTexturedBox(UIVertexBuffer builder, Matrix4f matrix, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
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
        this.flushBatch();

        int countX = (int) (((w - 1) / tileW) + 1);
        int countY = (int) (((h - 1) / tileH) + 1);
        float fillerX = w - (countX - 1) * tileW;
        float fillerY = h - (countY - 1) * tileH;

        Matrix4f matrix = this.context.getMatrices().peek().getPositionMatrix();
        UIVertexBuffer builder = immediateBuilder;

        GlStateManager.enableBlend();
        GlStateManager.bindTexture(texture.id);

        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);

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

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        builder.draw();
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
        if (BBSSettings.lightSurfaces())
        {
            shadow = false;
            color = darkenWhite(color);
        }

        this.drawTextDirect(label, x, y, color, shadow);
    }

    /** Actual text draw (theming is applied by the public text() before calling this). */
    private void drawTextDirect(String label, float x, float y, int color, boolean shadow)
    {
        this.flushBatch();

        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        BBSProfiler.count(BBSProfiler.Section.UI_DRAW_CALLS);
        /* This batcher also draws the wand/recording HUD outside UIScreen. Leaving
         * GL_ALWAYS there leaks into the next world pass on 1.12, whose renderer
         * does not reset the comparison each frame. Preserve the caller's mode:
         * ALWAYS inside the editor, LEQUAL for the game's HUD. */
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        try { this.context.drawText(this.font.getRenderer(), label, (int) x, (int) y, color, shadow); }
        finally { GlStateManager.depthFunc(depthFunction); }
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
            if (BBSSettings.lightSurfaces() && (background & 0xFFFFFF) == 0)
            {
                background = (background & 0xFF000000) | 0xFFFFFF;
            }

            this.box(x - offset, y - offset, x + this.font.getWidth(text) + offset - 1, y + this.font.getHeight() + offset, background);
        }

        this.text(text, x, y, color, shadow);
    }

    public void flush()
    {
        this.flushBatch();
    }
}
