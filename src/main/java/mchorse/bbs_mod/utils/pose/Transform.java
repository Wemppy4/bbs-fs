package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Transform implements IMapSerializable
{
    private static final Vector3f DEFAULT_SCALE = new Vector3f(1F, 1F, 1F);

    public static final Transform DEFAULT = new Transform();

    public final Vector3f translate = new Vector3f();
    public final Vector3f scale = new Vector3f(DEFAULT_SCALE);

    /**
     * How this transform stores its rotation, Blender-style per-bone
     * {@code rotation_mode}. {@link RotationMode#EULER} uses {@link #rotate}
     * (keeps &gt;360° spins and per-component curves); {@link RotationMode#QUATERNION}
     * uses {@link #quat} (no gimbal lock). The default is EULER, so untouched data
     * and old scenes behave exactly as before. {@link #createRotation()} is the
     * single point that reads the right one, so every consumer (render, gizmo, IK,
     * keyframes) follows the mode without knowing about it.
     */
    public RotationMode rotationMode = RotationMode.EULER;

    public final Vector3f rotate = new Vector3f();

    /** The rotation when {@link #rotationMode} is {@link RotationMode#QUATERNION}; identity otherwise. */
    public final Quaternionf quat = new Quaternionf();

    public void lerp(Transform transform, float a)
    {
        this.translate.lerp(transform.translate, a);
        this.scale.lerp(transform.scale, a);
        this.lerpRotation(transform, a);
    }

    /**
     * Blend this transform's rotation toward {@code transform}'s by {@code a},
     * mode-aware — the rotation half of {@link #lerp(Transform, float)}, factored
     * out so the pose-fix blend paths (form pose overlays) keep a quaternion bone
     * on a slerp instead of a component-wise euler lerp that would drop its
     * rotation. This is pose BLEND (a single shortest-arc slerp), not keyframe
     * interpolation — the latter lives in {@link #interpolateQuat} with per-component
     * curves.
     */
    public void lerpRotation(Transform transform, float a)
    {
        if (this.rotationMode == RotationMode.QUATERNION || transform.rotationMode == RotationMode.QUATERNION)
        {
            this.quat.set(this.createRotation()).slerp(transform.createRotation(), a);
            this.rotationMode = RotationMode.QUATERNION;
        }
        else
        {
            this.rotate.lerp(transform.rotate, a);
        }
    }

    public void lerp(Transform preA, Transform a, Transform b, Transform postB, IInterp interp, float x)
    {
        this.lerp(this.translate, preA.translate, a.translate, b.translate, postB.translate, interp, x);
        this.lerp(this.scale, preA.scale, a.scale, b.scale, postB.scale, interp, x);

        /* Quaternion keyframes interpolate PER COMPONENT (w,x,y,z) through the
         * very same curve as every other channel, then re-normalize — Blender's
         * quaternion F-curve model. This gives smooth (C1) tangents across key
         * joins that a per-segment slerp can't, and keeps euler and quaternion
         * on one uniform interpolation path. */
        if (a.rotationMode == RotationMode.QUATERNION || b.rotationMode == RotationMode.QUATERNION)
        {
            this.interpolateQuat(preA, a, b, postB,
                (pre, av, bv, post) -> interp.interpolate(IInterp.context.set(pre, av, bv, post, x)));
        }
        else
        {
            this.lerp(this.rotate, eulerControl(preA, a.rotate), a.rotate, b.rotate, eulerControl(postB, b.rotate), interp, x);
            this.rotationMode = RotationMode.EULER;
        }
    }

    private void lerp(Vector3f target, Vector3f preA, Vector3f a, Vector3f b, Vector3f postB, IInterp interp, float x)
    {
        target.x = (float) interp.interpolate(IInterp.context.set(preA.x, a.x, b.x, postB.x, x));
        target.y = (float) interp.interpolate(IInterp.context.set(preA.y, a.y, b.y, postB.y, x));
        target.z = (float) interp.interpolate(IInterp.context.set(preA.z, a.z, b.z, postB.z, x));
    }

    public void autoLerp(Transform preA, Transform a, Transform b, Transform postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        this.autoLerp(this.translate, preA.translate, a.translate, b.translate, postB.translate, pt, at, bt, qt, clamped, x);
        this.autoLerp(this.scale, preA.scale, a.scale, b.scale, postB.scale, pt, at, bt, qt, clamped, x);

        if (a.rotationMode == RotationMode.QUATERNION || b.rotationMode == RotationMode.QUATERNION)
        {
            this.interpolateQuat(preA, a, b, postB,
                (pre, av, bv, post) -> AutoBezier.get(pre, av, bv, post, pt, at, bt, qt, clamped, x));
        }
        else
        {
            this.autoLerp(this.rotate, eulerControl(preA, a.rotate), a.rotate, b.rotate, eulerControl(postB, b.rotate), pt, at, bt, qt, clamped, x);
            this.rotationMode = RotationMode.EULER;
        }
    }

    /**
     * A window-control rotation as euler angles for the euler interpolation
     * branch. The outer controls (preA/postB) only shape the curve's tangents,
     * but on a quaternion-mode neighbour the {@link #rotate} channel is stale —
     * reading it raw would bend the euler segment by a garbage control point.
     * Decompose the live quaternion instead, on the branch nearest the adjacent
     * key so the control can't land on a far 180°-flipped equivalent.
     */
    private static Vector3f eulerControl(Transform t, Vector3f ref)
    {
        return t.rotationMode == RotationMode.QUATERNION
            ? Matrices.toCompatibleEulerZYXRadians(t.quat, ref, new Vector3f())
            : t.rotate;
    }

    private void autoLerp(Vector3f target, Vector3f preA, Vector3f a, Vector3f b, Vector3f postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        target.x = (float) AutoBezier.get(preA.x, a.x, b.x, postB.x, pt, at, bt, qt, clamped, x);
        target.y = (float) AutoBezier.get(preA.y, a.y, b.y, postB.y, pt, at, bt, qt, clamped, x);
        target.z = (float) AutoBezier.get(preA.z, a.z, b.z, postB.z, pt, at, bt, qt, clamped, x);
    }

    /**
     * Interpolate the quaternion rotation of a keyframe window per component,
     * shared by both the manual-curve {@link #lerp} and the auto-bezier
     * {@link #autoLerp}. The four control rotations are read through
     * {@link #createRotation()} (so euler and quaternion keys can mix), then
     * sign-aligned into a single hemisphere around {@code a}: since {@code q}
     * and {@code -q} are the same orientation, a neighbour in the opposite
     * hemisphere would otherwise make the component curves wind the long way.
     * This is Blender's "make quaternion keyframes compatible", done locally at
     * evaluation time. The kernel supplies the actual per-component curve
     * (manual easing or auto bezier); the result is re-normalized back onto the
     * unit sphere. Writes {@link #quat} and switches to quaternion mode.
     */
    private void interpolateQuat(Transform preA, Transform a, Transform b, Transform postB, QuatComponent kernel)
    {
        Quaternionf qpre = preA.createRotation();
        Quaternionf qa = a.createRotation();
        Quaternionf qb = b.createRotation();
        Quaternionf qpost = postB.createRotation();

        if (qb.dot(qa) < 0F) negate(qb);
        if (qpre.dot(qa) < 0F) negate(qpre);
        if (qpost.dot(qb) < 0F) negate(qpost);

        this.quat.set(
            (float) kernel.get(qpre.x, qa.x, qb.x, qpost.x),
            (float) kernel.get(qpre.y, qa.y, qb.y, qpost.y),
            (float) kernel.get(qpre.z, qa.z, qb.z, qpost.z),
            (float) kernel.get(qpre.w, qa.w, qb.w, qpost.w)
        ).normalize();
        this.rotationMode = RotationMode.QUATERNION;
    }

    private static void negate(Quaternionf q)
    {
        q.set(-q.x, -q.y, -q.z, -q.w);
    }

    /** One interpolated quaternion component from its four window values (preA, a, b, postB). */
    @FunctionalInterface
    private interface QuatComponent
    {
        double get(float preA, float a, float b, float postB);
    }

    public void add(Transform transform)
    {
        this.translate.add(transform.translate);
        this.scale.mul(transform.scale);
        this.addRotation(transform);
    }

    /**
     * Additively stack {@code transform}'s rotation onto this one, mode-aware — the
     * rotation half of {@link #add(Transform)}, factored out so every overlay path
     * (action layers, form additional transforms, pose overlays) composes rotation
     * the same way instead of hand-rolling a component-wise euler add that silently
     * drops a quaternion bone's rotation. If either side is quaternion the result is
     * the quaternion product {@code base · layer} (matching the orient composition in
     * applyPose) and the mode flips to quaternion; both-euler keeps the legacy
     * component-wise angle add. Note the caller owns translate/scale — this touches
     * rotation only, so callers with a different scale convention (additive
     * {@code +scale-1} instead of {@code ·scale}) can still reuse it.
     */
    public void addRotation(Transform transform)
    {
        if (this.rotationMode == RotationMode.QUATERNION || transform.rotationMode == RotationMode.QUATERNION)
        {
            this.quat.set(this.createRotation()).mul(transform.createRotation());
            this.rotationMode = RotationMode.QUATERNION;
        }
        else
        {
            this.rotate.add(transform.rotate);
        }
    }

    public void identity()
    {
        this.translate.set(0, 0, 0);
        this.scale.set(1, 1, 1);
        this.rotate.set(0, 0, 0);
        this.quat.identity();
        this.rotationMode = RotationMode.EULER;
    }

    /**
     * Clear the rotation to identity in BOTH representations, keeping the mode —
     * the reset every editor shares. Clearing only {@link #rotate} looks complete
     * but leaves a quaternion bone turned (its render follows {@link #quat}).
     */
    public void resetRotation()
    {
        this.rotate.set(0, 0, 0);
        this.quat.identity();
    }

    /**
     * THE channel mirror convention: reflect this transform across the model's
     * YZ symmetry plane ({@code D·R·D} with {@code D = diag(-1,1,1)}) — translate.x
     * flips sign, the rotation keeps its X part and flips Y/Z, scale is untouched.
     * An involution (applying it twice is a no-op), which the mirror-edit fan-out
     * relies on. Every mirror/flip of pose channels goes through here.
     */
    public void mirrorX()
    {
        this.translate.mul(-1F, 1F, 1F);

        if (this.rotationMode == RotationMode.QUATERNION)
        {
            /* The quaternion mirror across the YZ plane: (w, x, -y, -z). Matches
             * the euler convention (a pure-X turn is unchanged, Y/Z flip) since
             * both are the D·R·D conjugation with D = diag(-1,1,1). */
            this.quat.set(this.quat.x, -this.quat.y, -this.quat.z, this.quat.w);
        }
        else
        {
            this.rotate.mul(1F, -1F, -1F);
        }
    }

    /**
     * THE full local rotation of this transform, radians — the single point every
     * consumer reads, so the storage mode stays invisible to them. In quaternion
     * mode it is {@link #quat}; in euler mode it is {@code ZYX(rotate)}.
     */
    public Quaternionf createRotation()
    {
        return this.rotationMode == RotationMode.QUATERNION
            ? new Quaternionf(this.quat)
            : Matrices.toQuaternionZYXRadians(this.rotate.x, this.rotate.y, this.rotate.z);
    }

    /**
     * Switch to quaternion storage, folding the current euler rotation into
     * {@link #quat}. A no-op if already in quaternion mode.
     */
    public void setModeQuaternion()
    {
        if (this.rotationMode != RotationMode.QUATERNION)
        {
            this.quat.set(Matrices.toQuaternionZYXRadians(this.rotate.x, this.rotate.y, this.rotate.z));
            this.rotationMode = RotationMode.QUATERNION;
        }
    }

    /**
     * Switch to euler storage, decomposing the current quaternion into
     * {@link #rotate} (ZYX) — on the branch nearest the (stale) euler channels,
     * so toggling euler&rarr;quaternion&rarr;euler lands back on the same values
     * instead of a far 180°-flipped equivalent. A no-op if already in euler mode.
     */
    public void setModeEuler()
    {
        if (this.rotationMode != RotationMode.EULER)
        {
            Matrices.toCompatibleEulerZYXRadians(this.quat, this.rotate, this.rotate);
            this.rotationMode = RotationMode.EULER;
        }
    }

    /**
     * The effective local rotation as euler ZYX angles (radians), written into
     * {@code dest} — {@link #rotate} directly in euler mode, or {@link #quat}
     * decomposed in quaternion mode. THE read for euler-only destinations that
     * cannot hold a quaternion (vanilla Minecraft {@code ModelPart} pitch/yaw/roll),
     * so a quaternion bone still contributes its rotation there instead of being
     * silently dropped — lossy only at a gimbal pole, which is inherent to any
     * three-angle target.
     */
    public Vector3f getEulerRotation(Vector3f dest)
    {
        if (this.rotationMode == RotationMode.QUATERNION)
        {
            return Matrices.toEulerZYXRadians(this.quat, dest);
        }

        return dest.set(this.rotate);
    }

    public Matrix3f createRotationMatrix()
    {
        return new Matrix3f().rotation(this.createRotation());
    }

    public Matrix4f createMatrix()
    {
        return this.setupMatrix(new Matrix4f());
    }

    /**
     * THE local matrix of a transform's channels, appended onto {@code matrix}:
     * {@code translate · rotation · scale}, with the rotation composed in the
     * one shared place ({@link #createRotation()}). Every pose/slot consumer
     * builds through here, so the composition order can't fork between call sites.
     */
    public Matrix4f setupMatrix(Matrix4f matrix)
    {
        matrix.translate(this.translate);
        matrix.rotate(this.createRotation());
        matrix.scale(this.scale);

        return matrix;
    }

    /**
     * THE inverse of {@link #setupMatrix(Matrix4f)}: read a matrix back into these channels.
     * Exact for anything of that shape ({@code translate · rotation · scale}), which is what a
     * composition of transforms is as long as no non-uniform scale sits above a rotation — such a
     * matrix carries shear, has no TRS decomposition at all, and lands on the nearest one here.
     *
     * <p>A mirrored matrix (negative determinant, the one flip a column's length cannot show) puts
     * the sign on X, the same axis {@link #mirrorX()} works on.</p>
     *
     * <p>In euler mode the angles land on the branch nearest the current ones, the way
     * {@link #setModeEuler()} does it — a channel sitting at 350&deg; stays there instead of
     * jumping to -10&deg; and dragging a keyframe curve across a whole turn with it.</p>
     */
    public void fromMatrix(Matrix4f matrix)
    {
        Matrix3f basis = matrix.get3x3(new Matrix3f());
        Vector3f axis = new Vector3f();

        this.translate.set(matrix.m30(), matrix.m31(), matrix.m32());
        this.scale.set(
            basis.getColumn(0, axis).length(),
            basis.getColumn(1, axis).length(),
            basis.getColumn(2, axis).length()
        );

        if (basis.determinant() < 0F)
        {
            this.scale.x = -this.scale.x;
        }

        /* Divide the scale out of the basis rather than normalizing it — normalizing would lose the
         * mirror above, and a column with no length left has no direction to keep, so its axis is
         * restored instead of turned into NaN (as in Matrices.compose). */
        for (int i = 0; i < 3; i++)
        {
            float length = i == 0 ? this.scale.x : (i == 1 ? this.scale.y : this.scale.z);

            if (Math.abs(length) < 1E-6F)
            {
                axis.set(i == 0 ? 1F : 0F, i == 1 ? 1F : 0F, i == 2 ? 1F : 0F);
            }
            else
            {
                basis.getColumn(i, axis).div(length);
            }

            basis.setColumn(i, axis);
        }

        Quaternionf rotation = new Quaternionf().setFromNormalized(basis);

        if (this.rotationMode == RotationMode.QUATERNION)
        {
            this.quat.set(rotation);
        }
        else
        {
            Matrices.toCompatibleEulerZYXRadians(rotation, this.rotate, this.rotate);
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof Transform)
        {
            Transform transform = (Transform) obj;

            if (this.rotationMode != transform.rotationMode
                || !this.translate.equals(transform.translate)
                || !this.scale.equals(transform.scale))
            {
                return false;
            }

            return this.rotationMode == RotationMode.QUATERNION
                ? this.quat.equals(transform.quat)
                : this.rotate.equals(transform.rotate);
        }

        return false;
    }

    /**
     * Content hash: changes whenever any stored component changes. Cheap (pure field mixing, no
     * allocation), for signature checks that run per frame — unlike serializing to data and
     * hashing the string.
     */
    public int contentHash()
    {
        int hash = this.translate.hashCode();

        hash = 31 * hash + this.scale.hashCode();
        hash = 31 * hash + this.rotate.hashCode();
        hash = 31 * hash + this.quat.hashCode();
        hash = 31 * hash + this.rotationMode.ordinal();

        return hash;
    }

    public Transform copy()
    {
        Transform transform = new Transform();

        transform.copy(this);

        return transform;
    }

    public void copy(Transform transform)
    {
        this.translate.set(transform.translate);
        this.scale.set(transform.scale);
        this.rotate.set(transform.rotate);
        this.quat.set(transform.quat);
        this.rotationMode = transform.rotationMode;
    }

    public boolean isDefault()
    {
        return this.equals(DEFAULT);
    }

    public void toRad()
    {
        this.rotate.x = MathUtils.toRad(this.rotate.x);
        this.rotate.y = MathUtils.toRad(this.rotate.y);
        this.rotate.z = MathUtils.toRad(this.rotate.z);
    }

    public void toDeg()
    {
        this.rotate.x = MathUtils.toDeg(this.rotate.x);
        this.rotate.y = MathUtils.toDeg(this.rotate.y);
        this.rotate.z = MathUtils.toDeg(this.rotate.z);
    }

    @Override
    public void toData(MapType data)
    {
        if (!this.isDefault())
        {
            data.put("t", DataStorageUtils.vector3fToData(this.translate));
            data.put("s", DataStorageUtils.vector3fToData(this.scale));

            /* The presence of "q" is itself the mode discriminator, so no separate
             * flag is needed and old scenes (only r) read back as EULER. */
            if (this.rotationMode == RotationMode.QUATERNION)
            {
                data.put("q", DataStorageUtils.quaternionfToData(this.quat));
            }
            else
            {
                data.put("r", DataStorageUtils.vector3fToData(this.rotate));
            }
        }
    }

    @Override
    public void fromData(MapType data)
    {
        this.identity();

        this.translate.set(DataStorageUtils.vector3fFromData(data.getList("t")));
        this.scale.set(DataStorageUtils.vector3fFromData(data.getList("s"), DEFAULT_SCALE));

        if (data.has("q"))
        {
            this.rotationMode = RotationMode.QUATERNION;
            this.quat.set(DataStorageUtils.quaternionfFromData(data.getList("q")));
        }
        else
        {
            this.rotationMode = RotationMode.EULER;
            this.rotate.set(DataStorageUtils.vector3fFromData(data.getList("r")));

            /* Legacy migration: rotate2 (the removed second euler stack) folds into
             * rotate so old scenes read identically — ZYX(rotate)·ZYX(rotate2)
             * decomposed to one euler. Exact for the pose value; a keyframed
             * rotate2 changes only the shape between keys (why it was removable). */
            if (data.has("r2"))
            {
                Vector3f rotate2 = DataStorageUtils.vector3fFromData(data.getList("r2"));

                if (rotate2.x != 0F || rotate2.y != 0F || rotate2.z != 0F)
                {
                    Quaternionf folded = Matrices.toQuaternionZYXRadians(this.rotate.x, this.rotate.y, this.rotate.z)
                        .mul(Matrices.toQuaternionZYXRadians(rotate2.x, rotate2.y, rotate2.z));

                    /* Reference = the naive channel sum r + r2 — the folded
                     * quaternion only knows the principal rotation, so the
                     * reference alone decides branch and WINDING. Anchoring to
                     * r (as this migration originally did) collapsed a wound
                     * r2 to its principal value: a keyframed r2 sweep of −227°
                     * migrated to +133° and the animation between those keys
                     * travelled the wrong way around. The sum is what the
                     * author effectively wrote across the two stacks, so the
                     * decomposition lands on their branch, spins included. */
                    Vector3f reference = new Vector3f(this.rotate).add(rotate2);

                    Matrices.toCompatibleEulerZYXRadians(folded, reference, this.rotate);
                }
            }
        }
    }

    /** Per-transform rotation storage, Blender's {@code rotation_mode} (Euler | Quaternion). */
    public enum RotationMode
    {
        EULER, QUATERNION
    }
}
