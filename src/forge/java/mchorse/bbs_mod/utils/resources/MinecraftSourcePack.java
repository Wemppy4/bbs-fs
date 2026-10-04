package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.util.*;

/** Minecraft-domain texture browser, indexed from the same active packs as vanilla rendering. */
public final class MinecraftSourcePack implements ISourcePack,IResourceManagerReloadListener
{
    private final Set<String> paths=new TreeSet<>();
    private IResourceManager manager;
    public MinecraftSourcePack()
    {
        ((IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(this);
    }
    @Override public void onResourceManagerReload(IResourceManager manager)
    {
        this.manager=manager;paths.clear();
        for(ResourceLocation id:NativeResourceIndex.find(manager,"textures",".png")) paths.add(id.getPath());
    }
    public void setupPaths() { onResourceManagerReload(Minecraft.getMinecraft().getResourceManager()); }
    @Override public String getPrefix() { return "minecraft"; }
    @Override public boolean hasAsset(Link link)
    {
        if(!getPrefix().equals(link.source))return false;
        try(IResource resource=manager.getResource(new ResourceLocation("minecraft",link.path))) { return true; }
        catch(IOException missing) { return false; }
    }
    @Override public InputStream getAsset(Link link) throws IOException
    {
        final IResource resource=manager.getResource(new ResourceLocation("minecraft",link.path));
        return new FilterInputStream(resource.getInputStream()) { @Override public void close() throws IOException { resource.close(); } };
    }
    @Override public File getFile(Link link) { return null; }
    @Override public Link getLink(File file) { return null; }
    @Override public void getLinksFromPath(Collection<Link> links,Link link,boolean recursive)
    {
        String prefix=link.path.isEmpty()?"":link.path.endsWith("/")?link.path:link.path+"/";
        Set<String> result=new TreeSet<>();
        for(String path:paths)
        {
            if(!path.startsWith(prefix))continue;
            String tail=path.substring(prefix.length());int slash=tail.indexOf('/');
            if(recursive || slash<0)result.add(path);
            while(slash>=0)
            {
                result.add(prefix+tail.substring(0,slash+1));
                if(!recursive)break;
                slash=tail.indexOf('/',slash+1);
            }
        }
        for(String path:result)links.add(new Link(getPrefix(),path));
    }
}
