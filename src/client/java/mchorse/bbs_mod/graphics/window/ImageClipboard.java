package mchorse.bbs_mod.graphics.window;

import mchorse.bbs_mod.utils.resources.Pixels;

/**
 * Image copy-paste, within BBS.
 *
 * <p>The clipboard has no native image channel in GLFW (it only carries strings), and OS-level image
 * interop via Java AWT is unreliable, so copied images are simply held in memory here for the
 * lifetime of the game. The full RGBA pixels (alpha included) are kept as an owned {@link Pixels}
 * copy; {@link #copy} and {@link #paste} each deep-copy so callers own the {@link Pixels} they pass
 * in or get back and may freely {@link Pixels#delete() delete} them.</p>
 *
 * <p>This is BBS-internal copy-paste only: images can be moved between BBS texture editors, but not
 * to or from other programs.</p>
 */
public class ImageClipboard
{
    private static Pixels stored;

    /** Copy the given pixels (a deep copy is kept; the caller keeps ownership of its own pixels). */
    public static void copy(Pixels pixels)
    {
        if (pixels == null)
        {
            return;
        }

        Pixels copy = pixels.createCopy(0, 0, pixels.width, pixels.height);

        if (stored != null)
        {
            stored.delete();
        }

        stored = copy;
    }

    /** A fresh copy of the stored image, or {@code null} when nothing was copied. Caller owns it. */
    public static Pixels paste()
    {
        if (stored == null)
        {
            return null;
        }

        return stored.createCopy(0, 0, stored.width, stored.height);
    }

    /** Whether an image has been copied. Cheap enough to gate paste actions/menu entries on. */
    public static boolean hasImage()
    {
        return stored != null;
    }
}
