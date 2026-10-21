package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Where a cube of the model stands in the viewport: the frame its group's cubes are drawn in,
 * recovered from the bone matrices the viewport captures, the cube's own frame inside it, and a
 * ray test against the cubes of a group for picking one with the cursor.
 *
 * <p>A captured bone matrix is the frame the group's cubes are drawn in, followed by the group's
 * own pivot and the model's half turn ({@code ModelInstance#captureMatrices}); taking those two
 * off again gives the cubes' frame back. In it a cube turns about its own pivot by its own
 * rotation, in the order the renderer applies it — Z, then Y, then X ({@code CubicCubeRenderer}) —
 * and its box runs from its corner to its corner plus its size, grown by its inflate on every
 * side. Everything is in the model's pixels, sixteen to the block.</p>
 */
public final class ModelCubeFrames
{
    private ModelCubeFrames()
    {}

    /** The frame the group's cubes are drawn in. */
    public static Matrix4f groupFrame(MatrixCacheEntry entry, ModelGroup group)
    {
        Vector3f pivot = group.initial.translate;

        return new Matrix4f(entry.matrix())
            .rotateY(MathUtils.PI)
            .translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    /** The cube's own frame: the group's, turned about the cube's pivot by the cube's rotation. */
    public static Matrix4f cubeFrame(Matrix4f groupFrame, ModelCube cube)
    {
        Vector3f pivot = cube.pivot;
        Vector3f rotate = cube.rotate;
        Matrix4f frame = new Matrix4f(groupFrame).translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        if (rotate.x != 0 || rotate.y != 0 || rotate.z != 0)
        {
            frame.rotateZ(MathUtils.toRad(rotate.z)).rotateY(MathUtils.toRad(rotate.y)).rotateX(MathUtils.toRad(rotate.x));
        }

        return frame.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    /**
     * Where the gizmo of a cube stands: on the cube's pivot, turned as the cube is for the space
     * that places a target on its own frame, and left in the group's turn for every other.
     *
     * <p>The half turn at the end is the convention a captured bone matrix carries, and what the
     * group's own gizmo is therefore drawn in — a cube's arrows point the way its group's do. It
     * doesn't reach the drag: the axes a handle moves along are measured numerically off this very
     * frame, so the flip cancels itself there.</p>
     */
    public static Matrix4f cubeGizmoFrame(Matrix4f groupFrame, ModelCube cube, boolean ownFrame)
    {
        Vector3f pivot = cube.pivot;
        Vector3f rotate = cube.rotate;
        Matrix4f frame = new Matrix4f(groupFrame).translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        if (ownFrame && (rotate.x != 0 || rotate.y != 0 || rotate.z != 0))
        {
            frame.rotateZ(MathUtils.toRad(rotate.z)).rotateY(MathUtils.toRad(rotate.y)).rotateX(MathUtils.toRad(rotate.x));
        }

        return frame.rotateY(MathUtils.PI);
    }

    /** The near corner of the cube's box in its frame, the inflate taken in, in blocks. */
    public static Vector3f boxMin(ModelCube cube, Vector3f out)
    {
        return out.set(cube.origin).sub(cube.inflate, cube.inflate, cube.inflate).div(16F);
    }

    /** The far corner of the cube's box in its frame, the inflate taken in, in blocks. */
    public static Vector3f boxMax(ModelCube cube, Vector3f out)
    {
        return out.set(cube.origin).add(cube.size).add(cube.inflate, cube.inflate, cube.inflate).div(16F);
    }

    /**
     * The cube of {@code group} the ray hits first, or -1 for none. The ray is given in the frame
     * the bones were captured in; it is carried into each cube's frame, where the cube is a plain
     * box, and its direction is left unscaled so the distances of the hits compare across cubes.
     */
    public static int pick(MatrixCacheEntry entry, ModelGroup group, Vector3f origin, Vector3f direction)
    {
        Matrix4f frame = groupFrame(entry, group);
        Vector3f localOrigin = new Vector3f();
        Vector3f localDirection = new Vector3f();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        Vector2f hit = new Vector2f();
        int picked = -1;
        float nearest = Float.MAX_VALUE;

        for (int i = 0; i < group.cubes.size(); i++)
        {
            ModelCube cube = group.cubes.get(i);
            Matrix4f inverse = cubeFrame(frame, cube).invert();

            inverse.transformPosition(origin, localOrigin);
            inverse.transformDirection(direction, localDirection);

            if (!Intersectionf.intersectRayAab(localOrigin, localDirection, boxMin(cube, min), boxMax(cube, max), hit) || hit.y < 0F)
            {
                continue;
            }

            /* A ray starting inside the box enters it behind its start; that still counts as this cube, at no distance. */
            float distance = Math.max(hit.x, 0F);

            if (distance < nearest)
            {
                nearest = distance;
                picked = i;
            }
        }

        return picked;
    }
}
