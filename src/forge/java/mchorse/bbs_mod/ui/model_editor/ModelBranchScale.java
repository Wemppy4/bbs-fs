package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One scale of a group's whole branch about the group's pivot — what the gizmo's scale handles do
 * to a picked group — carried from where the branch stood when it began.
 *
 * <p>Everything under the group grows about its pivot by the factor, axis by axis: every cube's
 * corner, size and pivot, the pivot of every group below it (the pose's with the rest's, as a move
 * takes both), every vertex of every mesh, shape keys and all. The group's own pivot is the centre
 * and stays. A cube's inflate grows too while the factor is the same on every axis — it is one
 * number for every side, and a stretch along one axis has no one inflate to give. A mesh's normals
 * lean against a stretch, as normals do.</p>
 *
 * <p>Rotations stay as they are. A factor the same on every axis is exact for all of the branch; a
 * stretch along one axis is exact for whatever isn't turned against that axis. A turned cube
 * stretched across its turn would stop being a box, and scaling its own size is the nearest a cube
 * can come; so is the content of a turned group below, and a turned mesh, whose origin stays where
 * it is, the way a move leaves it ({@code Model#shiftGroup}).</p>
 *
 * <p>Every number is worked out afresh from the start on every change, and an axis whose factor is
 * exactly one keeps its start to the bit — a gesture let go with Escape leaves no trace in the
 * file.</p>
 */
public class ModelBranchScale
{
    @com.github.bsideup.jabel.Desugar
    private record CubeStart(ModelCube cube, Vector3f origin, Vector3f size, Vector3f pivot, float inflate)
    {}

    @com.github.bsideup.jabel.Desugar
    private record PivotStart(ModelGroup group, Vector3f initial, Vector3f current)
    {}

    /** The group's pivot, which the branch grows about. */
    private final Vector3f center;

    private final List<ModelGroup> groups = new ArrayList<>();
    private final List<CubeStart> cubes = new ArrayList<>();
    private final List<PivotStart> pivots = new ArrayList<>();

    /** Every vertex and every normal of the branch's meshes, each beside the numbers it began at. */
    private final List<Vector3f> vertices = new ArrayList<>();
    private final List<Vector3f> vertexStarts = new ArrayList<>();
    private final List<Vector3f> normals = new ArrayList<>();
    private final List<Vector3f> normalStarts = new ArrayList<>();

    /** Take the branch of {@code group} as it stands now. */
    public ModelBranchScale(ModelGroup group)
    {
        this.center = new Vector3f(group.initial.translate);
        this.take(group, true);
    }

    private void take(ModelGroup group, boolean root)
    {
        this.groups.add(group);

        if (!root)
        {
            this.pivots.add(new PivotStart(group, new Vector3f(group.initial.translate), new Vector3f(group.current.translate)));
        }

        for (ModelCube cube : group.cubes)
        {
            this.cubes.add(new CubeStart(cube, new Vector3f(cube.origin), new Vector3f(cube.size), new Vector3f(cube.pivot), cube.inflate));
        }

        for (ModelMesh mesh : group.meshes)
        {
            this.take(mesh.baseData);

            for (ModelData shapeKey : mesh.data.values())
            {
                this.take(shapeKey);
            }
        }

        for (ModelGroup child : group.children)
        {
            this.take(child, false);
        }
    }

    private void take(ModelData data)
    {
        for (Vector3f vertex : data.vertices)
        {
            this.vertices.add(vertex);
            this.vertexStarts.add(new Vector3f(vertex));
        }

        for (Vector3f normal : data.normals)
        {
            this.normals.add(normal);
            this.normalStarts.add(new Vector3f(normal));
        }
    }

    /** Every group of the branch — what a scale of it leaves to be rebuilt. */
    public List<ModelGroup> groups()
    {
        return this.groups;
    }

    /**
     * The branch at {@code factor} times the size it began at, axis by axis, about the group's pivot.
     *
     * @return whether any number changed
     */
    public boolean scale(Vector3f factor)
    {
        boolean changed = false;
        boolean uniform = factor.x == factor.y && factor.y == factor.z;

        for (CubeStart start : this.cubes)
        {
            changed |= set(start.cube.origin, this.about(start.origin, factor));
            changed |= set(start.cube.size, scaled(start.size, factor));
            changed |= set(start.cube.pivot, this.about(start.pivot, factor));

            float inflate = uniform && factor.x != 1F ? start.inflate * Math.abs(factor.x) : start.inflate;

            if (start.cube.inflate != inflate)
            {
                start.cube.inflate = inflate;
                changed = true;
            }
        }

        for (PivotStart start : this.pivots)
        {
            changed |= set(start.group.initial.translate, this.about(start.initial, factor));
            changed |= set(start.group.current.translate, this.about(start.current, factor));
        }

        for (int i = 0; i < this.vertices.size(); i++)
        {
            changed |= set(this.vertices.get(i), this.about(this.vertexStarts.get(i), factor));
        }

        /* A normal takes the stretch inverted, back to its length: squashed flat, a surface faces
         * along the flattened axis all the more. A factor the same on every axis and positive leaves
         * it be; a flat axis has no inverse, and leaves the normals as they began. */
        boolean lean = !(uniform && factor.x > 0F) && factor.x != 0F && factor.y != 0F && factor.z != 0F;

        for (int i = 0; i < this.normals.size(); i++)
        {
            Vector3f start = this.normalStarts.get(i);
            Vector3f normal = lean ? new Vector3f(start).div(factor) : start;

            if (lean && normal.lengthSquared() > 0F)
            {
                normal.normalize();
            }

            changed |= set(this.normals.get(i), normal);
        }

        return changed;
    }

    /** A point of the branch at the factor about the centre; an axis at exactly one keeps its number. */
    private Vector3f about(Vector3f start, Vector3f factor)
    {
        Vector3f point = new Vector3f(start);

        for (int i = 0; i < 3; i++)
        {
            float f = factor.get(i);

            if (f != 1F)
            {
                float center = this.center.get(i);

                point.setComponent(i, center + (start.get(i) - center) * f);
            }
        }

        return point;
    }

    /** A size at the factor; an axis at exactly one keeps its number. */
    private static Vector3f scaled(Vector3f start, Vector3f factor)
    {
        Vector3f size = new Vector3f(start);

        for (int i = 0; i < 3; i++)
        {
            float f = factor.get(i);

            if (f != 1F)
            {
                size.setComponent(i, start.get(i) * f);
            }
        }

        return size;
    }

    private static boolean set(Vector3f target, Vector3f value)
    {
        if (target.equals(value))
        {
            return false;
        }

        target.set(value);

        return true;
    }
}
