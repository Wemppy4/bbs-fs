package mchorse.bbs_mod.graphics;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Off-screen targets handed out by size and taken back after the draw. Forms that render into a
 * framebuffer of their own ask here instead of keeping one each, so a hundred of them cost as many
 * targets as there are sizes on screen at once, not a hundred.
 */
public class FramebufferPool
{
    private static final int MAX_IDLE_BUFFERS = 8;
    private static final long MAX_IDLE_BYTES = 128L * 1024L * 1024L;

    private final Set<FormFramebuffer> idle = new LinkedHashSet<>();
    private final Set<FormFramebuffer> active = new HashSet<>();
    private long idleBytes;

    public FormFramebuffer get(int width, int height)
    {
        Iterator<FormFramebuffer> iterator = this.idle.iterator();

        while (iterator.hasNext())
        {
            FormFramebuffer framebuffer = iterator.next();

            if (framebuffer.width == width && framebuffer.height == height)
            {
                iterator.remove();
                this.idleBytes -= framebuffer.getBytes();
                this.active.add(framebuffer);

                return framebuffer;
            }
        }

        FormFramebuffer framebuffer = new FormFramebuffer(width, height);

        this.active.add(framebuffer);

        return framebuffer;
    }

    public void release(FormFramebuffer framebuffer)
    {
        if (!this.active.remove(framebuffer))
        {
            return;
        }

        this.idle.add(framebuffer);
        this.idleBytes += framebuffer.getBytes();

        Iterator<FormFramebuffer> iterator = this.idle.iterator();

        while (this.idle.size() > MAX_IDLE_BUFFERS || this.idleBytes > MAX_IDLE_BYTES)
        {
            FormFramebuffer oldest = iterator.next();

            iterator.remove();
            this.idleBytes -= oldest.getBytes();
            oldest.close();
        }
    }

    public void delete()
    {
        for (FormFramebuffer framebuffer : this.idle)
        {
            framebuffer.close();
        }

        for (FormFramebuffer framebuffer : this.active)
        {
            framebuffer.close();
        }

        this.idle.clear();
        this.active.clear();
        this.idleBytes = 0L;
    }
}
