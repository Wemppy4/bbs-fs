package mchorse.bbs_mod.fonts;

import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native 1.12 text adapter with lazy 256px glyph atlases. No modern Minecraft or
 * LWJGL3 classes are emulated. The default bitmap font remains a separate source. */
public final class NativeTrueTypeFontRenderer extends FontRenderer
{
    private static final int PAGE_SIZE = 256;
    private final TrueTypeFontData data;
    private final Map<Integer, TrueTypeFontData.Glyph> glyphs = new HashMap<>();
    private final Map<Integer, BakedGlyph> baked = new HashMap<>();
    private final List<Page> pages = new ArrayList<>();
    private final Map<Integer, List<Integer>> randomGlyphs = new HashMap<>();
    private final BufferBuilder buffer = new BufferBuilder(8192);
    private final WorldVertexBufferUploader uploader = new WorldVertexBufferUploader();
    private boolean closed;

    public NativeTrueTypeFontRenderer(TrueTypeFontData data)
    {
        super(Minecraft.getMinecraft().gameSettings, new ResourceLocation("textures/font/ascii.png"),
            Minecraft.getMinecraft().getTextureManager(), false);
        this.data = data;
        this.FONT_HEIGHT = data.metrics.lineHeight;
    }

    public boolean hasGlyph(int codepoint) { return !this.glyph(codepoint).missing; }
    public int getAtlasCount() { return this.pages.size(); }
    public int getOversample() { return this.data.oversample; }
    public boolean isClosed() { return this.closed; }

    private TrueTypeFontData.Glyph glyph(int codepoint)
    {
        TrueTypeFontData.Glyph glyph = this.glyphs.get(codepoint);
        if (glyph == null)
        {
            glyph = this.data.glyph(codepoint);
            this.glyphs.put(codepoint, glyph);
        }
        return glyph;
    }

    private double advance(int codepoint) { return this.glyph(codepoint).advance; }

    @Override public int getStringWidth(String text) { return FontTextLayout.width(text, this::advance); }
    @Override public int getCharWidth(char c) { return c == 167 ? -1 : (int) Math.ceil(this.advance(c)); }
    @Override public List<String> listFormattedStringToWidth(String text, int width)
    {
        return FontTextLayout.wrap(text, width, this::advance);
    }
    @Override public String trimStringToWidth(String text, int width)
    {
        return this.trimStringToWidth(text, width, false);
    }
    @Override public String trimStringToWidth(String text, int width, boolean reverse)
    {
        return FontTextLayout.trim(text, width, reverse, this::advance);
    }
    @Override public void drawSplitString(String text, int x, int y, int width, int color)
    {
        for (String line : this.listFormattedStringToWidth(text, width))
        {
            this.drawString(line, x, y, color, false);
            y += this.FONT_HEIGHT;
        }
    }
    @Override public int getWordWrappedHeight(String text, int width)
    {
        return this.listFormattedStringToWidth(text, width).size() * this.FONT_HEIGHT;
    }

    @Override public int drawString(String text, float x, float y, int color, boolean shadow)
    {
        if (text == null || this.closed) return (int) x;
        if ((color & 0xfc000000) == 0) color |= 0xff000000;
        List<FontTextLayout.Token> tokens = FontTextLayout.tokens(text, this::advance);
        List<BakedGlyph> draws = new ArrayList<>(tokens.size());
        for (FontTextLayout.Token t : tokens)
            draws.add(t.codepoint == '\n' || t.codepoint == '\r' ? null : this.bake(t.style.random ? this.randomize(t.codepoint) : t.codepoint));

        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int srcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try
        {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (Page page : this.pages) page.upload();
            if (shadow) this.drawPass(tokens, draws, x + 1, y + 1, color, true);
            this.drawPass(tokens, draws, x, y, color, false);
        }
        finally
        {
            GlStateManager.bindTexture(binding);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.setActiveTexture(active);
        }
        return (int) (x + this.getStringWidth(text) + (shadow ? 1 : 0));
    }

    private int randomize(int codepoint)
    {
        if (this.randomGlyphs.isEmpty())
        {
            for (int i = 33; i < 127; i++)
            {
                if (this.glyph(i).missing) continue;
                int width = (int) Math.ceil(this.advance(i));
                List<Integer> group = this.randomGlyphs.get(width);
                if (group == null) this.randomGlyphs.put(width, group = new ArrayList<>());
                group.add(i);
            }
        }
        List<Integer> group = this.randomGlyphs.get((int) Math.ceil(this.advance(codepoint)));
        return group == null || group.isEmpty() ? codepoint : group.get(this.fontRandom.nextInt(group.size()));
    }

    private void drawPass(List<FontTextLayout.Token> tokens, List<BakedGlyph> draws, float startX, float startY, int color, boolean shadow)
    {
        float x = startX, y = startY;
        Page current = null;
        boolean drawing = false;
        List<float[]> decorations = new ArrayList<>();
        List<Integer> decorationColors = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++)
        {
            FontTextLayout.Token t = tokens.get(i);
            if (t.codepoint == '\n') { x = startX; y += this.FONT_HEIGHT; continue; }
            if (t.codepoint == '\r') continue;
            int rgb = t.style.color == 0 ? color : (color & 0xff000000) | this.getColorCode(t.style.color);
            if (shadow) rgb = (rgb & 0xff000000) | ((rgb & 0xfcfcfc) >> 2);
            BakedGlyph glyph = draws.get(i);
            if (glyph != null && glyph.page != null)
            {
                if (current != glyph.page)
                {
                    if (drawing) this.flush();
                    current = glyph.page;
                    current.texture.bind();
                    this.buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                    drawing = true;
                }
                this.quad(glyph, x, y, rgb, t.style.italic);
                if (t.style.bold) this.quad(glyph, x + 1, y, rgb, t.style.italic);
            }
            /* Modern TextRenderer's decoration lines stay at its fixed 9px baseline,
             * even with a custom face. Keep that behavior; wrapper line spacing is separate. */
            if (t.style.strike) { decorations.add(new float[] {x - 1, y + 4.5F, x + t.advance, y + 3.5F}); decorationColors.add(rgb); }
            if (t.style.underline) { decorations.add(new float[] {x - 1, y + 9, x + t.advance, y + 8}); decorationColors.add(rgb); }
            x += t.advance;
        }
        if (drawing) this.flush();
        if (!decorations.isEmpty())
        {
            GlStateManager.disableTexture2D();
            this.buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < decorations.size(); i++)
            {
                float[] d = decorations.get(i);
                int c = decorationColors.get(i), r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, a = c >>> 24;
                this.buffer.pos(d[0], d[1], 0).color(r, g, b, a).endVertex();
                this.buffer.pos(d[2], d[1], 0).color(r, g, b, a).endVertex();
                this.buffer.pos(d[2], d[3], 0).color(r, g, b, a).endVertex();
                this.buffer.pos(d[0], d[3], 0).color(r, g, b, a).endVertex();
            }
            this.flush();
            GlStateManager.enableTexture2D();
        }
    }

    private void quad(BakedGlyph glyph, float x, float y, int c, boolean italic)
    {
        float x0 = x + glyph.left, x1 = x0 + glyph.width, y0 = y + glyph.top, y1 = y0 + glyph.height;
        float slant0 = italic ? 1 - 0.25F * glyph.top : 0;
        float slant1 = italic ? 1 - 0.25F * (glyph.top + glyph.height) : 0;
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, a = c >>> 24;
        this.buffer.pos(x0 + slant0, y0, 0).tex(glyph.u0, glyph.v0).color(r, g, b, a).endVertex();
        this.buffer.pos(x0 + slant1, y1, 0).tex(glyph.u0, glyph.v1).color(r, g, b, a).endVertex();
        this.buffer.pos(x1 + slant1, y1, 0).tex(glyph.u1, glyph.v1).color(r, g, b, a).endVertex();
        this.buffer.pos(x1 + slant0, y0, 0).tex(glyph.u1, glyph.v0).color(r, g, b, a).endVertex();
    }
    private void flush()
    {
        this.buffer.finishDrawing();
        mchorse.bbs_mod.graphics.shader.ShaderProgram shader = this.buffer.getVertexFormat().hasUvOffset(0)
            ? mchorse.bbs_mod.client.PixelArt.bind(true) : null;
        try { this.uploader.draw(this.buffer); }
        finally { if (shader != null) shader.unbind(); }
    }

    private BakedGlyph bake(int codepoint)
    {
        BakedGlyph existing = this.baked.get(codepoint);
        if (existing != null) return existing;
        TrueTypeFontData.Glyph glyph = this.glyph(codepoint);
        int scale = glyph.missing ? 1 : this.data.oversample;
        int w = glyph.bounds.width, h = glyph.bounds.height;
        if (w == 0 || h == 0 || w + 2 > PAGE_SIZE || h + 2 > PAGE_SIZE)
        {
            BakedGlyph empty = new BakedGlyph();
            this.baked.put(codepoint, empty);
            return empty;
        }
        Page page = this.pages.isEmpty() ? null : this.pages.get(this.pages.size() - 1);
        if (page == null || !page.fit(w, h))
        {
            page = new Page(); this.pages.add(page); page.fit(w, h);
        }
        int px = page.x, py = page.y;
        Graphics2D graphics = page.image.createGraphics();
        try
        {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            if (glyph.missing) graphics.drawRect(px, py, w - 1, h - 1);
            else graphics.drawGlyphVector(glyph.vector, px - glyph.bounds.x, py - glyph.bounds.y);
        }
        finally { graphics.dispose(); }
        page.x += w + 2; page.rowHeight = Math.max(page.rowHeight, h + 2); page.dirty = true;
        BakedGlyph baked = new BakedGlyph();
        baked.page = page; baked.left = glyph.x;
        /* GlyphRenderer.draw subtracts the modern font baseline offset. */
        baked.top = glyph.missing ? glyph.y : glyph.y - 3;
        baked.width = w / (float) scale; baked.height = h / (float) scale;
        baked.u0 = px / (float) PAGE_SIZE; baked.u1 = (px + w) / (float) PAGE_SIZE;
        baked.v0 = py / (float) PAGE_SIZE; baked.v1 = (py + h) / (float) PAGE_SIZE;
        this.baked.put(codepoint, baked);
        return baked;
    }

    /** Must run on the client/render thread, like the manager's watch callbacks. */
    public void close()
    {
        if (this.closed) return;
        this.closed = true;
        for (Page page : this.pages) if (page.texture != null) page.texture.delete();
        this.pages.clear(); this.glyphs.clear(); this.baked.clear(); this.randomGlyphs.clear();
    }

    private static final class BakedGlyph
    {
        Page page;
        float left, top, width, height, u0, v0, u1, v1;
    }
    private static final class Page
    {
        final BufferedImage image = new BufferedImage(PAGE_SIZE, PAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        final ByteBuffer pixels = BufferUtils.createByteBuffer(PAGE_SIZE * PAGE_SIZE * 4);
        Texture texture;
        int x = 1, y = 1, rowHeight;
        boolean dirty;
        boolean fit(int w, int h)
        {
            if (this.x + w + 1 > PAGE_SIZE) { this.x = 1; this.y += this.rowHeight; this.rowHeight = 0; }
            return this.y + h + 1 <= PAGE_SIZE;
        }
        void upload()
        {
            if (!this.dirty) return;
            if (this.texture == null)
            {
                this.texture = new Texture();
                this.texture.setFilter(GL11.GL_NEAREST);
                this.texture.setWrap(GL12.GL_CLAMP_TO_EDGE);
            }
            this.pixels.clear();
            for (int py = 0; py < PAGE_SIZE; py++)
                for (int px = 0; px < PAGE_SIZE; px++)
                    this.pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) (this.image.getRGB(px, py) >>> 24));
            this.pixels.flip();
            int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            try
            {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                this.texture.bind();
                this.texture.uploadTexture(GL11.GL_TEXTURE_2D, 0, PAGE_SIZE, PAGE_SIZE, this.pixels);
            }
            finally { GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack); }
            this.dirty = false;
        }
    }
}
