package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.resources.Link;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FramebufferManager
{
    public final Map<Link, Framebuffer> framebuffers = new HashMap<>();
    private FramebufferPool formFramebuffers;

    public FramebufferPool getFormFramebuffers()
    {
        if (this.formFramebuffers == null)
        {
            this.formFramebuffers = new FramebufferPool();
        }

        return this.formFramebuffers;
    }

    public Framebuffer getFramebuffer(Link key, Consumer<Framebuffer> setup)
    {
        Framebuffer framebuffer = this.framebuffers.get(key);

        if (framebuffer == null)
        {
            framebuffer = new Framebuffer();

            setup.accept(framebuffer);

            this.framebuffers.put(key, framebuffer);
        }

        return framebuffer;
    }

    public void delete()
    {
        for (Framebuffer framebuffer : this.framebuffers.values())
        {
            framebuffer.delete();
        }

        this.framebuffers.clear();

        if (this.formFramebuffers != null)
        {
            this.formFramebuffers.delete();
        }
    }
}