package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A GIF read into its frames: the pictures stacked top to bottom into one strip, the layout an
 * animated texture's sheet uses, so the strip is cut into frames the same way; and how long
 * each of them is shown, in milliseconds.
 *
 * <p>The strip belongs to whoever read the file, and is theirs to {@link Pixels#delete()}.</p>
 */
public class GifFrames
{
    public static final String EXTENSION = ".gif";

    /**
     * A frame that says it lasts (next to) no time is shown for a tenth of a second: the
     * convention every browser follows, and so the speed such a GIF was made to play at.
     */
    private static final int SHORTEST_DELAY = 20;
    private static final int DEFAULT_DELAY = 100;

    public final Pixels strip;
    public final int[] delays;

    public static boolean isGif(Link link)
    {
        return link != null && isGif(link.path);
    }

    public static boolean isGif(String path)
    {
        return path.endsWith(EXTENSION);
    }

    public static GifFrames read(InputStream stream) throws IOException
    {
        ByteBuffer image = IOUtils.readByteBuffer(stream, 8 * 1024);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer delays = stack.mallocPointer(1);
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer count = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_gif_from_memory(image, delays, width, height, count, channels, 4);

            if (pixels == null)
            {
                throw new IOException("Failed to read GIF: " + STBImage.stbi_failure_reason());
            }

            int frames = count.get(0);

            if (frames <= 0)
            {
                STBImage.stbi_image_free(pixels);

                throw new IOException("The GIF has no frames");
            }

            /* The delays are the decoder's to hand out and ours to free */
            IntBuffer times = MemoryUtil.memIntBuffer(delays.get(0), frames);
            int[] milliseconds = new int[frames];

            for (int i = 0; i < frames; i++)
            {
                int delay = times.get(i);

                milliseconds[i] = delay < SHORTEST_DELAY ? DEFAULT_DELAY : delay;
            }

            STBImage.nstbi_image_free(delays.get(0));

            return new GifFrames(new Pixels(pixels, width.get(0), height.get(0) * frames), milliseconds);
        }
        finally
        {
            MemoryUtil.memFree(image);
        }
    }

    public GifFrames(Pixels strip, int[] delays)
    {
        this.strip = strip;
        this.delays = delays;
    }

    public int count()
    {
        return this.delays.length;
    }

    public int frameHeight()
    {
        return this.strip.height / this.count();
    }
}
