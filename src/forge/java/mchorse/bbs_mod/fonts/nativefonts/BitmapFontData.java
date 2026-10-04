package mchorse.bbs_mod.fonts.nativefonts;

import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Original 1.20 bitmap/space/reference/unihex provider semantics, with CPU-owned pixels.
 * This class has no Minecraft, GL, AWT font rasterizer or installed-font dependency. */
public final class BitmapFontData
{
    public static final class Glyph
    {
        public final float advance, boldOffset, shadowOffset, scale, top;
        public final int width, height;
        public final String provider;
        private final BufferedImage image;
        private final int imageX, imageY, hexLeft;
        private final int[] rows;
        Glyph(float advance, float offset, float scale, float top, int width, int height,
            BufferedImage image, int x, int y, int[] rows, int hexLeft, String provider)
        {
            this.advance = advance; this.boldOffset = this.shadowOffset = offset; this.scale = scale;
            this.top = top; this.width = width; this.height = height; this.image = image;
            this.imageX = x; this.imageY = y; this.rows = rows; this.hexLeft = hexLeft; this.provider = provider;
        }
        public int pixel(int x, int y)
        {
            if (this.image != null) return this.image.getRGB(this.imageX + x, this.imageY + y);
            int column = this.hexLeft + x;
            return this.rows != null && column >= 0 && column < 32 && (this.rows[y] & 1 << (31 - column)) != 0 ? -1 : 0;
        }
    }
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Glyph missing;
    private BitmapFontData()
    {
        BufferedImage box = new BufferedImage(5, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 5; x++)
            box.setRGB(x, y, x == 0 || x == 4 || y == 0 || y == 7 ? -1 : 0);
        this.missing = new Glyph(6, 1, 1, 0, 5, 8, box, 0, 0, null, 0, "missing");
    }
    public Glyph glyph(int codepoint) { return this.glyphs.getOrDefault(codepoint, this.missing); }
    public boolean hasGlyph(int codepoint) { return this.glyphs.containsKey(codepoint); }
    public Set<Integer> codepoints() { return Collections.unmodifiableSet(this.glyphs.keySet()); }
    public int size() { return this.glyphs.size(); }

    public static BitmapFontData load(FontAssetCache cache) throws IOException
    {
        /* Called only after prepare(), which verifies the entire official source archives. */
        BitmapFontData data = new BitmapFontData();
        try (ZipFile client = new ZipFile(cache.path(FontAssetCache.CLIENT).toFile()))
        {
            data.readFont("minecraft:default", client, cache, new HashSet<>());
        }
        return data;
    }
    private void readFont(String name, ZipFile client, FontAssetCache cache, Set<String> stack) throws IOException
    {
        if (!stack.add(name)) throw new IOException("Цикл font reference: " + name);
        String path = resource(name, "font/", ".json");
        try (InputStream in = path.equals("assets/minecraft/font/include/unifont.json")
            ? Files.newInputStream(cache.path(FontAssetCache.UNIFONT_JSON)) : open(client, path))
        {
            JsonArray providers = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
            for (JsonElement value : providers)
            {
                JsonObject provider = value.getAsJsonObject();
                String type = provider.get("type").getAsString().replace("minecraft:", "");
                if (type.equals("reference")) this.readFont(provider.get("id").getAsString(), client, cache, stack);
                else if (type.equals("space"))
                {
                    for (Map.Entry<String, JsonElement> entry : provider.getAsJsonObject("advances").entrySet())
                        this.glyphs.putIfAbsent(entry.getKey().codePointAt(0), new Glyph(entry.getValue().getAsFloat(), 1, 1, 0, 0, 0, null, 0, 0, null, 0, "space"));
                }
                else if (type.equals("bitmap")) this.readBitmap(provider, client);
                else if (type.equals("unihex")) this.readUnihex(provider, cache);
                else throw new IOException("Неизвестный font provider: " + type);
            }
        }
        catch (RuntimeException e) { throw new IOException("Некорректный font provider " + name, e); }
        finally { stack.remove(name); }
    }
    private static String resource(String name, String prefix, String suffix) throws IOException
    {
        String[] parts = name.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "minecraft", path = parts.length == 2 ? parts[1] : parts[0];
        if (!namespace.matches("[a-z0-9_.-]+") || path.contains("..") || !path.matches("[a-z0-9/_.-]+"))
            throw new IOException("Некорректный resource id: " + name);
        return "assets/" + namespace + "/" + prefix + path + suffix;
    }
    private static InputStream open(ZipFile zip, String path) throws IOException
    {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) throw new FileNotFoundException(path);
        return zip.getInputStream(entry);
    }
    private void readBitmap(JsonObject provider, ZipFile client) throws IOException
    {
        String id = provider.get("file").getAsString();
        BufferedImage image;
        try (InputStream in = open(client, resource(id, "textures/", ""))) { image = ImageIO.read(in); }
        if (image == null) throw new IOException("Некорректный PNG: " + id);
        JsonArray chars = provider.getAsJsonArray("chars");
        int columns = chars.get(0).getAsString().codePointCount(0, chars.get(0).getAsString().length());
        int cellWidth = image.getWidth() / columns, cellHeight = image.getHeight() / chars.size();
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        int ascent = provider.get("ascent").getAsInt();
        float scale = height / (float) cellHeight;
        for (int row = 0; row < chars.size(); row++)
        {
            int[] codepoints = chars.get(row).getAsString().codePoints().toArray();
            if (codepoints.length != columns) throw new IOException("Неравные bitmap строки: " + id);
            for (int column = 0; column < columns; column++)
            {
                int cp = codepoints[column];
                if (cp == 0) continue;
                int occupied = 0;
                for (int x = cellWidth - 1; x >= 0 && occupied == 0; x--)
                    for (int y = 0; y < cellHeight; y++)
                        if ((image.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24) != 0) { occupied = x + 1; break; }
                Glyph glyph = new Glyph((int) (occupied * scale + .5F) + 1, 1, scale, 7 - ascent,
                    cellWidth, cellHeight, image, column * cellWidth, row * cellHeight, null, 0, id);
                this.glyphs.putIfAbsent(cp, glyph);
            }
        }
    }
    private void readUnihex(JsonObject provider, FontAssetCache cache) throws IOException
    {
        if (!"minecraft:font/unifont.zip".equals(provider.get("hex_file").getAsString()))
            throw new IOException("Неизвестный Unihex архив");
        List<int[]> overrides = new ArrayList<>();
        for (JsonElement element : provider.getAsJsonArray("size_overrides"))
        {
            JsonObject o = element.getAsJsonObject();
            overrides.add(new int[] {o.get("from").getAsString().codePointAt(0), o.get("to").getAsString().codePointAt(0), o.get("left").getAsInt(), o.get("right").getAsInt()});
        }
        try (ZipFile zip = new ZipFile(cache.path(FontAssetCache.UNIFONT_ZIP).toFile()))
        {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".hex")) continue;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.US_ASCII)))
                {
                    for (String line; (line = in.readLine()) != null;)
                    {
                        if (line.isEmpty()) continue;
                        int colon = line.indexOf(':');
                        if (colon < 4 || colon > 6) throw new IOException("Некорректная строка Unihex");
                        int cp = Integer.parseInt(line.substring(0, colon), 16), digits = (line.length() - colon - 1) / 16;
                        if ((digits != 2 && digits != 4 && digits != 6 && digits != 8) || line.length() != colon + 1 + digits * 16)
                            throw new IOException("Некорректная ширина Unihex U+" + Integer.toHexString(cp));
                        if (this.glyphs.containsKey(cp)) continue;
                        int[] rows = new int[16]; int mask = 0, bitWidth = digits * 4;
                        for (int y = 0; y < 16; y++)
                        {
                            rows[y] = (int) Long.parseLong(line.substring(colon + 1 + y * digits, colon + 1 + (y + 1) * digits), 16) << (32 - bitWidth);
                            mask |= rows[y];
                        }
                        int left = mask == 0 ? 0 : Integer.numberOfLeadingZeros(mask);
                        int right = mask == 0 ? bitWidth : 31 - Integer.numberOfTrailingZeros(mask);
                        for (int[] o : overrides) if (cp >= o[0] && cp <= o[1]) { left = o[2]; right = o[3]; break; }
                        int width = right - left + 1;
                        this.glyphs.put(cp, new Glyph(width / 2 + 1, .5F, .5F, 0, width, 16, null, 0, 0, rows, left, "unihex"));
                    }
                }
            }
        }
    }
}
