package mchorse.bbs_mod.fonts.nativefonts;

import mchorse.bbs_mod.fonts.FontTextLayout;
import org.junit.Test;
import org.junit.Assume;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.Assert.*;

public class BitmapFontDataTest
{
    @Test public void usesPerProviderHalfPixelBoldAdvanceAndWrapsWithoutDrift()
    {
        assertEquals(19, FontTextLayout.width("\u00a7l中中", cp -> 9, cp -> .5));
        assertEquals(Arrays.asList("\u00a7l中中", "\u00a7l中"), FontTextLayout.wrap("\u00a7l中中中", 19, cp -> 9, cp -> .5));
        assertEquals("\u00a7l中", FontTextLayout.trim("\u00a7l中中", 10, false, cp -> 9, cp -> .5));
    }
    /** Optional integration fixture is the same validated production cache. No network in tests. */
    @Test public void officialProvidersHaveOriginalCyrillicAndUnihexMetrics() throws Exception
    {
        Path root = Paths.get("run-forge1122/config/bbs/runtime/fonts-1.20.4");
        Assume.assumeTrue(Files.exists(root.resolve(FontAssetCache.CLIENT.name)));
        FontAssetCache cache = new FontAssetCache(root, asset -> { throw new IOException("Tests are offline"); });
        cache.prepare(message -> {});
        BitmapFontData data = BitmapFontData.load(cache);
        assertTrue(data.size() > 100000);
        assertEquals(67, FontTextLayout.width("Перемещение", cp -> data.glyph(cp).advance));
        assertEquals(78, FontTextLayout.width("\u00a7lПеремещение", cp -> data.glyph(cp).advance, cp -> data.glyph(cp).boldOffset));
        assertEquals(6, data.glyph('A').advance, 0);
        assertEquals(8, data.glyph('Ж').advance, 0);
        for (char cp : new char[] {'Ё', 'ё', 'Й', 'й'})
        {
            BitmapFontData.Glyph glyph = data.glyph(cp);
            assertEquals("minecraft:font/accented.png", glyph.provider);
            assertEquals(-3, glyph.top, 0);
            assertEquals(12, glyph.height);
            assertEquals(6, glyph.advance, 0);
        }
        assertEquals(4, data.glyph(' ').advance, 0);
        assertEquals(0, data.glyph(0x200c).advance, 0);
        BitmapFontData.Glyph han = data.glyph('中');
        assertEquals("unihex", han.provider);
        assertEquals(16, han.width);
        assertEquals(9, han.advance, 0);
        assertEquals(.5F, han.boldOffset, 0);
        assertEquals(.5F, han.shadowOffset, 0);
        assertEquals("missing", data.glyph(0x10fffd).provider);
        assertEquals(6, data.glyph(0x10fffd).advance, 0);
        assertEquals(-1, data.glyph(0x10fffd).pixel(0, 0));
        assertEquals(0, data.glyph(0x10fffd).pixel(2, 3));
    }
}
