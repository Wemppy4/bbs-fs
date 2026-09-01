package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.Stack;

public class MultiLinkThread implements Runnable
{
    private static MultiLinkThread instance;
    private static Thread thread;

    public Stack<MultiLink> links = new Stack<>();

    /**
     * Get stream for multi resource location
     */
    public static Pixels getStreamForMultiLink(MultiLink multi) throws IOException
    {
        if (multi.children.isEmpty())
        {
            throw new IOException("Given MultiLink is empty!");
        }

        try
        {
            if (BBSSettings.multiskinMultiThreaded.get())
            {
                add(multi);

                return null;
            }
            else
            {
                clear();

                return TextureProcessor.process(multi);
            }
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    public static synchronized void add(MultiLink location)
    {
        if (instance != null && !thread.isAlive())
        {
            instance = null;
        }

        if (instance == null)
        {
            instance = new MultiLinkThread();
            instance.addLink(location);
            thread = new Thread(instance);
            thread.start();
        }
        else
        {
            instance.addLink(location);
        }
    }

    public static void clear()
    {
        instance = null;
    }

    public synchronized void addLink(MultiLink link)
    {
        if (this.links.contains(link))
        {
            return;
        }

        this.links.add(link);
    }

    @Override
    public void run()
    {
        while (!this.links.isEmpty() && instance != null)
        {
            MultiLink location = this.links.peek();

            try
            {
                this.links.pop();

                Pixels pixels = TextureProcessor.process(location);

                MinecraftClient.getInstance().execute(() ->
                {
                    Texture newTexture = BBSModClient.getTextures().createTexture(location);

                    newTexture.bind();
                    newTexture.uploadTexture(pixels);

                    if (newTexture.isMipmap())
                    {
                        newTexture.generateMipmap();
                    }
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        instance = null;
        thread = null;
    }
}