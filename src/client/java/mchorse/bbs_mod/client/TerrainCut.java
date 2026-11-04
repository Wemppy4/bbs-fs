package mchorse.bbs_mod.client;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The orbit's wall cut: world blocks nearer the camera than a plane through the orbit centre
 * (moved towards the camera by the cut's depth) are not drawn, so a wall between the camera
 * and the actor stops hiding the actor. Only the terrain is cut - actors, models, the gizmo
 * and block entities are drawn whole - which is why this lives in the terrain's own vertex
 * shader instead of the camera's near plane: an actor standing at a wall reaches past any
 * plane that clears the wall.
 *
 * <p>The terrain vertex shaders (Sodium's own, or a shaderpack's through Iris) are wrapped
 * so they also write {@code gl_ClipDistance[0]} against a clip space plane uniform. The GL
 * switch for that distance is on only around a terrain pass whose program has the uniform,
 * and only while the orbit arms the cut for the frame ({@link BBSRendering#setTerrainCut});
 * otherwise the extra output is simply ignored.</p>
 */
public class TerrainCut
{
    public static final String UNIFORM = "bbs_TerrainCut";

    private static final Pattern VERSION = Pattern.compile("^\\s*#version\\s+(\\d+)", Pattern.MULTILINE);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)");

    private static final Matrix4f inverse = new Matrix4f();
    private static final Vector4f vector = new Vector4f();

    private static boolean enabled;

    /**
     * Wrap a terrain vertex shader's main() so it also writes the cut's clip distance. The
     * source comes back untouched when that can't be done safely: GLSL older than 1.30 has
     * no gl_ClipDistance, and a source without exactly one main() is not one we understand.
     */
    public static String patchVertexShader(String source)
    {
        if (source == null || source.contains(UNIFORM))
        {
            return source;
        }

        Matcher version = VERSION.matcher(source);

        if (!version.find() || Integer.parseInt(version.group(1)) < 130)
        {
            return source;
        }

        Matcher main = MAIN.matcher(source);

        if (!main.find())
        {
            return source;
        }

        int start = main.start();
        int end = main.end();

        if (main.find())
        {
            return source;
        }

        return source.substring(0, start) + "void bbs_terrainCutMain()" + source.substring(end)
            + "\n\nuniform vec4 " + UNIFORM + ";\n\n"
            + "void main()\n{\n"
            + "    bbs_terrainCutMain();\n"
            + "    gl_ClipDistance[0] = dot(gl_Position, " + UNIFORM + ");\n"
            + "}\n";
    }

    /**
     * Turn the cut on for the terrain pass about to be drawn with the currently bound program.
     *
     * @param projection the pass's projection matrix
     * @param modelView the pass's model view matrix; vertices reach it relative to the camera
     * @param x the camera's world position the vertices are relative to
     */
    public static void begin(Matrix4fc projection, Matrix4fc modelView, double x, double y, double z)
    {
        end();

        Vector3d center = BBSRendering.getTerrainCutCenter();

        if (center == null)
        {
            return;
        }

        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int location = program == 0 ? -1 : GL20.glGetUniformLocation(program, UNIFORM);

        if (location < 0)
        {
            return;
        }

        /* The plane in view space: everything at least this far ahead of the eye stays. */
        vector.set((float) (center.x - x), (float) (center.y - y), (float) (center.z - z), 1F);
        modelView.transform(vector);

        float distance = -vector.z - BBSRendering.getTerrainCutDepth();

        /* A point in view space v reaches clip space as P v, so the plane that keeps
         * dot(plane, v) >= 0 in view space is (P^-1)^T plane against gl_Position. */
        vector.set(0F, 0F, -1F, -distance);
        projection.invert(inverse).transformTranspose(vector);

        GL20.glUniform4f(location, vector.x, vector.y, vector.z, vector.w);
        GL11.glEnable(GL30.GL_CLIP_DISTANCE0);

        enabled = true;
    }

    public static void end()
    {
        if (enabled)
        {
            GL11.glDisable(GL30.GL_CLIP_DISTANCE0);

            enabled = false;
        }
    }
}
