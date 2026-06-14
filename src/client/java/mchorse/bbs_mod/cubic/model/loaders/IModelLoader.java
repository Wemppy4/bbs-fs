package mchorse.bbs_mod.cubic.model.loaders;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public interface IModelLoader
{
    public static Link getLink(Link link, Collection<Link> links, String suffix)
    {
        return getLink(link, links, (l) -> l.path.endsWith(suffix));
    }

    public static Link getLink(Link link, Collection<Link> links, Predicate<Link> predicate)
    {
        if (!links.contains(link))
        {
            for (Link l : links)
            {
                if (predicate.test(l))
                {
                    return l;
                }
            }
        }

        return link;
    }

    public static List<Link> getLinks(Collection<Link> links, String suffix)
    {
        return getLinks(links, (l) -> l.path.endsWith(suffix));
    }

    public static List<Link> getLinks(Collection<Link> links, Predicate<Link> predicate)
    {
        List<Link> newLinks = new ArrayList<>();

        for (Link l : links)
        {
            if (predicate.test(l))
            {
                newLinks.add(l);
            }
        }

        return newLinks;
    }

    /**
     * First PNG inside a folder named after the material ({@code <model>/.../<material>/...}),
     * or null if the material has no such folder. Used to resolve per-material default textures
     * for both OBJ (material name) and BOBJ (mesh name) models.
     */
    public static Link findMaterialTexture(Collection<Link> links, Link model, String material)
    {
        String prefix = model.toString();
        String folder = "/" + material + "/";

        for (Link link : links)
        {
            String string = link.toString();

            if (string.startsWith(prefix) && string.contains(folder) && string.endsWith(".png"))
            {
                return link;
            }
        }

        return null;
    }

    /**
     * Create an empty {@code <model>/textures/<material>/} folder so a model loaded without textures
     * surfaces a per-material folder the user can drop textures into (picked up on the next load).
     * No-op when the model's source can't provide files.
     */
    public static void ensureMaterialFolder(AssetProvider provider, Link model, String material)
    {
        File folder = provider.getFile(model.combine("textures/" + material));

        if (folder != null)
        {
            folder.mkdirs();
        }
    }

    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config);
}