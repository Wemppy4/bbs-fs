package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategyFactory;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformNumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIChoiceButton;
import mchorse.bbs_mod.ui.framework.elements.input.drag.FineCursor;
import mchorse.bbs_mod.ui.framework.elements.input.drag.HotkeyTarget;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * Transform editor that drives the gizmo and hotkey (G/S/R) edits. The
 * editor itself is a thin coordinator: it owns the edit session (what is
 * being edited, the start snapshot, accept/reject) while all per-gesture
 * state and math live in the active {@link DragStrategy}, created through
 * {@link DragStrategyFactory} when an edit starts and dropped when it ends.
 */
public class UIPropTransform extends UITransform
{
    private static final double[] CURSOR_X = new double[1];
    private static final double[] CURSOR_Y = new double[1];

    private static final Vector3f ZERO_RING_VEC = new Vector3f();

    private Transform transform;
    private Runnable preCallback;
    private Runnable postCallback;
    private Runnable endCallback;

    private boolean editing;
    private Axis axis = Axis.X;
    private Axis axis2;
    private Transform cache = new Transform();
    private Timer checker = new Timer(30);

    private boolean model;

    /** The reference frame the gizmo and constrained edits operate in. Replaces
     *  the old local/global boolean; {@code space == LOCAL} is the former {@code local}. */


    /** Dropdown trigger for {@link #space}; shows the active frame's icon and name. */
    /** The frame every edit is expressed in, and the dropdown that picks it. */
    private UIChoiceButton<TransformSpace> spacePicker;

    /* Quaternion rotation pads (w, x, y, z), shown in place of the euler x/y/z pads
     * while the edited bone is in QUATERNION mode. Editing any of them rebuilds a
     * normalised quaternion from all four and commits it through setRQuat. */
    private UITrackpad qw;
    private UITrackpad qx;
    private UITrackpad qy;
    private UITrackpad qz;

    /** Whether the rotate row currently shows the four quaternion pads (vs the three euler pads). */
    private boolean quatFields;

    /** Drag snapshot the active gesture works against (kept for the gizmo's pie preview). */
    private GizmoDrag drag;
    private boolean hotkeyMode;
    private Supplier<GizmoDrag> hotkeyDragSupplier;

    /** Whether the edited bone's rotation is owned by an enabled IK chain
     *  (wired by hosts that have an IK concept; see {@link #rotationConstrained}). */
    private Supplier<Boolean> rotationConstrainedSupplier;

    /** The live gesture; non-null exactly while {@link #editing}. */
    private DragStrategy strategy;
    private final DragContext bridge = new Bridge();

    private final TransformNumericInput numeric = new TransformNumericInput();

    /* Fine-drag (Shift) precision: a virtual cursor that lags the real one,
     * advancing at {@link DragStrategy#FINE_DRAG_FACTOR} speed while Shift is
     * held, so every ray gesture slows uniformly without per-mode code. The
     * lag is the accumulated offset between the two. */
    private final FineCursor fineCursor = new FineCursor();

    private UITransformHandler handler;

    public UIPropTransform()
    {
        this.handler = new UITransformHandler(this);

        this.buildQuaternionFields();

        this.context((menu) ->
        {
            /* Per-bone rotation mode (Blender's rotation_mode); the label names the
             * mode the action switches TO, so the current one is always readable. */
            if (this.transform != null)
            {
                boolean quat = this.transform.rotationMode == Transform.RotationMode.QUATERNION;

                menu.action(
                    Icons.CONVERT,
                    quat ? UIKeys.TRANSFORMS_CONTEXT_MODE_EULER : UIKeys.TRANSFORMS_CONTEXT_MODE_QUATERNION,
                    this::toggleRotationMode
                );
            }
        });

        /* The rotation-row icon toggles the bone's rotation storage (euler / quaternion);
         * the active state is drawn as a highlight in render(), like the other toggles.
         * It keeps the base's CONTROL_HEIGHT box, same as the equally clickable
         * uniform-scale icon next to it: an oversized box on this one alone made it
         * bulge out of the set and pushed its row taller than the others. */
        this.iconR.callback = (b) -> this.toggleRotationMode();
        this.iconR.highlight(() -> this.transform != null && this.transform.rotationMode == Transform.RotationMode.QUATERNION, Direction.LEFT);
        this.iconR.tooltip(UIKeys.TRANSFORMS_ROTATION_MODE_TOOLTIP);
        this.iconR.setEnabled(true);

        /* The space picker is a dropdown on its own row above T/S/R (it replaced the
         * old click-to-cycle on the translate-row icon, which is decorative again). */
        this.spacePicker = new UIChoiceButton<>(TransformSpace.DISPLAY_ORDER, (space) -> space.icon, (space) -> space.label)
            .unavailable((space) -> space.implemented, (space) -> UIKeys.TRANSFORMS_SPACE_WIP.format(space.label))
            .callback(TransformSpace::remember)
            .setValue(TransformSpace.load());
        this.spacePicker.tooltip(UIKeys.TRANSFORMS_SPACE_TOOLTIP);
        this.prepend(UI.labelRow(UIKeys.TRANSFORMS_SPACE_TITLE, this.spacePicker));
        /* Four uniform rows: the space picker above translate / scale / rotate.
         * (Was 3×CONTROL_HEIGHT + 20 — the 20 being the rotate row, which its
         * oversized toggle icon pushed past the others.) */
        this.h(4 * UIConstants.CONTROL_HEIGHT);

        /* Each finished value-field drag closes the current undo block, so dragging a
         * field several times in a row undoes one drag at a time (see endGesture). */
        for (UITrackpad field : new UITrackpad[]{this.tx, this.ty, this.tz, this.sx, this.sy, this.sz, this.rx, this.ry, this.rz, this.qw, this.qx, this.qy, this.qz})
        {
            field.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.endGesture());
        }

        /* The deferred uniform-scale row sync (see setTransform). Mouse events traverse
         * children by index, so restructuring the row here is safe, unlike mid-render. */
        for (UITrackpad field : new UITrackpad[]{this.sx, this.sy, this.sz})
        {
            field.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.syncUniformScaleRow());
        }

        this.noCulling();
    }

    /** Build the four quaternion pads mirrored on the rotate row in quaternion mode. */
    private void buildQuaternionFields()
    {
        IKey raw = IKey.constant("%s (%s)");

        this.qw = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qw.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, IKey.constant("W")));
        this.qw.textbox.setColor(Colors.LIGHTEST_GRAY);
        this.qx = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qx.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, UIKeys.GENERAL_X));
        this.qx.textbox.setColor(Colors.RED);
        this.qy = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qy.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, UIKeys.GENERAL_Y));
        this.qy.textbox.setColor(Colors.GREEN);
        this.qz = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qz.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, UIKeys.GENERAL_Z));
        this.qz.textbox.setColor(Colors.BLUE);
    }

    public UIPropTransform callbacks(Supplier<IValueNotifier> notifier)
    {
        return this.callbacks(
            () -> notifier.get().preNotify(),
            () -> notifier.get().postNotify(),
            () -> notifier.get().preNotify(IValueListener.FLAG_UNMERGEABLE)
        );
    }

    public UIPropTransform callbacks(Runnable pre, Runnable post)
    {
        return this.callbacks(pre, post, null);
    }

    public UIPropTransform callbacks(Runnable pre, Runnable post, Runnable end)
    {
        this.preCallback = pre;
        this.postCallback = post;
        this.endCallback = end;

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

    /**
     * Close the current undo block so the next transform gesture starts a fresh,
     * separately-undoable entry. Fired at each gesture boundary — a value-field drag
     * end and the gizmo commit — rather than per value change, so one continuous drag
     * still merges into a single undo while consecutive drags stay distinct.
     */
    public void endGesture()
    {
        if (this.endCallback != null) this.endCallback.run();
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

    /** Wire the IK-ownership probe for the edited bone's rotation (hosts with an IK concept). */
    public UIPropTransform rotationConstrained(Supplier<Boolean> supplier)
    {
        this.rotationConstrainedSupplier = supplier;

        return this;
    }

    /**
     * Whether the edited bone's rotation is owned by an enabled IK chain: the
     * render follows the solve there, so the rotation gestures refuse to start
     * and the gizmo dims its rings (the value pads still edit the FK channels —
     * the blend base and the pose IK falls back to).
     */
    public boolean isRotationConstrained()
    {
        return this.rotationConstrainedSupplier != null && Boolean.TRUE.equals(this.rotationConstrainedSupplier.get());
    }

    public boolean isLocal()
    {
        return this.spacePicker.getValue().isLocal();
    }

    /** The reference frame the gizmo and constrained edits operate in. */
    public TransformSpace getSpace()
    {
        return this.spacePicker.getValue();
    }

    @Override
    protected Transform getEditedTransform()
    {
        return this.transform;
    }

    public Axis getAxis2()
    {
        return this.axis2;
    }

    public boolean isScreenTranslate()
    {
        return this.strategy != null && this.strategy.isScreenTranslate();
    }

    /** Old-logic no-op: kept so hosts that gave the spaces bar a backdrop still compile. */
    public UIPropTransform barBackground()
    {
        return this;
    }

    protected boolean supportsMirror()
    {
        return false;
    }

    public boolean isMirrorEdit()
    {
        return BBSSettings.poseMirrorEdit.get();
    }

    public boolean isAlternateInvert()
    {
        return BBSSettings.poseAlternateInvert.get();
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
        return this.enableHotkeys(() -> true);
    }

    public UIPropTransform enableHotkeys(Supplier<Boolean> enabled)
    {
        IKey category = UIKeys.TRANSFORMS_KEYS_CATEGORY;
        Supplier<Boolean> active = () -> enabled.get() && this.editing;

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, () -> this.enableMode(TransformOp.TRANSLATE)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SCALE, () -> this.enableMode(TransformOp.SCALE)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATE, () -> this.enableMode(TransformOp.ROTATE)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_X, () -> this.setEditingAxis(Axis.X)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Y, () -> this.setEditingAxis(Axis.Y)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Z, () -> this.setEditingAxis(Axis.Z)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SPACE_MENU, this.spacePicker::open).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATION_MODE, this::toggleRotationMode).active(enabled).category(category);

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

    /** The active edit's operation, or {@code null} when nothing is being edited. */
    public TransformOp getOp()
    {
        return this.strategy == null ? null : this.strategy.op();
    }

    /**
     * The live gesture driving the edit, or {@code null}. Every (re)start —
     * including an axis switch mid-edit — builds a fresh instance, so the
     * gizmo uses its identity to scope per-gesture state (the ring freeze).
     */
    public DragStrategy getStrategy()
    {
        return this.strategy;
    }

    /** Whether the active rotation is one of the sphere's kinds (trackball or arcball). */
    public boolean isSphereRotate()
    {
        return this.strategy != null && this.strategy.isSphere();
    }

    public boolean isViewRotate()
    {
        return this.strategy != null && this.strategy.isView();
    }

    /** Whether the active scale drives all three axes off one lever (centre scale
     *  handle or an unconstrained S). Distinct from {@link #isUniformScale()}, which
     *  is the trackpad's scale-field linking. */
    public boolean isScaleAll()
    {
        return this.strategy != null && this.strategy.isScaleAll();
    }

    public Vector3f getInitialDragRingVec()
    {
        Vector3f vec = this.strategy == null ? null : this.strategy.initialRingVec();

        return vec == null ? ZERO_RING_VEC : vec;
    }

    public float getAccumulatedRotateDeg()
    {
        return this.strategy == null ? 0F : this.strategy.accumulatedRotateDeg();
    }

    /** Screen-space start edge of the view sweep pie (radians, Y-down convention). */
    public float getViewGrabScreenAngle()
    {
        return this.strategy == null ? 0F : this.strategy.viewGrabScreenAngle();
    }

    /** Signed screen-space span of the view sweep, in radians. */
    public float getViewScreenSweepRad()
    {
        return this.strategy == null ? 0F : this.strategy.viewScreenSweepRad();
    }

    /**
     * A short summary of what the active drag has changed so far, for the gizmo's
     * on-screen readout: degrees for a rotation (axis or view ring by swept angle,
     * the 3D sphere by net turn), the per-axis offset for a move, the per-axis
     * factor delta for a scale. Returns {@code null} when there is nothing to show.
     */
    public String getDragReadout()
    {
        if (!this.editing || this.transform == null || this.strategy == null)
        {
            return null;
        }

        return this.strategy.readout();
    }

    public GizmoDrag getDrag()
    {
        return this.drag;
    }

    public int getDebugLineStencilIndex()
    {
        if (!this.editing || this.isScreenTranslate())
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

    private boolean isScaleFieldDragging()
    {
        return this.sx.isDragging() || this.sy.isDragging() || this.sz.isDragging();
    }

    /**
     * Collapse the scale row when all three scale coordinates are equal, expand it when
     * they differ (the {@link BBSSettings#uniformScale} option). Compared against the
     * row's own state — not {@link #isUniformScale()}, which is the SPACE/RMB field
     * linking — so matching states are a no-op instead of a blind toggle.
     */
    private void syncUniformScaleRow()
    {
        if (this.transform == null || !BBSSettings.uniformScale.get())
        {
            return;
        }

        Vector3f scale = this.transform.scale;

        if ((scale.x == scale.y && scale.y == scale.z) != this.isScaleRowCollapsed())
        {
            this.toggleUniformScale();
        }
    }

    public void setTransform(Transform transform)
    {
        this.transform = transform;

        /* Match the rotate row to how the bone stores its rotation (three euler
         * pads or four quaternion pads) before filling the fields below. */
        this.syncRotationMode();

        if (transform == null)
        {
            this.disable();
            this.fillT(0, 0, 0);
            this.fillS(1, 1, 1);
            this.fillR(0, 0, 0);

            return;
        }

        /* The uniform-scale auto-sync restructures the scale row (removeAll/add), and a
         * scale trackpad applies its drag from inside render() (through the delta editor
         * this loops right back here): mutating the element tree mid-traversal throws
         * ConcurrentModificationException. So the sync is deferred past any live gesture —
         * a gizmo/hotkey edit (editing) or a scale-field drag — and runs when a transform
         * is loaded into the panel, plus once more when the gesture ends (disable() for
         * hotkey edits, the drag-end listeners in the constructor for field drags). */
        if (!this.editing && !this.isScaleFieldDragging())
        {
            this.syncUniformScaleRow();
        }

        this.fillT(transform.translate.x, transform.translate.y, transform.translate.z);
        this.fillS(transform.scale.x, transform.scale.y, transform.scale.z);

        if (transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.fillQ(transform.quat.x, transform.quat.y, transform.quat.z, transform.quat.w);

            /* Keep the (hidden) euler pads mirroring the quaternion's ZYX equivalent so
             * the euler-based readers — clipboard copy, the drag value card — stay correct. */
            Vector3f euler = Matrices.toEulerZYXRadians(transform.quat, new Vector3f());

            this.fillR(MathUtils.toDeg(euler.x), MathUtils.toDeg(euler.y), MathUtils.toDeg(euler.z));
        }
        else
        {
            this.fillR(MathUtils.toDeg(transform.rotate.x), MathUtils.toDeg(transform.rotate.y), MathUtils.toDeg(transform.rotate.z));
        }
    }

    /**
     * Show the rotate row in the mode the current transform stores its rotation in:
     * three euler-degree pads, or four raw quaternion pads (with the toggle icon lit).
     * Only rebuilds the row when the mode actually flips, so the per-frame
     * {@link #setTransform} stays cheap.
     */
    private void syncRotationMode()
    {
        boolean quat = this.transform != null && this.transform.rotationMode == Transform.RotationMode.QUATERNION;

        if (quat == this.quatFields)
        {
            return;
        }

        this.quatFields = quat;
        this.rotateRow.removeAll();

        if (quat)
        {
            this.rotateRow.add(this.iconR, this.qw, this.qx, this.qy, this.qz);
        }
        else
        {
            this.rotateRow.add(this.iconR, this.rx, this.ry, this.rz);
        }

        /* Re-lay the row's new children within the panel (same pattern as the
         * uniform-scale swap); only runs on an actual mode flip, not per frame. */
        UIElement parentContainer = this.getParentContainer();

        if (parentContainer != null)
        {
            parentContainer.resize();
        }
    }

    /** Fill the quaternion pads (raw x/y/z/w, as stored) without notifying the callback. */
    private void fillQ(float x, float y, float z, float w)
    {
        this.qx.setValue(x);
        this.qy.setValue(y);
        this.qz.setValue(z);
        this.qw.setValue(w);
    }

    /**
     * Commit the four quaternion pads as one rotation: rebuild the quaternion from
     * the fields, renormalise it (raw component edits drift off the unit sphere,
     * exactly like Blender's W/X/Y/Z fields), and route it through the normal
     * quaternion write so the delta editors still fan it across a selection.
     */
    private void setQuatFromFields()
    {
        if (this.transform == null)
        {
            return;
        }

        Quaternionf quat = new Quaternionf((float) this.qx.value, (float) this.qy.value, (float) this.qz.value, (float) this.qw.value);

        if (quat.lengthSquared() < 1.0E-8F)
        {
            /* All-zero is not a rotation; ignore until the user types something real. */
            return;
        }

        this.setRQuat(quat.normalize());
    }

    /**
     * Flip the edited bone between euler and quaternion rotation storage
     * (Blender's per-bone {@code rotation_mode}), converting its rotation data
     * once. Quaternion mode is gimbal-free; euler keeps &gt;360° spins and
     * per-component curves.
     */
    public void toggleRotationMode()
    {
        if (this.transform == null)
        {
            return;
        }

        boolean quaternion = this.transform.rotationMode != Transform.RotationMode.QUATERNION;

        this.preCallback();
        this.applyRotationMode(quaternion);
        this.postCallback();
        this.setTransform(this.transform);
        this.endGesture();
        UIUtils.playClick();
    }

    /**
     * Apply the storage-mode flip of {@link #toggleRotationMode}. The base
     * editor converts the single edited transform; the delta editors override
     * this to fan the flip across the whole selection (selected keyframes of a
     * limb track, selected bones with their mirror partners) — a bone's mode is
     * a property of the TRACK, and leaving unselected keyframes behind in euler
     * would quietly keep the track on mixed interpolation.
     */
    protected void applyRotationMode(boolean quaternion)
    {
        if (quaternion)
        {
            this.transform.setModeQuaternion();
        }
        else
        {
            this.transform.setModeEuler();
        }
    }

    /* Edit entry points. The mouse path (a gizmo handle pick) supplies the axes
     * directly; the keyboard path walks the user-configured hotkey orders. Both
     * funnel into startEdit, and both start their operation on the first press —
     * the gizmo shows every element at once, so there is no display mode for a
     * press to switch first. */

    public void enableMode(TransformOp op)
    {
        GizmoDrag drag = this.getHotkeyDrag();
        boolean ray = drag != null;

        /* G/S/R walk their handles in the user-configured order (the
         * *_hotkey_order settings), wrapping past the end back to the first
         * step. Steps whose handle is unavailable drop out: the ray-driven ones
         * without a rendered gizmo. Hiding an element does NOT drop its step —
         * the keyboard reaches handles the eye isn't shown, which is the only
         * thing keeping every operation reachable when the gizmo is stripped bare.
         * Scale's uniform three-axis lever is a step of that walk like any
         * other (Blender's plain S, first in the default order) — it used to
         * short-circuit the whole method, which left every repeat press of S
         * restarting it and the scale order setting driving nothing. */
        HotkeyTarget target = this.nextHotkeyTarget(op, ray);

        if (target == HotkeyTarget.VIEW)
        {
            this.enableViewRotate(drag, true);
        }
        else if (target == HotkeyTarget.SPHERE)
        {
            this.enableSphereRotate(drag, true);
        }
        else if (target == HotkeyTarget.SCREEN)
        {
            this.enableScreenTranslate(drag, true);
        }
        else if (target == HotkeyTarget.ALL)
        {
            this.enableUniformScale(drag, true);
        }
        else
        {
            this.enableHotkeyAxis(op, target.axis, drag);
        }
    }

    /** The walk step the active edit corresponds to ({@code null} when not editing this op). */
    private HotkeyTarget currentHotkeyTarget(TransformOp op)
    {
        if (!this.editing || this.getOp() != op)
        {
            return null;
        }

        if (this.isViewRotate()) return HotkeyTarget.VIEW;
        if (this.isSphereRotate()) return HotkeyTarget.SPHERE;
        if (this.isScreenTranslate()) return HotkeyTarget.SCREEN;
        /* Before the axis checks: the uniform lever parks on Axis.X, so reading
         * the axis alone would report it as the X step and the walk would skip
         * straight past X on the next press. */
        if (this.isScaleAll()) return HotkeyTarget.ALL;
        if (this.axis == Axis.Y) return HotkeyTarget.Y;
        if (this.axis == Axis.Z) return HotkeyTarget.Z;

        return HotkeyTarget.X;
    }

    private HotkeyTarget nextHotkeyTarget(TransformOp op, boolean ray)
    {
        return HotkeyTarget.next(op, ray, this.currentHotkeyTarget(op));
    }

    /**
     * Start (or switch to) a hotkey-driven operation along a specific axis.
     * Unlike the mouse path this keeps the hotkey semantics (numeric input,
     * accept/reject overlay); the axis comes from the configured hotkey order
     * rather than a fixed cycle.
     */
    private void enableHotkeyAxis(TransformOp op, Axis axis, GizmoDrag drag)
    {
        this.startEdit(op, axis, null, DragStrategyFactory.Variant.AXIS, drag, true);
    }

    public void enableMode(TransformOp op, Axis axis)
    {
        this.enableMode(op, axis, null, null);
    }

    public void enableMode(TransformOp op, Axis axis, Axis axis2)
    {
        this.enableMode(op, axis, axis2, null);
    }

    /**
     * Start an operation from a mouse handle pick: the axes come straight
     * from the picked handle, so this never cycles. The keyboard path goes
     * through {@link #enableMode(TransformOp)} and the configured hotkey
     * orders instead.
     */
    public void enableMode(TransformOp op, Axis axis, Axis axis2, GizmoDrag drag)
    {
        this.startEdit(op, axis == null ? Axis.X : axis, axis2, DragStrategyFactory.Variant.AXIS, drag, axis == null);
    }

    public void enableSphereRotate(GizmoDrag drag)
    {
        this.enableSphereRotate(drag, false);
    }

    /** Start whichever free rotation the sphere is configured to drive. */
    public void enableSphereRotate(GizmoDrag drag, boolean hotkeyMode)
    {
        if (BBSSettings.rotate3dSphereMode.get() == 1) this.enableArcball(drag, hotkeyMode);
        else this.enableTrackball(drag, hotkeyMode);
    }

    public void enableTrackball(GizmoDrag drag)
    {
        this.enableTrackball(drag, false);
    }

    public void enableTrackball(GizmoDrag drag, boolean hotkeyMode)
    {
        this.startEdit(TransformOp.ROTATE, null, null, DragStrategyFactory.Variant.TRACKBALL, drag, hotkeyMode);
    }

    public void enableArcball(GizmoDrag drag)
    {
        this.enableArcball(drag, false);
    }

    public void enableArcball(GizmoDrag drag, boolean hotkeyMode)
    {
        this.startEdit(TransformOp.ROTATE, null, null, DragStrategyFactory.Variant.ARCBALL, drag, hotkeyMode);
    }

    public void enableViewRotate(GizmoDrag drag)
    {
        this.enableViewRotate(drag, false);
    }

    public void enableViewRotate(GizmoDrag drag, boolean hotkeyMode)
    {
        this.startEdit(TransformOp.ROTATE, null, null, DragStrategyFactory.Variant.VIEW, drag, hotkeyMode);
    }

    /**
     * Start a uniform (three-axis) scale: one lever axis drives all three, the
     * same math Ctrl+axis-scale uses. Reached by a mouse pick on the centre cube
     * and as a step of the S-key walk alike.
     */
    public void enableUniformScale(GizmoDrag drag)
    {
        this.enableUniformScale(drag, false);
    }

    public void enableUniformScale(GizmoDrag drag, boolean hotkeyMode)
    {
        this.startEdit(TransformOp.SCALE, Axis.X, null, DragStrategyFactory.Variant.UNIFORM_SCALE, drag, hotkeyMode);
    }

    /**
     * Start a screen-space (view-plane) translate: the object moves along the
     * camera's right/up axes in the plane facing the camera. Reached by grabbing
     * the centre cube and as a step of the G-key walk alike.
     */
    public void enableScreenTranslate(GizmoDrag drag)
    {
        this.enableScreenTranslate(drag, false);
    }

    public void enableScreenTranslate(GizmoDrag drag, boolean hotkeyMode)
    {
        this.startEdit(TransformOp.TRANSLATE, Axis.X, Axis.Y, DragStrategyFactory.Variant.SCREEN, drag, hotkeyMode);
    }

    /**
     * The one edit-start ritual every entry point funnels into: close any
     * previous edit, snapshot the transform, build the strategy for the
     * request and anchor it at the cursor, then raise the accept/reject
     * overlay.
     */
    private void startEdit(TransformOp op, Axis axis, Axis axis2, DragStrategyFactory.Variant variant, GizmoDrag drag, boolean hotkeyMode)
    {
        UIContext context = this.getContext();

        if (context == null || this.transform == null)
        {
            return;
        }

        this.numeric.clear();

        if (this.editing)
        {
            this.restore();
        }
        else
        {
            /* Arm the physics rewind for this gesture. Only on a fresh one: re-entering while editing
             * (switching the op mid-drag) rewinds the transform to the SAME start snapshot, so the sim
             * the gesture must be able to return to is still the one captured back then. */
            ModelPhysicsRuntime.checkpoint();
        }

        this.editing = true;
        this.axis = axis;
        this.axis2 = axis2;
        this.hotkeyMode = hotkeyMode;
        this.drag = drag;

        /* Scope the IK solve dump to this gesture — the log then holds exactly
         * the drag being investigated (see ModelIKRuntime#logGesture). */
        ModelIKRuntime.logGesture(true);

        this.cache.copy(this.transform);
        Gizmo.INSTANCE.trackTransform(this);

        this.strategy = DragStrategyFactory.create(this.bridge, op, axis, axis2, variant);
        this.strategy.begin(context.mouseX, context.mouseY);

        if (!this.handler.hasParent())
        {
            context.menu.overlay.add(this.handler);
        }
    }

    private GizmoDrag getHotkeyDrag()
    {
        return this.hotkeyDragSupplier == null ? null : this.hotkeyDragSupplier.get();
    }

    /**
     * Constrain the live edit to an axis (or, with Shift, to the plane
     * perpendicular to it): rewind to the start values and rebuild the
     * gesture as a plain axis drag of the same operation.
     */
    private void setEditingAxis(Axis axis)
    {
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

        TransformOp op = this.getOp();

        this.restore();

        UIContext context = this.getContext();

        if (context != null && op != null)
        {
            this.strategy = DragStrategyFactory.create(this.bridge, op, this.axis, this.axis2, DragStrategyFactory.Variant.AXIS);
            this.strategy.begin(context.mouseX, context.mouseY);
        }

        /* Re-route an in-progress typed amount onto the freshly picked axis. */
        if (this.numeric.isActive())
        {
            this.applyNumericInput();
        }
    }

    /** Rewind every channel to the values captured when the edit began. */
    private void restore()
    {
        this.setT(null, this.cache.translate.x, this.cache.translate.y, this.cache.translate.z);
        this.setS(null, this.cache.scale.x, this.cache.scale.y, this.cache.scale.z);

        if (this.cache.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.setRQuat(new Quaternionf(this.cache.quat));
        }
        else
        {
            this.setR(null, MathUtils.toDeg(this.cache.rotate.x), MathUtils.toDeg(this.cache.rotate.y), MathUtils.toDeg(this.cache.rotate.z));
        }
    }

    private void disable()
    {
        ModelIKRuntime.logGesture(false);
        ModelPhysicsRuntime.dropCheckpoint();

        this.editing = false;
        this.axis2 = null;
        this.hotkeyMode = false;
        this.strategy = null;
        this.drag = null;
        this.fineCursor.forget();
        this.numeric.clear();
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
        this.endGesture();
    }

    public void rejectChanges()
    {
        if (this.transform == null)
        {
            this.disable();

            return;
        }

        /* Rewind BEFORE tearing down: restore() routes a pivot-session revert
         * through the session's per-bone snapshots, and disable() nulls that
         * session. Do it the other way round and the rewind falls back to the
         * per-channel path, which fans the primary's values onto the whole
         * selection — the bones come back crooked instead of where they were. */
        this.restore();

        /* The pose is back where it started, so the simulation it drove goes back too — otherwise the
         * chains stay where the drag flung them and lash home over a single tick. */
        ModelPhysicsRuntime.rewindToCheckpoint();

        this.disable();
        this.setTransform(this.transform);
    }

    /** Route a wheel event into the live gesture (depth move, sphere roll). */
    public boolean scrollDrag(UIContext context)
    {
        return this.editing && this.transform != null && this.strategy != null && this.strategy.scroll(context);
    }

    /* Numeric (keyboard) input for hotkey-driven transforms */

    /**
     * Numeric input only rides on the GSR keyboard operations ({@link #hotkeyMode}),
     * never on a mouse handle drag; the active gesture additionally has a say
     * (the screen-space grab spreads one drag across two camera axes, so a
     * single typed scalar is ambiguous there).
     */
    private boolean acceptsNumericInput()
    {
        return this.editing && this.hotkeyMode && this.transform != null
            && this.strategy != null && this.strategy.acceptsNumeric();
    }

    /**
     * Feed one key into the live numeric buffer: digits and the decimal point
     * extend it, {@code -} flips the sign, backspace trims it (and hands control
     * back to the cursor once everything is erased). Returns whether the key was
     * consumed as numeric input.
     */
    private boolean handleNumericInputKey(UIContext context)
    {
        if (!this.acceptsNumericInput())
        {
            return false;
        }

        KeyAction action = context.getKeyAction();

        if (action != KeyAction.PRESSED && action != KeyAction.REPEAT)
        {
            return false;
        }

        int key = context.getKeyCode();

        /* While typing on the sphere, X/Y aim the typed angle at the
         * horizontal (screen-up axis) or vertical (screen-right axis) turn.
         * Without typed digits they must fall through to the axis keybinds
         * and constrain to a ring — otherwise they read as dead keys. */
        if (this.numeric.isActive() && this.strategy.handleNumericAxisKey(key))
        {
            this.applyNumericInput();

            return true;
        }

        switch (this.numeric.feedKey(key))
        {
            case EMPTIED:
                this.stopNumericInput(context);

                return true;

            case CHANGED:
                this.applyNumericInput();

                return true;

            case CONSUMED:
                return true;

            default:
                return false;
        }
    }

    /**
     * Erasing the whole buffer cancels numeric mode: rewind to the operation's
     * start and re-anchor the cursor drag at the current pointer so mouse
     * control resumes without a jump.
     */
    private void stopNumericInput(UIContext context)
    {
        this.numeric.clear();
        this.restore();

        /* The cursor was free to roam while typing; re-anchor the precision
         * tracking here so the resumed drag doesn't inherit a stale lag. */
        this.fineCursor.reset(context.mouseX, context.mouseY);

        if (this.strategy != null)
        {
            this.strategy.begin(context.mouseX, context.mouseY);
        }

        this.setTransform(this.transform);
    }

    /** Recompute the transform from the start snapshot plus the typed amount. */
    private void applyNumericInput()
    {
        if (this.transform == null || this.strategy == null)
        {
            return;
        }

        this.strategy.applyNumeric(this.numeric.value());
        this.setTransform(this.transform);
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

        /* A quaternion-mode bone has no euler channel to write, so typed angles
         * fold straight into its quaternion (leaving it gimbal-free storage). */
        if (this.transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.transform.quat.set(Matrices.toQuaternionZYXDegrees((float) x, (float) y, (float) z));
        }
        else
        {
            this.transform.rotate.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), MathUtils.toRad((float) z));
        }

        this.postCallback();
    }

    /**
     * Store a full rotation as a quaternion (the gizmo drag's gimbal-free commit
     * path for a quaternion-mode bone). Overridden by the delta editors to fan a
     * quaternion delta across the selection.
     */
    @Override
    public void setRQuat(Quaternionf quat)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.quat.set(quat);
        this.transform.rotationMode = Transform.RotationMode.QUATERNION;
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
            else if (this.handleNumericInputKey(context))
            {
                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    /** Short label of what the active drag grabs: axis letters, the screen
     *  plane, the view ring, or one of the sphere's rotations. */
    private String editingTargetLabel()
    {
        String special = this.strategy == null ? null : this.strategy.editingTargetLabel();

        if (special != null)
        {
            return special;
        }

        if (this.getOp() == TransformOp.SCALE && (this.isScaleAll() || Window.isCtrlPressed()))
        {
            return "XYZ";
        }

        String label = this.axis == null ? "" : this.axis.name();

        if (this.axis2 != null)
        {
            label += this.axis2.name();
        }

        return label;
    }

    /** Axis letters tint to their gizmo colors; everything else stays white. */
    private int editingTargetColor()
    {
        boolean singleAxis = this.axis != null && this.axis2 == null
            && !this.isScreenTranslate()
            && !(this.getOp() == TransformOp.SCALE && (this.isScaleAll() || Window.isCtrlPressed()));

        if (!singleAxis)
        {
            return Colors.WHITE;
        }

        if (this.axis == Axis.X) return Colors.A100 | Colors.RED;
        if (this.axis == Axis.Y) return Colors.A100 | Colors.GREEN;

        return Colors.A100 | Colors.BLUE;
    }

    /** Space chip; scale ignores the space toggle, so it gets none. */
    private String editingSpaceLabel()
    {
        if (this.getOp() == TransformOp.SCALE)
        {
            return null;
        }

        return this.spacePicker.getValue().label.get();
    }

    /** The live vector of the edited channel, for the cursor's value card. */
    private Vector3f getValue()
    {
        if (this.transform == null)
        {
            return new Vector3f();
        }

        TransformOp op = this.getOp();

        if (op == TransformOp.SCALE)
        {
            return this.transform.scale;
        }
        else if (op == TransformOp.ROTATE)
        {
            /* A quaternion bone's channels are stale; show its live rotation. */
            return this.transform.rotationMode == Transform.RotationMode.QUATERNION
                ? this.transform.getEulerRotation(new Vector3f())
                : this.transform.rotate;
        }

        return this.transform.translate;
    }

    /**
     * Advance the live gesture: wrap the cursor at the window edges (re-anchoring
     * the strategy at the teleported position) and feed the strategy the cursor —
     * virtual (Shift-slowed) for ray gestures, raw for the additive fallback,
     * which damps Shift through its step factor instead.
     */
    private void updateDrag(UIContext context)
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

        this.fineCursor.update(context.mouseX, context.mouseY);

        if (rawX <= border || rawX >= w - border)
        {
            int wrapX;

            if (rawX <= border)
            {
                Window.moveCursor(w - borderPadding, (int) mc.mouse.getY());
                wrapX = context.menu.width - (int) (borderPadding / fx);
            }
            else
            {
                Window.moveCursor(borderPadding, (int) mc.mouse.getY());
                wrapX = (int) (borderPadding / fx);
            }

            this.checker.mark();

            /* The wrap re-anchors the drag at the teleported position, so the
             * virtual cursor resets there too — no lag carries across the seam. */
            this.fineCursor.reset(wrapX, context.mouseY);

            if (this.strategy != null)
            {
                this.strategy.begin(wrapX, context.mouseY);
            }

            return;
        }

        if (this.strategy != null)
        {
            if (this.strategy.usesFineCursor())
            {
                this.strategy.update(this.fineCursor.x(context.mouseX), this.fineCursor.y(context.mouseY));
            }
            else
            {
                this.strategy.update(context.mouseX, context.mouseY);
            }

            this.strategy.logDrag();
        }

        this.setTransform(this.transform);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.editing && !this.numeric.isActive() && this.checker.isTime())
        {
            this.updateDrag(context);
        }

        super.render(context);

        if (this.editing)
        {
            FontRenderer font = context.batcher.getFont();
            TransformOp editOp = this.getOp();
            String op = (editOp == TransformOp.TRANSLATE ? UIKeys.TRANSFORMS_TRANSLATE : editOp == TransformOp.SCALE ? UIKeys.TRANSFORMS_SCALE : UIKeys.TRANSFORMS_ROTATE).get();
            String target = this.editingTargetLabel();
            String space = this.editingSpaceLabel();

            /* Chip row: the operation on the primary color, then what is
             * grabbed (axis letters in their gizmo colors), then the editing
             * space. The 5s account for textCard's box overhang at the
             * default card offset. */
            int gap = 2;
            int rowWidth = font.getWidth(op) + 5 + gap + font.getWidth(target) + 5;

            if (space != null)
            {
                rowWidth += gap + font.getWidth(space) + 5;
            }

            int x = this.area.mx(rowWidth) + 3;
            int y = this.area.my(font.getHeight());

            context.batcher.textCard(op, x, y, Colors.WHITE, BBSSettings.primaryColor(Colors.A50));
            x += font.getWidth(op) + 5 + gap;
            context.batcher.textCard(target, x, y, this.editingTargetColor(), Colors.A50);

            if (space != null)
            {
                x += font.getWidth(target) + 5 + gap;
                context.batcher.textCard(space, x, y, Colors.LIGHTEST_GRAY, Colors.A50);
            }

            /* Label echoed both at the cursor and (when typing) under the info row. */
            String numericLabel = null;

            if (this.axis != null)
            {
                Vector3f v = this.getValue();
                float val = this.axis == Axis.X ? v.x : (this.axis == Axis.Y ? v.y : v.z);

                if (editOp == TransformOp.ROTATE)
                {
                    val = MathUtils.toDeg(val);
                }

                String valueLabel = String.format(java.util.Locale.US, "%.2f", val);

                if (this.axis2 != null)
                {
                    float val2 = this.axis2 == Axis.X ? v.x : (this.axis2 == Axis.Y ? v.y : v.z);

                    if (editOp == TransformOp.ROTATE)
                    {
                        val2 = MathUtils.toDeg(val2);
                    }

                    valueLabel += ", " + String.format(java.util.Locale.US, "%.2f", val2);
                }

                /* While typing, lead with the raw input so the user sees exactly
                 * what they've entered, with the resulting value in parentheses. */
                String cursorLabel = this.numeric.isActive()
                    ? this.numeric.display() + " (" + valueLabel + ")"
                    : valueLabel;

                if (this.numeric.isActive())
                {
                    numericLabel = cursorLabel;
                }

                context.batcher.textCard(cursorLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
            }
            else if (this.numeric.isActive())
            {
                /* The view ring and the sphere have no single axis component to
                 * echo, so show the typed angle, plus the aimed direction. */
                String prefix = this.strategy == null ? "" : this.strategy.numericPrefix();

                numericLabel = prefix + this.numeric.display() + "°";

                context.batcher.textCard(numericLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
            }

            /* Mirror the live numeric input on its own card right under the info row. */
            if (numericLabel != null)
            {
                int nx = this.area.mx(font.getWidth(numericLabel));
                int ny = y + font.getHeight() + 8;

                context.batcher.textCard(numericLabel, nx, ny, Colors.WHITE, BBSSettings.primaryColor(Colors.A50));
            }
        }
    }

    /**
     * Bridge the active {@link DragStrategy} works through: it exposes the
     * edit session's state and funnels every write back through the editor's
     * virtual {@code setT/setS/setR/setR2}, so the delta editors keep fanning
     * edits onto their selections.
     */
    private class Bridge implements DragContext
    {
        @Override
        public Transform transform()
        {
            return UIPropTransform.this.transform;
        }

        @Override
        public Transform cache()
        {
            return UIPropTransform.this.cache;
        }

        @Override
        public GizmoDrag drag()
        {
            return UIPropTransform.this.drag;
        }

        @Override
        public void setDrag(GizmoDrag drag)
        {
            UIPropTransform.this.drag = drag;
        }

        @Override
        public GizmoDrag freshHotkeyDrag()
        {
            return UIPropTransform.this.getHotkeyDrag();
        }

        @Override
        public boolean isLocal()
        {
            return UIPropTransform.this.isLocal();
        }

        @Override
        public TransformSpace space()
        {
            return UIPropTransform.this.spacePicker.getValue();
        }

        @Override
        public boolean isModel()
        {
            return UIPropTransform.this.model;
        }

        @Override
        public boolean rotationConstrained()
        {
            return UIPropTransform.this.isRotationConstrained();
        }

        /* Blender-style snapping: every gesture is free by default and snaps to
         * the configured step only while Ctrl is held. Typed numeric input is
         * exact already, so it never snaps. */
        @Override
        public boolean shouldSnap(TransformOp op)
        {
            return UIPropTransform.this.editing && UIPropTransform.this.getOp() == op
                && Window.isCtrlPressed() && !UIPropTransform.this.numeric.isActive();
        }

        @Override
        public float additiveFactor(TransformOp op)
        {
            UITrackpad reference = op == TransformOp.TRANSLATE ? UIPropTransform.this.tx : (op == TransformOp.SCALE ? UIPropTransform.this.sx : UIPropTransform.this.rx);

            return (float) reference.getValueModifier();
        }

        @Override
        public Vector3f localTranslateVector(double factor, Axis axis)
        {
            return UIPropTransform.this.calculateLocalVector(factor, axis);
        }

        @Override
        public float sphereWorldRadius()
        {
            return Gizmo.INSTANCE.getSphereWorldRadius();
        }

        @Override
        public void refreshFields()
        {
            UIPropTransform.this.setTransform(UIPropTransform.this.transform);
        }

        @Override
        public void writeTranslate(float x, float y, float z)
        {
            UIPropTransform.this.setT(null, x, y, z);
        }

        @Override
        public void writeScale(float x, float y, float z)
        {
            UIPropTransform.this.setS(null, x, y, z);
        }

        @Override
        public void writeRotateDeg(float xDeg, float yDeg, float zDeg)
        {
            UIPropTransform.this.setR(null, xDeg, yDeg, zDeg);
        }

        @Override
        public void writeRotationQuat(Quaternionf quat)
        {
            UIPropTransform.this.setRQuat(quat);
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
            /* While sphere-dragging the wheel rolls about the view axis; during a
             * screen-space grab it drives depth; otherwise it keeps adjusting
             * the drag sensitivity amplifier as before. */
            if (this.transform.scrollDrag(context))
            {
                return true;
            }

            UITrackpad.updateAmplifier(context);

            return true;
        }
    }
}
