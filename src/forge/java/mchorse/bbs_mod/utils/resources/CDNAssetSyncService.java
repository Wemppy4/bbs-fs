package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * API for working with custom CDN service. Once I'll upload the script,
 * I'll add the URL to the script over here.
 */
public class CDNAssetSyncService
{
    private final URI cdn;
    private final Path assets;
    private final Consumer<Pair<CDNStatus, IKey>> callback;

    private static String sha1OfFile(Path path) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            try (InputStream in = Files.newInputStream(path))
            {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = in.read(buffer)) != -1)
                {
                    digest.update(buffer, 0, read);
                }
            }

            return toHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    private static String toHex(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder(bytes.length * 2);

        for (byte b : bytes)
        {
            int value = b & 0xFF;

            if (value < 16) builder.append('0');

            builder.append(Integer.toHexString(value));
        }

        return builder.toString();
    }

    private static List<RemoteFile> parseRemoteFiles(String json)
    {
        List<RemoteFile> files = new ArrayList<>();
        ListType list = DataToString.listFromString(json);

        if (list == null)
        {
            return files;
        }

        for (BaseType baseType : list)
        {
            if (!baseType.isMap())
            {
                continue;
            }

            MapType map = baseType.asMap();

            files.add(new RemoteFile(map.getString("path"), map.getInt("size"), map.getString("sha1")));
        }

        return files;
    }

    private static String normalizeRelativePath(String path)
    {
        return path.replace(FileSystems.getDefault().getSeparator(), "/");
    }

    public CDNAssetSyncService(String baseUrl, Path localRootDir, Consumer<Pair<CDNStatus, IKey>> callback)
    {
        this.cdn = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.assets = localRootDir.toAbsolutePath().normalize();
        this.callback = callback;
    }

    private void issueStatus(CDNStatus status, IKey message)
    {
        if (this.callback != null)
        {
            this.callback.accept(new Pair<>(status, message));
        }
    }

    private List<RemoteFile> fetchRemoteFiles() throws IOException, InterruptedException
    {
        HttpURLConnection connection = this.request("/files", null, null, null);
        try
        {
            int status = connection.getResponseCode();
            if (status != 200)
            {
                this.issueStatus(CDNStatus.FAILURE, UIKeys.CDN_STATUS_FAILED_FETCH);
                throw new IOException("Failed to fetch /files: HTTP " + status);
            }
            return parseRemoteFiles(readResponse(connection));
        }
        finally { connection.disconnect(); }
    }

    public void syncOnce() throws IOException, InterruptedException
    {
        List<RemoteFile> remoteFiles = this.fetchRemoteFiles();
        Map<String, String> sha1Map = this.buildLocalSha1Map();

        for (RemoteFile remoteFile : remoteFiles)
        {
            String normalizedPath = remoteFile.path.replace("/", FileSystems.getDefault().getSeparator());
            Path localPath = this.resolveLocalPath(normalizedPath);
            String localSha1 = sha1Map.get(remoteFile.path);
            boolean needsDownload = !remoteFile.sha1.equalsIgnoreCase(localSha1);

            if (needsDownload)
            {
                System.out.println("[CDN] Downloading: " + remoteFile.path);

                this.downloadFile(remoteFile.path, localPath);
                this.issueStatus(CDNStatus.DOWNLOADED, UIKeys.CDN_STATUS_DOWNLOADED.format(remoteFile.path));
            }
        }

        this.issueStatus(CDNStatus.SUCCESS, UIKeys.CDN_STATUS_SUCCESS_DOWNLOADING);
    }

    private Map<String, String> buildLocalSha1Map() throws IOException
    {
        Map<String, String> result = new HashMap<>();

        Files.walkFileTree(this.assets, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                if (!attrs.isRegularFile())
                {
                    return FileVisitResult.CONTINUE;
                }

                String relativePath = normalizeRelativePath(CDNAssetSyncService.this.assets.relativize(file).toString());
                String sha1 = sha1OfFile(file);

                result.put(relativePath, sha1);

                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    private void downloadFile(String remotePath, Path localPath) throws IOException, InterruptedException
    {
        byte[] form = ("path=" + URLEncoder.encode(remotePath, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = this.request("/file", form, "application/x-www-form-urlencoded", null);
        try
        {
            int status = connection.getResponseCode();
            if (status != 200)
            {
                this.issueStatus(CDNStatus.FAILURE, UIKeys.CDN_STATUS_FAILED_DOWNLOADING.format(remotePath));
                throw new IOException("Failed to download " + remotePath + ": HTTP " + status);
            }
            Files.createDirectories(localPath.getParent());
            Path temporary = Files.createTempFile(localPath.getParent(), ".bbs-cdn-", ".part");
            try
            {
                try (InputStream in = connection.getInputStream())
                { Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING); }
                Files.move(temporary, localPath, StandardCopyOption.REPLACE_EXISTING);
            }
            finally { Files.deleteIfExists(temporary); }
        }
        finally { connection.disconnect(); }
    }

    public void pushChangedFiles(String uploadToken) throws IOException, InterruptedException
    {
        List<RemoteFile> remoteFiles = this.fetchRemoteFiles();
        Map<String, String> remoteSha1 = new HashMap<>();

        for (RemoteFile rf : remoteFiles)
        {
            remoteSha1.put(rf.path, rf.sha1);
        }

        Files.walkFileTree(this.assets, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                if (!attrs.isRegularFile())
                {
                    return FileVisitResult.CONTINUE;
                }

                String relativePath = normalizeRelativePath(CDNAssetSyncService.this.assets.relativize(file).toString());

                String localSha1 = sha1OfFile(file);
                String remote = remoteSha1.get(relativePath);

                boolean needsUpload = (remote == null) || !remote.equalsIgnoreCase(localSha1);

                if (needsUpload)
                {
                    System.out.println("[CDN] Uploading: " + relativePath);

                    try
                    {
                        CDNAssetSyncService.this.uploadFileToCDN(file, relativePath, uploadToken);
                        CDNAssetSyncService.this.issueStatus(CDNStatus.UPLOADED, UIKeys.CDN_STATUS_UPLOADED.format(relativePath));
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        throw new IOException("Upload interrupted for " + relativePath, e);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        for (RemoteFile remoteFile : remoteFiles)
        {
            String normalizedPath = remoteFile.path.replace("/", FileSystems.getDefault().getSeparator());
            Path localPath = this.resolveLocalPath(normalizedPath);

            if (!Files.exists(localPath))
            {
                System.out.println("[CDN] Deleting remote: " + remoteFile.path);

                try
                {
                    this.deleteRemoteFile(remoteFile.path, uploadToken);
                    this.issueStatus(CDNStatus.DELETED, UIKeys.CDN_STATUS_DELETED.format(remoteFile.path));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();

                    throw new IOException("Delete interrupted for " + remoteFile.path, e);
                }
            }
        }

        this.issueStatus(CDNStatus.SUCCESS, UIKeys.CDN_STATUS_SUCCESS_UPLOADING);
    }

    private void uploadFileToCDN(Path file, String remotePath, String uploadToken) throws IOException, InterruptedException
    {
        String boundary = "----CdnBoundary" + System.currentTimeMillis();

        byte[] fileBytes = Files.readAllBytes(file);
        String fileName = file.getFileName().toString();
        String mimeType = Files.probeContentType(file);

        if (mimeType == null)
        {
            mimeType = "application/octet-stream";
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true);

        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"path\"\r\n\r\n");
        writer.append(remotePath).append("\r\n");

        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        writer.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
        writer.flush();

        baos.write(fileBytes);
        baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

        writer.append("--").append(boundary).append("--\r\n");
        writer.flush();

        byte[] body = baos.toByteArray();

        HttpURLConnection connection = this.request("/upload", body, "multipart/form-data; boundary=" + boundary, uploadToken);
        try
        {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Failed to upload " + remotePath + ": HTTP " + status + " body=" + readResponse(connection));
            readResponse(connection);
        }
        finally { connection.disconnect(); }
    }

    private void deleteRemoteFile(String remotePath, String uploadToken) throws IOException, InterruptedException
    {
        byte[] form = ("path=" + URLEncoder.encode(remotePath, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = this.request("/delete", form, "application/x-www-form-urlencoded", uploadToken);
        try
        {
            int status = connection.getResponseCode();
            if (status != 200 && status != 204 && status != 404)
                throw new IOException("Failed to delete " + remotePath + ": HTTP " + status + " body=" + readResponse(connection));
            readResponse(connection);
        }
        finally { connection.disconnect(); }
    }

    /** Same CDN protocol using Java 8's HTTP transport. Called only by the UI's worker threads. */
    private HttpURLConnection request(String route, byte[] body, String type, String token) throws IOException, InterruptedException
    {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("CDN request interrupted");
        URI uri = this.cdn.resolve(route);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
            throw new IOException("CDN URL must use HTTP or HTTPS");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(body == null ? "GET" : "POST");
        if (type != null) connection.setRequestProperty("Content-Type", type);
        if (token != null) connection.setRequestProperty("X-Token", token);
        if (body != null)
        {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            catch (IOException e) { connection.disconnect(); throw e; }
        }
        return connection;
    }

    private static String readResponse(HttpURLConnection connection) throws IOException
    {
        InputStream stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream result = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) result.write(buffer, 0, count);
            return new String(result.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private Path resolveLocalPath(String path) throws IOException
    {
        Path result = this.assets.resolve(path).normalize();
        Path root = this.assets.toFile().getCanonicalFile().toPath();
        if (!result.startsWith(this.assets) || !result.toFile().getCanonicalFile().toPath().startsWith(root))
            throw new IOException("CDN path leaves the asset library: " + path);
        return result;
    }

    public static class RemoteFile
    {
        public final String path;
        public final long size;
        public final String sha1;

        public RemoteFile(String path, long size, String sha1)
        {
            this.path = path;
            this.size = size;
            this.sha1 = sha1;
        }
    }

    public static enum CDNStatus
    {
        SUCCESS(Colors.GREEN), DOWNLOADED(Colors.BLUE), UPLOADED(Colors.ACTIVE), DELETED(Colors.ORANGE), FAILURE(Colors.RED);

        public final int color;

        private CDNStatus(int color)
        {
            this.color = color;
        }
    }
}