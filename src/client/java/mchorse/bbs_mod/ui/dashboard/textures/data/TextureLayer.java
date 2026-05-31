package mchorse.bbs_mod.ui.dashboard.textures.data;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

public class TextureLayer
{
    public String name;
    public Pixels pixels;
    public Texture texture;
    public boolean visible = true;
    public float opacity = 1.0F;

    public TextureLayer(String name, Pixels pixels)
    {
        this.name = name;
        this.pixels = pixels;
        this.texture = new Texture();
        this.texture.setFilter(GL11.GL_NEAREST);
        
        this.updateTexture();
    }

    public void updateTexture()
    {
        if (this.pixels != null)
        {
            this.pixels.rewindBuffer();
            this.texture.bind();
            this.texture.updateTexture(this.pixels);
        }
    }

    public void delete()
    {
        if (this.pixels != null)
        {
            this.pixels.delete();
        }
        
        if (this.texture != null)
        {
            this.texture.delete();
        }
    }
}
