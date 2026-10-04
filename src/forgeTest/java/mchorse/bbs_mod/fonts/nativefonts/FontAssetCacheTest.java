package mchorse.bbs_mod.fonts.nativefonts;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FontAssetCacheTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] GOOD = "abc".getBytes(StandardCharsets.US_ASCII);
    private static final FontAssetCache.Asset ASSET = new FontAssetCache.Asset("fixture.bin", "a9993e364706816aba3e25717850c26c9cd0d89d", 3, "https://example.invalid/fixture");
    private Path root() { return temporary.getRoot().toPath(); }
    private static FontAssetCache.Downloader offline()
    { return asset -> { throw new IOException("offline fixture"); }; }

    @Test public void firstDownloadThenOfflineReadsExactlySameVerifiedCache() throws Exception
    {
        AtomicInteger calls = new AtomicInteger();
        FontAssetCache cache = new FontAssetCache(root(), asset -> { calls.incrementAndGet(); return new ByteArrayInputStream(GOOD); });
        Path file = cache.ensure(ASSET, message -> {});
        assertArrayEquals(GOOD, Files.readAllBytes(file));
        assertTrue(FontAssetCache.isValid(file, ASSET));
        assertEquals(1, calls.get());
        assertEquals(file, new FontAssetCache(root(), offline()).ensure(ASSET, message -> {}));
    }
    @Test public void detectsSameLengthCorruptionAndRepairsIt() throws Exception
    {
        Path file = root().resolve(ASSET.name); Files.write(file, "xyz".getBytes(StandardCharsets.US_ASCII));
        assertFalse(FontAssetCache.isValid(file, ASSET));
        new FontAssetCache(root(), asset -> new ByteArrayInputStream(GOOD)).ensure(ASSET, message -> {});
        assertTrue(FontAssetCache.isValid(file, ASSET));
    }
    @Test public void rejectsDamagedCacheWhenOfflineAndLeavesEvidence() throws Exception
    {
        Path file = root().resolve(ASSET.name); Files.write(file, "xyz".getBytes(StandardCharsets.US_ASCII));
        try { new FontAssetCache(root(), offline()).ensure(ASSET, message -> {}); fail("Must not serve damaged cache"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("offline fixture")); }
        assertFalse(FontAssetCache.isValid(file, ASSET));
        assertNoParts();
    }
    @Test public void missingCacheOfflineFailsWithoutCreatingTarget() throws Exception
    {
        try { new FontAssetCache(root(), offline()).ensure(ASSET, message -> {}); fail("Must report first-run network failure"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("fixture.bin")); }
        assertFalse(Files.exists(root().resolve(ASSET.name)));
        assertNoParts();
    }
    @Test public void rejectsWrongChecksumAndOversizeDownloads() throws Exception
    {
        for (String value : new String[] {"xyz", "abcdef", "a"})
        {
            FontAssetCache cache = new FontAssetCache(root(), asset -> new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII)));
            try { cache.ensure(ASSET, message -> {}); fail("Must reject invalid downloaded bytes"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("fixture.bin")); }
            assertFalse(Files.exists(root().resolve(ASSET.name)));
            assertNoParts();
        }
    }
    private void assertNoParts() throws Exception
    {
        try (java.util.stream.Stream<Path> files = Files.list(root()))
        { assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".part"))); }
    }
}
