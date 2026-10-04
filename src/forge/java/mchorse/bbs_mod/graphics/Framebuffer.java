package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import java.util.ArrayList;
import java.util.List;

public class Framebuffer
{
    private static final FloatBuffer CLEAR_COLOR = BufferUtils.createFloatBuffer(4);
    private static final FloatBuffer CLEAR_DEPTH = (FloatBuffer) BufferUtils.createFloatBuffer(4).put(1F).put(0F).put(0F).put(0F).flip();

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
        IntBuffer buffer = BufferUtils.createIntBuffer(attachments.length);
        buffer.put(attachments).flip();
        GL20.glDrawBuffers(buffer);
    }

    public void applyClear()
    {
        this.apply();
        this.clear();
    }

    public void apply()
    {
        Texture texture = this.getMainTexture();

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
                        GL30.glClearBuffer(GL11.GL_COLOR, i, CLEAR_COLOR);
                    }

                    i += 1;
                }
                else if (texture.isClearable())
                {
                    GL30.glClearBuffer(GL11.GL_DEPTH, 0, CLEAR_DEPTH);
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