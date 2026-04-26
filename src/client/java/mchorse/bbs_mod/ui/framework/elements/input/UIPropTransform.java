package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

public class UIPropTransform extends UITransform
{
    private static final double[] CURSOR_X = new double[1];
    private static final double[] CURSOR_Y = new double[1];

    private Transform transform;
    private Runnable preCallback;
    private Runnable postCallback;

    private boolean editing;
    private int mode;
    private Axis axis = Axis.X;
    private Axis axis2;
    private int lastX;
    private Transform cache = new Transform();
    private Timer checker = new Timer(30);

    private boolean model;
    private boolean local;

    /* Ray-based drag state for translate mode */
    private GizmoDrag drag;
    /**
     * World-space direction (with scale) for one unit of drag along each
     * gizmo handle. Columns correspond to the {@link Axis} the user is
     * dragging. Used to project the cursor's world delta back onto the
     * relevant handle.
     */
    private final Matrix3f dragWorldBasis = new Matrix3f();
    /**
     * Direction (in {@code transform.translate} units) for one unit of drag
     * along each gizmo handle. Together with {@link #dragWorldBasis} this
     * solves the mapping from world cursor motion to the change of
     * {@code transform.translate} regardless of what units that translate is
     * in (blocks, pixels, ...).
     */
    private final Matrix3f dragTranslateBasis = new Matrix3f();
    private final Vector3f dragPlaneNormal = new Vector3f();
    private final Vector3d dragStartHit = new Vector3d();
    private final Vector3f dragStartTranslate = new Vector3f();

    /* Per-mode start state. Translate uses dragStartTranslate above; scale and
     * rotate get their own snapshots so each mode is self-contained. */
    private final Vector3f dragStartScale = new Vector3f();
    private final Vector3f dragStartRotateDeg = new Vector3f();
    /** World-space direction of the active handle, captured at drag start. */
    private final Vector3f dragAxisDir = new Vector3f();
    /** Signed projection of {@code (startHit - origin)} onto {@link #dragAxisDir}. Used for ratio-based scale. */
    private float dragStartScaleProj;
    /** Unit ring direction (perpendicular to rotation axis) at drag start. */
    private final Vector3f dragStartRingVec = new Vector3f();
    /** Whether {@link #dragStartRotateDeg} should be written back to {@code rotate2} instead of {@code rotate}. */
    private boolean dragRotateGizmoSpace;
    private boolean dragHasStart;
    private Supplier<GizmoDrag> hotkeyDragSupplier;

    private UITransformHandler handler;

    public UIPropTransform()
    {
        this.handler = new UITransformHandler(this);
        this.local = BBSSettings.transformLocalDefault.get();

        this.context((menu) ->
        {
            menu.action(
                this.local ? Icons.FULLSCREEN : Icons.MINIMIZE,
                this.local ? UIKeys.TRANSFORMS_CONTEXT_SWITCH_GLOBAL : UIKeys.TRANSFORMS_CONTEXT_SWITCH_LOCAL,
                this::toggleLocal
            );

            menu.actions.add(0, menu.actions.remove(menu.actions.size() - 1));
        });

        this.iconT.callback = (b) -> this.toggleLocal();
        this.iconT.hoverColor = Colors.LIGHTEST_GRAY;
        this.iconT.setEnabled(true);
        this.updateLocalUI();

        this.noCulling();
    }

    public UIPropTransform callbacks(Supplier<IValueNotifier> notifier)
    {
        return this.callbacks(
            () -> notifier.get().preNotify(),
            () -> notifier.get().postNotify()
        );
    }

    public UIPropTransform callbacks(Runnable pre, Runnable post)
    {
        this.preCallback = pre;
        this.postCallback = post;

        return this;
    }

    public void preCallback()
    {
        if (this.preCallback != null) this.preCallback.run();
    }

    public void postCallback()
    {
        if (this.postCallback != null) this.postCallback.run();
    }

    public void setModel()
    {
        this.model = true;
    }

    public UIPropTransform hotkeyDrag(Supplier<GizmoDrag> supplier)
    {
        this.hotkeyDragSupplier = supplier;

        return this;
    }

    public boolean isLocal()
    {
        return this.local;
    }

    private void toggleLocal()
    {
        this.local = !this.local;

        if (!this.local && this.transform != null)
        {
            this.fillT(this.transform.translate.x, this.transform.translate.y, this.transform.translate.z);
        }

        this.updateLocalUI();
    }

    private void updateLocalUI()
    {
        this.tx.forcedLabel(this.local ? UIKeys.GENERAL_X : null);
        this.ty.forcedLabel(this.local ? UIKeys.GENERAL_Y : null);
        this.tz.forcedLabel(this.local ? UIKeys.GENERAL_Z : null);
        this.tx.relative(this.local);
        this.ty.relative(this.local);
        this.tz.relative(this.local);
        this.iconT.tooltip(this.local ? UIKeys.TRANSFORMS_CONTEXT_SWITCH_GLOBAL : UIKeys.TRANSFORMS_CONTEXT_SWITCH_LOCAL);
    }

    private Vector3f calculateLocalVector(double factor, Axis axis)
    {
        if (this.transform == null)
        {
            return new Vector3f();
        }

        Vector3f vector3f = new Vector3f(
            (float) (axis == Axis.X ? factor : 0D),
            (float) (axis == Axis.Y ? factor : 0D),
            (float) (axis == Axis.Z ? factor : 0D)
        );
        /* I have no fucking idea why I have to rotate it 180 degrees by X axis... but it works! */
        Matrix3f matrix = new Matrix3f()
            .rotateX(this.model ? MathUtils.PI : 0F)
            .mul(this.transform.createRotationMatrix());

        matrix.transform(vector3f);

        return vector3f;
    }

    public UIPropTransform enableHotkeys()
    {
        IKey category = UIKeys.TRANSFORMS_KEYS_CATEGORY;
        Supplier<Boolean> active = () -> this.editing;

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, () -> this.enableMode(0)).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SCALE, () -> this.enableMode(1)).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATE, () -> this.enableMode(2)).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_X, () -> this.setEditingAxis(Axis.X)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Y, () -> this.setEditingAxis(Axis.Y)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Z, () -> this.setEditingAxis(Axis.Z)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_TOGGLE_LOCAL, () ->
        {
            this.toggleLocal();
            UIUtils.playClick();
        }).category(category);

        return this;
    }

    public Transform getTransform()
    {
        return this.transform;
    }

    public int getDebugLineStencilIndex()
    {
        if (!this.editing)
        {
            return -1;
        }

        if (this.axis2 != null)
        {
            if ((this.axis == Axis.X && this.axis2 == Axis.Z) || (this.axis == Axis.Z && this.axis2 == Axis.X))
            {
                return Gizmo.STENCIL_XZ;
            }

            if ((this.axis == Axis.X && this.axis2 == Axis.Y) || (this.axis == Axis.Y && this.axis2 == Axis.X))
            {
                return Gizmo.STENCIL_XY;
            }

            if ((this.axis == Axis.Z && this.axis2 == Axis.Y) || (this.axis == Axis.Y && this.axis2 == Axis.Z))
            {
                return Gizmo.STENCIL_ZY;
            }
        }

        if (this.axis == Axis.X) return Gizmo.STENCIL_X;
        if (this.axis == Axis.Y) return Gizmo.STENCIL_Y;
        if (this.axis == Axis.Z) return Gizmo.STENCIL_Z;

        return -1;
    }

    public void refillTransform()
    {
        this.setTransform(this.getTransform());
    }

    public void setTransform(Transform transform)
    {
        this.transform = transform;

        if (transform == null)
        {
            this.disable();
            this.fillT(0, 0, 0);
            this.fillS(1, 1, 1);
            this.fillR(0, 0, 0);
            this.fillR2(0, 0, 0);

            return;
        }

        float minScale = Math.min(transform.scale.x, Math.min(transform.scale.y, transform.scale.z));
        float maxScale = Math.max(transform.scale.x, Math.max(transform.scale.y, transform.scale.z));

        if (BBSSettings.uniformScale.get())
        {
            if (
                (minScale == maxScale && !this.isUniformScale()) ||
                (minScale != maxScale && this.isUniformScale())
            ) {
                this.toggleUniformScale();
            }
        }

        this.fillT(transform.translate.x, transform.translate.y, transform.translate.z);
        this.fillS(transform.scale.x, transform.scale.y, transform.scale.z);
        this.fillR(MathUtils.toDeg(transform.rotate.x), MathUtils.toDeg(transform.rotate.y), MathUtils.toDeg(transform.rotate.z));
        this.fillR2(MathUtils.toDeg(transform.rotate2.x), MathUtils.toDeg(transform.rotate2.y), MathUtils.toDeg(transform.rotate2.z));
    }

    public void enableMode(int mode)
    {
        this.enableMode(mode, null, null, this.getHotkeyDrag());
    }

    public void enableMode(int mode, Axis axis)
    {
        this.enableMode(mode, axis, null, null);
    }

    public void enableMode(int mode, Axis axis, Axis axis2)
    {
        this.enableMode(mode, axis, axis2, null);
    }

    public void enableMode(int mode, Axis axis, Axis axis2, GizmoDrag drag)
    {
        if (Gizmo.INSTANCE.setMode(Gizmo.Mode.values()[mode]) && axis == null)
        {
            return;
        }

        UIContext context = this.getContext();
        if (context == null)
        {
            return;
        }

        if (this.transform == null)
        {
            return;
        }

        if (this.editing)
        {
            Axis[] values = Axis.values();

            this.axis = values[MathUtils.cycler(this.axis.ordinal() + 1, 0, values.length - 1)];
            this.axis2 = null;
            this.drag = drag;

            this.restore(true);
        }
        else
        {
            this.axis = axis == null ? Axis.X : axis;
            this.axis2 = axis2;
            this.lastX = context.mouseX;
            this.drag = drag;
        }

        this.editing = true;
        this.mode = mode;

        this.cache.copy(this.transform);
        Gizmo.INSTANCE.trackTransform(this);

        if (this.drag != null)
        {
            this.beginRayDrag(context.mouseX, context.mouseY);
        }

        if (!this.handler.hasParent())
        {
            context.menu.overlay.add(this.handler);
        }
    }

    private GizmoDrag getHotkeyDrag()
    {
        return this.hotkeyDragSupplier == null ? null : this.hotkeyDragSupplier.get();
    }

    private void setEditingAxis(Axis axis)
    {
        this.axis = axis;
        this.axis2 = null;

        if (!this.editing)
        {
            return;
        }

        this.restore(true);

        if (this.drag != null)
        {
            UIContext context = this.getContext();

            if (context != null)
            {
                this.beginRayDrag(context.mouseX, context.mouseY);
            }
        }
    }

    /**
     * Resolve the cursor's current intersection with the drag plane and
     * dispatch to the per-mode handler. Bails silently when the ray turns
     * parallel to the plane so the transform doesn't jump.
     */
    private void applyRayDrag(int mouseX, int mouseY)
    {
        if (!this.dragHasStart || this.transform == null)
        {
            return;
        }

        Vector3d hit = new Vector3d();

        if (!this.drag.intersectPlane(mouseX, mouseY, this.dragPlaneNormal, hit))
        {
            return;
        }

        switch (this.mode)
        {
            case 0:
                this.applyRayTranslate(hit);
                break;
            case 1:
                this.applyRayScale(hit);
                break;
            case 2:
                this.applyRayRotate(hit);
                break;
        }
    }

    private void applyRayTranslate(Vector3d hit)
    {
        Vector3f delta = new Vector3f(
            (float) (hit.x - this.dragStartHit.x),
            (float) (hit.y - this.dragStartHit.y),
            (float) (hit.z - this.dragStartHit.z)
        );

        Vector3f result = new Vector3f();

        this.accumulateAlongAxis(delta, this.axis, result);

        if (this.axis2 != null)
        {
            this.accumulateAlongAxis(delta, this.axis2, result);
        }

        this.setT(null,
            this.dragStartTranslate.x + result.x,
            this.dragStartTranslate.y + result.y,
            this.dragStartTranslate.z + result.z
        );
    }

    /**
     * Scale by ratio of cursor-to-origin distances along the active axis. The
     * "lever" picked at drag start defines 1.0; pulling further multiplies the
     * scale, dragging closer shrinks it. Falls back to additive delta if the
     * starting projection is too small to safely divide by.
     */
    private void applyRayScale(Vector3d hit)
    {
        boolean all = Window.isCtrlPressed();
        Vector3f s = new Vector3f(this.dragStartScale);

        this.applyRayScaleAxis(hit, this.axis, all, s);

        if (this.axis2 != null)
        {
            this.applyRayScaleAxis(hit, this.axis2, all, s);
        }

        this.setS(null, s.x, s.y, s.z);
    }

    private void applyRayScaleAxis(Vector3d hit, Axis currentAxis, boolean all, Vector3f s)
    {
        Vector3f axisDir = this.dragWorldBasis.getColumn(currentAxis.ordinal(), new Vector3f());
        
        if (axisDir.lengthSquared() < 1.0E-8F)
        {
            return;
        }
        
        axisDir.normalize();

        double rx = hit.x - this.drag.gizmoOrigin.x;
        double ry = hit.y - this.drag.gizmoOrigin.y;
        double rz = hit.z - this.drag.gizmoOrigin.z;
        float currentProj = (float) (rx * axisDir.x + ry * axisDir.y + rz * axisDir.z);

        double srx = this.dragStartHit.x - this.drag.gizmoOrigin.x;
        double sry = this.dragStartHit.y - this.drag.gizmoOrigin.y;
        double srz = this.dragStartHit.z - this.drag.gizmoOrigin.z;
        float startProj = (float) (srx * axisDir.x + sry * axisDir.y + srz * axisDir.z);

        float delta = currentProj - startProj;

        if (Math.abs(startProj) < 1.0E-4F)
        {
            if (all || currentAxis == Axis.X) s.x += delta;
            if (all || currentAxis == Axis.Y) s.y += delta;
            if (all || currentAxis == Axis.Z) s.z += delta;
        }
        else
        {
            float ratio = currentProj / startProj;

            if (all || currentAxis == Axis.X) s.x *= ratio;
            if (all || currentAxis == Axis.Y) s.y *= ratio;
            if (all || currentAxis == Axis.Z) s.z *= ratio;
        }
    }

    /**
     * Rotate around the active axis by the signed angle swept on the rotation
     * plane between the start hit and the current hit. The axis convention
     * matches the dx-based code: when {@code local && gizmos enabled} the
     * change goes into {@code rotate2}, otherwise into {@code rotate}.
     */
    private void applyRayRotate(Vector3d hit)
    {
        Vector3f rel = new Vector3f(
            (float) (hit.x - this.drag.gizmoOrigin.x),
            (float) (hit.y - this.drag.gizmoOrigin.y),
            (float) (hit.z - this.drag.gizmoOrigin.z)
        );

        float along = rel.dot(this.dragAxisDir);

        rel.sub(new Vector3f(this.dragAxisDir).mul(along));

        if (rel.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        rel.normalize();

        float dot = this.dragStartRingVec.dot(rel);
        Vector3f cross = new Vector3f();

        this.dragStartRingVec.cross(rel, cross);

        float crossSign = cross.dot(this.dragAxisDir);
        float angleDeg = MathUtils.toDeg((float) Math.atan2(crossSign, dot));

        float rx = this.dragStartRotateDeg.x;
        float ry = this.dragStartRotateDeg.y;
        float rz = this.dragStartRotateDeg.z;

        switch (this.axis)
        {
            case X: rx += angleDeg; break;
            case Y: ry += angleDeg; break;
            case Z: rz += angleDeg; break;
        }

        if (this.dragRotateGizmoSpace) this.setR2(null, rx, ry, rz);
        else this.setR(null, rx, ry, rz);
    }

    /**
     * Convert the cursor's world-space delta into a contribution to
     * {@code transform.translate} for the given handle.
     *
     * The math is the same for both LOCAL and GLOBAL modes: project the
     * world delta onto the handle's world direction (with scale), then map
     * it back into translate-space using the precomputed translate basis.
     * This automatically handles cases where one unit of {@code translate}
     * is not one block in world (cubic groups, scaled parents, etc.).
     */
    private void accumulateAlongAxis(Vector3f delta, Axis axis, Vector3f out)
    {
        Vector3f worldAxis = new Vector3f();

        this.dragWorldBasis.getColumn(axis.ordinal(), worldAxis);

        float lenSq = worldAxis.lengthSquared();

        if (lenSq < 1.0E-12F)
        {
            return;
        }

        float t = worldAxis.dot(delta) / lenSq;

        Vector3f translateAxis = new Vector3f();

        this.dragTranslateBasis.getColumn(axis.ordinal(), translateAxis);
        out.add(translateAxis.mul(t));
    }

    /**
     * Capture the world-space anchor for ray-based translate. Recomputed when
     * the cursor wraps, so that the delta always restarts from zero relative
     * to the current pointer position.
     */
    private void beginRayDrag(int mouseX, int mouseY)
    {
        if (this.drag == null || this.transform == null)
        {
            this.dragHasStart = false;

            return;
        }

        switch (this.mode)
        {
            case 0:
                this.beginRayTranslate(mouseX, mouseY);
                break;
            case 1:
                this.beginRayScale(mouseX, mouseY);
                break;
            case 2:
                this.beginRayRotate(mouseX, mouseY);
                break;
            default:
                this.dragHasStart = false;
                break;
        }
    }

    private void beginRayTranslate(int mouseX, int mouseY)
    {
        Matrix3f jacobian = new Matrix3f(this.drag.translateJacobian);

        if (this.local)
        {
            /* Local: each handle moves along the form's own axes (after the
             * legacy 180° X correction). The translate-space direction is
             * R.col(axis); pushed through the Jacobian we get the world-space
             * direction with the proper scale baked in. */
            Matrix3f rotation = new Matrix3f()
                .rotateX(this.model ? MathUtils.PI : 0F)
                .mul(this.transform.createRotationMatrix());

            this.dragTranslateBasis.set(rotation);
            this.dragWorldBasis.set(jacobian).mul(rotation);
        }
        else
        {
            /* Global (Parent space): handles align with the bone's origin axes
             * (which includes parent rotations and entity yaw). 
             * We use the rendered gizmo's world axes for the drag plane, and
             * push them through the inverse Jacobian to find the matching 
             * change in translate-space. */
            Matrix3f inverse = new Matrix3f(jacobian);

            if (Math.abs(inverse.determinant()) < 1.0E-8F)
            {
                inverse.identity();
            }
            else
            {
                inverse.invert();
            }

            this.dragTranslateBasis.set(inverse).mul(this.drag.gizmoWorldAxes);
            this.dragWorldBasis.set(this.drag.gizmoWorldAxes);
        }

        if (this.axis2 == null)
        {
            this.drag.planeNormalForAxis(mouseX, mouseY, this.dragWorldBasis, this.axis, this.dragPlaneNormal);
        }
        else
        {
            this.drag.planeNormalForPlane(this.dragWorldBasis, this.axis, this.axis2, this.dragPlaneNormal);
        }

        this.dragStartTranslate.set(this.transform.translate);
        this.dragHasStart = this.drag.intersectPlane(mouseX, mouseY, this.dragPlaneNormal, this.dragStartHit);
    }

    /**
     * Anchor a scale drag on a plane that contains the picked axis and faces
     * the camera. The cursor's projection onto that axis at start defines the
     * "1.0" lever for the ratio computed in {@link #applyRayScale}.
     */
    private void beginRayScale(int mouseX, int mouseY)
    {
        this.dragWorldBasis.set(this.drag.gizmoWorldAxes);

        if (this.axis2 == null)
        {
            this.drag.planeNormalForAxis(mouseX, mouseY, this.dragWorldBasis, this.axis, this.dragPlaneNormal);
        }
        else
        {
            this.drag.planeNormalForPlane(this.dragWorldBasis, this.axis, this.axis2, this.dragPlaneNormal);
        }

        if (!this.drag.intersectPlane(mouseX, mouseY, this.dragPlaneNormal, this.dragStartHit))
        {
            this.dragHasStart = false;

            return;
        }

        this.dragStartScale.set(this.transform.scale);
        this.dragHasStart = true;
    }

    /**
     * Anchor a rotate drag on the plane perpendicular to the picked axis. The
     * unit vector from the gizmo origin to the start hit becomes the reference
     * direction; subsequent intersections produce a signed angle around the
     * axis via {@code atan2}.
     */
    private void beginRayRotate(int mouseX, int mouseY)
    {
        /* Use the renderer's actual rotation axis (filled by the editor via
         * GizmoDrag.computeRotateAxes), not the visible gizmo arrow direction.
         * For cubic models these can differ in sign on X/Z because the renderer
         * post-multiplies by Ry(180°) after the bone's own rotation, flipping
         * bone-local X and Z while preserving Y. Without this the angle we
         * write into transform.rotate winds up running opposite to the user's
         * physical drag. */
        Vector3f axisDir = this.drag.rotateAxes.getColumn(this.axis.ordinal(), new Vector3f());

        if (axisDir.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        axisDir.normalize();
        this.dragAxisDir.set(axisDir);
        this.dragPlaneNormal.set(axisDir);

        if (!this.drag.intersectPlane(mouseX, mouseY, this.dragPlaneNormal, this.dragStartHit))
        {
            this.dragHasStart = false;

            return;
        }

        Vector3f ring = new Vector3f(
            (float) (this.dragStartHit.x - this.drag.gizmoOrigin.x),
            (float) (this.dragStartHit.y - this.drag.gizmoOrigin.y),
            (float) (this.dragStartHit.z - this.drag.gizmoOrigin.z)
        );

        float along = ring.dot(axisDir);

        ring.sub(new Vector3f(axisDir).mul(along));

        if (ring.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        ring.normalize();
        this.dragStartRingVec.set(ring);

        this.dragRotateGizmoSpace = this.local && BBSSettings.gizmos.get();

        Vector3f source = this.dragRotateGizmoSpace ? this.transform.rotate2 : this.transform.rotate;

        this.dragStartRotateDeg.set(
            MathUtils.toDeg(source.x),
            MathUtils.toDeg(source.y),
            MathUtils.toDeg(source.z)
        );

        this.dragHasStart = true;
    }

    private Vector3f getValue()
    {
        if (this.transform == null)
        {
            return new Vector3f();
        }

        if (this.mode == 1)
        {
            return this.transform.scale;
        }
        else if (this.mode == 2)
        {
            return this.local && BBSSettings.gizmos.get() ? this.transform.rotate2 : this.transform.rotate;
        }

        return this.transform.translate;
    }

    private void restore(boolean fully)
    {
        if (this.mode == 0 || fully) this.setT(null, this.cache.translate.x, this.cache.translate.y, this.cache.translate.z);
        if (this.mode == 1 || fully) this.setS(null, this.cache.scale.x, this.cache.scale.y, this.cache.scale.z);
        if (this.mode == 2 || fully)
        {
            this.setR(null, MathUtils.toDeg(this.cache.rotate.x), MathUtils.toDeg(this.cache.rotate.y), MathUtils.toDeg(this.cache.rotate.z));
            this.setR2(null, MathUtils.toDeg(this.cache.rotate2.x), MathUtils.toDeg(this.cache.rotate2.y), MathUtils.toDeg(this.cache.rotate2.z));
        }
    }

    private void disable()
    {
        this.editing = false;
        this.axis2 = null;
        this.drag = null;
        this.dragHasStart = false;
        Gizmo.INSTANCE.clearTrackedTransform(this);

        if (this.handler.hasParent())
        {
            this.handler.removeFromParent();
        }
    }

    public void acceptChanges()
    {
        this.disable();
        this.setTransform(this.transform);
    }

    public void rejectChanges()
    {
        this.disable();

        if (this.transform == null)
        {
            return;
        }

        this.restore(true);
        this.setTransform(this.transform);
    }

    @Override
    protected void internalSetT(double x, Axis axis)
    {
        if (this.transform == null)
        {
            return;
        }

        if (this.local)
        {
            try
            {
                Vector3f vector3f = this.calculateLocalVector(x, axis);

                this.setT(null,
                    this.transform.translate.x + vector3f.x,
                    this.transform.translate.y + vector3f.y,
                    this.transform.translate.z + vector3f.z
                );
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            super.internalSetT(x, axis);
        }
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.translate.set((float) x, (float) y, (float) z);
        this.postCallback();
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.scale.set((float) x, (float) y, (float) z);
        this.postCallback();
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.rotate.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), MathUtils.toRad((float) z));
        this.postCallback();
    }

    @Override
    public void setR2(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.rotate2.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), MathUtils.toRad((float) z));
        this.postCallback();
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.editing)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ENTER))
            {
                this.acceptChanges();

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.rejectChanges();

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.editing && this.checker.isTime())
        {
            /* UIContext.mouseX can't be used because when cursor is outside of window
             * its position stops being updated. That's why it has to be queried manually
             * through GLFW...
             *
             * It gets updated outside the window only when one of mouse buttons is
             * being held! */
            GLFW.glfwGetCursorPos(Window.getWindow(), CURSOR_X, CURSOR_Y);

            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getWidth();

            double rawX = CURSOR_X[0];
            double fx = Math.ceil(w / (double) context.menu.width);
            int border = 5;
            int borderPadding = border + 1;

            if (rawX <= border)
            {
                Window.moveCursor(w - borderPadding, (int) mc.mouse.getY());

                this.lastX = context.menu.width - (int) (borderPadding / fx);
                this.checker.mark();

                if (this.drag != null) this.beginRayDrag(this.lastX, context.mouseY);
            }
            else if (rawX >= w - border)
            {
                Window.moveCursor(borderPadding, (int) mc.mouse.getY());

                this.lastX = (int) (borderPadding / fx);
                this.checker.mark();

                if (this.drag != null) this.beginRayDrag(this.lastX, context.mouseY);
            }
            else if (this.drag != null)
            {
                this.applyRayDrag(context.mouseX, context.mouseY);
            }
            else
            {
                int dx = context.mouseX - this.lastX;
                Vector3f vector = this.getValue();
                boolean all = this.mode == 1 && Window.isCtrlPressed();
                UITrackpad reference = this.mode == 0 ? this.tx : (this.mode == 1 ? this.sx : this.rx);
                float factor = (float) reference.getValueModifier();

                if (this.local && this.mode == 0)
                {
                    Vector3f vector3f = this.calculateLocalVector(factor * dx, this.axis);

                    if (this.axis2 != null)
                    {
                        vector3f.add(this.calculateLocalVector(factor * dx, this.axis2));
                    }

                    this.setT(null, vector.x + vector3f.x, vector.y + vector3f.y, vector.z + vector3f.z);
                }
                else
                {
                    Vector3f vector3f = new Vector3f(vector);

                    if (this.mode == 2)
                    {
                        vector3f.mul(180F / MathUtils.PI);
                    }

                    if (this.axis == Axis.X || all) vector3f.x += factor * dx;
                    if (this.axis == Axis.Y || all) vector3f.y += factor * dx;
                    if (this.axis == Axis.Z || all) vector3f.z += factor * dx;
                    if (!all && this.axis2 == Axis.X) vector3f.x += factor * dx;
                    if (!all && this.axis2 == Axis.Y) vector3f.y += factor * dx;
                    if (!all && this.axis2 == Axis.Z) vector3f.z += factor * dx;

                    if (this.mode == 0) this.setT(null, vector3f.x, vector3f.y, vector3f.z);
                    if (this.mode == 1) this.setS(null, vector3f.x, vector3f.y, vector3f.z);
                    if (this.mode == 2)
                    {
                        if (this.local && BBSSettings.gizmos.get()) this.setR2(null, vector3f.x, vector3f.y, vector3f.z);
                        else this.setR(null, vector3f.x, vector3f.y, vector3f.z);
                    }
                }
            }

            if (rawX < border || rawX >= w - border)
            {
                // Cursor wrapped, lastX is already updated
            }
            else
            {
                this.setTransform(this.transform);
                this.lastX = context.mouseX;
            }
        }

        super.render(context);

        if (this.editing)
        {
            String label = UIKeys.TRANSFORMS_EDITING.get();
            FontRenderer font = context.batcher.getFont();
            int x = this.area.mx(font.getWidth(label));
            int y = this.area.my(font.getHeight());

            context.batcher.textCard(label, x, y, Colors.WHITE, BBSSettings.primaryColor(Colors.A50));

            if (this.axis != null)
            {
                Vector3f v = this.getValue();
                float val = this.axis == Axis.X ? v.x : (this.axis == Axis.Y ? v.y : v.z);
                
                if (this.mode == 2)
                {
                    val = MathUtils.toDeg(val);
                }
                
                String valueLabel = String.format(java.util.Locale.US, "%.2f", val);
                
                if (this.axis2 != null)
                {
                    float val2 = this.axis2 == Axis.X ? v.x : (this.axis2 == Axis.Y ? v.y : v.z);
                    
                    if (this.mode == 2)
                    {
                        val2 = MathUtils.toDeg(val2);
                    }
                    
                    valueLabel += ", " + String.format(java.util.Locale.US, "%.2f", val2);
                }

                context.batcher.textCard(valueLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
            }
        }
    }

    public static class UITransformHandler extends UIElement
    {
        private UIPropTransform transform;

        public UITransformHandler(UIPropTransform transform)
        {
            this.transform = transform;
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (this.transform.editing)
            {
                if (context.mouseButton == 0)
                {
                    this.transform.acceptChanges();

                    return true;
                }
                else if (context.mouseButton == 1)
                {
                    this.transform.rejectChanges();

                    return true;
                }
            }
            
            return super.subMouseClicked(context);
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            UITrackpad.updateAmplifier(context);

            return true;
        }
    }
}
