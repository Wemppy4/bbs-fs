package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

public class Framebuffer
{
    private static final float[] CLEAR_COLOR = {0F, 0F, 0F, 0F};
    private static final float[] CLEAR_DEPTH = {1F};

    public int id;
    public List<Texture> textures = new ArrayList<>();
    public final List<Renderbuffer> renderbuffers = new ArrayList<>();

    private boolean deleteTextures;
    private boolean advancedClearing;

    public Framebuffer()
    {
        this.id = GL30.glGenFramebuffers();
    }

    public Framebuffer enableAdvancedClearing()
    {
        this.advancedClearing = true;

        return this;
    }

    public Framebuffer deleteTextures()
    {
        this.deleteTextures = true;

        return this;
    }

    public Texture getMainTexture()
    {
        return this.textures.get(0);
    }

    /**
     * Attach a texture as one of the attachment buffers
     */
    public Framebuffer attach(Texture texture, int attachment)
    {
        this.textures.add(texture);

        this.bind();
        texture.bind();

        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, texture.target, texture.id, 0);

        return this;
    }

    /**
     * Attach a renderbuffer as one of the attachment buffers
     */
    public void attach(Renderbuffer renderbuffer)
    {
        this.renderbuffers.add(renderbuffer);
        renderbuffer.bind();

        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, renderbuffer.target, GL30.GL_RENDERBUFFER, renderbuffer.id);
    }

    public void attachments(int count)
    {
        int[] attachments = new int[count];

        for (int i = 0; i < count; i++)
        {
            attachments[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }

        this.attachments(attachments);
    }

    public void attachments(int... attachments)
    {
        GL30.glDrawBuffers(attachments);
    }

    public void applyClear()
    {
        this.apply();
        this.clear();
    }

    public void apply()
    {
        Texture texture = this.getMainTexture();

        /* TODO(1.21.11 render): 1.21.1 routes this through RenderSystem.viewport and NEVER raw
         * GL11.glViewport — both Minecraft and Sodium keep their own record of the viewport, and
         * Sodium 0.8+ skips a glViewport call whose arguments match its record
         * (GlStateManagerMixin#skipRedundantViewport, @WrapWithCondition on GlStateManager._viewport).
         * A raw call sets the GPU without updating either record, so the next legitimate restore back
         * to the previous viewport looks redundant to Sodium and is swallowed — leaving the whole UI
         * drawn into this framebuffer's viewport. RenderSystem.viewport() no longer exists here (a
         * RenderPass carries its own viewport), so the raw call stands until this framebuffer path is
         * rebuilt on render passes. */
        GL11.glViewport(0, 0, texture.width, texture.height);
        this.bind();
    }

    public void clear()
    {
        if (this.advancedClearing)
        {
            int i = 0;

            for (Texture texture : this.textures)
            {
                if (texture.getFormat().isColor())
                {
                    if (texture.isClearable())
                    {
                        GL30.glClearBufferfv(GL30.GL_COLOR, i, CLEAR_COLOR);
                    }

                    i += 1;
                }
                else if (texture.isClearable())
                {
                    GL30.glClearBufferfv(GL30.GL_DEPTH, 0, CLEAR_DEPTH);
                }
            }
        }
        else
        {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }
    }

    public void bind()
    {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.id);
    }

    public void unbind()
    {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public void resize(int w, int h)
    {
        for (Texture texture : this.textures)
        {
            texture.bind();
            texture.setSize(w, h);
        }

        for (Renderbuffer renderbuffer : this.renderbuffers)
        {
            renderbuffer.bind();
            renderbuffer.resize(w, h);
            renderbuffer.unbind();
        }
    }

    public void delete()
    {
        GL30.glDeleteFramebuffers(this.id);

        if (this.deleteTextures)
        {
            for (Texture texture : this.textures)
            {
                texture.delete();
            }

            this.textures.clear();
        }

        for (Renderbuffer renderbuffer : this.renderbuffers)
        {
            renderbuffer.delete();
        }

        this.renderbuffers.clear();
    }
}