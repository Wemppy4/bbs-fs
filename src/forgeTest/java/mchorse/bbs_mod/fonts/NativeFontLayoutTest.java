package mchorse.bbs_mod.fonts;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static org.junit.Assert.*;

public class NativeFontLayoutTest
{
    @Test public void readsBbsMetricsAndSharedFinalHmtxAdvance()
    {
        TrueTypeFontData.Metrics metrics = TrueTypeFontData.Metrics.read(fontTables(), 20);
        assertEquals(16, metrics.height);
        assertEquals(22, metrics.lineHeight);
        assertEquals(0.02F, metrics.scale, 0.00001F);
        assertEquals(500, metrics.advance(0));
        assertEquals(650, metrics.advance(1));
        assertEquals(650, metrics.advance(2));
        assertEquals(-30, metrics.bearing(2));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsTruncatedTable()
    {
        byte[] bytes = fontTables();
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(12 + 3 * 16 + 12, 6);
        TrueTypeFontData.Metrics.read(bytes, 20);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsTableOutsideFile()
    {
        byte[] bytes = fontTables();
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(20, Integer.MAX_VALUE);
        TrueTypeFontData.Metrics.read(bytes, 20);
    }
    @Test public void sumsFractionalAdvancesAndResetsBoldOnColor()
    {
        assertEquals(8, FontTextLayout.width("abc", cp -> 2.5));
        assertEquals(9, FontTextLayout.width("\u00a7la\u00a7cb\u00a7rc", cp -> 2.5));
        assertEquals(7, FontTextLayout.width("\u00a7lAB", cp -> 2.5));
    }
    @Test public void wrapsFormattingAndCyrillicWithoutCountingCodes()
    {
        assertEquals(Arrays.asList("\u00a7cПривет", "\u00a7cмир"), FontTextLayout.wrap("\u00a7cПривет мир", 12, cp -> 2));
        assertEquals(Arrays.asList("\u00a7lAB", "\u00a7lCD"), FontTextLayout.wrap("\u00a7lABCD", 6, cp -> 2));
        assertEquals(Arrays.asList("A", "B", ""), FontTextLayout.wrap("A\nB\n", 100, cp -> 2));
    }
    @Test public void keepsWholeCodepointsAndMakesProgressWhenNarrow()
    {
        String supplementary = new String(Character.toChars(0x1f642));
        assertEquals(Arrays.asList(supplementary, "A"), FontTextLayout.wrap(supplementary + "A", 0, cp -> 2));
        assertEquals(supplementary, FontTextLayout.trim(supplementary + "A", 2, false, cp -> 2));
        assertEquals("\u00a7cB", FontTextLayout.trim("\u00a7cAB", 2, true, cp -> 2));
    }
    private static byte[] fontTables()
    {
        ByteBuffer b = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0x00010000).putShort((short) 4);
        table(b, 0, 0x68656164, 80, 54);
        table(b, 1, 0x68686561, 136, 36);
        table(b, 2, 0x6d617870, 172, 6);
        table(b, 3, 0x686d7478, 180, 10);
        b.putShort(98, (short) 1000);
        b.putShort(140, (short) 800).putShort(142, (short) -200).putShort(144, (short) 100);
        b.putShort(170, (short) 2).putShort(176, (short) 3);
        b.putShort(180, (short) 500).putShort(182, (short) 20);
        b.putShort(184, (short) 650).putShort(186, (short) 10).putShort(188, (short) -30);
        return b.array();
    }
    private static void table(ByteBuffer b, int id, int tag, int offset, int length)
    {
        b.position(12 + id * 16);
        b.putInt(tag).putInt(0).putInt(offset).putInt(length);
    }
}
