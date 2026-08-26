package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BOBJBone implements RigBone
{
    /* Meta information */
    public int index;
    public String name;
    public String parent;
    public BOBJBone parentBone;

    /* Transformations */
    public final Transform transform = new Transform();

    /**
     * Computed bone matrix which is used for transformations. This 
     * matrix isn't multiplied by inverse bone matrix. 
     */
    public Matrix4f mat = new Matrix4f();

    public Matrix4f originMat = new Matrix4f();

    /**
     * Bone matrix 
     */
    public Matrix4f boneMat;

    /**
     * Inverse bone matrix 
     */
    public Matrix4f invBoneMat = new Matrix4f();

    /**
     * Relative-to-parent bone matrix
     */
    public Matrix4f relBoneMat = new Matrix4f();

    /**
     * Transient full local orientation for this bone, applied raw in place of the channel rotation —
     * the evaluated rotation of the pipeline {@code rest → channels → constraint stack → render}. Same
     * two-phase lifecycle as {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#orient}: layer composers
     * keep it in lockstep with the channels (null = channels compose trivially, may be reset on channel
     * re-author); constraint stages (IK → physics → limits) treat the channels as read-only FK truth,
     * read the evaluated-so-far rotation via {@link #evaluatedRotation()}, and write their blended
     * result here — never to the channels, and never null.
     */
    public Quaternionf orient;

    /**
     * Transient CUMULATIVE world translation the IK stretch gives this bone, applied to the SKINNING
     * matrix only, so the deformed mesh follows a chain that reached past its rest length — vertices
     * weighted across bones blend the neighbouring shifts into a smooth stretch instead of a seam.
     * Deliberately kept off {@link #mat}/{@link #originMat}: the skeleton frames that the solve and the
     * debug overlay read stay nominal, and the shift each bone carries is already its full cumulative
     * one. Null when the bone has no shift this frame.
     */
    public Vector3f offset;

    public BOBJBone(int index, String name, String parent, Matrix4f boneMat)
    {
        this.index = index;
        this.name = name;
        this.parent = parent;
        this.boneMat = boneMat;

        this.invBoneMat.set(boneMat);
        this.invBoneMat.invert();

        this.relBoneMat.identity();
    }

    public Matrix4f compute()
    {
        Matrix4f mat = this.computeMatrix(new Matrix4f());

        this.mat.set(mat);
        mat.mul(this.invBoneMat);

        /* Stretch rides the skinning matrix alone — pre-multiplied, so the deformed vertices land
         * `offset` further along in world. mat/originMat stay nominal, so child bones and pivot frames
         * are unaffected; the offset is already this bone's full cumulative shift. */
        if (this.offset != null)
        {
            mat.translateLocal(this.offset);
        }

        return mat;
    }

    public Matrix4f computeMatrix(Matrix4f m)
    {
        this.mat.set(this.relBoneMat);
        this.originMat.set(this.relBoneMat);
        this.applyTransformations();

        if (this.parentBone != null)
        {
            m.set(this.parentBone.mat).mul(this.originMat);
            this.originMat.set(m);
            m.identity().set(this.parentBone.mat);
        }

        m.mul(this.mat);

        return m;
    }

    public void applyTransformations()
    {
        this.mat.translate(this.transform.translate);
        this.originMat.translate(this.transform.translate);

        if (this.orient != null)
        {
            /* orient is the full local rotation, so the euler channel is skipped. */
            this.mat.rotate(this.orient);
        }
        else if (this.transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.mat.rotate(this.transform.quat);
        }
        else
        {
            Vector3f rotate = this.transform.rotate;

            /* Rest bones (all angles zero) skip the trig entirely; BOBJ channels are radians. */
            if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
            {
                this.mat.rotate(Matrices.toLocalRotationZYXRadians(rotate));
            }
        }

        this.mat.scale(this.transform.scale);
    }

    /**
     * The bone's evaluated local rotation as of this point in the pipeline — {@link #orient} when set,
     * otherwise the channel rotation (mode-aware; BOBJ channels are radians). THE read for every
     * constraint-stack stage; see {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#evaluatedRotation()}.
     * Returns a fresh instance safe to mutate.
     */
    @Override

    public Quaternionf evaluatedRotation()
    {
        return this.orient != null ? new Quaternionf(this.orient) : this.transform.createRotation();
    }

    /**
     * Composes one rotation layer into {@link #orient} (BOBJ rotations are radians). Mirrors
     * {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#composeOrient}: the first layer seeds from the euler
     * accumulated so far (rotate folded with rotate2) so a single layer is byte-identical; later layers
     * multiply their delta. Call AFTER the layer's additive euler readback to {@code transform.rotate}.
     */
    @Override
    public String getBoneName()
    {
        return this.name;
    }

    @Override
    public RigBone getParentBone()
    {
        return this.parentBone;
    }

    /** A BOBJ bone's editable transform is {@code transform}. */
    @Override
    public Transform getBoneTransform()
    {
        return this.transform;
    }

    /** A BOBJ bone rests where its bind matrix puts it. */
    @Override
    public Vector3f getRestTranslation()
    {
        return this.boneMat.getTranslation(new Vector3f());
    }

    /** BOBJ channels are radians. */
    @Override
    public boolean isRotationInDegrees()
    {
        return false;
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
            this.orient = this.transform.createRotation();
        }
        else
        {
            this.orient.mul(delta);
        }
    }

    public void reset()
    {
        this.transform.identity();
        this.orient = null;
        this.offset = null;
    }
}