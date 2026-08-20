package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.OS;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The path to ffmpeg's binary, drawn the same way as the export path - it is a
 * path like any other, and it used to be squeezed into the narrow field the
 * short settings get.
 *
 * Its right click menu keeps the sweep over the drives that was there before,
 * for when the binary is somewhere on the machine but nobody remembers where.
 */
public class UIEncoderPathRow extends UIPathRow
{
    public UIEncoderPathRow(ValueString path)
    {
        super(path);
    }

    @Override
    protected IKey getTooltip()
    {
        return UIKeys.GENERAL_FFMPEG_PICK;
    }

    @Override
    protected void pick(UITextbox textbox)
    {
        /* Only Windows names the binary by extension, so there is nothing to
         * narrow the list down by anywhere else */
        String[] patterns = OS.CURRENT == OS.WINDOWS ? new String[] {"*.exe"} : null;

        UIFileDialogs.pickFile(UIKeys.GENERAL_DIALOG_ENCODER, this.getEncoder(), patterns, UIKeys.GENERAL_DIALOG_ENCODER, (file) -> this.set(textbox, file));
    }

    @Override
    protected void context(ContextMenuManager menu, UIElement element, UITextbox textbox)
    {
        File encoder = this.getEncoder();

        if (encoder != null)
        {
            menu.action(Icons.FOLDER, UIKeys.GENERAL_FFMPEG_OPEN_FOLDER, () -> UIUtils.openFolder(encoder.getAbsoluteFile().getParentFile()));
        }

        if (OS.CURRENT == OS.WINDOWS)
        {
            menu.action(Icons.SEARCH, UIKeys.GENERAL_FFMPEG_FIND, () -> this.find(element, textbox));
        }
    }

    private void find(UIElement element, UITextbox textbox)
    {
        element.getContext().replaceContextMenu((submenu) ->
        {
            File[] files = File.listRoots();
            File file = files.length == 0 ? new File("C:\\") : files[0];
            Optional<Path> ffmpeg = FFMpegUtils.findFFMpeg(file.toPath());

            if (ffmpeg.isPresent())
            {
                Path path = ffmpeg.get();
                String pathString = path.toAbsolutePath().toString();

                submenu.action(Icons.VIDEO_CAMERA, IKey.constant(pathString), () ->
                {
                    textbox.setText(pathString);
                    this.path.set(pathString);
                });
            }
        });
    }

    /**
     * The default is the bare "ffmpeg" off PATH rather than a path, so this
     * stays null until the setting actually points at a file - a dialog opening
     * nowhere in particular beats one opening at a folder that isn't there.
     */
    private File getEncoder()
    {
        File file = new File(this.path.get());

        return file.isFile() ? file : null;
    }
}
