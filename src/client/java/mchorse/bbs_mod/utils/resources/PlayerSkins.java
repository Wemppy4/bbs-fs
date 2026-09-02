package mchorse.bbs_mod.utils.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Skins by nickname, behind the <code>player:</code> source: <code>player:Notch</code> is
 * that player's skin, wherever a texture link is accepted.
 *
 * <p>A skin is looked up in three places, in this order: the profile of a player who is on
 * the server right now (no request to Mojang at all, and it works on servers that serve their
 * own skins), Mojang's API by nickname, and the disk cache of everything fetched before. The
 * PNG is normalized on the way in — an old 64x32 skin is grown into the 64x64 layout — and
 * kept in {@link #folder} so the next session doesn't go to the network.</p>
 *
 * <p>Fetching happens on a background thread: {@link #open(Link)} is called from the render
 * thread and must not block it, so it hands back the vanilla default skin and uploads the real
 * one into the same GL texture once it arrives, the way {@link MultiLinkThread} does.</p>
 */
public class PlayerSkins
{
    public static final String SOURCE = "player";

    /** Mojang nicknames: 1 to 16 letters, digits and underscores, and nothing else. */
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** How long a cached skin is trusted — players do change their skin. */
    private static final long TTL = 24L * 60L * 60L * 1000L;

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    /** Stand-ins while a skin is being fetched, first one the resource packs actually have. */
    private static final String[] DEFAULT_SKINS = {"textures/entity/player/wide/steve.png", "textures/entity/steve.png"};

    private static File folder;
    private static File cacheFile;

    /**
     * Lowercased nickname &rarr; what was fetched for it. The file is always
     * <code>&lt;key&gt;.png</code>. Concurrent because a multiskin reads its layers on
     * {@link MultiLinkThread}, so a skin can be asked for off the render thread.
     */
    private static final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /** Nicknames a background fetch is running for, so a stalled network can't queue one per frame. */
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    /* One thread: skins arrive one after another, which also keeps Mojang's rate limit far away */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor((runnable) ->
    {
        Thread thread = new Thread(runnable, "BBS player skins");

        thread.setDaemon(true);

        return thread;
    });

    private static class Entry
    {
        /** The nickname as it was typed, kept for showing it back in its own case. */
        public final String name;
        public long time;

        public Entry(String name, long time)
        {
            this.name = name;
            this.time = time;
        }
    }

    public static void init(File folder)
    {
        PlayerSkins.folder = folder;
        PlayerSkins.cacheFile = new File(folder, "cache.json");

        folder.mkdirs();
        readCache();
    }

    /**
     * The nickname a link points at, or <code>null</code> when it isn't a player skin link.
     * Both <code>player:Notch</code> and <code>player:Notch.png</code> are accepted — the
     * texture browser lists the cached skins with the extension so they show up as files.
     */
    public static String nickname(Link link)
    {
        if (link == null || !SOURCE.equals(link.source))
        {
            return null;
        }

        String path = link.path;

        if (path.endsWith(".png"))
        {
            path = path.substring(0, path.length() - 4);
        }

        return NICKNAME.matcher(path).matches() ? path : null;
    }

    public static boolean isNickname(String nickname)
    {
        return nickname != null && NICKNAME.matcher(nickname).matches();
    }

    public static Link link(String nickname)
    {
        return new Link(SOURCE, nickname);
    }

    /**
     * The skin's stream: the cached PNG when there is one, the default skin while it's being
     * fetched. A cached skin that is past its {@link #TTL} is still handed back — it's the
     * user's own skin from yesterday, not an error — and refreshed in the background.
     */
    public static InputStream open(Link link) throws IOException
    {
        String nickname = nickname(link);

        if (nickname == null)
        {
            throw new FileNotFoundException("\"" + link + "\" isn't a player skin link!");
        }

        File file = getFile(nickname);

        if (file != null)
        {
            if (isStale(nickname))
            {
                request(link, nickname, null);
            }

            return new FileInputStream(file);
        }

        request(link, nickname, null);

        return openDefaultSkin();
    }

    /** The cached PNG of this nickname, or <code>null</code> when it was never fetched. */
    public static File getFile(String nickname)
    {
        Entry entry = cache.get(nickname.toLowerCase());

        if (entry == null)
        {
            return null;
        }

        File file = skinFile(nickname);

        return file.isFile() ? file : null;
    }

    private static boolean isStale(String nickname)
    {
        Entry entry = cache.get(nickname.toLowerCase());

        return entry == null || System.currentTimeMillis() - entry.time > TTL;
    }

    private static File skinFile(String nickname)
    {
        return new File(folder, nickname.toLowerCase() + ".png");
    }

    /** Every nickname fetched before, in the case it was typed in. */
    public static Collection<String> getNicknames()
    {
        List<String> nicknames = new ArrayList<>();

        for (Entry entry : cache.values())
        {
            if (skinFile(entry.name).isFile())
            {
                nicknames.add(entry.name);
            }
        }

        return nicknames;
    }

    /** Drops what was fetched for a nickname, so the next look-up goes to the network again. */
    public static void forget(String nickname)
    {
        String key = nickname.toLowerCase();

        if (cache.remove(key) != null)
        {
            skinFile(key).delete();
            saveCache();

            BBSResources.markAssetsChanged();
        }
    }

    /**
     * Fetches the skin in the background, unless a fetch for it is already running. The
     * profile of an online player is read here, on the render thread, because that is where
     * the network handler may be touched; everything else happens off the thread.
     *
     * @param callback run on the render thread once the fetch is over, told whether a skin
     *                 actually came back, may be <code>null</code>
     */
    public static void request(Link link, String nickname, Consumer<Boolean> callback)
    {
        if (folder == null || !isNickname(nickname) || !pending.add(nickname.toLowerCase()))
        {
            return;
        }

        String url = skinUrlFromOnlinePlayers(nickname);

        EXECUTOR.execute(() ->
        {
            boolean loaded = false;

            try
            {
                loaded = fetch(link, nickname, url);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                pending.remove(nickname.toLowerCase());
            }

            if (callback != null)
            {
                boolean result = loaded;

                /* Behind the upload queued by the fetch, so the cache is already in by then */
                MinecraftClient.getInstance().execute(() -> callback.accept(result));
            }
        });
    }

    private static boolean fetch(Link link, String nickname, String onlineUrl) throws Exception
    {
        String url = onlineUrl == null ? skinUrlFromMojang(nickname) : onlineUrl;

        if (url == null)
        {
            System.out.println("Player skin \"" + nickname + "\" couldn't be found!");

            return false;
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(download(url)));

        if (image == null)
        {
            System.out.println("Player skin \"" + nickname + "\" wasn't a picture!");

            return false;
        }

        File file = skinFile(nickname);

        ImageIO.write(PlayerSkinImage.normalize(image), "png", file);

        MinecraftClient.getInstance().execute(() ->
        {
            cache.put(nickname.toLowerCase(), new Entry(nickname, System.currentTimeMillis()));
            saveCache();

            /* A skin typed by hand into a path is a new file in the "player:" source as far as
             * a texture browser is concerned — it relists off this, the same as for the disk */
            BBSResources.markAssetsChanged();

            upload(link, file);
        });

        return true;
    }

    /**
     * Replaces the pixels of the texture the link already got — the default skin handed out
     * while the fetch was running — instead of dropping it: everything that took the texture
     * keeps the same GL id, and the skin simply appears.
     */
    private static void upload(Link link, File file)
    {
        try (InputStream stream = new FileInputStream(file))
        {
            Texture texture = BBSModClient.getTextures().createTexture(link);

            texture.bind();
            texture.uploadTexture(Pixels.fromPNGStream(stream));

            System.out.println("Player skin \"" + link + "\" was loaded!");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /* Resolving */

    /**
     * The skin of a player who is on the server right now, straight out of the profile the
     * server sent: no request to Mojang, and it's the skin this very server shows. A multiskin
     * asks for its layers off the render thread, where the player list may be changing under
     * this loop — a miss there simply falls through to Mojang.
     */
    private static String skinUrlFromOnlinePlayers(String nickname)
    {
        try
        {
            ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();

            if (handler == null)
            {
                return null;
            }

            for (PlayerListEntry entry : handler.getPlayerList())
            {
                GameProfile profile = entry.getProfile();

                if (nickname.equalsIgnoreCase(profile.getName()))
                {
                    return skinUrlFromProfile(profile);
                }
            }
        }
        catch (Exception e)
        {}

        return null;
    }

    private static String skinUrlFromProfile(GameProfile profile)
    {
        for (Property property : profile.getProperties().get("textures"))
        {
            String url = skinUrlFromTextures(property.value());

            if (url != null)
            {
                return url;
            }
        }

        return null;
    }

    /** The skin's URL out of the base64 blob Mojang signs the textures with. */
    private static String skinUrlFromTextures(String base64)
    {
        try
        {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");

            if (textures == null || !textures.has("SKIN"))
            {
                return null;
            }

            return textures.getAsJsonObject("SKIN").get("url").getAsString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String skinUrlFromMojang(String nickname) throws IOException
    {
        String profile = get(PROFILE_URL + nickname);

        if (profile == null || profile.isEmpty())
        {
            return null;
        }

        JsonElement id = JsonParser.parseString(profile).getAsJsonObject().get("id");

        if (id == null)
        {
            return null;
        }

        String session = get(SESSION_URL + id.getAsString());

        if (session == null || session.isEmpty())
        {
            return null;
        }

        JsonArray properties = JsonParser.parseString(session).getAsJsonObject().getAsJsonArray("properties");

        if (properties == null)
        {
            return null;
        }

        for (JsonElement element : properties)
        {
            JsonObject property = element.getAsJsonObject();

            if ("textures".equals(property.get("name").getAsString()))
            {
                String url = skinUrlFromTextures(property.get("value").getAsString());

                if (url != null)
                {
                    return url;
                }
            }
        }

        return null;
    }

    /* Network */

    private static String get(String url) throws IOException
    {
        HttpURLConnection connection = connect(url);

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
        {
            return null;
        }

        try (InputStream stream = connection.getInputStream())
        {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] download(String url) throws IOException
    {
        try (InputStream stream = connect(url).getInputStream())
        {
            return stream.readAllBytes();
        }
    }

    private static HttpURLConnection connect(String url) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        connection.setRequestProperty("User-Agent", "curl/8.9.0");
        connection.setRequestProperty("Accept", "*/*");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        return connection;
    }

    private static InputStream openDefaultSkin() throws IOException
    {
        for (String path : DEFAULT_SKINS)
        {
            try
            {
                InputStream stream = BBSMod.getProvider().getAsset(new Link("minecraft", path));

                if (stream != null)
                {
                    return stream;
                }
            }
            catch (Exception e)
            {}
        }

        throw new FileNotFoundException("Default player skin couldn't be found!");
    }

    /* Cache file */

    private static void readCache()
    {
        try
        {
            MapType data = (MapType) DataToString.read(cacheFile);

            cache.clear();

            for (String key : data.keys())
            {
                MapType entry = data.getMap(key);

                cache.put(key, new Entry(entry.getString("name"), entry.getLong("time")));
            }
        }
        catch (Exception e)
        {}
    }

    private static void saveCache()
    {
        try
        {
            MapType data = new MapType();

            for (Map.Entry<String, Entry> entry : cache.entrySet())
            {
                MapType value = new MapType();

                value.putString("name", entry.getValue().name);
                value.putLong("time", entry.getValue().time);

                data.put(entry.getKey(), value);
            }

            DataToString.writeSilently(cacheFile, data, true);
        }
        catch (Exception e)
        {}
    }
}
