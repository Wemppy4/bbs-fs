package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.resources.Link;
import org.w3c.dom.Node;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/** Java 8 GIF decoder with composed frames and the original BBS tick timing. */
public class GifFrames
{
    public static final String EXTENSION = ".gif";
    public final Pixels strip;
    public final int[] delays;
    public GifFrames(Pixels strip, int[] delays) { this.strip = strip; this.delays = delays; }
    public static boolean isGif(Link link) { return link != null && isGif(link.path); }
    public static boolean isGif(String path) { return path.endsWith(EXTENSION); }
    public int count() { return delays.length; }
    public int frameHeight() { return strip.height / count(); }

    private static Node child(Node node, String name)
    {
        for (Node item = node.getFirstChild(); item != null; item = item.getNextSibling())
            if (name.equals(item.getNodeName())) return item;
        return null;
    }
    private static String attribute(Node node, String name, String fallback)
    {
        Node value = node == null ? null : node.getAttributes().getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }
    private static int integer(Node node, String name, int fallback)
    {
        return Integer.parseInt(attribute(node, name, Integer.toString(fallback)));
    }

    public static GifFrames read(InputStream stream) throws IOException
    {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No GIF decoder installed");
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(stream))
        {
            reader.setInput(input);
            int count = reader.getNumImages(true);
            if (count == 0) throw new IOException("The GIF has no frames");
            Node logical = child(reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
            int width = integer(logical, "logicalScreenWidth", reader.getWidth(0));
            int height = integer(logical, "logicalScreenHeight", reader.getHeight(0));
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Pixels strip = Pixels.fromSize(width, Math.multiplyExact(height, count));
            int[] delays = new int[count];
            try
            {
                for (int i = 0; i < count; i++)
                {
                    Node metadata = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    Node descriptor = child(metadata, "ImageDescriptor"), control = child(metadata, "GraphicControlExtension");
                    int x = integer(descriptor, "imageLeftPosition", 0), y = integer(descriptor, "imageTopPosition", 0);
                    int delay = integer(control, "delayTime", 10) * 10;
                    delays[i] = delay < 20 ? 100 : delay;
                    String disposal = attribute(control, "disposalMethod", "none");
                    int[] previous = "restoreToPrevious".equals(disposal) ? canvas.getRGB(0, 0, width, height, null, 0, width) : null;
                    BufferedImage frame = reader.read(i);
                    Graphics2D graphics = canvas.createGraphics();
                    graphics.drawImage(frame, x, y, null);
                    int[] colors = canvas.getRGB(0, 0, width, height, null, 0, width);
                    for (int pixel = 0; pixel < colors.length; pixel++)
                        strip.setColor(i * colors.length + pixel, strip.color.set(colors[pixel]));
                    if ("restoreToBackgroundColor".equals(disposal))
                    {
                        graphics.setComposite(AlphaComposite.Clear);
                        graphics.fillRect(x, y, frame.getWidth(), frame.getHeight());
                    }
                    graphics.dispose();
                    if (previous != null) canvas.setRGB(0, 0, width, height, previous, 0, width);
                }
                strip.rewindBuffer();
                return new GifFrames(strip, delays);
            }
            catch (IOException | RuntimeException error)
            {
                strip.delete();
                throw error;
            }
        }
        finally { reader.dispose(); }
    }
}
