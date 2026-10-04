package mchorse.bbs_mod.utils.watchdog;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WatchDog implements Runnable
{
    private Path folder;
    private Consumer<Runnable> spawner;
    private WatchDogProxy proxy = new WatchDogProxy();

    private WatchService service;
    private Map<WatchKey, Path> keys = new HashMap<>();
    private Thread thread;
    private volatile boolean stopThread;

    private boolean onlyTop;
    private final boolean watchTree;
    private final WatchEvent.Modifier treeModifier;

    public WatchDog(File folder, boolean onlyTop, Consumer<Runnable> spawner)
    {
        this.folder = folder.toPath();
        this.spawner = spawner;
        this.onlyTop = onlyTop;

        /* Separate handles for descendants prevent renaming their parent on Windows.
         * Its native recursive watch only needs to keep the asset root open. */
        this.watchTree = !onlyTop && System.getProperty("os.name").startsWith("Windows");
        this.treeModifier = this.watchTree ? windowsFileTree() : null;
    }

    /** Resolve the actual JDK Windows modifier without linking a non-standard Java API. */
    private static WatchEvent.Modifier windowsFileTree()
    {
        try
        {
            Class<?> type = Class.forName("com.sun.nio.file.ExtendedWatchEventModifier");

            return (WatchEvent.Modifier) type.getField("FILE_TREE").get(null);
        }
        catch (ReflectiveOperationException | ClassCastException e)
        {
            throw new IllegalStateException("Windows recursive asset watching requires the JDK FILE_TREE modifier", e);
        }
    }

    public WatchDogProxy getProxy()
    {
        return this.proxy;
    }

    public void registerFolder(Path path)
    {
        try
        {
            WatchEvent.Kind<?>[] events = {
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            };
            WatchKey key = this.watchTree
                ? path.register(this.service, events, this.treeModifier)
                : path.register(this.service, events);

            this.keys.put(key, path);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void registerFolderRecursive(final Path path) throws IOException
    {
        if (this.onlyTop || this.watchTree)
        {
            this.registerFolder(path);
        }
        else
        {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
                {
                    WatchDog.this.registerFolder(dir);

                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public void start()
    {
        this.thread = new Thread(this);

        this.thread.start();
    }

    public void stop()
    {
        this.stopThread = true;

        if (this.thread != null)
        {
            this.thread.interrupt();
        }
    }

    @Override
    public void run()
    {
        try
        {
            this.service = FileSystems.getDefault().newWatchService();

            this.registerFolderRecursive(this.folder);

            while (!this.stopThread && this.pollEvents())
            {}
        }
        catch (IOException e)
        {
            System.err.println("Failed to start a watch dog thread!");

            e.printStackTrace();
        }

        finally
        {
            if (this.service != null)
            {
                try
                {
                    this.service.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }

            this.keys.clear();
        }
    }

    /**
     * @return {@code true} if everything is fine, {@code false} if watch dog has to be
     * stopped.
     */
    private boolean pollEvents()
    {
        WatchKey key;

        try
        {
            key = this.service.poll(1, TimeUnit.SECONDS);
        }
        catch (InterruptedException x)
        {
            return false;
        }

        if (key == null)
        {
            return true;
        }

        Path folder = this.keys.get(key);

        for (WatchEvent<?> event : key.pollEvents())
        {
            WatchEvent.Kind<?> kind = event.kind();

            if (kind == StandardWatchEventKinds.OVERFLOW)
            {
                return true;
            }

            WatchEvent<Path> e = (WatchEvent<Path>) event;
            Path filename = e.context();
            Path file = folder.resolve(filename);

            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.toFile().length() == 0)
            {
                continue;
            }

            if (kind == StandardWatchEventKinds.ENTRY_CREATE && !this.onlyTop && !this.watchTree
                && Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
            {
                try
                {
                    this.registerFolderRecursive(file);
                }
                catch (Exception x)
                {
                    x.printStackTrace();
                }
            }

            WatchDogEvent type = WatchDogEvent.CREATED;

            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) type = WatchDogEvent.MODIFIED;
            else if (kind == StandardWatchEventKinds.ENTRY_DELETE) type = WatchDogEvent.DELETED;

            final WatchDogEvent finalType = type;

            this.spawner.accept(() ->
            {
                this.proxy.accept(file, finalType);
            });
        }

        if (!key.reset())
        {
            this.keys.remove(key);

            if (this.keys.isEmpty())
            {
                return false;
            }
        }

        return true;
    }
}
