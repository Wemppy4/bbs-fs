package mchorse.bbs_mod.resources.packs;

import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Assets in an addon's own JAR or development resource directory. */
public class InternalAssetsSourcePack implements ISourcePack {
    private final String prefix;
    private final String internalPrefix;
    private final Class<?> owner;
    private Set<String> paths;

    public InternalAssetsSourcePack() { this(Link.ASSETS, "assets/bbs/assets", InternalAssetsSourcePack.class); }
    public InternalAssetsSourcePack(String prefix, String internalPrefix, Class<?> owner) {
        this.prefix=prefix; this.internalPrefix=internalPrefix.replaceAll("^/+|/+$", ""); this.owner=owner;
    }
    public String getPrefix() { return this.prefix; }
    public boolean hasAsset(Link link) { return this.owner.getClassLoader().getResource(this.internalPrefix+"/"+link.path)!=null; }
    public InputStream getAsset(Link link) throws IOException {
        InputStream stream=this.owner.getClassLoader().getResourceAsStream(this.internalPrefix+"/"+link.path);
        if(stream==null)throw new FileNotFoundException(link.toString());
        return stream;
    }
    public File getFile(Link link) { return null; }
    public Link getLink(File file) { return null; }
    public synchronized void getLinksFromPath(Collection<Link> links, Link link, boolean recursive) {
        if(this.paths==null) this.paths=readPaths();
        String root=link.path.isEmpty()?"":link.path.endsWith("/")?link.path:link.path+"/";
        Set<String> results=new TreeSet<>();
        for(String path:this.paths) if(path.startsWith(root) && path.length()>root.length()) {
            int slash=path.indexOf('/',root.length());
            results.add(!recursive && slash>=0 ? path.substring(0,slash+1) : path);
        }
        for(String path:results)links.add(new Link(this.prefix,path));
    }
    private Set<String> readPaths() {
        Set<String> found=new TreeSet<>();
        try {
            Enumeration<URL> roots=this.owner.getClassLoader().getResources(this.internalPrefix);
            while(roots.hasMoreElements())scan(roots.nextElement(),found);
            /* JARs need not contain directory entries. The owning class still identifies their archive. */
            URL classResource=this.owner.getResource("/"+this.owner.getName().replace('.','/')+".class");
            if(classResource!=null && "jar".equals(classResource.getProtocol()))scan(classResource,found);
        } catch(Exception error) {
            throw new IllegalStateException("Could not list addon resources " + this.internalPrefix, error);
        }
        return found;
    }
    private void scan(URL url,Set<String> found) throws Exception {
        if("jar".equals(url.getProtocol())) {
            JarURLConnection connection=(JarURLConnection)url.openConnection(); connection.setUseCaches(false);
            try(ZipFile zip=new ZipFile(Paths.get(connection.getJarFileURL().toURI()).toFile())) {
                Enumeration<? extends ZipEntry> entries=zip.entries();
                String root=this.internalPrefix+"/";
                while(entries.hasMoreElements()) {
                    String name=entries.nextElement().getName();
                    if(name.startsWith(root)&&name.length()>root.length())addPath(found,name.substring(root.length()));
                }
            }
        } else if("file".equals(url.getProtocol())) {
            Path root=Paths.get(url.toURI());
            if(Files.isDirectory(root))try(java.util.stream.Stream<Path> files=Files.walk(root)) {
                files.filter(path->!path.equals(root)).forEach(path->addPath(found,root.relativize(path).toString().replace('\\','/')+(Files.isDirectory(path)?"/":"")));
            }
        }
    }
    private static void addPath(Set<String> found,String path) {
        found.add(path);
        for(int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1))found.add(path.substring(0,slash+1));
    }
}