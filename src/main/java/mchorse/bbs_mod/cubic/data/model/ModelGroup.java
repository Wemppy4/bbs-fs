package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.cubic.RigBone;
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

public class ModelGroup implements IMapSerializable, RigBone
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
    /* The bone's color overlay from the pose (RGB = color, A = strength); neutral at zero strength. */
    public Color overlay = new Color(1F, 1F, 1F, 0F);
    public Transform initial = new Transform();
    public Transform current = new Transform();

    /* Transient full local orientation for this bone, applied raw in the render matrix IN PLACE OF the
     * channel rotation — the evaluated rotation of the pipeline `rest → channels → constraint stack →
     * render`. Its lifecycle is two-phased:
     *
     * CHANNELS phase (reset → actions → pose): layer composers keep it in lockstep with their additive
     * euler readbacks via composeOrient (quat-true composition of stacked layers); null here means "the
     * channels compose trivially" and a composer may reset it to null when it re-authors the channels
     * outright (fix-blend, the sneaking pose).
     *
     * CONSTRAINT phase (IK → physics → limits): the channels are READ-ONLY FK truth (the gizmo/keyframe
     * domain). Every stage reads the evaluated-so-far rotation through evaluatedRotation(), blends its
     * result against that base by its weight, and writes the outcome HERE — never to current.rotate, and
     * never null. */
    public Quaternionf orient;

    /* Transient translation for this bone, applied in the render matrix BEFORE its own translate — in the
     * bone's parent world frame, so it shifts this bone and everything below it without touching the pose.
     * The IK stretch writes it: a bone that lengthened pushes its children out along the limb by the extra
     * length, which is how a cubic chain reaches past its rest length (its cubes do not deform, so the
     * joints between them open — welds seal that seam). Part of the constraint stack's write set alongside
     * orient, never a channel; null when the bone has no shift this frame. */
    public Vector3f offset;

    public ModelGroup(String id)
    {
        this.id = id;
    }

    public void reset()
    {
        this.lighting = 0F;
        this.color.set(1F, 1F, 1F);
        this.overlay.set(1F, 1F, 1F, 0F);
        this.current.copy(this.initial);
        this.orient = null;
        this.offset = null;
    }

    /**
     * The bone's evaluated local rotation as of this point in the pipeline — {@link #orient} when a layer
     * or constraint stage has composed one, otherwise the rotation the renderer would reconstruct from the
     * channels (mode-aware; cubic channels are degrees). THE read for every constraint-stack stage: blend
     * bases, twist references, clamp inputs all start from this, so stages stack instead of overwriting
     * each other. Returns a fresh instance safe to mutate.
     */
    @Override

    public Quaternionf evaluatedRotation()
    {
        if (this.orient != null)
        {
            return new Quaternionf(this.orient);
        }

        if (this.current.rotationMode == Transform.RotationMode.QUATERNION)
        {
            return new Quaternionf(this.current.quat);
        }

        return Matrices.toLocalRotationZYXDegrees(this.current.rotate);
    }

    /**
     * Composes one rotation layer into {@link #orient}, the quaternion the renderer applies in place of the
     * euler triples. The FIRST layer on a bone seeds orient from the euler accumulated so far (this layer's
     * own {@code +=} included), so a single layer renders byte-identically to the euler path; every later
     * layer multiplies its delta as a quaternion, so stacked layers compose without the euler-pole flip.
     * Call this AFTER the layer has applied its additive euler readback to {@code current.rotate}.
     */
    @Override
    public String getBoneName()
    {
        return this.id;
    }

    @Override
    public RigBone getParentBone()
    {
        return this.parent;
    }

    /** The cubic group's editable transform is {@code current}. */
    @Override
    public Transform getBoneTransform()
    {
        return this.current;
    }

    /** A cubic group rests where its bind transform puts it. */
    @Override
    public Vector3f getRestTranslation()
    {
        return this.initial.translate;
    }

    /** Cubic channels are degrees. */
    @Override
    public boolean isRotationInDegrees()
    {
        return true;
    }

    @Override
    public Quaternionf getOrient()
    {
        return this.orient;
    }

    @Override
    public void setOrient(Quaternionf orient)
    {
        this.orient = orient;
    }

    @Override
    public Vector3f getOffset()
    {
        return this.offset;
    }

    @Override
    public void setOffset(Vector3f offset)
    {
        this.offset = offset;
    }

    @Override
    public void composeOrient(Quaternionf delta)
    {
        if (this.orient == null)
        {
            this.orient = Matrices.toLocalRotationZYXDegrees(this.current.rotate);
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