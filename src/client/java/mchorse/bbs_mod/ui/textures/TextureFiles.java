package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.utils.PNGEncoder;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The file operations a texture browser offers, on the sources that are real folders on disk
 * (the assets folder; not http). A texture's sidecars travel with it — the {@code .mcmeta}
 * animation and the {@code .dat} editor project are renamed, copied, moved and deleted
 * alongside, so an animation never comes apart from its frames, nor a texture from its layers.
 *
 * <p>Every operation returns what it produced — the new link — or null when it couldn't, and
 * tells the browsers to relist so the change shows without waiting for the watchdog.</p>
 */
public class TextureFiles
{
    public static final String COPY_SUFFIX = "_copy";

    /** What belongs to a texture and goes wherever it goes: the animation and the editor's project. */
    private static final String[] SIDECARS = {".mcmeta", Document.EXTENSION};

    public static File file(Link link)
    {
        return link == null ? null : BBSMod.getProvider().getFile(link);
    }

    /**
     * Whether a link is something on disk the user may rename, move or delete. What isn't —
     * a texture inside the mod's jar — is read-only: it can still be copied out.
     */
    public static boolean canModify(Link link)
    {
        File file = file(link);

        return file != null && file.exists();
    }

    /** Whether a folder (or a source root) is read-only: nothing in it can be changed, only copied out. */
    public static boolean isReadOnly(Link folder)
    {
        return folder != null && !folder.source.isEmpty() && !isFolder(folder);
    }

    public static boolean isFolder(Link link)
    {
        File file = file(link);

        return file != null && file.isDirectory();
    }

    public static Link rename(Link link, String newName)
    {
        File file = file(link);

        if (file == null || !file.exists() || newName.isEmpty())
        {
            return null;
        }

        File target = new File(file.getParentFile(), newName);

        if (target.exists())
        {
            return null;
        }

        try
        {
            Files.move(file.toPath(), target.toPath());
            moveSidecars(file, target);
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }

        return moved(link, done(target, link));
    }

    public static Link duplicate(Link link)
    {
        File file = file(link);

        if (file == null || !file.isFile())
        {
            return null;
        }

        File target = uniqueCopy(file);

        try
        {
            Files.copy(file.toPath(), target.toPath());
            copySidecars(file, target);
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }

        return done(target, link);
    }

    /** Move a file or a folder into {@code folder}; refuses to move a folder into itself. */
    public static Link move(Link link, Link folder)
    {
        File file = file(link);
        File into = file(folder);

        if (file == null || into == null || !file.exists() || !into.isDirectory())
        {
            return null;
        }

        if (file.isDirectory() && into.toPath().startsWith(file.toPath()))
        {
            return null;
        }

        File target = new File(into, file.getName());

        if (target.exists() || target.equals(file))
        {
            return null;
        }

        try
        {
            Files.move(file.toPath(), target.toPath());
            moveSidecars(file, target);
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }

        return moved(link, done(target, link));
    }

    /**
     * Copy a texture into {@code folder}, keeping its name (or a {@code _copy} one when that's
     * taken). The source may be read-only — a texture inside the mod's own jar, say: it's
     * read as a stream, so anything the provider can open can be copied out onto the disk.
     */
    public static Link copyInto(Link link, Link folder)
    {
        File into = file(folder);

        if (link == null || link.path.endsWith("/") || into == null || !into.isDirectory())
        {
            return null;
        }

        File target = new File(into, StringUtils.fileName(link.path));

        if (target.exists())
        {
            target = uniqueCopy(target);
        }

        try
        {
            copyAsset(link, target);

            for (String extension : SIDECARS)
            {
                copyAsset(new Link(link.source, link.path + extension), sidecar(target, extension));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }

        return done(target, link);
    }

    /** Write an asset to a file; a missing asset (no sidecar, for one) is simply skipped. */
    private static void copyAsset(Link link, File target) throws IOException
    {
        try (InputStream stream = BBSMod.getProvider().getAsset(link))
        {
            Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (java.io.FileNotFoundException | java.util.NoSuchElementException e)
        {}
    }

    /** Write a blank, transparent PNG of the given size; null when the name is taken or the folder isn't on disk. */
    public static Link create(Link folder, String name, int width, int height)
    {
        File into = file(folder);

        if (into == null || !into.isDirectory() || name.isEmpty())
        {
            return null;
        }

        File target = new File(into, name.endsWith(".png") ? name : name + ".png");

        if (target.exists())
        {
            return null;
        }

        Pixels pixels = Pixels.fromSize(Math.max(1, width), Math.max(1, height));

        try
        {
            PNGEncoder.writeToFile(pixels, target);
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }
        finally
        {
            pixels.delete();
        }

        return done(target, folder);
    }

    public static boolean delete(Link link)
    {
        File file = file(link);

        if (file == null || !file.exists())
        {
            return false;
        }

        try
        {
            if (file.isDirectory())
            {
                try (Stream<java.nio.file.Path> walk = Files.walk(file.toPath()))
                {
                    walk.sorted(Comparator.reverseOrder()).forEach((path) -> path.toFile().delete());
                }
            }
            else
            {
                Files.delete(file.toPath());
                deleteSidecars(file);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return false;
        }

        BBSResources.markAssetsChanged();
        TexturePins.follow(link, null);

        return true;
    }

    public static Link newFolder(Link parent, String name)
    {
        File into = file(parent);

        if (into == null || !into.isDirectory() || name.isEmpty())
        {
            return null;
        }

        File target = new File(into, name);

        if (!target.mkdirs())
        {
            return null;
        }

        return TextureEntry.folderLink(done(target, parent));
    }

    /** {@code name.png} → {@code name_copy.png}, {@code name_copy2.png}… whichever is free. */
    private static File uniqueCopy(File file)
    {
        String name = file.getName();
        String base = StringUtils.removeExtension(name);
        String extension = name.length() > base.length() ? name.substring(base.length()) : "";
        File target = new File(file.getParentFile(), base + COPY_SUFFIX + extension);

        for (int i = 2; target.exists(); i++)
        {
            target = new File(file.getParentFile(), base + COPY_SUFFIX + i + extension);
        }

        return target;
    }

    private static File sidecar(File file, String extension)
    {
        return new File(file.getParentFile(), file.getName() + extension);
    }

    private static void moveSidecars(File from, File to) throws IOException
    {
        for (String extension : SIDECARS)
        {
            File sidecar = sidecar(from, extension);

            if (sidecar.isFile())
            {
                Files.move(sidecar.toPath(), sidecar(to, extension).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void copySidecars(File from, File to) throws IOException
    {
        for (String extension : SIDECARS)
        {
            File sidecar = sidecar(from, extension);

            if (sidecar.isFile())
            {
                Files.copy(sidecar.toPath(), sidecar(to, extension).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteSidecars(File file) throws IOException
    {
        for (String extension : SIDECARS)
        {
            File sidecar = sidecar(file, extension);

            if (sidecar.isFile())
            {
                Files.delete(sidecar.toPath());
            }
        }
    }

    /**
     * A file just changed place or name: the pins on it (and on whatever is inside, when it's
     * a folder) go along. Every move a browser makes — a drag, a paste of a cut, an undo of
     * either — passes through here, which is why the pins are kept here rather than there.
     */
    private static Link moved(Link from, Link to)
    {
        TexturePins.follow(from, to);

        return to;
    }

    private static Link done(File target, Link fallback)
    {
        BBSResources.markAssetsChanged();

        Link link = BBSMod.getProvider().getLink(target);

        return link == null ? fallback : link;
    }
}
