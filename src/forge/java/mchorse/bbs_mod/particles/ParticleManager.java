package mchorse.bbs_mod.particles;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.manager.BaseManager;
import mchorse.bbs_mod.utils.manager.storage.JSONLikeStorage;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class ParticleManager extends BaseManager<ParticleScheme>
{
    private ISourcePack assets;

    public ParticleManager(Supplier<File> folder)
    {
        super(folder);

        this.storage = new JSONLikeStorage().json();
    }

    public ParticleManager(Supplier<File> folder, ISourcePack assets)
    {
        this(folder);
        this.assets = assets;
    }

    @Override
    public File getFile(String name)
    {
        return this.assets == null ? super.getFile(name) : this.assets.getFile(Link.assets("particles/" + name + this.getExtension()));
    }

    @Override
    public Collection<String> getKeys()
    {
        if (this.assets == null)
        {
            return super.getKeys();
        }

        Set<Link> links = new HashSet<>();
        Set<String> keys = new HashSet<>();
        this.assets.getLinksFromPath(links, Link.assets("particles"), true);

        for (Link link : links)
        {
            if (link.path.contains("/_"))
            {
                continue;
            }

            String path = link.path.substring("particles/".length());

            if (path.endsWith(this.getExtension()))
            {
                keys.add(path.substring(0, path.length() - this.getExtension().length()));
            }
            else if (path.endsWith("/"))
            {
                keys.add(path);
            }
        }

        return keys;
    }

    @Override
    protected ParticleScheme createData(String id, MapType data)
    {
        ParticleScheme scheme = new ParticleScheme();

        if (data != null)
        {
            try
            {
                System.out.println("Parsing \"" + id + "\" particle effect.");

                ParticleScheme.PARSER.fromData(scheme, data);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            scheme.setup();
        }
        else
        {
            scheme.setup();
        }

        return scheme;
    }
}
