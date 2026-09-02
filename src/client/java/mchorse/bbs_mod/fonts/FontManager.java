package mchorse.bbs_mod.fonts;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontFilterType;
import net.minecraft.client.font.FreeTypeUtil;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TrueTypeFont;
import net.minecraft.util.Identifier;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hands out {@link FontRenderer}s over TrueType files the user dropped into the
 * "fonts" folder of the assets.
 *
 * <p>Minecraft's own font system is fed from resource packs and only through a full
 * resource reload - neither hot, nor addressable by a file path, nor something that
 * travels with a film. The pieces below it, however, are public: a {@link TrueTypeFont}
 * rasterizes glyphs, a {@link FontStorage} bakes them into atlases, and a
 * {@link TextRenderer} lays them out. Assembled by hand they give back a renderer that
 * every existing draw call takes as-is - no styled text, no formatting code round trip.</p>
 */
public class FontManager implements IWatchDogListener
{
    public static final String FOLDER = "fonts";

    public static final int MIN_SIZE = 4;
    public static final int MAX_SIZE = 96;

    /** A font nobody asked for during this long hands its glyph atlases back. */
    private static final long DELETE_MS = 30_000;

    /**
     * Glyphs are baked into 256x256 atlas pages, and a glyph that doesn't fit into a page
     * never gets drawn at all. Its bitmap is about this tall and, for a wide letter, about
     * as much again in width - which is where the page runs out.
     */
    private static final int MAX_RASTER = 192;

    /**
     * For text whose on-screen size isn't knowable up front - a label standing in the
     * world is drawn at 1/16 of a block and then scaled by whatever the camera and its
     * transform do to it. It gets the finest raster the atlas can hold.
     */
    public static final float MAX_DETAIL = Float.MAX_VALUE;

    private final Map<FontKey, FontEntry> fonts = new HashMap<>();

    public static boolean isFont(String path)
    {
        String lower = path.toLowerCase();

        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    /** Every font file sitting in the assets, for the pickers. */
    public static List<Link> getFontLinks()
    {
        List<Link> links = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets(FOLDER)))
        {
            if (isFont(link.path))
            {
                links.add(link);
            }
        }

        return links;
    }

    public static File getFolder()
    {
        return new File(BBSMod.getAssetsFolder(), FOLDER);
    }

    /**
     * The font at the given link, laid out at the given size, or null when there is no
     * such file or it isn't a font - the caller then falls back to the default renderer.
     *
     * <p>The scale is how many screen pixels one unit of that layout is going to cover
     * where the text is about to be drawn. Glyph atlases are point-sampled (the text
     * render layer binds them with blur off), so a glyph only looks right while its
     * atlas pixels land about one to one on screen pixels: rasterized too coarsely it
     * comes out as chewed-up stair steps when it's blown up, too finely and the
     * sampling throws most of it away. Callers that know their scale - the interface,
     * a subtitle - pass it; the rest pass {@link #MAX_DETAIL}.</p>
     */
    public FontRenderer get(Link link, int size, float scale)
    {
        if (link == null)
        {
            return null;
        }

        FontKey key = new FontKey(link, Math.max(MIN_SIZE, Math.min(MAX_SIZE, size)), scale);
        FontEntry entry = this.fonts.get(key);

        if (entry == null)
        {
            entry = this.load(key);

            this.fonts.put(key, entry);
        }

        entry.lastUsed = System.currentTimeMillis();

        return entry.font;
    }

    /** Drops the atlases of fonts that went unused, see {@link #DELETE_MS}. */
    public void update()
    {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<FontKey, FontEntry>> it = this.fonts.entrySet().iterator();

        while (it.hasNext())
        {
            FontEntry entry = it.next().getValue();

            if (now - entry.lastUsed > DELETE_MS)
            {
                entry.delete();
                it.remove();
            }
        }
    }

    /** A font file that changed on disk gets rebuilt on the next request. */
    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        if (!isFont(path.toString()))
        {
            return;
        }

        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link == null)
        {
            return;
        }

        Iterator<Map.Entry<FontKey, FontEntry>> it = this.fonts.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<FontKey, FontEntry> entry = it.next();

            if (entry.getKey().link.equals(link))
            {
                entry.getValue().delete();
                it.remove();
            }
        }
    }

    public void delete()
    {
        for (FontEntry entry : this.fonts.values())
        {
            entry.delete();
        }

        this.fonts.clear();
    }

    private FontEntry load(FontKey key)
    {
        File file = BBSMod.getProvider().getFile(key.link);

        if (file == null || !file.isFile())
        {
            return new FontEntry(null, null);
        }

        ByteBuffer buffer = null;
        FT_Face face = null;
        boolean adopted = false;

        try
        {
            byte[] bytes = Files.readAllBytes(file.toPath());

            /* FreeType keeps reading out of this buffer for as long as the font lives, so it
             * has to be off-heap - and it's TrueTypeFont that frees it when it's closed. */
            buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            /* Since 1.21.1 the game rasterizes with FreeType, not stb: the face is built the
             * same way vanilla's own TrueTypeFontLoader builds it, under FreeType's lock. */
            synchronized (FreeTypeUtil.LOCK)
            {
                try (MemoryStack stack = MemoryStack.stackPush())
                {
                    PointerBuffer pointer = stack.mallocPointer(1);

                    FreeTypeUtil.checkFatalError(FreeType.FT_New_Memory_Face(FreeTypeUtil.initialize(), buffer, 0L, pointer), "Initializing font face");

                    face = FT_Face.create(pointer.get());
                }

                if (!"TrueType".equals(FreeType.FT_Get_Font_Format(face)))
                {
                    throw new IllegalArgumentException("Not a TrueType font: " + key.link);
                }

                FreeTypeUtil.checkFatalError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
            }

            FontMetrics metrics = FontMetrics.read(face, key.size);
            TrueTypeFont ttf = new TrueTypeFont(buffer, face, key.size, key.oversample, 0F, 0F, "");

            adopted = true;

            FontStorage storage = new FontStorage(MinecraftClient.getInstance().getTextureManager(), this.getStorageId(key));

            storage.setFonts(Collections.singletonList(new Font.FontFilterPair(ttf, FontFilterType.FilterMap.NO_FILTER)), Collections.emptySet());

            FontRenderer font = new FontRenderer();

            /* Every identifier resolves to the one storage: the font is picked by which
             * renderer is drawing, not by the style of the text being drawn. */
            font.setRenderer(new TextRenderer((id) -> storage, false), metrics.height, metrics.lineHeight);

            return new FontEntry(font, storage);
        }
        catch (Exception e)
        {
            e.printStackTrace();

            if (!adopted)
            {
                if (face != null)
                {
                    synchronized (FreeTypeUtil.LOCK)
                    {
                        FreeType.FT_Done_Face(face);
                    }
                }

                if (buffer != null)
                {
                    MemoryUtil.memFree(buffer);
                }
            }

            return new FontEntry(null, null);
        }
    }

    /** Names the storage's atlas pages - it has to be unique per font and per size. */
    private Identifier getStorageId(FontKey key)
    {
        StringBuilder builder = new StringBuilder("font/");

        for (char c : key.link.path.toLowerCase().toCharArray())
        {
            boolean valid = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-' || c == '/';

            builder.append(valid ? c : '_');
        }

        return Identifier.of(BBSMod.MOD_ID, builder.append('_').append(key.size).append('x').append(key.oversample).toString());
    }

    /**
     * Vertical metrics of a font at a given layout size. {@link TextRenderer#fontHeight}
     * is a constant 9 no matter which font is drawing, so nothing that lays text out can
     * ask the renderer how tall it is.
     */
    private static class FontMetrics
    {
        public final int height;
        public final int lineHeight;

        public static FontMetrics read(FT_Face face, int size)
        {
            /* FreeType keeps the vertical metrics in font units, so they scale by the design
             * grid the same way stb's scale-for-pixel-height did. face.height() is already
             * ascender - descender + line gap. */
            int unitsPerEm = face.units_per_EM();
            float scale = unitsPerEm == 0 ? 0F : size / (float) unitsPerEm;
            int height = Math.max(1, Math.round(face.ascender() * scale));

            return new FontMetrics(height, Math.max(height + 1, Math.round(face.height() * scale)));
        }

        public FontMetrics(int height, int lineHeight)
        {
            this.height = height;
            this.lineHeight = lineHeight;
        }
    }

    private static class FontKey
    {
        public final Link link;
        public final int size;

        /**
         * How many times finer than the layout the glyphs are rasterized. Rounded to a
         * whole number on purpose: an animated scale would otherwise build a fresh set
         * of atlases on every frame it moved through.
         */
        public final int oversample;

        public FontKey(Link link, int size, float scale)
        {
            this.link = link;
            this.size = size;
            this.oversample = Math.max(1, Math.min(Math.max(1, MAX_RASTER / size), Math.round(Math.min(scale, MAX_RASTER))));
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof FontKey)
            {
                FontKey key = (FontKey) obj;

                return this.size == key.size && this.oversample == key.oversample && Objects.equals(this.link, key.link);
            }

            return false;
        }

        @Override
        public int hashCode()
        {
            return (this.link.hashCode() * 31 + this.size) * 31 + this.oversample;
        }
    }

    private static class FontEntry
    {
        /** null when the file is missing or isn't a font. */
        public final FontRenderer font;

        private final FontStorage storage;

        public long lastUsed;

        public FontEntry(FontRenderer font, FontStorage storage)
        {
            this.font = font;
            this.storage = storage;
            this.lastUsed = System.currentTimeMillis();
        }

        /** Closing the storage also closes the fonts that were handed to it. */
        public void delete()
        {
            if (this.storage != null)
            {
                this.storage.close();
            }
        }
    }
}
