package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Framebuffers handed out by size and taken back after the draw. Forms that render into a
 * framebuffer of their own ask here instead of keeping one each, so a hundred of them cost
 * as many buffers as there are sizes on screen at once, not a hundred.
 */
public class FramebufferPool
{
    private static final int MAX_IDLE_BUFFERS = 8;
    private static final long MAX_IDLE_BYTES = 128L * 1024L * 1024L;

    private final Set<Framebuffer> idle = new LinkedHashSet<>();
    private final Set<Framebuffer> active = new HashSet<>();
    private long idleBytes;

    private static long getBytes(Framebuffer framebuffer)
    {
        Texture texture = framebuffer.getMainTexture();

        return (long) texture.width * texture.height * 8L;
    }

    private static Framebuffer create(int width, int height)
    {
        int previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        Framebuffer framebuffer = new Framebuffer();

        try
        {
            Texture texture = new Texture();

            texture.setSize(width, height);
            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);
            texture.setParameter(GL30.GL_TEXTURE_MAX_LEVEL, 0);

            Renderbuffer renderbuffer = new Renderbuffer();

            renderbuffer.resize(width, height);

            framebuffer.deleteTextures().attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            framebuffer.attach(renderbuffer);

            return framebuffer;
        }
        catch (RuntimeException | Error e)
        {
            framebuffer.delete();

            throw e;
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }
    }

    public Framebuffer get(int width, int height)
    {
        Iterator<Framebuffer> iterator = this.idle.iterator();

        while (iterator.hasNext())
        {
            Framebuffer framebuffer = iterator.next();
            Texture texture = framebuffer.getMainTexture();

            if (texture.width == width && texture.height == height)
            {
                iterator.remove();
                this.idleBytes -= getBytes(framebuffer);
                this.active.add(framebuffer);

                return framebuffer;
            }
        }

        Framebuffer framebuffer = create(width, height);

        this.active.add(framebuffer);

        return framebuffer;
    }

    public void release(Framebuffer framebuffer)
    {
        if (!this.active.remove(framebuffer))
        {
            return;
        }

        this.idle.add(framebuffer);
        this.idleBytes += getBytes(framebuffer);

        Iterator<Framebuffer> iterator = this.idle.iterator();

        while (this.idle.size() > MAX_IDLE_BUFFERS || this.idleBytes > MAX_IDLE_BYTES)
        {
            Framebuffer oldest = iterator.next();

            iterator.remove();
            this.idleBytes -= getBytes(oldest);
            oldest.delete();
        }
    }

    public void delete()
    {
        for (Framebuffer framebuffer : this.idle)
        {
            framebuffer.delete();
        }

        for (Framebuffer framebuffer : this.active)
        {
            framebuffer.delete();
        }

        this.idle.clear();
        this.active.clear();
        this.idleBytes = 0L;
    }
}
