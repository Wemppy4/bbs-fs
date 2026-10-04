package mchorse.bbs_mod.fonts;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** CPU side of the native font backend. Layout uses the same SFNT tables as STB.
 * AWT supplies outline rasterization only; its hinting is not pixel-identical to STB. */
public final class TrueTypeFontData
{
    public final Metrics metrics;
    public final int oversample;
    public final Font font;
    public final FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);

    public TrueTypeFontData(byte[] bytes, int size, int oversample) throws Exception
    {
        this.metrics = Metrics.read(bytes, size);
        this.oversample = oversample;
        this.font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes))
            .deriveFont(this.metrics.unitsPerEm * this.metrics.scale * oversample);
    }

    public Glyph glyph(int codepoint)
    {
        if (!this.font.canDisplay(codepoint)) return Glyph.missing();
        GlyphVector vector = this.font.createGlyphVector(this.context, Character.toChars(codepoint));
        int id = vector.getGlyphCode(0);
        if (id == 0 || id >= this.metrics.glyphCount) return Glyph.missing();
        Rectangle bounds = vector.getGlyphPixelBounds(0, this.context, 0, 0);
        /* TrueTypeFont.TtfGlyph includes the hmtx left side bearing as well as the
         * raster box's x offset. Keep that convention, including its fractional part. */
        float x = this.metrics.bearing(id) * this.metrics.scale + bounds.x / (float) this.oversample;
        float y = this.metrics.ascent * this.metrics.scale + bounds.y / (float) this.oversample;
        return new Glyph(vector, bounds, this.metrics.advance(id) * this.metrics.scale, x, y, false);
    }

    public static final class Glyph
    {
        public final GlyphVector vector;
        public final Rectangle bounds;
        public final float advance, x, y;
        public final boolean missing;
        Glyph(GlyphVector vector, Rectangle bounds, float advance, float x, float y, boolean missing)
        {
            this.vector = vector; this.bounds = bounds; this.advance = advance;
            this.x = x; this.y = y; this.missing = missing;
        }
        static Glyph missing() { return new Glyph(null, new Rectangle(0, 0, 5, 8), 6, 0, 0, true); }
    }

    /** hhea gives STB's vertical metrics; hmtx gives unhinted advances, independently
     * of raster scale, operating system font substitution or Java's line metrics. */
    public static final class Metrics
    {
        public final int ascent, descent, gap, unitsPerEm, glyphCount, height, lineHeight;
        public final float scale;
        private final int[] advances, bearings;
        private Metrics(int ascent, int descent, int gap, int units, int[] advances, int[] bearings, int size)
        {
            this.ascent = ascent; this.descent = descent; this.gap = gap; this.unitsPerEm = units;
            this.advances = advances; this.bearings = bearings; this.glyphCount = advances.length;
            this.scale = size / (float) (ascent - descent);
            this.height = Math.max(1, Math.round(ascent * this.scale));
            this.lineHeight = Math.max(this.height + 1, Math.round((ascent - descent + gap) * this.scale));
        }
        public int advance(int glyph) { return this.advances[glyph]; }
        public int bearing(int glyph) { return this.bearings[glyph]; }

        public static Metrics read(byte[] bytes, int size)
        {
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            range(b, 0, 12);
            int signature = b.getInt(0);
            if (signature != 0x00010000 && signature != 0x4f54544f && signature != 0x74727565)
                throw new IllegalArgumentException("Not a TrueType/OpenType SFNT font");
            int hhea = table(b, 0x68686561, 36), head = table(b, 0x68656164, 54);
            int maxp = table(b, 0x6d617870, 6), hmtx = table(b, 0x686d7478, 0);
            int ascent = b.getShort(hhea + 4), descent = b.getShort(hhea + 6), gap = b.getShort(hhea + 8);
            int units = u16(b, head + 18), count = u16(b, maxp + 4), metrics = u16(b, hhea + 34);
            if (size <= 0 || ascent <= descent || units == 0 || count == 0 || metrics == 0 || metrics > count)
                throw new IllegalArgumentException("Invalid SFNT font metrics");
            table(b, 0x686d7478, metrics * 4 + (count - metrics) * 2);
            int[] advances = new int[count], bearings = new int[count];
            for (int i = 0; i < count; i++)
            {
                advances[i] = u16(b, hmtx + Math.min(i, metrics - 1) * 4);
                bearings[i] = b.getShort(hmtx + (i < metrics ? i * 4 + 2 : metrics * 4 + (i - metrics) * 2));
            }
            return new Metrics(ascent, descent, gap, units, advances, bearings, size);
        }
        private static int u16(ByteBuffer b, int at) { return b.getShort(at) & 65535; }
        private static int table(ByteBuffer b, int tag, int minLength)
        {
            int count = u16(b, 4);
            range(b, 12, count * 16);
            for (int i = 0; i < count; i++)
            {
                int record = 12 + i * 16;
                if (b.getInt(record) == tag)
                {
                    int offset = b.getInt(record + 8), length = b.getInt(record + 12);
                    range(b, offset, length);
                    if (length < minLength) throw new IllegalArgumentException("Truncated SFNT table");
                    return offset;
                }
            }
            throw new IllegalArgumentException("Missing SFNT table " + Integer.toHexString(tag));
        }
        private static void range(ByteBuffer b, int offset, int length)
        {
            if (offset < 0 || length < 0 || (long) offset + length > b.limit())
                throw new IllegalArgumentException("SFNT table outside file");
        }
    }
}
