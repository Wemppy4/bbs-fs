package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The file operations a texture browser offers, on the sources that are real folders on disk
 * (the assets folder; not http). A texture's {@code .mcmeta} sidecar travels with it: renamed,
 * copied, moved and deleted alongside, so an animation never comes apart from its frames.
 *
 * <p>Every operation returns what it produced — the new link — or null when it couldn't, and
 * tells the browsers to relist so the change shows without waiting for the watchdog.</p>
 */
public class TextureFiles
{
    public static final String COPY_SUFFIX = "_copy";

    public static File file(Link link)
    {
        return link == null ? null : BBSMod.getProvider().getFile(link);
    }

    /** Whether a link is something on disk the user may rename, move, copy or delete. */
    public static boolean canModify(Link link)
    {
        File file = file(link);

        return file != null && file.exists();
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
            moveSidecar(file, target);
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }

        return done(target, link);
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

            File sidecar = sidecar(file);

            if (sidecar.exists())
            {
                Files.copy(sidecar.toPath(), sidecar(target).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
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
            moveSidecar(file, target);
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }

        return done(target, link);
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

                File sidecar = sidecar(file);

                if (sidecar.exists())
                {
                    Files.delete(sidecar.toPath());
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return false;
        }

        BBSResources.markAssetsChanged();

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

    private static File sidecar(File file)
    {
        return new File(file.getParentFile(), file.getName() + ".mcmeta");
    }

    private static void moveSidecar(File from, File to) throws IOException
    {
        File sidecar = sidecar(from);

        if (sidecar.isFile())
        {
            Files.move(sidecar.toPath(), sidecar(to).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Link done(File target, Link fallback)
    {
        BBSResources.markAssetsChanged();

        Link link = BBSMod.getProvider().getLink(target);

        return link == null ? fallback : link;
    }
}
