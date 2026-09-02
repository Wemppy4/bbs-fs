package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SoundManager implements IWatchDogListener
{
    /**
     * An owned source whose owner stopped asking for it this long is orphaned: the clip
     * was deleted, or the film it belongs to was rebuilt. Nothing announces either.
     */
    private static final long OWNED_IDLE_MS = 5_000;

    private AssetProvider provider;
    private Map<Link, SoundBuffer> buffers = new HashMap<>();
    private List<SoundPlayer> sounds = new ArrayList<>();

    public SoundManager(AssetProvider provider)
    {
        this.provider = provider;
    }

    public Collection<SoundPlayer> getPlayers()
    {
        return this.sounds;
    }

    /**
     * Load a sound buffer (optionally include a waveform).
     */
    public SoundBuffer load(Link link, boolean includeWaveform)
    {
        try
        {
            Wave wave = AudioReader.read(this.provider, link);
            Waveform waveform = null;

            if (includeWaveform)
            {
                if (wave.getBytesPerSample() > 2)
                {
                    wave = wave.convertTo16();
                }

                waveform = new Waveform();
                waveform.generate(wave, this.readColorCodes(link), BBSSettings.audioWaveformDensity.get(), 40);
            }

            SoundBuffer buffer = new SoundBuffer(link, wave, waveform);

            this.buffers.put(link, buffer);

            System.out.println("Sound \"" + link + "\" was loaded!");

            return buffer;
        }
        catch (Exception e)
        {
            this.buffers.put(link, null);

            e.printStackTrace();
        }

        return null;
    }

    public List<ColorCode> readColorCodes(Link link)
    {
        try (InputStream stream = this.provider.getAsset(new Link(link.source, link.path + ".json")))
        {
            String string = IOUtils.readText(stream);
            ListType data = DataToString.listFromString(string);

            if (data != null && !data.isEmpty())
            {
                List<ColorCode> colorCodes = new ArrayList<>();

                for (BaseType type : data)
                {
                    if (!type.isList())
                    {
                        continue;
                    }

                    ColorCode colorCode = new ColorCode();

                    colorCode.fromData(type.asList());
                    colorCodes.add(colorCode);
                }

                if (!colorCodes.isEmpty())
                {
                    return colorCodes;
                }
            }
        }
        catch (IOException e)
        {}

        return null;
    }

    public void saveColorCodes(Link link, List<ColorCode> colorCodes)
    {
        File file = this.provider.getFile(link);

        if (file != null)
        {
            ListType data = new ListType();

            for (ColorCode color : colorCodes)
            {
                data.add(color.toData());
            }

            try
            {
                IOUtils.writeText(file, DataToString.toString(data, true));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public SoundBuffer get(Link link, boolean includeWaveform)
    {
        if (!this.buffers.containsKey(link))
        {
            return this.load(link, includeWaveform);
        }

        SoundBuffer player = this.buffers.get(link);

        if (player != null && includeWaveform && player.getWaveform() == null)
        {
            player.delete();

            return this.load(link, true);
        }

        return player;
    }

    public SoundPlayer play(Link link)
    {
        SoundBuffer buffer = this.get(link, false);

        if (buffer != null)
        {
            SoundPlayer player = new SoundPlayer(buffer);

            player.play();
            this.sounds.add(player);

            return player;
        }

        return null;
    }

    /**
     * The owner's own source for this file, made on the first ask.
     *
     * <p>Keyed by the OWNER, not by the file: two clips playing the same audio sit at
     * different positions, and one shared source can only be at one of them - the
     * later clip used to simply overwrite the earlier one's playback.</p>
     */
    public SoundPlayer playUnique(Object owner, Link link)
    {
        Iterator<SoundPlayer> it = this.sounds.iterator();

        while (it.hasNext())
        {
            SoundPlayer player = it.next();

            if (player.getOwner() != owner)
            {
                continue;
            }

            if (player.getBuffer().getId().equals(link))
            {
                player.refresh();

                return player;
            }

            /* The owner points at another file now - its old source has nothing left to play */
            player.stop();
            player.delete();
            it.remove();

            break;
        }

        SoundBuffer buffer = this.get(link, true);

        if (buffer != null)
        {
            SoundPlayer player = new SoundPlayer(buffer).unique().owner(owner);

            player.setRelative(true);
            player.play();
            this.sounds.add(player);

            return player;
        }

        return null;
    }

    /**
     * Dispose of an owner's source (a clip whose context is shutting down).
     */
    public void stopOwned(Object owner)
    {
        Iterator<SoundPlayer> it = this.sounds.iterator();

        while (it.hasNext())
        {
            SoundPlayer player = it.next();

            if (player.getOwner() == owner)
            {
                player.stop();
                player.delete();
                it.remove();
            }
        }
    }

    public void stop(Link link)
    {
        Iterator<SoundPlayer> it = this.sounds.iterator();

        while (it.hasNext())
        {
            SoundPlayer player = it.next();

            if (player.getBuffer().getId().equals(link))
            {
                player.stop();
                player.delete();

                it.remove();
            }
        }
    }

    /* Updating methods (general update, update position, velocity and orientation) */

    public void update()
    {
        long now = System.currentTimeMillis();
        Iterator<SoundPlayer> it = this.sounds.iterator();

        while (it.hasNext())
        {
            SoundPlayer player = it.next();
            boolean orphaned = player.getOwner() != null && now - player.getLastUsed() > OWNED_IDLE_MS;

            if (player.canBeRemoved() || orphaned)
            {
                player.stop();
                player.delete();
                it.remove();
            }
        }
    }

    public void deleteSounds()
    {
        for (SoundPlayer player : this.sounds)
        {
            if (player != null)
            {
                player.delete();
            }
        }

        this.sounds.clear();

        for (SoundBuffer buffer : this.buffers.values())
        {
            if (buffer != null)
            {
                buffer.delete();
            }
        }

        this.buffers.clear();
    }

    public void deleteSound(Link audio)
    {
        SoundBuffer buffer = this.buffers.remove(audio);

        if (buffer != null)
        {
            Iterator<SoundPlayer> it = this.sounds.iterator();

            if (it.hasNext())
            {
                SoundPlayer player = it.next();

                if (player.getBuffer() == buffer)
                {
                    it.remove();
                    player.delete();
                }
            }

            buffer.delete();
        }
    }

    /* Watch dog listener implementation */

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        if (!Files.isRegularFile(path))
        {
            return;
        }

        Link link = BBSMod.getProvider().getLink(path.toFile());
        String pathLower = link.path.toLowerCase();

        if (!(pathLower.endsWith(".ogg") || pathLower.endsWith(".wav")))
        {
            return;
        }

        if (this.buffers.containsKey(link))
        {
            this.stop(link);

            SoundBuffer buffer = this.buffers.remove(link);

            if (buffer != null)
            {
                buffer.delete();
            }
        }
    }
}