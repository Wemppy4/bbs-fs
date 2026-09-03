package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIChoiceButton;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGesture;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGestureHud;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * Transform editor that drives the gizmo and hotkey (G/S/R) edits. The editor itself is a
 * plain field widget — the value rows, the quaternion pads, the space dropdown — plus the
 * write path ({@link #setT}/{@link #setS}/{@link #setR}/{@link #setRQuat}) that the delta
 * editors override to fan edits onto selections. The edit session itself — what operation
 * runs, the start snapshot, accept/reject, the cursor pumping — is its {@link TransformGesture},
 * which reaches back here through {@link TransformGesture.Host}.
 */
public class UIPropTransform extends UITransform implements TransformGesture.Host
{
    private Transform transform;
    private Runnable preCallback;
    private Runnable postCallback;
    private Runnable endCallback;

    /* What the field rows currently show. The editor is bound to its property and re-read on
     * every frame, so the common call to setTransform changes nothing — this is what lets it
     * cost nothing instead of reformatting thirteen text fields and re-deciding the uniform
     * scale row sixty times a second. Compared by value and not by reference: the transform is
     * edited in place, so holding on to it would only ever compare it against itself. */
    private final Transform filled = new Transform();
    private boolean hasFilled;

    private boolean model;

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

    private Supplier<GizmoDrag> hotkeyDragSupplier;

    /** Whether the edited bone's rotation is owned by an enabled IK chain
     *  (wired by hosts that have an IK concept; see {@link #rotationConstrained}). */
    private Supplier<Boolean> rotationConstrainedSupplier;

    /** The edit session this editor's transform is worked through. */
    private final TransformGesture gesture = new TransformGesture(this);

    public UIPropTransform()
    {
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
            .callback(this::pickSpace)
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
    @Override
    public void endGesture()
    {
        if (this.endCallback != null) this.endCallback.run();
    }

    public void setModel()
    {
        this.model = true;
    }

    /** Drop the scale row — for a target that has no scale to speak of, such as a group's rest in the model editor. */
    public UIPropTransform noScale()
    {
        this.scaleRow.setVisible(false);
        this.h(3 * UIConstants.CONTROL_HEIGHT);

        return this;
    }

    @Override
    public boolean isModel()
    {
        return this.model;
    }

    public UIPropTransform hotkeyDrag(Supplier<GizmoDrag> supplier)
    {
        this.hotkeyDragSupplier = supplier;

        return this;
    }

    @Override
    public GizmoDrag freshHotkeyDrag()
    {
        return this.hotkeyDragSupplier == null ? null : this.hotkeyDragSupplier.get();
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
    @Override
    public boolean isRotationConstrained()
    {
        return this.rotationConstrainedSupplier != null && Boolean.TRUE.equals(this.rotationConstrainedSupplier.get());
    }

    /**
     * Whether this editor's rotation channels are gimbal angles with no third axis to
     * spare, so rotation rings turn their own channel instead of composing a
     * gimbal-free delta. False for everything that edits a real {@link Transform};
     * overridden by the replay-root editor, whose rotation is Minecraft's yaw/pitch pair.
     */
    @Override
    public boolean isRotationChannelOnly()
    {
        return false;
    }

    /** A frame picked from the dropdown: remembered mod-wide, and — since the picker is
     *  reachable mid-gesture (Q) — it also ends whatever frame the axis walk had put the
     *  live edit in, which would otherwise keep overriding the hand-picked one. */
    private void pickSpace(TransformSpace space)
    {
        this.gesture.clearWalk();

        space.remember();
    }

    @Override
    public TransformSpace pickedSpace()
    {
        return this.spacePicker.getValue();
    }

    /** The frame the gizmo and constrained edits operate in — the session's one accessor,
     *  kept here because hosts place their gizmos by asking the editor. */
    public TransformSpace getSpace()
    {
        return this.gesture.space();
    }

    /** The edit session running this editor's gizmo and hotkey edits. */
    public TransformGesture getGesture()
    {
        return this.gesture;
    }

    @Override
    protected Transform getEditedTransform()
    {
        return this.transform;
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

    @Override
    public Vector3f localTranslateVector(double factor, Axis axis)
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

    @Override
    public float additiveFactor(TransformOp op)
    {
        UITrackpad reference = op == TransformOp.TRANSLATE ? this.tx : (op == TransformOp.SCALE ? this.sx : this.rx);

        return (float) reference.getValueModifier();
    }

    public UIPropTransform enableHotkeys()
    {
        return this.enableHotkeys(() -> true);
    }

    public UIPropTransform enableHotkeys(Supplier<Boolean> enabled)
    {
        IKey category = UIKeys.TRANSFORMS_KEYS_CATEGORY;
        Supplier<Boolean> active = () -> enabled.get() && this.gesture.isEditing();

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, () -> this.gesture.enableMode(TransformOp.TRANSLATE)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SCALE, () -> this.gesture.enableMode(TransformOp.SCALE)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATE, () -> this.gesture.enableMode(TransformOp.ROTATE)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_X, () -> this.gesture.setAxis(Axis.X)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Y, () -> this.gesture.setAxis(Axis.Y)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Z, () -> this.gesture.setAxis(Axis.Z)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SPACE_MENU, this.spacePicker::open).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATION_MODE, this::toggleRotationMode).active(enabled).category(category);

        return this;
    }

    @Override
    public Transform getTransform()
    {
        return this.transform;
    }

    public boolean isEditing()
    {
        return this.gesture.isEditing();
    }

    /**
     * A gesture owns the transform for as long as it runs — the gizmo drag, a G/S/R operation, a
     * typed amount — so a host bound to its property must not read it back underneath. The value
     * rows answer for themselves through the base's walk over the subtree.
     */
    @Override
    public boolean isUserEditing()
    {
        return this.gesture.isEditing() || super.isUserEditing();
    }

    public void refillTransform()
    {
        this.setTransform(this.getTransform());
    }

    @Override
    public void refreshFields()
    {
        this.setTransform(this.transform);
    }

    /** The session was torn down: the deferred uniform-scale row sync is owed a run, which
     *  the "nothing moved" shortcut of {@link #setTransform} would otherwise swallow — the
     *  drag filled the fields with these very values a frame ago. */
    @Override
    public void gestureStopped()
    {
        this.hasFilled = false;
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
            this.hasFilled = false;

            this.gesture.stopEditing();
            this.fillT(0, 0, 0);
            this.fillS(1, 1, 1);
            this.fillR(0, 0, 0);

            return;
        }

        /* Nothing moved since the last fill, so there is nothing to restate. Beyond the saved
         * work this is what keeps the per-frame read from undoing a deliberate click: expanding
         * an equal-scale row by hand would otherwise be collapsed again by the auto-sync below
         * on the very next frame. */
        if (this.hasFilled && this.filled.equals(transform))
        {
            return;
        }

        this.hasFilled = true;
        this.filled.copy(transform);

        /* The uniform-scale auto-sync restructures the scale row (removeAll/add), and a
         * scale trackpad applies its drag from inside render() (through the delta editor
         * this loops right back here): mutating the element tree mid-traversal throws
         * ConcurrentModificationException. So the sync is deferred past any live gesture —
         * a gizmo/hotkey edit or a scale-field drag — and runs when a transform is loaded
         * into the panel, plus once more when the gesture ends (gestureStopped for hotkey
         * edits, the drag-end listeners in the constructor for field drags). */
        if (!this.gesture.isEditing() && !this.isScaleFieldDragging())
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
        if (this.gesture.keyPressed(context))
        {
            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.gesture.pump(context);

        super.render(context);

        TransformGestureHud.render(context, this.gesture, this.area);
    }
}
