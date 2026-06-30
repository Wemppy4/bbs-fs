package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ModelGroup implements IMapSerializable
{
    public final String id;
    public Model owner;
    public ModelGroup parent;
    public List<ModelGroup> children = new ArrayList<>();
    public List<ModelCube> cubes = new ArrayList<>();
    public List<ModelMesh> meshes = new ArrayList<>();
    public boolean visible = true;
    public int index = -1;

    public float lighting = 0F;
    public Color color = new Color().set(1F, 1F, 1F);
    public Transform initial = new Transform();
    public Transform current = new Transform();

    /* Transient full local orientation for this bone, applied raw in the render matrix IN PLACE OF the
     * euler rotate triple — so layered rotation (IK, plus animation/pose composition) owns the whole
     * orientation without round-tripping through euler and hitting the pole. Null when the bone has no
     * overlay this frame, in which case the renderer falls back to the euler rotate/rotate2 triples. */
    public Quaternionf orient;

    /* Transient translation for this bone, applied raw in the render matrix BEFORE its own translate — in
     * the bone's parent world frame, so it shifts this bone and everything below it without touching the
     * pose. IK "stretch" telescopes a chain past its reach by pushing each bone out along the limb (the
     * gaps between bones open up); null when the bone has no such shift this frame. RENDER ONLY — it is
     * deliberately NOT applied when collecting pivot frames, so the IK solve and the debug overlay read
     * the un-stretched solved chain (the rotation solve), and orb/line sizing stays stable. */
    public Vector3f offset;

    public ModelGroup(String id)
    {
        this.id = id;
    }

    public void reset()
    {
        this.lighting = 0F;
        this.color.set(1F, 1F, 1F);
        this.current.copy(this.initial);
        this.orient = null;
        this.offset = null;
    }

    /**
     * Composes one rotation layer into {@link #orient}, the quaternion the renderer applies in place of the
     * euler triples. The FIRST layer on a bone seeds orient from the euler accumulated so far (this layer's
     * own {@code +=} included), so a single layer renders byte-identically to the euler path; every later
     * layer multiplies its delta as a quaternion, so stacked layers compose without the euler-pole flip.
     * Call this AFTER the layer has applied its additive euler readback to {@code current.rotate}.
     */
    public void composeOrient(Quaternionf delta)
    {
        if (this.orient == null)
        {
            this.orient = Matrices.toQuaternionZYXDegrees(this.current.rotate.x, this.current.rotate.y, this.current.rotate.z);

            if (this.current.rotate2.x != 0F || this.current.rotate2.y != 0F || this.current.rotate2.z != 0F)
            {
                this.orient.mul(Matrices.toQuaternionZYXDegrees(this.current.rotate2.x, this.current.rotate2.y, this.current.rotate2.z));
            }
        }
        else
        {
            this.orient.mul(delta);
        }
    }

    @Override
    public void fromData(MapType data)
    {
        /* Setup initial transformations */
        if (data.has("origin")) this.initial.translate.set(DataStorageUtils.vector3fFromData(data.getList("origin")));
        if (data.has("rotate")) this.initial.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate")));

        /* Setup cubes and meshes */
        if (data.has("cubes"))
        {
            for (BaseType element : data.getList("cubes"))
            {
                ModelCube cube = new ModelCube();

                cube.fromData((MapType) element);

                this.cubes.add(cube);
            }

        }

        if (data.has("meshes"))
        {
            for (BaseType element : data.getList("meshes"))
            {
                ModelMesh mesh = new ModelMesh();

                mesh.fromData((MapType) element);

                this.meshes.add(mesh);
            }
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.put("origin", DataStorageUtils.vector3fToData(this.initial.translate));
        data.put("rotate", DataStorageUtils.vector3fToData(this.initial.rotate));

        if (!this.cubes.isEmpty())
        {
            ListType list = new ListType();

            for (ModelCube cube : this.cubes)
            {
                list.add(cube.toData());
            }

            data.put("cubes", list);
        }

        if (!this.meshes.isEmpty())
        {
            ListType list = new ListType();

            for (ModelMesh mesh : this.meshes)
            {
                list.add(mesh.toData());
            }

            data.put("meshes", list);
        }
    }
}