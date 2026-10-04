package mchorse.bbs_mod.resources.packs;

import mchorse.bbs_mod.resources.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Forge/JAR resource implementation of BBS's source-pack contract. */
public class ClasspathSourcePack implements ISourcePack
{
    private final Set<String> paths = new TreeSet<>();
    public ClasspathSourcePack() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/assets/bbs/asset-index.txt")) {
            if (stream == null) throw new FileNotFoundException("BBS asset index");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) paths.add(line);
        }
    }
    public String getPrefix() { return Link.ASSETS; }
    public boolean hasAsset(Link link) { return paths.contains(link.path); }
    public InputStream getAsset(Link link) throws IOException {
        InputStream stream = getClass().getResourceAsStream("/assets/bbs/assets/" + link.path);
        if (stream == null) throw new FileNotFoundException(link.toString());
        return stream;
    }
    public File getFile(Link link) { return null; }
    public Link getLink(File file) { return null; }
    public void getLinksFromPath(Collection<Link> links, Link link, boolean recursive) {
        String prefix = link.path.endsWith("/") ? link.path : link.path + "/";
        for (String path : paths) if (path.startsWith(prefix)) {
            if (recursive || path.substring(prefix.length()).indexOf('/') < 0) links.add(Link.assets(path));
        }
    }
}
