package mchorse.bbs_mod.graphics.texture;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.resources.GifFrames;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AnimatedTexture
{
    /** A game tick, in the milliseconds a GIF counts its frames in. */
    private static final int TICK = 50;

    public final List<Texture> textures = new ArrayList<>();
    public final KeyframeChannel<Integer> index;
    public final int length;

    /** An {@code .mcmeta} animation over a strip; the strip stays the caller's to delete. */
    public static AnimatedTexture load(InputStream stream, Pixels pixels) throws Exception
    {
        MapType data = DataToString.mapFromString(IOUtils.readText(stream));
        MapType animation = data.getMap("animation");
        ListType listFrames = animation.getList("frames");

        int frameTime = animation.getInt("frametime", 1);
        int w = animation.getInt("width", Math.min(pixels.width, pixels.height));
        int h = animation.getInt("height", Math.min(pixels.width, pixels.height));

        AnimatedTexture texture = computeFrames(pixels, listFrames, h, frameTime);

        texture.addFrames(pixels, w, h);

        if (texture.textures.isEmpty())
        {
            throw new Exception("For some reason, the animated texture is empty...");
        }

        return texture;
    }

    /**
     * A GIF: every picture in the file is a frame, shown for as long as the file says. The
     * decoder stacks the pictures top to bottom, so the strip is cut the way an {@code .mcmeta}
     * one is.
     */
    public static AnimatedTexture fromGif(InputStream stream) throws Exception
    {
        GifFrames gif = GifFrames.read(stream);

        try
        {
            AnimatedTexture texture = computeFrames(gif.delays);

            texture.addFrames(gif.strip, gif.strip.width, gif.frameHeight());

            return texture;
        }
        finally
        {
            gif.strip.delete();
        }
    }

    private static AnimatedTexture computeFrames(Pixels pixels, ListType frames, int h, int frameTime)
    {
        KeyframeChannel<Integer> index = new KeyframeChannel<>("", KeyframeFactories.INTEGER);
        int length = 0;

        if (frames == null || frames.isEmpty())
        {
            int c = pixels.height / h;

            for (int i = 0; i < c; i++)
            {
                index.insert(i * frameTime, i);
            }

            length = c * frameTime;
        }
        else
        {
            int x = 0;

            for (BaseType frame : frames)
            {
                int i = 0;
                int time = frameTime;

                if (frame.isNumeric())
                {
                    i = frame.asNumeric().intValue();
                }
                else if (frame.isMap())
                {
                    MapType map = frame.asMap();

                    i = map.getInt("index", 0);
                    time = map.getInt("time", frameTime);
                }

                index.insert(x, i);

                x += time;
            }

            length = x;
        }

        return new AnimatedTexture(index, length);
    }

    /**
     * A GIF's milliseconds on the tick clock: a frame starts at the tick nearest to where the
     * file puts it, so a GIF faster than twenty frames a second drops frames rather than slowing
     * down, and a long one ends when it should. Of frames landing on one tick the last stays.
     */
    private static AnimatedTexture computeFrames(int[] delays)
    {
        KeyframeChannel<Integer> index = new KeyframeChannel<>("", KeyframeFactories.INTEGER);
        int time = 0;

        for (int i = 0; i < delays.length; i++)
        {
            index.insert(toTicks(time), i);

            time += delays[i];
        }

        return new AnimatedTexture(index, Math.max(1, toTicks(time)));
    }

    private static int toTicks(int milliseconds)
    {
        return Math.round(milliseconds / (float) TICK);
    }

    public AnimatedTexture(KeyframeChannel<Integer> index, int length)
    {
        this.index = index;
        this.length = length;
    }

    /** Cut the strip into the frames' textures, top to bottom, {@code w} by {@code h} each. */
    private void addFrames(Pixels strip, int w, int h)
    {
        for (int i = 0, c = strip.height / h; i < c; i++)
        {
            Pixels frame = strip.createCopy(0, i * h, w, h);

            frame.rewindBuffer();

            Texture texture = Texture.textureFromPixels(frame, GL11.GL_NEAREST);

            this.textures.add(texture);
            texture.setParent(this);
        }
    }

    public Texture getTexture(int tick)
    {
        if (this.length == 0)
        {
            return null;
        }

        KeyframeSegment<Integer> segment = this.index.find(tick % this.length);
        int frame = segment == null ? 0 : segment.a.getValue();
        Texture texture = CollectionUtils.getSafe(this.textures, frame);

        return texture == null ? CollectionUtils.getSafe(this.textures, 0) : texture;
    }

    public void delete()
    {
        for (Texture texture : this.textures)
        {
            texture.delete();
        }
    }
}
