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
    private static boolean hasOrigin;
    private static int originX;
    private static int originY;

    /**
     * Copy the given pixels with no known canvas position (a deep copy is kept; the caller keeps
     * ownership of its own pixels). A {@link #paste()} of this image will be centered.
     */
    public static void copy(Pixels pixels)
    {
        store(pixels, false, 0, 0);
    }

    /**
     * Copy the given pixels, remembering the document-space position of the image's top-left corner
     * so {@link #paste()} can reproduce it in place instead of centering. {@code originX/Y} are the
     * canvas coordinates the copied region was sitting at.
     */
    public static void copy(Pixels pixels, int originX, int originY)
    {
        store(pixels, true, originX, originY);
    }

    private static void store(Pixels pixels, boolean withOrigin, int x, int y)
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
        hasOrigin = withOrigin;
        originX = x;
        originY = y;
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

    /**
     * Whether the stored image carries a canvas position, so {@link #paste()} should reproduce it in
     * place ({@link #getOriginX()}/{@link #getOriginY()}) rather than centering.
     */
    public static boolean hasOrigin()
    {
        return stored != null && hasOrigin;
    }

    /** Document-space X of the copied image's top-left corner; only meaningful when {@link #hasOrigin()}. */
    public static int getOriginX()
    {
        return originX;
    }

    /** Document-space Y of the copied image's top-left corner; only meaningful when {@link #hasOrigin()}. */
    public static int getOriginY()
    {
        return originY;
    }

    /** Whether an image has been copied. Cheap enough to gate paste actions/menu entries on. */
    public static boolean hasImage()
    {
        return stored != null;
    }
}
