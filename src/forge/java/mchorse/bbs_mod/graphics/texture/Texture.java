package mchorse.bbs_mod.graphics.texture;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

/**
 * Texture class
 * 
 * This class is responsible for managing a state of a texture
 */
public class Texture
{
    public int id;
    public int target;

    public int width;
    public int height;

    private boolean mipmap;
    private boolean clearable;
    private boolean translucent;

    private AnimatedTexture parent;
    private TextureFormat format = TextureFormat.RGBA_U8;
    private int filter;

    public static Pixels pixelsFromTexture(Texture texture)
    {
        if (!texture.isValid())
        {
            return null;
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(texture.width * texture.height * 4);
        int active = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int packBuffer = GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try
        {
            texture.bind();
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glGetTexImage(texture.target, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        }
        finally
        {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GlStateManager.bindTexture(binding);
            GlStateManager.setActiveTexture(active);
        }

        return new Pixels(buffer, texture.width, texture.height);
    }

    public static Texture textureFromPixels(Pixels pixels, int filter)
    {
        Texture texture = new Texture();

        texture.setFilter(filter);
        texture.uploadTexture(pixels);
        texture.unbind();

        return texture;
    }

    public Texture()
    {
        this.id = TextureUtil.glGenTextures();
        this.target = GL11.GL_TEXTURE_2D;

        this.bind();
    }

    public void setParent(AnimatedTexture parent)
    {
        this.parent = parent;
    }

    public AnimatedTexture getParent()
    {
        return this.parent;
    }

    public void setClearable(boolean clearable)
    {
        this.clearable = clearable;
    }

    public boolean isClearable()
    {
        return this.clearable;
    }

    public TextureFormat getFormat()
    {
        return this.format;
    }

    public boolean isMipmap()
    {
        return this.mipmap;
    }

    /**
     * Whether this texture has semi-transparent pixels (alpha strictly between the model
     * shader's 0.1 cutout threshold and fully opaque). Such textures need the two-pass
     * translucency treatment; plain opaque/cutout textures render in a single pass.
     */
    public boolean hasTranslucency()
    {
        return this.translucent;
    }

    public boolean isReallyMipmap()
    {
        return this.mipmap && this.getParameter(org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL) > 0;
    }

    public boolean isValid()
    {
        return this.id >= 0;
    }

    public void bind()
    {
        if (this.target == GL11.GL_TEXTURE_2D) GlStateManager.bindTexture(this.id);
        else GL11.glBindTexture(this.target, this.id);
    }

    public void bind(int texture)
    {
        GlStateManager.setActiveTexture(texture);
        if (this.target == GL11.GL_TEXTURE_2D) GlStateManager.bindTexture(this.id);
        else GL11.glBindTexture(this.target, this.id);
    }

    public void unbind()
    {
        if (this.target == GL11.GL_TEXTURE_2D) GlStateManager.bindTexture(0);
        else GL11.glBindTexture(this.target, 0);
    }

    public void unbind(int texture)
    {
        GlStateManager.setActiveTexture(texture);
        if (this.target == GL11.GL_TEXTURE_2D) GlStateManager.bindTexture(0);
        else GL11.glBindTexture(this.target, 0);
    }

    public void setFormat(TextureFormat format)
    {
        this.format = format;
    }

    public int getFilter()
    {
        return this.filter;
    }

    public boolean isLinear()
    {
        return this.filter == GL11.GL_LINEAR || this.filter == GL11.GL_LINEAR_MIPMAP_NEAREST;
    }

    public int getParameter(int parameter)
    {
        return GL11.glGetTexParameteri(this.target, parameter);
    }

    public void setFilterMipmap(boolean linear, boolean mipmap)
    {
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;

        this.setFilter(filter);
        this.setParameter(org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, mipmap ? 4 : 0);

        if (mipmap && !this.isMipmap())
        {
            this.generateMipmap();
        }

        this.setParameter(GL11.GL_TEXTURE_MIN_FILTER, mipmap
            ? (linear ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_LINEAR) : filter);
    }

    public void setFilter(int filter)
    {
        this.filter = filter;

        this.setParameter(GL11.GL_TEXTURE_MAG_FILTER, filter);
        this.setParameter(GL11.GL_TEXTURE_MIN_FILTER, filter);
    }

    public void setWrap(int mode)
    {
        this.setParameter(GL11.GL_TEXTURE_WRAP_S, mode);
        this.setParameter(GL11.GL_TEXTURE_WRAP_T, mode);
    }

    public void setParameter(int param, int value)
    {
        GL11.glTexParameteri(this.target, param, value);
    }

    public void delete()
    {
        GlStateManager.deleteTexture(this.id);
        this.id = -1;
    }

    public void setSize(int width, int height)
    {
        this.width = width;
        this.height = height;

        GL11.glTexImage2D(this.target, 0, this.format.internal, width, height, 0, this.format.format, this.format.type, (ByteBuffer) null);
    }

    public void updateTexture(Pixels pixels)
    {
        this.updateTexture(this.target, pixels);
    }

    public void updateTexture(int target, Pixels pixels)
    {
        this.uploadTexture(target, 0, pixels.width, pixels.height, pixels.getBuffer());
    }

    public void uploadTexture(Pixels pixels)
    {
        this.uploadTexture(this.target, pixels);
    }

    public void uploadTexture(int target, Pixels pixels)
    {
        this.uploadTexture(target, 0, pixels);
    }

    public void uploadTexture(int target, int level, Pixels pixels)
    {
        /* Some textures might not be pixel aligned. For example FunkyFight's 398x444 avatar
         * wasn't aligned, and it caused some interesting visual issues when loading
         * the texture. This fixes it.
         *
         * https://www.khronos.org/opengl/wiki/Pixel_Transfer#Pixel_layout */
        if (level == 0)
        {
            this.translucent = scanTranslucency(pixels);
        }

        this.setFormat(pixels.bits == 4 ? TextureFormat.RGBA_U8 : TextureFormat.RGB_U8);
        this.uploadTexture(target, level, pixels.width, pixels.height, pixels.getBuffer());

        pixels.delete();
    }

    public void uploadTexture(int target, int level, int w, int h, ByteBuffer buffer)
    {
        /* Unlike NativeImage in newer Minecraft, 1.12's TextureUtil assumes the
         * default pixel-store state. Leaving an atlas width here corrupts its
         * next lightmap upload and can make the whole world disappear. */
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        try
        {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, w);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glTexImage2D(target, level, this.format.internal, w, h, 0, this.format.format, this.format.type, buffer);
        }
        finally
        {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
        }

        if (level == 0)
        {
            this.width = w;
            this.height = h;
        }
    }

    public void generateMipmap()
    {
        this.mipmap = true;

        GL30.glGenerateMipmap(this.target);
    }

    /**
     * The 26..254 alpha range mirrors the model shader: below 0.1 the fragment is discarded
     * outright (cutout), at 255 it's opaque — only the range between makes blending matter.
     */
    private static boolean scanTranslucency(Pixels pixels)
    {
        if (pixels.bits != 4)
        {
            return false;
        }

        ByteBuffer buffer = pixels.getBuffer();

        for (int i = 3, c = buffer.limit(); i < c; i += 4)
        {
            int alpha = buffer.get(i) & 0xff;

            if (alpha >= 26 && alpha <= 254)
            {
                return true;
            }
        }

        return false;
    }
}
