package mchorse.bbs_mod.fonts;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** On-demand user TTF/OTF fonts backed by native 1.12 atlases. Public lifecycle,
 * oversampling keys, expiration and filesystem invalidation follow BBS FontManager.
 * Rasterization is AWT; layout metrics come directly from the font's SFNT tables. */
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
        String lower = path.toLowerCase(java.util.Locale.ROOT);

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

        NativeTrueTypeFontRenderer renderer = null;

        try
        {
            TrueTypeFontData data = new TrueTypeFontData(Files.readAllBytes(file.toPath()), key.size, key.oversample);
            int active = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            int binding = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            try { renderer = new NativeTrueTypeFontRenderer(data); }
            finally
            {
                net.minecraft.client.renderer.GlStateManager.bindTexture(binding);
                net.minecraft.client.renderer.GlStateManager.setActiveTexture(active);
            }
            FontRenderer font = new FontRenderer();
            font.setRenderer(renderer, data.metrics.height, data.metrics.lineHeight);
            return new FontEntry(font, renderer);
        }
        catch (Exception e)
        {
            if (renderer != null) renderer.close();
            e.printStackTrace();
            return new FontEntry(null, null);
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

        private final NativeTrueTypeFontRenderer storage;

        public long lastUsed;

        public FontEntry(FontRenderer font, NativeTrueTypeFontRenderer storage)
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
