package mchorse.bbs_mod.fonts.nativefonts;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

/** Version-pinned official sources. Never executes code from the downloaded client archive.
 * Metadata provenance: piston-meta.mojang.com 1.20.4 version manifest and asset index 12.
 * Only the three files containing font data are downloaded; no game installation is needed. */
public final class FontAssetCache
{
    public static final class Asset
    {
        public final String name, sha1, url;
        public final long size;
        Asset(String name, String sha1, long size, String url)
        { this.name = name; this.sha1 = sha1; this.size = size; this.url = url; }
    }
    public static final Asset CLIENT = new Asset("client-1.20.4.jar", "fd19469fed4a4b4c15b2d5133985f0e3e7816a8a", 24445539,
        "https://piston-data.mojang.com/v1/objects/fd19469fed4a4b4c15b2d5133985f0e3e7816a8a/client.jar");
    public static final Asset UNIFONT_JSON = object("unifont.json", "f8d4768707b20359f2f7660346bd3a84b6ee27b1", 1879);
    public static final Asset UNIFONT_ZIP = object("unifont.zip", "109663114d0099c48a703626c8462e07d802e08b", 1615995);
    public static final List<Asset> ASSETS = Collections.unmodifiableList(Arrays.asList(CLIENT, UNIFONT_JSON, UNIFONT_ZIP));
    public interface Downloader { InputStream open(Asset asset) throws IOException; }
    private final Path root;
    private final Downloader downloader;
    private static Asset object(String name, String hash, long size)
    { return new Asset(name, hash, size, "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash); }
    public FontAssetCache(Path root) { this(root, FontAssetCache::download); }
    public FontAssetCache(Path root, Downloader downloader) { this.root = root; this.downloader = downloader; }
    public Path path(Asset asset) { return this.root.resolve(asset.name); }

    public void prepare(Consumer<String> status) throws IOException
    {
        Files.createDirectories(this.root);
        for (Asset asset : ASSETS) this.ensure(asset, status);
    }

    public Path ensure(Asset asset, Consumer<String> status) throws IOException
    {
        Path file = path(asset);
        status.accept("Проверка " + asset.name);
        if (isValid(file, asset)) return file;
        boolean damaged = Files.exists(file);
        status.accept((damaged ? "Восстановление " : "Загрузка ") + asset.name);
        Files.createDirectories(this.root);
        Path temporary = Files.createTempFile(this.root, asset.name + ".", ".part");
        try
        {
            MessageDigest digest = sha1();
            long received = 0, announced = -1;
            try (InputStream in = this.downloader.open(asset); OutputStream out = Files.newOutputStream(temporary))
            {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) != -1;)
                {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Загрузка прервана");
                    received += n;
                    if (received > asset.size) throw new IOException("Неверный размер " + asset.name);
                    out.write(buffer, 0, n); digest.update(buffer, 0, n);
                    long percent = received * 100 / asset.size;
                    if (percent != announced) { status.accept("Загрузка " + asset.name + ": " + percent + "%"); announced = percent; }
                }
            }
            if (received != asset.size || !hex(digest.digest()).equals(asset.sha1))
                throw new IOException("Контрольная сумма SHA-1 не совпала: " + asset.name);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            return file;
        }
        catch (IOException e)
        {
            throw new IOException((damaged ? "Кэш повреждён; " : "") + asset.name + ": " + e.getMessage(), e);
        }
        finally { Files.deleteIfExists(temporary); }
    }

    public static boolean isValid(Path file, Asset asset) throws IOException
    {
        if (!Files.isRegularFile(file) || Files.size(file) != asset.size) return false;
        MessageDigest digest = sha1();
        try (InputStream in = Files.newInputStream(file))
        {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        return hex(digest.digest()).equals(asset.sha1);
    }
    private static MessageDigest sha1()
    {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static String hex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder(40);
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private static InputStream download(Asset asset) throws IOException
    {
        final HttpURLConnection connection = (HttpURLConnection) new URL(asset.url).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "BBS-FS-Forge/1.12.2 (Minecraft font resources)");
        int response = connection.getResponseCode();
        if (response != 200) { connection.disconnect(); throw new IOException("HTTP " + response); }
        long length = connection.getContentLengthLong();
        if (length >= 0 && length != asset.size) { connection.disconnect(); throw new IOException("Неверный Content-Length"); }
        return new FilterInputStream(connection.getInputStream())
        {
            @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
        };
    }
}
