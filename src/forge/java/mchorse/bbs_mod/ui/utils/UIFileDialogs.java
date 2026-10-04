package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.OS;
import net.minecraft.client.Minecraft;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.FileSystems;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JFileChooser;

/** Native platform dialogs keep the original asynchronous callback contract on LWJGL 2. */
public class UIFileDialogs
{
    private static final AtomicBoolean OPEN = new AtomicBoolean();
    public static void pickFolder(IKey title, File folder, Consumer<File> callback)
    {
        String caption = title.get();
        open(() ->
        {
            if (OS.CURRENT == OS.WINDOWS)
            {
                try { return WindowsFolderDialog.pick(caption, folder == null ? "" : folder.getAbsolutePath()); }
                catch (Throwable unavailable) { unavailable.printStackTrace(); }
            }
            final String[] selected = {null};
            try
            {
                EventQueue.invokeAndWait(() ->
                {
                    JFileChooser chooser = new JFileChooser(folder);
                    chooser.setDialogTitle(caption);
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setAcceptAllFileFilterUsed(false);
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                        selected[0] = chooser.getSelectedFile().getAbsolutePath();
                });
            }
            catch (Exception e) { throw new RuntimeException(e); }
            return selected[0];
        }, callback);
    }
    public static void pickFile(IKey title, File file, String[] patterns, IKey description, Consumer<File> callback)
    {
        String caption = title.get();
        open(() ->
        {
            final String[] selected = {null};
            try
            {
                EventQueue.invokeAndWait(() ->
                {
                    FileDialog dialog = new FileDialog((Frame) null, caption, FileDialog.LOAD);
                    try
                    {
                        if (file != null)
                        {
                            dialog.setDirectory(file.isDirectory() ? file.getAbsolutePath() : file.getParent());
                            if (!file.isDirectory()) dialog.setFile(file.getName());
                        }
                        if (patterns != null && patterns.length > 0)
                        {
                            dialog.setFile(String.join(";", patterns));
                            dialog.setFilenameFilter((directory, name) ->
                            {
                                for (String pattern : patterns)
                                    if (FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(new File(name).toPath())) return true;
                                return false;
                            });
                        }
                        dialog.setVisible(true);
                        if (dialog.getFile() != null) selected[0] = new File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
                    }
                    finally { dialog.dispose(); }
                });
            }
            catch (Exception e) { throw new RuntimeException(e); }
            return selected[0];
        }, callback);
    }
    private static void open(Supplier<String> dialog, Consumer<File> callback)
    {
        if (!OPEN.compareAndSet(false, true)) return;
        Thread thread = new Thread(() ->
        {
            String selected = null;
            try { selected = dialog.get(); }
            catch (Throwable error) { error.printStackTrace(); }
            finally { OPEN.set(false); }
            if (selected != null && !selected.isEmpty())
            {
                File result = new File(selected);
                Minecraft.getMinecraft().addScheduledTask(() -> callback.accept(result));
            }
        }, "BBS file dialog");
        thread.setDaemon(true);
        thread.start();
    }
}
