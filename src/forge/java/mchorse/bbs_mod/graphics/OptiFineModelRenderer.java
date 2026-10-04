package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.graphics.render.VertexFormat;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/** Native shader-pack vertex ABI (gl_Vertex/gl_Normal/gl_MultiTexCoord0/1).
 * The same geometry buffers serve both BBS's programs and OptiFine's gbuffer/shadow
 * programs; poses, shape keys, skinning and deferred transparency stay with BBS. */
public final class OptiFineModelRenderer
{
    private static int vao, normalTexture, specularTexture;
    private OptiFineModelRenderer() {}

    public static Scope begin(Matrix4f modelView, Matrix4f projection, float r, float g, float b, float a, int light)
    {
        return new Scope(modelView, projection, r, g, b, a, light, false);
    }

    /** Vanilla's uploader supplies client-memory pointers and must not inherit a BBS VBO/VAO. */
    public static Scope beginNative(Matrix4f modelView, Matrix4f projection, float r, float g, float b, float a, int light)
    {
        return new Scope(modelView, projection, r, g, b, a, light, true);
    }

    public static void arrays(int positions, int normals, int uvs)
    {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positions);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L); GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normals);
        GL11.glNormalPointer(GL11.GL_FLOAT, 0, 0L); GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvs);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0L); GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    }

    public static void interleaved(int buffer, VertexFormat format)
    {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        int stride = format.getVertexSizeByte(), offset = 0;
        for (VertexFormat.Element element : format.elements)
        {
            switch (element)
            {
                case POSITION:
                    GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, (long) offset);
                    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY); break;
                case NORMAL:
                    GL11.glNormalPointer(GL11.GL_FLOAT, stride, (long) offset);
                    GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY); break;
                case COLOR:
                    GL11.glColorPointer(4, GL11.GL_FLOAT, stride, (long) offset);
                    GL11.glEnableClientState(GL11.GL_COLOR_ARRAY); break;
                case TEXTURE: case LIGHT:
                    GL13.glClientActiveTexture(element == VertexFormat.Element.TEXTURE ? GL13.GL_TEXTURE0 : GL13.GL_TEXTURE1);
                    GL11.glTexCoordPointer(2, element.type, stride, (long) offset);
                    GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY); break;
                default: break;
            }
            offset += element.bytes();
        }
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
    }

    public static final class Scope implements AutoCloseable
    {
        private final int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final int[] textures = new int[3];
        private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
        private final FloatBuffer lightCoord = BufferUtils.createFloatBuffer(16);
        private final OptiFineShaders.EntityPass pass;
        private final boolean nativeVertices;

        private Scope(Matrix4f modelView, Matrix4f projection, float r, float g, float b, float a, int light, boolean nativeVertices)
        {
            this.nativeVertices = nativeVertices;
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
            for (int i = 0; i < 3; i++)
            {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE1 + i);
                textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            if (normalTexture == 0) normalTexture = pixel(128, 128, 255, 255);
            if (specularTexture == 0) specularTexture = pixel(0, 0, 0, 0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE2); GlStateManager.bindTexture(normalTexture);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE3); GlStateManager.bindTexture(specularTexture);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1); GlStateManager.bindTexture(RenderSystem.nativeLightmap());
            GL11.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, lightCoord);
            GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
            GlStateManager.scale(1F / 256F, 1F / 256F, 1F / 256F); GlStateManager.translate(8F, 8F, 8F);
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, light & 65535, light >>> 16);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix(); load(modelView);
            GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix(); load(projection);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            pass = OptiFineShaders.entities();
            if (nativeVertices)
            {
                GL30.glBindVertexArray(0);
                GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            }
            else
            {
                if (vao == 0) vao = GL30.glGenVertexArrays();
                GL30.glBindVertexArray(vao);
            }
            /* A stale enabled pointer can make the driver read released client memory.
             * The uploader (or arrays/interleaved below) enables only the data it supplies. */
            for (int i = 0, count = GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS); i < count; i++)
            {
                GL20.glDisableVertexAttribArray(i);
            }
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            for (int i = 0, count = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS); i < count; i++)
            {
                GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            }
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.color(r, g, b, a);
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            attribute(program, "mc_Entity", -1, -1, -1, 0);
            attribute(program, "mc_midTexCoord", 0, 0, 0, 1);
            attribute(program, "at_tangent", 1, 0, 0, 1);
        }

        @Override public void close()
        {
            if (nativeVertices)
            {
                GL30.glBindVertexArray(0);
                GL11.glPopClientAttrib();
            }
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL13.glClientActiveTexture(clientTexture);
            pass.close();
            GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix();
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
            GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.popMatrix();
            GL13.glMultiTexCoord4f(GL13.GL_TEXTURE1, lightCoord.get(0), lightCoord.get(1), lightCoord.get(2), lightCoord.get(3));
            for (int i = 0; i < 3; i++)
            {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE1 + i); GlStateManager.bindTexture(textures[i]);
            }
            GlStateManager.setActiveTexture(activeTexture); GlStateManager.matrixMode(matrixMode);
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }

    private static void attribute(int program, String name, float x, float y, float z, float w)
    {
        int location = GL20.glGetAttribLocation(program, name);
        if (location >= 0) { GL20.glDisableVertexAttribArray(location); GL20.glVertexAttrib4f(location, x, y, z, w); }
    }
    private static void load(Matrix4f matrix)
    {
        FloatBuffer data = BufferUtils.createFloatBuffer(16); matrix.get(data); GL11.glLoadMatrix(data);
    }
    private static int pixel(int r, int g, int b, int a)
    {
        int texture = GlStateManager.generateTexture(); GlStateManager.bindTexture(texture);
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) r).put((byte) g).put((byte) b).put((byte) a).flip();
        int pbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int row = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), rows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS), pixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        try
        {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return texture;
        }
        finally
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,row); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,rows); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,pixels);
        }
    }
}
