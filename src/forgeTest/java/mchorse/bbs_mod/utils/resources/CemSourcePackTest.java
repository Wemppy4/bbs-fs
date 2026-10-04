package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.MetadataSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class CemSourcePackTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private void write(File pack, String path, String text) throws Exception
    {
        File target = new File(pack, "assets/minecraft/" + path);
        target.getParentFile().mkdirs();
        Files.write(target.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private String read(CemSourcePack pack, String path) throws Exception
    {
        try (InputStream input = pack.getAsset(Link.assets(path)))
        {
            assertNotNull(path, input);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int count;
            while ((count = input.read(data)) >= 0) result.write(data, 0, count);
            return new String(result.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test public void indexesPartsLayersAndTexturesThroughResourceManagerPriority() throws Exception
    {
        File low = temporary.newFolder("low"), high = temporary.newFolder("high");
        write(low, "optifine/cem/sheep.jem", "{\"models\":[{\"model\":\"leg.jpm\"}]}");
        write(low, "optifine/cem/leg.jpm", "{\"id\":\"leg\"}");
        write(low, "optifine/cem/sheep_wool.jem", "{\"models\":[]}");
        write(low, "textures/entity/sheep/sheep.png", "low texture");
        write(low, "textures/entity/sheep/sheep_fur.png", "wool texture");
        write(high, "textures/entity/sheep/sheep.png", "high texture");
        SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager(new MetadataSerializer());
        manager.reloadResources(Arrays.asList(new FolderResourcePack(low), new FolderResourcePack(high)));
        CemSourcePack pack = new CemSourcePack(manager);
        assertEquals("high texture", read(pack, "models/cem/sheep/model.png"));
        assertTrue(read(pack, "models/cem/sheep/leg.jpm").contains("leg"));
        assertEquals("wool texture", read(pack, "models/cem/sheep/textures/wool/model.png"));
        assertTrue(pack.hasAsset(Link.assets("models/cem/sheep/sheep_wool.jem")));
        assertFalse(pack.hasAsset(Link.assets("models/cem/sheep_wool/sheep_wool.jem")));
        assertNull(pack.getFile(Link.assets("models/cem/sheep/model.png")));
        Set<Link> links = new HashSet<>();
        pack.getLinksFromPath(links, Link.assets("models/cem/"), false);
        assertEquals(Collections.singleton(Link.assets("models/cem/sheep/")), links);
        manager.reloadResources(Collections.singletonList(new FolderResourcePack(low)));
        pack.reindex();
        assertEquals("low texture", read(pack, "models/cem/sheep/model.png"));
        manager.reloadResources(Collections.emptyList());
        pack.reindex();
        assertFalse(pack.hasAsset(Link.assets("models/cem/sheep/sheep.jem")));
    }
}
