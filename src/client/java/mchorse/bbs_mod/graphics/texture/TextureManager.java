package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.MultiLink;
import mchorse.bbs_mod.utils.resources.MultiLinkThread;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TextureManager implements IWatchDogListener
{
    public final Map<Link, Texture> textures = new HashMap<>();
    public final Map<Link, AnimatedTexture> animatedTextures = new HashMap<>();

    /**
     * Variant copies of loaded textures (link &rarr; variant key &rarr; texture), used by the
     * material PBR sliders: Iris caches PBR holders by the albedo's GL id, so a material with
     * its own slider values needs its own GL texture. The variant key encodes the slider
     * values — moving a slider lands on a NEW id, which is how edits invalidate Iris' cache.
     * A few variants per link are kept; older ones are deleted.
     */
    private final Map<Link, LinkedHashMap<String, Texture>> variants = new HashMap<>();

    /**
     * Links whose asset couldn't be read. The error texture stands in for them, but it must NEVER
     * be stored as their value: it is ONE shared texture, while everything inside {@link #textures},
     * {@link #animatedTextures} and {@link #variants} is owned there and deleted with its link.
     * Storing the stand-in among them let a single link's deletion (Iris closing a PBR wrapper, the
     * watchdog seeing a file change, the reload button) kill it for every other missing texture at
     * once — and a deleted texture keeps id -1, which the shader binds on every draw that follows.
     */
    private final Set<Link> failed = new HashSet<>();

    public AssetProvider provider;

    private Texture error;
    private TextureExtruder extruder = new TextureExtruder();
    private int tick;
    private Texture lastBound;

    public TextureManager(AssetProvider provider)
    {
        this.provider = provider;
    }

    public TextureExtruder getExtruder()
    {
        return this.extruder;
    }

    public Texture getError()
    {
        if (this.error != null && !this.error.isValid())
        {
            /* Somebody deleted the stand-in — rebuild it instead of handing out a dead GL id. */
            this.error = null;
        }

        if (this.error == null)
        {
            try
            {
                Pixels pixels = Pixels.fromSize(16, 16);
                Color a = new Color().set(0xff009fe0);
                Color b = new Color().set(0xffe00073);

                for (int x = 0; x < pixels.width; x++)
                {
                    for (int y = 0; y < pixels.height; y++)
                    {
                        Color color = a;

                        if ((x / 4) % 2 == 0 ^ (y / 4) % 2 == 0)
                        {
                            color = b;
                        }

                        pixels.setColor(x, y, color);
                    }
                }

                pixels.rewindBuffer();

                Texture texture = new Texture();
                texture.setFilter(GL11.GL_NEAREST);
                texture.uploadTexture(pixels);
                texture.unbind();

                this.error = texture;
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return this.error;
    }

    public void bindTexture(Link texture)
    {
        this.bindTexture(texture, 0);
    }

    public void bindTexture(Link texture, int unit)
    {
        this.bindTexture(this.getTexture(texture), unit);
    }

    public void bindTexture(Texture texture)
    {
        this.bindTexture(texture, 0);
    }

    public void bindTexture(Texture texture, int unit)
    {
        BBSRendering.trackTexture(texture);

        if (unit == 0)
        {
            this.lastBound = texture;
        }

        RenderSystem.setShaderTexture(unit, texture.id);
    }

    /**
     * The texture most recently bound to unit 0 through this manager. Model renderers use it
     * to reach the {@link Texture} of the form's base texture (bound before the model draws)
     * when a material doesn't resolve its own override.
     */
    public Texture getLastBound()
    {
        return this.lastBound;
    }

    public void bind(Link texture)
    {
        this.getTexture(texture).bind();
    }

    public void bind(Link texture, int unit)
    {
        this.getTexture(texture).bind(unit);
    }

    public boolean has(Link link)
    {
        return this.getTexture(link) != this.getError();
    }

    public void delete(Link link)
    {
        this.failed.remove(link);

        Texture texture = this.textures.remove(link);

        if (texture != null)
        {
            texture.delete();
        }

        AnimatedTexture animatedTexture = this.animatedTextures.remove(link);

        if (animatedTexture != null)
        {
            animatedTexture.delete();
        }

        this.deleteVariants(link);
    }

    private void deleteVariants(Link link)
    {
        LinkedHashMap<String, Texture> byKey = this.variants.remove(link);

        if (byKey != null)
        {
            for (Texture variant : byKey.values())
            {
                variant.delete();
            }
        }
    }

    /**
     * A separate GL copy of the texture under the given variant key, loaded from the same
     * pixels. Returns the error texture when the source can't be read.
     */
    public Texture getVariant(Link link, String key)
    {
        if (this.failed.contains(link))
        {
            return this.getError();
        }

        LinkedHashMap<String, Texture> byKey = this.variants.computeIfAbsent(link, (l) -> new LinkedHashMap<>());
        Texture texture = byKey.get(key);

        if (texture == null)
        {
            Pixels pixels;

            try
            {
                pixels = this.getPixels(link);
            }
            catch (Exception e)
            {
                pixels = null;
            }

            if (pixels == null)
            {
                this.failed.add(link);

                return this.getError();
            }

            texture = Texture.textureFromPixels(pixels, GL11.GL_NEAREST);

            byKey.put(key, texture);

            /* Slider drags walk through many intermediate values; keep the tail short. */
            Iterator<Texture> it = byKey.values().iterator();

            while (byKey.size() > 4 && it.hasNext())
            {
                Texture old = it.next();

                it.remove();
                old.delete();
            }
        }

        return texture;
    }

    public Texture createTexture(Link link)
    {
        return this.createTexture(link, GL11.GL_NEAREST);
    }

    public Texture createTexture(Link link, int filter)
    {
        Texture texture = this.textures.get(link);

        if (texture == null)
        {
            texture = new Texture();
            texture.setFilter(filter);

            this.textures.put(link, texture);
            this.failed.remove(link);
        }

        return texture;
    }

    public Pixels getPixels(Link link) throws Exception
    {
        Pixels pixels;

        if (Link.COLOR.equals(link.source))
        {
            pixels = Pixels.fromSize(1, 1);
            pixels.setColor(0, 0, new Color().set((int) Long.parseLong(link.path, 16)));
            pixels.rewindBuffer();

            return pixels;
        }

        if (link instanceof MultiLink)
        {
            pixels = MultiLinkThread.getStreamForMultiLink((MultiLink) link);
        }
        else
        {
            try (InputStream asset = this.provider.getAsset(link))
            {
                pixels = Pixels.fromPNGStream(asset);
            }
        }

        return pixels;
    }

    public Texture getTexture(Link link)
    {
        return this.getTexture(link, GL11.GL_NEAREST);
    }

    public Texture getTexture(Link link, int filter)
    {
        return this.getTexture(link, filter, false);
    }

    public Texture getTexture(Link link, int filter, boolean silent)
    {
        Texture texture = this.get(link);

        if (texture == null)
        {
            if (this.failed.contains(link))
            {
                /* Asked before and the asset wasn't there — don't re-read the file every frame. */
                return this.getError();
            }

            try
            {
                Pixels pixels = this.getPixels(link);

                if (pixels != null)
                {
                    if (!link.source.startsWith("http"))
                    {
                        try (InputStream stream = this.provider.getAsset(new Link(link.source, link.path + ".mcmeta")))
                        {
                            AnimatedTexture animatedTexture = AnimatedTexture.load(stream, pixels);

                            texture = animatedTexture.getTexture(this.tick);

                            System.out.println("Animated texture \"" + link + "\" was loaded!");

                            this.animatedTextures.put(link, animatedTexture);

                            return texture;
                        }
                        catch (Exception e)
                        {}
                    }

                    texture = Texture.textureFromPixels(pixels, filter);

                    System.out.println("Texture \"" + link + "\" was loaded!");

                    this.textures.put(link, texture);
                }
                else
                {
                    this.failed.add(link);

                    return this.getError();
                }
            }
            catch (Exception e)
            {
                if (!silent)
                {
                    e.printStackTrace();
                }

                this.failed.add(link);

                return this.getError();
            }
        }

        return texture;
    }

    private Texture get(Link link)
    {
        if (this.animatedTextures.containsKey(link))
        {
            Texture texture = this.animatedTextures.get(link).getTexture(this.tick);

            return texture == null ? this.getError() : texture;
        }

        return this.textures.get(link);
    }

    public void delete()
    {
        for (Texture texture : this.textures.values())
        {
            texture.delete();
        }

        for (AnimatedTexture animatedTexture : this.animatedTextures.values())
        {
            animatedTexture.delete();
        }

        for (LinkedHashMap<String, Texture> byKey : this.variants.values())
        {
            for (Texture variant : byKey.values())
            {
                variant.delete();
            }
        }

        this.variants.clear();
        this.textures.clear();
        this.animatedTextures.clear();
        this.failed.clear();
        this.extruder.deleteAll();
    }

    public void update()
    {
        this.tick += 1;
    }

    /**
     * Watch dog listener implementation. This method should reload any texture
     * from "assets" source (which is in game's assets folder).
     */
    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link == null)
        {
            return;
        }

        if (link.path.endsWith(".mcmeta"))
        {
            link = new Link(link.source, StringUtils.removeExtension(link.path));
        }

        this.failed.remove(link);

        Texture texture = this.textures.remove(link);

        if (texture != null)
        {
            texture.delete();
        }

        AnimatedTexture remove = this.animatedTextures.remove(link);

        if (remove != null)
        {
            remove.delete();
        }

        this.deleteVariants(link);
        this.extruder.delete(link);
    }
}