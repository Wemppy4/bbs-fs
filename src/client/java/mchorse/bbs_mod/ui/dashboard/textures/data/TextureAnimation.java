package mchorse.bbs_mod.ui.dashboard.textures.data;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The animation of a texture as its {@code .mcmeta} sidecar describes it — the editor's view of
 * the {@code animation} section {@link mchorse.bbs_mod.graphics.texture.AnimatedTexture} reads.
 *
 * <p>The PNG is a strip of same-sized images stacked top to bottom; the frames are the order
 * they are shown in, each pointing at an image by number and lasting {@link Frame#time} ticks
 * ({@link #frametime} when it says nothing). No list of frames in the file means every image
 * once, in order.</p>
 *
 * <p>This is an editor for the format, not a format of its own: whatever else the file holds —
 * {@code interpolate}, sections other than {@code animation}, keys this class doesn't know — is
 * kept as read and written back untouched. Only {@code frametime}, {@code width}, {@code height}
 * and {@code frames} are rewritten, and each only when it says something the loader wouldn't
 * assume anyway, so a plain animation makes the same plain file it always did. The {@code .dat}
 * project never carries the animation: the {@code .mcmeta} is the one place it lives.</p>
 */
public class TextureAnimation implements IMapSerializable
{
    public static final String EXTENSION = ".mcmeta";

    /** What the loader assumes when the file doesn't say. */
    public static final int DEFAULT_FRAMETIME = 1;

    /** What an animation made in the editor starts with. */
    public static final int NEW_FRAMETIME = 2;

    public int frametime = DEFAULT_FRAMETIME;

    /** Size of one image; 0 means the loader's default, a square of the strip's smaller side. */
    public int width;
    public int height;

    /** The order the images are shown in; never empty once read or created. */
    public final List<Frame> frames = new ArrayList<>();

    /** The file as it was read, so what this class doesn't edit goes back unchanged; null for a new animation. */
    private MapType source;

    public static class Frame
    {
        /** Which image of the strip, top to bottom from 0. */
        public int index;

        /** Ticks this frame lasts; 0 means the animation's {@link #frametime}. */
        public int time;

        public Frame(int index, int time)
        {
            this.index = index;
            this.time = time;
        }

        public Frame copy()
        {
            return new Frame(this.index, this.time);
        }
    }

    /** The {@code .mcmeta} sidecar of a texture file ({@code skin.png} -> {@code skin.png.mcmeta}). */
    public static File file(File textureFile)
    {
        return new File(textureFile.getParentFile(), textureFile.getName() + EXTENSION);
    }

    /**
     * Read the animation of a texture through the provider — the way the game does, so a texture
     * inside the jar reads too; null when there is no sidecar or it can't be read.
     */
    public static TextureAnimation read(Link link, int stripW, int stripH)
    {
        /* The loader doesn't look for one next to a downloaded texture either */
        if (link == null || link.source.startsWith("http"))
        {
            return null;
        }

        try (InputStream stream = BBSMod.getProvider().getAsset(new Link(link.source, link.path + EXTENSION)))
        {
            MapType data = DataToString.mapFromString(IOUtils.readText(stream));

            return data == null || !data.has("animation") ? null : fromMcmeta(data, stripW, stripH);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** The section as {@link mchorse.bbs_mod.graphics.texture.AnimatedTexture#load} reads it. */
    public static TextureAnimation fromMcmeta(MapType data, int stripW, int stripH)
    {
        TextureAnimation animation = new TextureAnimation();
        MapType section = data.getMap("animation");

        animation.source = data;
        animation.frametime = Math.max(1, section.getInt("frametime", DEFAULT_FRAMETIME));
        animation.width = Math.max(0, section.getInt("width", 0));
        animation.height = Math.max(0, section.getInt("height", 0));

        for (BaseType frame : section.getList("frames"))
        {
            if (frame.isNumeric())
            {
                animation.frames.add(new Frame(frame.asNumeric().intValue(), 0));
            }
            else if (frame.isMap())
            {
                MapType map = frame.asMap();

                animation.frames.add(new Frame(map.getInt("index", 0), Math.max(0, map.getInt("time", 0))));
            }
        }

        if (animation.frames.isEmpty())
        {
            animation.fillDefaultFrames(stripW, stripH);
        }

        return animation;
    }

    /** A fresh animation for a texture: the whole texture is the one image, shown as the one frame. */
    public static TextureAnimation create(int stripW, int stripH)
    {
        TextureAnimation animation = new TextureAnimation();

        animation.frametime = NEW_FRAMETIME;
        animation.width = stripW;
        animation.height = stripH;
        animation.frames.add(new Frame(0, 0));

        return animation;
    }

    /* The strip */

    /** The loader's frame size when the file doesn't give one: a square of the strip's smaller side. */
    public static int defaultFrameSize(int stripW, int stripH)
    {
        return Math.max(1, Math.min(stripW, stripH));
    }

    public int frameWidth(int stripW, int stripH)
    {
        return this.width > 0 ? this.width : defaultFrameSize(stripW, stripH);
    }

    public int frameHeight(int stripW, int stripH)
    {
        return this.height > 0 ? this.height : defaultFrameSize(stripW, stripH);
    }

    /** How many images the strip holds; a strip shorter than one image holds none, as in the loader. */
    public int imageCount(int stripW, int stripH)
    {
        return stripH / this.frameHeight(stripW, stripH);
    }

    /** Every image once, in order — what the loader plays when the file lists no frames. */
    public void fillDefaultFrames(int stripW, int stripH)
    {
        this.frames.clear();

        for (int i = 0, c = Math.max(1, this.imageCount(stripW, stripH)); i < c; i++)
        {
            this.frames.add(new Frame(i, 0));
        }
    }

    /** Whether the order is the one the loader assumes without a list: every image once, in order, for {@link #frametime}. */
    public boolean isDefaultSequence(int stripW, int stripH)
    {
        if (this.frames.size() != Math.max(1, this.imageCount(stripW, stripH)))
        {
            return false;
        }

        for (int i = 0; i < this.frames.size(); i++)
        {
            Frame frame = this.frames.get(i);

            if (frame.index != i || !this.isDefaultTime(frame))
            {
                return false;
            }
        }

        return true;
    }

    private boolean isDefaultTime(Frame frame)
    {
        return frame.time <= 0 || frame.time == this.frametime;
    }

    /** Ticks a frame stays on show. */
    public int timeOf(Frame frame)
    {
        return frame.time > 0 ? frame.time : this.frametime;
    }

    /** Ticks one run of the animation takes. */
    public int length()
    {
        int length = 0;

        for (Frame frame : this.frames)
        {
            length += this.timeOf(frame);
        }

        return length;
    }

    /** The frame on show at a tick, looping — the walk {@link mchorse.bbs_mod.graphics.texture.AnimatedTexture#getTexture} makes. */
    public int frameAt(int tick)
    {
        int length = this.length();

        if (length <= 0 || this.frames.isEmpty())
        {
            return 0;
        }

        int t = ((tick % length) + length) % length;

        for (int i = 0; i < this.frames.size(); i++)
        {
            t -= this.timeOf(this.frames.get(i));

            if (t < 0)
            {
                return i;
            }
        }

        return this.frames.size() - 1;
    }

    /* The file */

    /**
     * The file to write: what was read, with the known keys of the {@code animation} section
     * rewritten. A key the file didn't have is added only when it says something the loader
     * wouldn't assume anyway; a key the file did have stays, even at its default — so a file
     * that wasn't touched comes out saying exactly what it said.
     */
    public MapType toMcmeta(int stripW, int stripH)
    {
        MapType data = this.source == null ? new MapType() : this.source;
        MapType section = data.getMap("animation");
        int size = defaultFrameSize(stripW, stripH);

        if (this.frametime != DEFAULT_FRAMETIME || section.has("frametime"))
        {
            section.putInt("frametime", this.frametime);
        }

        putSize(section, "width", this.width, size);
        putSize(section, "height", this.height, size);

        if (this.isDefaultSequence(stripW, stripH) && !section.has("frames"))
        {
            section.remove("frames");
        }
        else
        {
            ListType list = new ListType();

            for (Frame frame : this.frames)
            {
                if (this.isDefaultTime(frame))
                {
                    list.addInt(frame.index);
                }
                else
                {
                    MapType map = new MapType();

                    map.putInt("index", frame.index);
                    map.putInt("time", frame.time);
                    list.add(map);
                }
            }

            section.put("frames", list);
        }

        data.put("animation", section);

        return data;
    }

    private static void putSize(MapType section, String key, int value, int defaultSize)
    {
        if (value > 0 && (value != defaultSize || section.has(key)))
        {
            section.putInt(key, value);
        }
        else
        {
            section.remove(key);
        }
    }

    /** Write the sidecar beside a texture file; whether it worked. */
    public boolean write(File textureFile, int stripW, int stripH)
    {
        return DataToString.writeSilently(file(textureFile), this.toMcmeta(stripW, stripH), true);
    }

    /* Undo snapshots — the editable state only; the file as read stays with the object */

    @Override
    public void toData(MapType data)
    {
        data.putInt("frametime", this.frametime);
        data.putInt("width", this.width);
        data.putInt("height", this.height);

        ListType list = new ListType();

        for (Frame frame : this.frames)
        {
            MapType map = new MapType();

            map.putInt("index", frame.index);
            map.putInt("time", frame.time);
            list.add(map);
        }

        data.put("frames", list);
    }

    @Override
    public void fromData(MapType data)
    {
        this.frametime = Math.max(1, data.getInt("frametime", DEFAULT_FRAMETIME));
        this.width = data.getInt("width", 0);
        this.height = data.getInt("height", 0);
        this.frames.clear();

        for (BaseType frame : data.getList("frames"))
        {
            if (frame.isMap())
            {
                MapType map = frame.asMap();

                this.frames.add(new Frame(map.getInt("index", 0), map.getInt("time", 0)));
            }
        }
    }
}
