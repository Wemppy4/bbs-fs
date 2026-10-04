package mchorse.bbs_mod.particles;
import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import org.joml.Matrix4f;
/** The native particle format is POSITION/TEX/COLOR/LIGHT; keep its stride independent of entity shaders. */
public final class ParticleVertexBuffer extends UIVertexBuffer {
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    public static final ParticleVertexBuffer INSTANCE = new ParticleVertexBuffer();
    private ParticleVertexBuffer() { super(262144); }
    @Override public ParticleVertexBuffer vertex(Matrix4f matrix,float x,float y,float z){super.vertex(matrix,x,y,z);return this;}
    @Override public ParticleVertexBuffer texture(float u,float v){super.texture(u,v);return this;}
    @Override public ParticleVertexBuffer color(float r,float g,float b,float a){super.color(r,g,b,a);return this;}
    public ParticleVertexBuffer light(int light){lightmap(light & 65535, light >>> 16 & 65535);return this;}
    @Override public void draw(){finishDrawing();UPLOADER.draw(this);}
}
