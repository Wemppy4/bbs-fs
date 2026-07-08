package mchorse.bbs_mod.cubic.data.model;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Rounds a cube's edges by rebuilding its quads with a bevel: faces get inset by the radius, the edge
 * between two present faces becomes a quarter-cylinder strip and the corner between three present faces
 * becomes a sphere octant. Runs over the sharp quads {@link ModelCube#generateQuads(int, int)} built,
 * so UV comes straight from them: a face keeps its texture at the same scale and leaves the outer
 * radius-wide band of its region to the strip, which wraps it around the edge — each strip half unrolls
 * onto its own face and meets the other half at the arc's midline. Corners project onto their nearest
 * face's corner band. Strips and octants build their rows from the same normalized integer directions,
 * so the pieces share boundary vertices exactly and the surface stays watertight.
 */
public class ModelCubeBevel
{
    private final ModelCube cube;
    private final float r;
    private final int n;

    /**
     * Faces that keep their exact geometry — welded faces, whose corners the weld seam snaps and bends
     * by. Their edges and corners stay sharp; the rest of the cube still rounds.
     */
    private final Set<ModelQuad> locked;

    private final float[] min = new float[3];
    private final float[] max = new float[3];
    private final ModelQuad[][] faces = new ModelQuad[3][2];
    private final List<ModelQuad> quads = new ArrayList<>();

    public static void apply(ModelCube cube, float bevel, int segments)
    {
        apply(cube, bevel, segments, Collections.emptySet());
    }

    public static void apply(ModelCube cube, float bevel, int segments, Set<ModelQuad> locked)
    {
        float thickness = Math.min(cube.size.x, Math.min(cube.size.y, cube.size.z)) + cube.inflate * 2F;
        float radius = Math.min(bevel, thickness / 2F);

        if (radius <= 0F || cube.quads.isEmpty())
        {
            return;
        }

        new ModelCubeBevel(cube, radius / 16F, segments, locked).build();
    }

    private ModelCubeBevel(ModelCube cube, float r, int segments, Set<ModelQuad> locked)
    {
        this.cube = cube;
        this.r = r;
        this.locked = locked;

        /* An even row count puts a vertex row exactly on the arc's midline, where a strip switches from
         * one face's texture band to the other's. */
        this.n = Math.max(2, (segments + 1) / 2 * 2);
    }

    private void build()
    {
        this.min[0] = (this.cube.origin.x - this.cube.inflate) / 16F;
        this.min[1] = (this.cube.origin.y - this.cube.inflate) / 16F;
        this.min[2] = (this.cube.origin.z - this.cube.inflate) / 16F;
        this.max[0] = (this.cube.origin.x + this.cube.size.x + this.cube.inflate) / 16F;
        this.max[1] = (this.cube.origin.y + this.cube.size.y + this.cube.inflate) / 16F;
        this.max[2] = (this.cube.origin.z + this.cube.size.z + this.cube.inflate) / 16F;

        /* Faces are the sharp axis-aligned quads generateQuads() built — nothing else qualifies for a slot. */
        for (ModelQuad quad : this.cube.quads)
        {
            Vector3f normal = quad.normal;
            int axis = Math.abs(normal.x) > 0.99F ? 0 : (Math.abs(normal.y) > 0.99F ? 1 : (Math.abs(normal.z) > 0.99F ? 2 : -1));

            if (axis >= 0 && quad.vertices.size() == 4)
            {
                this.faces[axis][normal.get(axis) > 0F ? 1 : 0] = quad;
            }
        }

        for (int axis = 0; axis < 3; axis++)
        {
            this.insetFace(axis, -1);
            this.insetFace(axis, 1);
        }

        for (int a = 0; a < 3; a++)
        {
            for (int b = a + 1; b < 3; b++)
            {
                for (int sa = -1; sa <= 1; sa += 2)
                {
                    for (int sb = -1; sb <= 1; sb += 2)
                    {
                        this.strip(a, sa, b, sb);
                    }
                }
            }
        }

        for (int sx = -1; sx <= 1; sx += 2)
        {
            for (int sy = -1; sy <= 1; sy += 2)
            {
                for (int sz = -1; sz <= 1; sz += 2)
                {
                    this.corner(sx, sy, sz);
                }
            }
        }

        this.cube.quads.clear();
        this.cube.quads.addAll(this.quads);
    }

    private ModelQuad face(int axis, int sign)
    {
        return this.faces[axis][sign > 0 ? 1 : 0];
    }

    /** Whether the face exists and its edges may round — locked (welded) faces keep sharp surroundings. */
    private boolean beveled(int axis, int sign)
    {
        ModelQuad face = this.face(axis, sign);

        return face != null && !this.locked.contains(face);
    }

    private float extreme(int axis, int sign)
    {
        return sign > 0 ? this.max[axis] : this.min[axis];
    }

    /** UV of a point on a face's plane — affine off the face quad's corners, so mirror/rotation carry. */
    private Vector2f uv(ModelQuad face, Vector3f point)
    {
        Vector3f c = face.vertices.get(0).vertex;
        Vector3f e1 = new Vector3f(face.vertices.get(1).vertex).sub(c);
        Vector3f e2 = new Vector3f(face.vertices.get(3).vertex).sub(c);
        Vector3f d = new Vector3f(point).sub(c);
        float s = d.dot(e1) / e1.lengthSquared();
        float t = d.dot(e2) / e2.lengthSquared();
        Vector2f uv0 = face.vertices.get(0).uv;
        Vector2f uv1 = face.vertices.get(1).uv;
        Vector2f uv3 = face.vertices.get(3).uv;

        return new Vector2f(
            uv0.x + s * (uv1.x - uv0.x) + t * (uv3.x - uv0.x),
            uv0.y + s * (uv1.y - uv0.y) + t * (uv3.y - uv0.y)
        );
    }

    private void insetFace(int axis, int sign)
    {
        ModelQuad face = this.face(axis, sign);

        if (face == null)
        {
            return;
        }

        if (this.locked.contains(face))
        {
            this.quads.add(face);

            return;
        }

        ModelQuad quad = new ModelQuad();

        for (ModelVertex vertex : face.vertices)
        {
            Vector3f position = new Vector3f(vertex.vertex);

            for (int other = 0; other < 3; other++)
            {
                if (other == axis)
                {
                    continue;
                }

                int side = position.get(other) > (this.min[other] + this.max[other]) / 2F ? 1 : -1;

                if (this.beveled(other, side))
                {
                    position.setComponent(other, position.get(other) - side * this.r);
                }
            }

            Vector2f uv = this.uv(face, position);

            quad.vertex(position.x, position.y, position.z, uv.x, uv.y);
        }

        quad.normal(face.normal.x, face.normal.y, face.normal.z);
        this.quads.add(quad);
    }

    private void strip(int a, int sa, int b, int sb)
    {
        ModelQuad faceA = this.face(a, sa);
        ModelQuad faceB = this.face(b, sb);

        if (!this.beveled(a, sa) || !this.beveled(b, sb))
        {
            return;
        }

        int c = 3 - a - b;
        float t0 = this.min[c] + (this.beveled(c, -1) ? this.r : 0F);
        float t1 = this.max[c] - (this.beveled(c, 1) ? this.r : 0F);

        if (t1 <= t0)
        {
            return;
        }

        Vector3f[] dirs = new Vector3f[this.n + 1];

        for (int j = 0; j <= this.n; j++)
        {
            dirs[j] = new Vector3f();
            dirs[j].setComponent(a, sa * (this.n - j));
            dirs[j].setComponent(b, sb * j);
            dirs[j].normalize();
        }

        for (int j = 0; j < this.n; j++)
        {
            boolean onA = j < this.n / 2;
            ModelQuad face = onA ? faceA : faceB;
            Vector3f mid = new Vector3f(dirs[j]).add(dirs[j + 1]).normalize();
            ModelQuad quad = new ModelQuad();

            vertex(quad, this.point(a, sa, b, sb, c, t0, dirs[j]), this.uv(face, this.unrolled(a, sa, b, sb, c, t0, j, onA)), dirs[j]);
            vertex(quad, this.point(a, sa, b, sb, c, t0, dirs[j + 1]), this.uv(face, this.unrolled(a, sa, b, sb, c, t0, j + 1, onA)), dirs[j + 1]);
            vertex(quad, this.point(a, sa, b, sb, c, t1, dirs[j + 1]), this.uv(face, this.unrolled(a, sa, b, sb, c, t1, j + 1, onA)), dirs[j + 1]);
            vertex(quad, this.point(a, sa, b, sb, c, t1, dirs[j]), this.uv(face, this.unrolled(a, sa, b, sb, c, t1, j, onA)), dirs[j]);

            orient(quad, mid);
            quad.normal.set(mid);
            this.quads.add(quad);
        }
    }

    private static void vertex(ModelQuad quad, Vector3f position, Vector2f uv, Vector3f normal)
    {
        quad.vertex(position.x, position.y, position.z, uv.x, uv.y, normal);
    }

    /** Flip the vertex order if the winding faces inward — it varies with the edge/octant sign combo. */
    private static void orient(ModelQuad quad, Vector3f outward)
    {
        List<ModelVertex> vertices = quad.vertices;
        Vector3f base = vertices.get(0).vertex;
        Vector3f e1 = new Vector3f(vertices.get(1).vertex).sub(base);
        Vector3f e2 = new Vector3f(vertices.get(vertices.size() - 1).vertex).sub(base);

        if (e1.cross(e2).dot(outward) < 0F)
        {
            Collections.reverse(vertices);
        }
    }

    private Vector3f point(int a, int sa, int b, int sb, int c, float t, Vector3f dir)
    {
        Vector3f point = new Vector3f();

        point.setComponent(a, this.extreme(a, sa) - sa * this.r + dir.get(a) * this.r);
        point.setComponent(b, this.extreme(b, sb) - sb * this.r + dir.get(b) * this.r);
        point.setComponent(c, t);

        return point;
    }

    /** Where a strip row lands when its face's outer texture band is unrolled flat onto the face's plane. */
    private Vector3f unrolled(int a, int sa, int b, int sb, int c, float t, int j, boolean onA)
    {
        if (!onA)
        {
            /* Face B mirrors face A's unroll with the roles swapped and the row counted from its end. */
            return this.unrolled(b, sb, a, sa, c, t, this.n - j, true);
        }

        Vector3f point = new Vector3f();
        int half = this.n / 2;
        float fraction = Math.min(j, half) / (float) half;

        point.setComponent(a, this.extreme(a, sa));
        point.setComponent(b, this.extreme(b, sb) - sb * this.r + sb * this.r * fraction);
        point.setComponent(c, t);

        return point;
    }

    private void corner(int sx, int sy, int sz)
    {
        if (!this.beveled(0, sx) || !this.beveled(1, sy) || !this.beveled(2, sz))
        {
            return;
        }

        int[] signs = {sx, sy, sz};
        Vector3f center = new Vector3f(
            this.extreme(0, sx) - sx * this.r,
            this.extreme(1, sy) - sy * this.r,
            this.extreme(2, sz) - sz * this.r
        );
        Vector3f[][] dirs = new Vector3f[this.n + 1][];

        for (int i = 0; i <= this.n; i++)
        {
            dirs[i] = new Vector3f[i + 1];

            for (int j = 0; j <= i; j++)
            {
                dirs[i][j] = new Vector3f(sx * (this.n - i), sy * (i - j), sz * j).normalize();
            }
        }

        for (int i = 0; i < this.n; i++)
        {
            for (int j = 0; j <= i; j++)
            {
                this.cornerTriangle(center, signs, dirs[i][j], dirs[i + 1][j], dirs[i + 1][j + 1]);
            }

            for (int j = 0; j < i; j++)
            {
                this.cornerTriangle(center, signs, dirs[i][j], dirs[i + 1][j + 1], dirs[i][j + 1]);
            }
        }
    }

    private void cornerTriangle(Vector3f center, int[] signs, Vector3f d0, Vector3f d1, Vector3f d2)
    {
        Vector3f centroid = new Vector3f(d0).add(d1).add(d2).normalize();
        int axis = 0;

        if (Math.abs(centroid.y) > Math.abs(centroid.get(axis))) axis = 1;
        if (Math.abs(centroid.z) > Math.abs(centroid.get(axis))) axis = 2;

        ModelQuad face = this.face(axis, signs[axis]);
        ModelQuad quad = new ModelQuad();

        /* Each vertex projects onto the triangle's dominant face, so the patch samples that face's
         * corner band with a real UV extent — a constant UV would hand the shader tangent solver a
         * zero area and blow up the lighting on the corners. */
        for (Vector3f dir : new Vector3f[] {d0, d1, d2})
        {
            Vector3f position = new Vector3f(dir).mul(this.r).add(center);

            vertex(quad, position, this.uv(face, this.projected(position, axis, signs[axis])), dir);
        }

        orient(quad, centroid);
        quad.normal.set(centroid);
        this.quads.add(quad);
    }

    private Vector3f projected(Vector3f point, int axis, int sign)
    {
        Vector3f projected = new Vector3f(point);

        projected.setComponent(axis, this.extreme(axis, sign));

        return projected;
    }
}
