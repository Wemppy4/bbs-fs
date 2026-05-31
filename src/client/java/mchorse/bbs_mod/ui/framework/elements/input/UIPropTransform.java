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
import org.joml.Vector2f;
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
    private int lastY;
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
    /** View axis expressed in the bone's parent frame, captured at view-rotate start. */
    private final Vector3f viewLocalAxis = new Vector3f();
    /** Signed projection of {@code (startHit - origin)} onto {@link #dragAxisDir}. Used for ratio-based scale. */
    private float dragStartScaleProj;
    /** Original unit ring direction (perpendicular to rotation axis) captured at the very start of the drag. */
    private final Vector3f initialDragRingVec = new Vector3f();
    private float accumulatedRotateDeg;
    /** Projected gizmo origin in viewport pixels, captured at ring-drag start. */
    private final Vector2f dragScreenCenter = new Vector2f();
    /** Previous cursor angle (radians) around {@link #dragScreenCenter}, unwrapped each frame. */
    private float dragLastScreenAngle;
    /** Maps screen-space angular motion to a rotation about {@link #dragAxisDir} (+1 or -1). */
    private float dragRotateSign = 1F;
    /** Screen right/up axes in the bone's parent frame, captured at trackball-drag start. */
    private final Vector3f trackballRightLocal = new Vector3f();
    private final Vector3f trackballUpLocal = new Vector3f();
    /** Previous cursor position, for the trackball's frame-to-frame mouse delta. */
    private int trackballLastX;
    private int trackballLastY;
    /** Whether {@link #dragStartRotateDeg} should be written back to {@code rotate2} instead of {@code rotate}. */
    private boolean dragRotateGizmoSpace;
    private boolean dragHasStart;
    private RotateKind rotateKind = RotateKind.AXIS;
    private boolean hotkeyMode;
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
        this.keys().register(Keys.TRANSFORMATIONS_COMBINED, () -> Gizmo.INSTANCE.toggleCombined()).strict().category(category);
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

    public boolean isEditing()
    {
        return this.editing;
    }

    public Axis getAxis()
    {
        return this.axis;
    }

    public int getMode()
    {
        return this.mode;
    }

    public boolean isTrackball()
    {
        return this.rotateKind == RotateKind.TRACKBALL;
    }

    public boolean isViewRotate()
    {
        return this.rotateKind == RotateKind.VIEW;
    }

    public Vector3f getInitialDragRingVec()
    {
        return this.initialDragRingVec;
    }

    public float getAccumulatedRotateDeg()
    {
        return this.accumulatedRotateDeg;
    }

    public GizmoDrag getDrag()
    {
        return this.drag;
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
        GizmoDrag drag = this.getHotkeyDrag();

        if (mode == Gizmo.Mode.ROTATE.ordinal() && BBSSettings.transformHotkeys3dRay.get() && drag != null)
        {
            boolean rotating = this.editing && this.mode == mode;
            boolean sphere = BBSSettings.rotate3dSphere.get();

            /* R walks the 3D rotation handles before falling through to the
             * per-axis rings: screen-plane ring -> trackball sphere -> X/Y/Z,
             * each skipped when its handle is turned off. The screen-plane ring
             * is always available (it is excluded from "Hide rotation rings"). */
            boolean wantView = !rotating;
            boolean wantSphere = sphere && rotating && this.rotateKind == RotateKind.VIEW;

            if (wantView)
            {
                this.enableViewRotate(drag, true);

                return;
            }

            if (wantSphere)
            {
                this.enableTrackball(drag, true);

                return;
            }
        }

        this.enableMode(mode, null, null, drag);
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
        /* Only the keyboard path (axis == null) flips the gizmo's display mode,
         * and never while combined is on screen: there G/S/R run their operation
         * and leave every handle visible. Grabbing a handle with the mouse
         * (axis != null) likewise must not switch the displayed mode. */
        if (axis == null && Gizmo.INSTANCE.getMode() != Gizmo.Mode.COMBINED && Gizmo.INSTANCE.setMode(Gizmo.Mode.values()[mode]))
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

            this.axis = values[MathUtils.cycler(this.axis != null ? this.axis.ordinal() + 1 : 0, 0, values.length - 1)];
            this.axis2 = null;
            this.rotateKind = RotateKind.AXIS;
            this.drag = drag;

            this.restore(true);
        }
        else
        {
            this.axis = axis == null ? Axis.X : axis;
            this.axis2 = axis2;
            this.rotateKind = RotateKind.AXIS;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
            this.drag = drag;
        }

        this.editing = true;
        this.mode = mode;
        this.hotkeyMode = axis == null;

        this.cache.copy(this.transform);
        Gizmo.INSTANCE.trackTransform(this);

        if (this.useRayDrag())
        {
            this.beginRayDrag(context.mouseX, context.mouseY);
        }

        if (!this.handler.hasParent())
        {
            context.menu.overlay.add(this.handler);
        }
    }

    public void enableTrackball(GizmoDrag drag)
    {
        this.enableTrackball(drag, false);
    }

    public void enableTrackball(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && Gizmo.INSTANCE.getMode() != Gizmo.Mode.COMBINED && Gizmo.INSTANCE.setMode(Gizmo.Mode.ROTATE))
        {
            return;
        }

        UIContext context = this.getContext();
        if (context == null || this.transform == null)
        {
            return;
        }

        if (this.editing)
        {
            this.restore(true);
        }

        this.editing = true;
        this.rotateKind = RotateKind.TRACKBALL;
        this.mode = 2; // ROTATE
        this.axis = null;
        this.axis2 = null;
        this.hotkeyMode = hotkeyMode;
        this.drag = drag;
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        this.cache.copy(this.transform);
        Gizmo.INSTANCE.trackTransform(this);

        this.beginRayRotateTrackball(context.mouseX, context.mouseY);

        if (!this.handler.hasParent())
        {
            context.menu.overlay.add(this.handler);
        }
    }

    public void enableViewRotate(GizmoDrag drag)
    {
        this.enableViewRotate(drag, false);
    }

    public void enableViewRotate(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && Gizmo.INSTANCE.getMode() != Gizmo.Mode.COMBINED && Gizmo.INSTANCE.setMode(Gizmo.Mode.ROTATE))
        {
            return;
        }

        UIContext context = this.getContext();
        if (context == null || this.transform == null)
        {
            return;
        }

        if (this.editing)
        {
            this.restore(true);
        }

        this.editing = true;
        this.rotateKind = RotateKind.VIEW;
        this.mode = 2; // ROTATE
        this.axis = null;
        this.axis2 = null;
        this.hotkeyMode = hotkeyMode;
        this.drag = drag;
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        this.cache.copy(this.transform);
        Gizmo.INSTANCE.trackTransform(this);

        this.beginRayRotateView(context.mouseX, context.mouseY);

        if (!this.handler.hasParent())
        {
            context.menu.overlay.add(this.handler);
        }
    }

    private GizmoDrag getHotkeyDrag()
    {
        return this.hotkeyDragSupplier == null ? null : this.hotkeyDragSupplier.get();
    }

    private boolean useRayDrag()
    {
        if (this.hotkeyMode && !BBSSettings.transformHotkeys3dRay.get())
        {
            return false;
        }

        return this.drag != null && (this.mode != 2 || this.axis2 == null || this.rotateKind == RotateKind.TRACKBALL);
    }

    private void setEditingAxis(Axis axis)
    {
        this.rotateKind = RotateKind.AXIS;

        if (Window.isShiftPressed())
        {
            switch (axis)
            {
                case X:
                    this.axis = Axis.Y;
                    this.axis2 = Axis.Z;
                    break;
                case Y:
                    this.axis = Axis.Z;
                    this.axis2 = Axis.X;
                    break;
                case Z:
                    this.axis = Axis.X;
                    this.axis2 = Axis.Y;
                    break;
            }
        }
        else
        {
            this.axis = axis;
            this.axis2 = null;
        }

        if (!this.editing)
        {
            return;
        }

        this.restore(true);

        if (this.useRayDrag())
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

        if (this.mode == 2)
        {
            if (this.rotateKind == RotateKind.TRACKBALL) this.applyRayRotateTrackball(mouseX, mouseY);
            else if (this.rotateKind == RotateKind.VIEW) this.applyRayRotateView(mouseX, mouseY);
            else this.applyScreenRotate(mouseX, mouseY);

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
     * Rotate around the active axis by the angle the cursor sweeps around the
     * projected gizmo center on screen. Unlike the previous ring-plane
     * projection this stays accurate when the ring faces the camera (where the
     * plane hit degenerates), and the per-frame deltas are unwrapped and
     * accumulated without limit so the user can wind several full turns in
     * either direction. When {@code local && gizmos enabled} the change goes
     * into {@code rotate2}, otherwise into {@code rotate}.
     */
    private void applyScreenRotate(int mouseX, int mouseY)
    {
        float current = this.screenAngle(mouseX, mouseY);
        float delta = current - this.dragLastScreenAngle;

        /* Unwrap across the ±180° seam so a small motion there registers as a
         * small step instead of a near-full turn the other way. */
        if (delta > MathUtils.PI) delta -= MathUtils.PI * 2F;
        else if (delta < -MathUtils.PI) delta += MathUtils.PI * 2F;

        this.dragLastScreenAngle = current;

        float angleDeg = MathUtils.toDeg(delta) * this.dragRotateSign;

        this.accumulatedRotateDeg += angleDeg;

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

        this.dragStartRotateDeg.set(rx, ry, rz);
    }

    /**
     * Cursor angle (radians) around the projected gizmo center, in the viewport
     * pixel convention (Y down) so it lines up with {@link GizmoDrag#projectToScreen}.
     */
    private float screenAngle(int mouseX, int mouseY)
    {
        return (float) Math.atan2(mouseY - this.dragScreenCenter.y, mouseX - this.dragScreenCenter.x);
    }

    /**
     * World-space ring direction (perpendicular to the rotation axis) at the
     * point where the drag started. Only feeds the rotation pie preview, so a
     * perpendicular fallback is acceptable when the cursor ray runs parallel to
     * the ring plane.
     */
    private Vector3f computeStartRingVec(int mouseX, int mouseY, Vector3f axisDir)
    {
        Vector3f ring = new Vector3f();
        Vector3d hit = new Vector3d();

        if (this.drag.intersectPlane(mouseX, mouseY, axisDir, hit))
        {
            ring.set(
                (float) (hit.x - this.drag.gizmoOrigin.x),
                (float) (hit.y - this.drag.gizmoOrigin.y),
                (float) (hit.z - this.drag.gizmoOrigin.z)
            );

            float along = ring.dot(axisDir);

            ring.sub(new Vector3f(axisDir).mul(along));
        }

        if (ring.lengthSquared() < 1.0E-8F)
        {
            Vector3f fallback = Math.abs(axisDir.y) < 0.9F ? new Vector3f(0F, 1F, 0F) : new Vector3f(1F, 0F, 0F);

            axisDir.cross(fallback, ring);
        }

        return ring.normalize();
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
                if (this.rotateKind == RotateKind.TRACKBALL)
                {
                    this.beginRayRotateTrackball(mouseX, mouseY);
                }
                else if (this.rotateKind == RotateKind.VIEW)
                {
                    this.beginRayRotateView(mouseX, mouseY);
                }
                else
                {
                    this.beginRayRotate(mouseX, mouseY);
                }
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
     * Anchor a trackball drag. The sphere is no longer projected onto &mdash;
     * the turn is driven purely by cursor motion (Blender-style), so all we
     * capture here is the screen's right/up axes mapped once into the bone's
     * parent frame (constant for the drag) and the starting cursor position.
     */
    private void beginRayRotateTrackball(int mouseX, int mouseY)
    {
        this.dragRotateGizmoSpace = this.local && BBSSettings.gizmos.get();

        Vector3f source = this.dragRotateGizmoSpace ? this.transform.rotate2 : this.transform.rotate;
        Matrix3f parentInverse = this.computeParentInverse(source);

        if (parentInverse == null)
        {
            this.dragHasStart = false;

            return;
        }

        Matrix3f invView = this.drag.view.get3x3(new Matrix3f()).invert();

        parentInverse.transform(invView.getColumn(0, new Vector3f()).normalize(), this.trackballRightLocal);
        parentInverse.transform(invView.getColumn(1, new Vector3f()).normalize(), this.trackballUpLocal);

        if (this.trackballRightLocal.lengthSquared() < 1.0E-8F || this.trackballUpLocal.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        this.trackballRightLocal.normalize();
        this.trackballUpLocal.normalize();
        this.trackballLastX = mouseX;
        this.trackballLastY = mouseY;
        this.dragHasStart = true;
    }

    /**
     * Roll the bone by the cursor's frame-to-frame motion: horizontal travel
     * turns it about the screen's vertical axis, vertical travel about the
     * screen's horizontal axis, like spinning a ball under the fingertip. The
     * two turns are composed and premultiplied onto the live rotation matrix
     * (then read back as Euler), so it stays smooth through gimbal lock and a
     * circular drag naturally accumulates roll &mdash; the trackball feel.
     */
    private void applyRayRotateTrackball(int mouseX, int mouseY)
    {
        int dx = mouseX - this.trackballLastX;
        int dy = mouseY - this.trackballLastY;

        this.trackballLastX = mouseX;
        this.trackballLastY = mouseY;

        if (dx == 0 && dy == 0)
        {
            return;
        }

        Vector3f source = this.dragRotateGizmoSpace ? this.transform.rotate2 : this.transform.rotate;

        Matrix3f rotation = new Matrix3f()
            .rotationZ(source.z)
            .rotateY(source.y)
            .rotateX(source.x);

        float sensitivity = BBSSettings.trackballSensitivity.get();
        float yaw = MathUtils.toRad(dx * sensitivity);
        float pitch = MathUtils.toRad(dy * sensitivity);

        Vector3f euler = new Matrix3f()
            .rotation(yaw, this.trackballUpLocal)
            .rotate(pitch, this.trackballRightLocal.x, this.trackballRightLocal.y, this.trackballRightLocal.z)
            .mul(rotation)
            .getEulerAnglesZYX(new Vector3f());

        float rx = MathUtils.toDeg(euler.x);
        float ry = MathUtils.toDeg(euler.y);
        float rz = MathUtils.toDeg(euler.z);

        if (this.dragRotateGizmoSpace) this.setR2(null, rx, ry, rz);
        else this.setR(null, rx, ry, rz);
    }

    /**
     * Anchor a view-plane rotation: the axis is fixed to the gizmo-to-camera
     * direction, and the angle comes from sweeping the cursor around the
     * projected gizmo center, exactly like the per-axis ring. Unlike a single
     * ring, the resulting world-space turn is spread across all three rotate
     * components (see {@link #applyRayRotateView}), so it stays "common" to the
     * three axes.
     */
    private void beginRayRotateView(int mouseX, int mouseY)
    {
        Vector3f viewAxis = new Vector3f(
            (float) (this.drag.cameraOrigin.x - this.drag.gizmoOrigin.x),
            (float) (this.drag.cameraOrigin.y - this.drag.gizmoOrigin.y),
            (float) (this.drag.cameraOrigin.z - this.drag.gizmoOrigin.z)
        );

        if (viewAxis.lengthSquared() < 1.0E-8F || !this.drag.projectToScreen(this.drag.gizmoOrigin, this.dragScreenCenter))
        {
            this.dragHasStart = false;

            return;
        }

        this.dragAxisDir.set(viewAxis.normalize());
        this.dragLastScreenAngle = this.screenAngle(mouseX, mouseY);

        /* The axis points at the camera (out of the screen), so an increasing
         * screen angle (clockwise, Y down) is a negative turn about it &mdash;
         * same convention {@link #beginRayRotate} derives for any axis. */
        this.dragRotateSign = -1F;
        this.accumulatedRotateDeg = 0;

        this.dragRotateGizmoSpace = this.local && BBSSettings.gizmos.get();

        /* Express the view axis once in the bone's parent frame; it stays
         * constant for the whole drag, while applyRayRotateView premultiplies
         * the live rotation by a turn about it. */
        Vector3f source = this.dragRotateGizmoSpace ? this.transform.rotate2 : this.transform.rotate;
        Matrix3f parentInverse = this.computeParentInverse(source);

        if (parentInverse == null)
        {
            this.dragHasStart = false;

            return;
        }

        parentInverse.transform(this.dragAxisDir, this.viewLocalAxis);

        if (this.viewLocalAxis.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        this.viewLocalAxis.normalize();
        this.dragHasStart = true;
    }

    /**
     * World-direction &rarr; bone-parent-frame map captured at drag start:
     * {@code parent^-1 = startEulerAxes * rotateAxes^-1}. {@code rotateAxes}
     * already folds in the parent and any model flips, so this recovers the
     * pure parent rotation; it is constant for the whole drag since the parent
     * doesn't move. Returns {@code null} when {@code rotateAxes} is degenerate.
     */
    private Matrix3f computeParentInverse(Vector3f sourceRadians)
    {
        Matrix3f rotateAxesInverse = new Matrix3f(this.drag.rotateAxes);

        if (Math.abs(rotateAxesInverse.determinant()) < 1.0E-4F)
        {
            return null;
        }

        return this.eulerAxes(sourceRadians).mul(rotateAxesInverse.invert());
    }

    /**
     * Columns of the returned matrix are the (parent-frame) axes that
     * {@code rotate.x}, {@code rotate.y} and {@code rotate.z} rotate around for
     * the renderer's {@code Rz * Ry * Rx} order. They are orthonormal at rest
     * but skew as the bone turns, which is exactly why the decomposition has to
     * be re-evaluated against the live pose rather than a frozen snapshot.
     */
    private Matrix3f eulerAxes(Vector3f rotateRadians)
    {
        Matrix3f axes = new Matrix3f();

        axes.setColumn(0, new Matrix3f().rotationZ(rotateRadians.z).rotateY(rotateRadians.y).transform(new Vector3f(1F, 0F, 0F)));
        axes.setColumn(1, new Matrix3f().rotationZ(rotateRadians.z).transform(new Vector3f(0F, 1F, 0F)));
        axes.setColumn(2, new Vector3f(0F, 0F, 1F));

        return axes;
    }

    /**
     * Turn the edited rotation by the angle swept around the screen center,
     * about the view axis. Rather than solving per-frame Euler deltas (which the
     * matrix inverse makes unstable near gimbal lock &mdash; a full 360 sweep
     * passes through it and twitches), it premultiplies the live rotation matrix
     * by the spin and reads the Euler angles back out. The orientation stays
     * continuous through gimbal; only its Euler representation jumps, which is
     * invisible. The spin axis is the constant parent-frame {@link #viewLocalAxis}.
     */
    private void applyRayRotateView(int mouseX, int mouseY)
    {
        float current = this.screenAngle(mouseX, mouseY);
        float delta = current - this.dragLastScreenAngle;

        if (delta > MathUtils.PI) delta -= MathUtils.PI * 2F;
        else if (delta < -MathUtils.PI) delta += MathUtils.PI * 2F;

        this.dragLastScreenAngle = current;

        float angle = delta * this.dragRotateSign;

        if (angle == 0F)
        {
            return;
        }

        this.accumulatedRotateDeg += MathUtils.toDeg(angle);

        Vector3f source = this.dragRotateGizmoSpace ? this.transform.rotate2 : this.transform.rotate;

        Matrix3f rotation = new Matrix3f()
            .rotationZ(source.z)
            .rotateY(source.y)
            .rotateX(source.x);

        Vector3f euler = new Matrix3f()
            .rotation(angle, this.viewLocalAxis)
            .mul(rotation)
            .getEulerAnglesZYX(new Vector3f());

        float rx = MathUtils.toDeg(euler.x);
        float ry = MathUtils.toDeg(euler.y);
        float rz = MathUtils.toDeg(euler.z);

        if (this.dragRotateGizmoSpace) this.setR2(null, rx, ry, rz);
        else this.setR(null, rx, ry, rz);
    }

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

        /* Screen-space ring rotation pivots around the gizmo origin projected to
         * the viewport. If the origin can't be projected (behind the camera)
         * there's nothing sensible to orbit around, so bail. */
        if (!this.drag.projectToScreen(this.drag.gizmoOrigin, this.dragScreenCenter))
        {
            this.dragHasStart = false;

            return;
        }

        this.dragLastScreenAngle = this.screenAngle(mouseX, mouseY);

        /* A positive rotation about +axis shows up as an increasing screen angle
         * (atan2 with Y pointing down) when the axis points away from the camera
         * (into the screen), and a decreasing one when it points back at it. */
        Vector3f intoScreen = new Vector3f(
            (float) (this.drag.gizmoOrigin.x - this.drag.cameraOrigin.x),
            (float) (this.drag.gizmoOrigin.y - this.drag.cameraOrigin.y),
            (float) (this.drag.gizmoOrigin.z - this.drag.cameraOrigin.z)
        );

        this.dragRotateSign = Math.signum(axisDir.dot(intoScreen));

        if (this.dragRotateSign == 0F)
        {
            this.dragRotateSign = 1F;
        }

        this.initialDragRingVec.set(this.computeStartRingVec(mouseX, mouseY, axisDir));
        this.accumulatedRotateDeg = 0;

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
        this.hotkeyMode = false;
        this.rotateKind = RotateKind.AXIS;
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

    private boolean shouldSnapGizmoValues()
    {
        return this.editing && this.mode == 2 && this.rotateKind == RotateKind.AXIS && !Window.isAltPressed();
    }

    private double snapGizmoValue(double value)
    {
        if (!this.shouldSnapGizmoValues())
        {
            return value;
        }

        return value < 0D ? Math.ceil(value) : Math.floor(value);
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

        x = this.snapGizmoValue(x);
        y = this.snapGizmoValue(y);
        z = this.snapGizmoValue(z);

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

        x = this.snapGizmoValue(x);
        y = this.snapGizmoValue(y);
        z = this.snapGizmoValue(z);

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

                if (this.useRayDrag()) this.beginRayDrag(this.lastX, context.mouseY);
            }
            else if (rawX >= w - border)
            {
                Window.moveCursor(borderPadding, (int) mc.mouse.getY());

                this.lastX = (int) (borderPadding / fx);
                this.checker.mark();

                if (this.useRayDrag()) this.beginRayDrag(this.lastX, context.mouseY);
            }
            else if (this.useRayDrag())
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
                this.lastY = context.mouseY;
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

    /**
     * Which flavour of rotation a drag performs: around a single gizmo ring
     * ({@link #AXIS}), freely about the picked point on the sphere
     * ({@link #TRACKBALL}), or in the screen plane about the view axis
     * ({@link #VIEW}) &mdash; the ring shared by all three axes.
     */
    public enum RotateKind
    {
        AXIS, TRACKBALL, VIEW;
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
