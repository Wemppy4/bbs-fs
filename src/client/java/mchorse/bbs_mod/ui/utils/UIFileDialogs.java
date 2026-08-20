package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.OS;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The operating system's own file and folder pickers, so a path can be picked
 * instead of typed. LWJGL ships tinyfd with the game, so this costs no
 * dependency.
 *
 * Folders on Windows are the exception: tinyfd puts up SHBrowseForFolder there,
 * which has looked its age since Windows 2000, so that one goes through
 * {@link WindowsFolderDialog} instead and falls back here if COM won't play
 * along. Everywhere else tinyfd's dialogs are the native ones already.
 *
 * These dialogs are modal to the OS and block whichever thread opened them, so
 * one runs on a thread of its own and the pick comes back through the client's
 * task queue - the game keeps rendering while the window is up, and the
 * callback still runs on the main thread where the interface may be touched.
 *
 * A cancelled dialog and an unavailable one both come back as null, which
 * tinyfd doesn't tell apart, so the callback simply never fires and the field
 * keeps what was typed into it. Typing a path stays the way that always works.
 */
public class UIFileDialogs
{
    /**
     * One dialog at a time - the button stays clickable while the window is up,
     * and a second dialog would leave two of them fighting over focus.
     */
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    public static void pickFolder(IKey title, File folder, Consumer<File> callback)
    {
        /* Resolved here rather than on the dialog's thread, since translating
         * is the main thread's business */
        String titleString = title.get();
        String path = path(folder);

        open(() ->
        {
            if (OS.CURRENT == OS.WINDOWS)
            {
                try
                {
                    return WindowsFolderDialog.pick(titleString, path);
                }
                catch (Throwable t)
                {
                    /* COM wouldn't hand over the dialog - the old picker still
                     * beats no picker */
                    t.printStackTrace();
                }
            }

            utf8();

            /* tinyfd reads the last segment as a file name unless the path ends
             * with the separator, and opens the parent folder instead */
            return TinyFileDialogs.tinyfd_selectFolderDialog(titleString, path.isEmpty() ? path : path + File.separator);
        }, callback);
    }

    /**
     * @param patterns file masks such as "*.exe", or null for any file
     */
    public static void pickFile(IKey title, File file, String[] patterns, IKey description, Consumer<File> callback)
    {
        String titleString = title.get();
        String descriptionString = description == null ? null : description.get();
        String path = path(file);

        open(() ->
        {
            utf8();

            try (MemoryStack stack = MemoryStack.stackPush())
            {
                PointerBuffer filters = null;

                if (patterns != null && patterns.length > 0)
                {
                    filters = stack.mallocPointer(patterns.length);

                    for (String pattern : patterns)
                    {
                        filters.put(stack.UTF8(pattern));
                    }

                    filters.flip();
                }

                return TinyFileDialogs.tinyfd_openFileDialog(titleString, path, filters, descriptionString, false);
            }
        }, callback);
    }

    /**
     * Windows hands the path back in the system's ANSI code page unless this is
     * on, while LWJGL reads it as UTF-8 either way - so any non-ASCII folder on
     * the way to it comes back mangled. Set on every dialog rather than once,
     * since it is a plain assignment in the native and there is no init to hook
     * into.
     */
    private static void utf8()
    {
        TinyFileDialogs.tinyfd_setGlobalInt(TinyFileDialogs.tinyfd_winUtf8, 1);
    }

    private static String path(File file)
    {
        return file == null ? "" : file.getAbsolutePath();
    }

    private static void open(Supplier<String> dialog, Consumer<File> callback)
    {
        if (!OPEN.compareAndSet(false, true))
        {
            return;
        }

        Thread thread = new Thread(() ->
        {
            String picked = null;

            try
            {
                picked = dialog.get();
            }
            catch (Throwable t)
            {
                /* Linux without zenity/kdialog, or natives that didn't extract.
                 * Throwable rather than Exception: a missing native is an
                 * UnsatisfiedLinkError, and it must not kill the thread quietly */
                t.printStackTrace();
            }
            finally
            {
                OPEN.set(false);
            }

            if (picked != null && !picked.isEmpty())
            {
                File file = new File(picked);

                MinecraftClient.getInstance().execute(() -> callback.accept(file));
            }
        }, "BBS file dialog");

        /* The game must not be held open by a dialog nobody answered */
        thread.setDaemon(true);
        thread.start();
    }
}
