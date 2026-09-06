package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * One transform edit session: what operation runs, along which axes and in which frame, the
 * start snapshot to accept or reject against, the axis-key walk, typed numeric input and the
 * per-frame cursor pumping. The session used to live inside the transform editor widget; it is
 * its own object because everything that talks to it — the gizmo, its sweep pie, the viewport
 * interaction — asks gesture questions, never widget ones.
 *
 * <p>The split of responsibilities around it:
 * <ul>
 * <li>the per-gesture state and math of a single drag kind live in the active
 *     {@link DragStrategy}, created through {@link DragStrategyFactory} when an edit starts and
 *     dropped when it ends; the session is the strategy's {@link DragContext};</li>
 * <li>everything the session needs from the widget that owns it — the edited transform, the
 *     picked frame, the virtual channel writes that fan edits onto selections — comes through
 *     {@link Host}, which the widget implements;</li>
 * <li>the widget stays a plain field editor: rows of value pads that read and write the same
 *     transform the session edits.</li>
 * </ul>
 */
public class TransformGesture implements DragContext
{
    private static final double[] CURSOR_X = new double[1];
    private static final double[] CURSOR_Y = new double[1];

    private static final Vector3f ZERO_RING_VEC = new Vector3f();

    /**
     * Everything the session needs from the editor that owns it. The channel writes MUST stay
     * virtual calls on the editor — that is what routes an edit onto a whole selection in the
     * delta editors, and into keyframe channels for a replay's root.
     */
    public interface Host
    {
        /** The transform being edited (live values); {@code null} when nothing is loaded. */
        Transform getTransform();

        /** The UI context, for the cursor and the accept/reject overlay; {@code null} off screen. */
        UIContext getContext();

        /** Whether the editor renders itself this frame (a rendered editor pumps its own drag). */
        boolean isVisible();

        /** The frame chosen in the editor's space dropdown. */
        TransformSpace pickedSpace();

        /** Whether the edited transform belongs to a model bone (flips the local translate frame). */
        boolean isModel();

        /** Whether the edited bone's rotation is owned by an enabled IK chain. */
        boolean isRotationConstrained();

        /** Whether rotation channels are gimbal angles with no third axis to spare. */
        boolean isRotationChannelOnly();

        /** Per-pixel step of the additive (non-ray) drag for the given operation. */
        float additiveFactor(TransformOp op);

        /** {@code factor} along {@code axis} rotated into the transform's local frame. */
        Vector3f localTranslateVector(double factor, Axis axis);

        /** A freshly built drag from the editor's hotkey supplier, or {@code null}. */
        GizmoDrag freshHotkeyDrag();

        /** Re-read the transform into the editor's value fields (after an off-frame write). */
        void refreshFields();

        void setT(Axis axis, double x, double y, double z);

        void setS(Axis axis, double x, double y, double z);

        void setR(Axis axis, double x, double y, double z);

        void setRQuat(Quaternionf quat);

        /** Close the current undo block (fired when a gesture is accepted). */
        void endGesture();

        /** The session was torn down (accepted, rejected or dismissed) — drop cached reads. */
        void gestureStopped();

        /** Diagnostic only: which editor owns this gesture, for the drag log. */
        default String targetName()
        {
            return this.getClass().getSimpleName();
        }

        /** Prefix for the gizmo's readout card, naming the target when the editor draws no
         *  chips of its own (the replay root names its record here); {@code null} for none. */
        default String readoutPrefix()
        {
            return null;
        }
    }

    private final Host host;

    private boolean editing;
    private Axis axis = Axis.X;
    private Axis axis2;
    private final Transform cache = new Transform();
    private final Timer checker = new Timer(30);

    /** Drag snapshot the active gesture works against (kept for the gizmo's pie preview). */
    private GizmoDrag drag;
    private boolean hotkeyMode;

    /* The axis-key walk (see AxisSpaceCycle): which axis it is on, whether in its plane
     * form (Shift), and how many presses deep. A different axis, or any fresh edit
     * start, restarts it. */
    private Axis axisWalkAxis;
    private boolean axisWalkPlane;
    private int axisWalkStep;

    /** Frame the live edit was walked into by the axis keys, or {@code null} for the
     *  picker's. Deliberately NOT written back: a walked frame lasts for that edit only. */
    private TransformSpace editSpace;

    /** The live gesture; non-null exactly while {@link #editing}. */
    private DragStrategy strategy;

    private final TransformNumericInput numeric = new TransformNumericInput();

    /* Fine-drag (Shift) precision: a virtual cursor that lags the real one,
     * advancing at {@link DragStrategy#FINE_DRAG_FACTOR} speed while Shift is
     * held, so every ray gesture slows uniformly without per-mode code. The
     * lag is the accumulated offset between the two. */
    private final FineCursor fineCursor = new FineCursor();

    /** Full-screen overlay raised while the session runs: LMB accepts, RMB rejects,
     *  the wheel goes to the live gesture (depth, roll, drag sensitivity). */
    private final UIElement overlay = new AcceptRejectOverlay();

    public TransformGesture(Host host)
    {
        this.host = host;
    }

    /* Edit entry points. The mouse path (a gizmo handle pick) supplies the axes
     * directly; the keyboard path walks the user-configured hotkey orders. Both
     * funnel into startEdit, and both start their operation on the first press —
     * the gizmo shows every element at once, so there is no display mode for a
     * press to switch first. */

    public void enableMode(TransformOp op)
    {
        GizmoDrag drag = this.host.freshHotkeyDrag();
        boolean ray = drag != null;

        /* G/S/R walk their handles in the *_hotkey_order the user configured, wrapping
         * past the end. Only ray-driven steps with no rendered gizmo drop out — HIDING an
         * element does not drop its step, or a stripped-bare gizmo would take a whole
         * operation away from the keyboard. Scale's uniform lever is a step like any other. */
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
            /* A hotkey-driven operation along a specific axis keeps the hotkey semantics
             * (numeric input, accept/reject overlay); the axis comes from the configured
             * hotkey order rather than a fixed cycle. */
            this.startEdit(op, target.axis, null, DragStrategyFactory.Variant.AXIS, drag, true);
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
        UIContext context = this.host.getContext();

        if (context == null || this.host.getTransform() == null)
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

        /* Every fresh operation starts back in the picker's frame with the walk at zero
         * — G/S/R, a handle pick and the walk's own release all come through here. */
        this.editSpace = null;
        this.axisWalkAxis = null;
        this.axisWalkPlane = false;
        this.axisWalkStep = 0;

        /* Scope the IK solve dump to this gesture — the log then holds exactly
         * the drag being investigated (see ModelIKRuntime#logGesture). */
        ModelIKRuntime.logGesture(true);

        this.cache.copy(this.host.getTransform());
        Gizmo.INSTANCE.trackGesture(this);

        this.strategy = DragStrategyFactory.create(this, op, axis, axis2, variant);
        this.strategy.begin(context.mouseX, context.mouseY);

        if (!this.overlay.hasParent())
        {
            context.menu.overlay.add(this.overlay);
        }
    }

    /**
     * Constrain the live edit to an axis (with Shift, to the plane perpendicular to it):
     * rewind to the start values and rebuild the gesture as a plain axis drag.
     *
     * <p>The SAME axis pressed again walks Blender's cycle instead of rebuilding the
     * same constraint: the picker's frame, then the other one ({@link AxisSpaceCycle}),
     * then no constraint at all ({@link #releaseConstraint}), then over. A different
     * axis — or the plane form of the same one — restarts the walk.
     */
    public void setAxis(Axis axis)
    {
        boolean plane = Window.isShiftPressed();
        boolean same = this.editing && axis == this.axisWalkAxis && plane == this.axisWalkPlane;
        int step = same ? this.axisWalkStep + 1 : 0;
        List<TransformSpace> spaces = AxisSpaceCycle.spaces(this.getOp(), this.host.pickedSpace());

        if (step >= spaces.size())
        {
            if (this.releaseConstraint())
            {
                return;
            }

            /* Nothing to fall back to (translate's and rotate's free gestures are
             * ray-driven), so the walk wraps instead of stalling on that step. */
            step = 0;
        }

        this.axisWalkAxis = axis;
        this.axisWalkPlane = plane;
        this.axisWalkStep = step;
        this.editSpace = spaces.get(step);

        if (plane)
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

        this.rebuildConstrainedGesture();
    }

    /** Rewind to the start values and rebuild the live edit as a plain axis drag on the
     *  current axes and frame. Rebuilding from the start snapshot every time is what
     *  makes repeating it free of drift. */
    private void rebuildConstrainedGesture()
    {
        TransformOp op = this.getOp();

        this.restore();

        UIContext context = this.host.getContext();

        if (context != null && op != null)
        {
            this.strategy = DragStrategyFactory.create(this, op, this.axis, this.axis2, DragStrategyFactory.Variant.AXIS);
            this.strategy.begin(context.mouseX, context.mouseY);
        }

        /* Re-route an in-progress typed amount onto the freshly picked axis. */
        if (this.numeric.isActive())
        {
            this.applyNumericInput();
        }
    }

    /**
     * Drop the axis constraint, the last step of the cycle: the operation falls back to
     * its own free gesture — screen-plane grab, uniform lever, view spin — which are the
     * same ones a plain G/S/R offers, so no fourth kind of drag is needed.
     *
     * <p>Returns whether it could: translate's and rotate's free gestures are ray-driven
     * (cf. {@link HotkeyTarget#needsRay}), so a keyboard edit with no gizmo has nothing
     * to drop into and the caller wraps the walk instead.
     */
    private boolean releaseConstraint()
    {
        TransformOp op = this.getOp();

        if (op == null)
        {
            return false;
        }

        if (op == TransformOp.SCALE)
        {
            this.enableUniformScale(this.drag, this.hotkeyMode);

            return true;
        }

        if (this.drag == null)
        {
            return false;
        }

        if (op == TransformOp.TRANSLATE)
        {
            this.enableScreenTranslate(this.drag, this.hotkeyMode);
        }
        else
        {
            this.enableViewRotate(this.drag, this.hotkeyMode);
        }

        return true;
    }

    /** A frame was picked in the dropdown: the walk's frame would keep overriding the
     *  hand-picked one for the rest of the edit, so the walk ends here. */
    public void clearWalk()
    {
        this.editSpace = null;
        this.axisWalkAxis = null;
        this.axisWalkStep = 0;
    }

    /** Rewind every channel to the values captured when the edit began. */
    private void restore()
    {
        this.host.setT(null, this.cache.translate.x, this.cache.translate.y, this.cache.translate.z);
        this.host.setS(null, this.cache.scale.x, this.cache.scale.y, this.cache.scale.z);

        if (this.cache.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.host.setRQuat(new Quaternionf(this.cache.quat));
        }
        else
        {
            this.host.setR(null, MathUtils.toDeg(this.cache.rotate.x), MathUtils.toDeg(this.cache.rotate.y), MathUtils.toDeg(this.cache.rotate.z));
        }
    }

    private void disable()
    {
        ModelIKRuntime.logGesture(false);
        ModelPhysicsRuntime.dropCheckpoint();

        this.editing = false;
        this.axis2 = null;
        this.hotkeyMode = false;
        this.editSpace = null;
        this.axisWalkAxis = null;
        this.axisWalkStep = 0;
        this.strategy = null;
        this.drag = null;
        this.fineCursor.forget();
        this.numeric.clear();

        this.host.gestureStopped();

        Gizmo.INSTANCE.clearTrackedGesture(this);

        if (this.overlay.hasParent())
        {
            this.overlay.removeFromParent();
        }
    }

    /** Tear the session down without touching the transform (the editor was pointed at
     *  nothing). Accepting and rejecting are the two proper ends of a live session. */
    public void stopEditing()
    {
        this.disable();
    }

    public void accept()
    {
        this.disable();
        this.host.refreshFields();
        this.host.endGesture();
    }

    public void reject()
    {
        if (this.host.getTransform() == null)
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
        this.host.refreshFields();
    }

    /** Route a wheel event into the live gesture (depth move, sphere roll). */
    public boolean scroll(UIContext context)
    {
        return this.editing && this.host.getTransform() != null && this.strategy != null && this.strategy.scroll(context);
    }

    /**
     * The session's share of the keyboard: Enter accepts, Escape rejects, and the rest
     * feeds the numeric buffer. Returns whether the key was consumed.
     */
    public boolean keyPressed(UIContext context)
    {
        if (!this.editing)
        {
            return false;
        }

        if (context.isPressed(GLFW.GLFW_KEY_ENTER))
        {
            this.accept();

            return true;
        }
        else if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.reject();

            return true;
        }

        return this.handleNumericInputKey(context);
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
        return this.editing && this.hotkeyMode && this.host.getTransform() != null
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

        this.host.refreshFields();
    }

    /** Recompute the transform from the start snapshot plus the typed amount. */
    private void applyNumericInput()
    {
        if (this.host.getTransform() == null || this.strategy == null)
        {
            return;
        }

        this.strategy.applyNumeric(this.numeric.value());
        this.host.refreshFields();
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

        this.host.refreshFields();
    }

    /** Advance a running gesture if it is due. Called from the owning editor's render. */
    public void pump(UIContext context)
    {
        if (this.editing && !this.numeric.isActive() && this.checker.isTime())
        {
            this.updateDrag(context);
        }
    }

    /**
     * Keep a running gesture moving when the editor that owns it is NOT drawn — the film's
     * replay-root gizmo edits a transform with no visible fields at all, and a bone drag
     * froze the moment its keyframe panel was closed. Called by
     * {@link mchorse.bbs_mod.ui.utils.GizmoInteraction#update} every frame.
     *
     * <p>Strictly only then: a drawn editor pumps from its render, and pumping it from here
     * as well would advance the same gesture twice in one frame. That is not free the way it
     * looks — the per-gesture timer that appears to throttle it is only ever armed when the
     * cursor wraps at a screen edge, so both calls really do go through, and any strategy
     * that is not perfectly idempotent moves twice.
     */
    public void pumpIfHidden(UIContext context)
    {
        if (!this.host.isVisible())
        {
            this.pump(context);
        }
    }

    /* Session state, mostly for the gizmo and its pie */

    public boolean isEditing()
    {
        return this.editing;
    }

    public Axis getAxis()
    {
        return this.axis;
    }

    public Axis getAxis2()
    {
        return this.axis2;
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

    public boolean isHotkeyMode()
    {
        return this.hotkeyMode;
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

    public boolean isScreenTranslate()
    {
        return this.strategy != null && this.strategy.isScreenTranslate();
    }

    /** Whether the active scale drives all three axes off one lever (centre scale
     *  handle or an unconstrained S). */
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
     * factor delta for a scale. Prefixed by the host's target name when it has one.
     * Returns {@code null} when there is nothing to show.
     */
    public String getReadout()
    {
        if (!this.editing || this.host.getTransform() == null || this.strategy == null)
        {
            return null;
        }

        String readout = this.strategy.readout();
        String prefix = readout == null ? null : this.host.readoutPrefix();

        return prefix == null ? readout : prefix + readout;
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

    /* HUD accessors (see TransformGestureHud) */

    public boolean isNumericActive()
    {
        return this.numeric.isActive();
    }

    public String numericDisplay()
    {
        return this.numeric.display();
    }

    /* DragContext — what the strategies read and write */

    @Override
    public Transform transform()
    {
        return this.host.getTransform();
    }

    @Override
    public Transform cache()
    {
        return this.cache;
    }

    @Override
    public GizmoDrag drag()
    {
        return this.drag;
    }

    @Override
    public void setDrag(GizmoDrag drag)
    {
        this.drag = drag;
    }

    @Override
    public GizmoDrag freshHotkeyDrag()
    {
        return this.host.freshHotkeyDrag();
    }

    /**
     * The frame the gizmo and constrained edits operate in: the picker's choice, unless
     * the live edit was walked into another by its axis key ({@link #setAxis}). The ONLY
     * frame accessor — the strategies, the hosts' gizmo placement and the HUD chip all
     * read it here, so nothing can ask a second, staler question.
     */
    @Override
    public TransformSpace space()
    {
        return this.editing && this.editSpace != null ? this.editSpace : this.host.pickedSpace();
    }

    @Override
    public boolean isModel()
    {
        return this.host.isModel();
    }

    @Override
    public boolean rotationConstrained()
    {
        return this.host.isRotationConstrained();
    }

    @Override
    public boolean rotationChannelOnly()
    {
        return this.host.isRotationChannelOnly();
    }

    @Override
    public String targetName()
    {
        return this.host.targetName();
    }

    /* Blender-style snapping: every gesture is free by default and snaps to
     * the configured step only while Ctrl is held. Typed numeric input is
     * exact already, so it never snaps. */
    @Override
    public boolean shouldSnap(TransformOp op)
    {
        return this.editing && this.getOp() == op && Window.isCtrlPressed() && !this.numeric.isActive();
    }

    @Override
    public float additiveFactor(TransformOp op)
    {
        return this.host.additiveFactor(op);
    }

    @Override
    public Vector3f localTranslateVector(double factor, Axis axis)
    {
        return this.host.localTranslateVector(factor, axis);
    }

    @Override
    public float sphereWorldRadius()
    {
        return Gizmo.INSTANCE.getSphereWorldRadius();
    }

    @Override
    public void refreshFields()
    {
        this.host.refreshFields();
    }

    @Override
    public void writeTranslate(float x, float y, float z)
    {
        this.host.setT(null, x, y, z);
    }

    @Override
    public void writeScale(float x, float y, float z)
    {
        this.host.setS(null, x, y, z);
    }

    @Override
    public void writeRotateDeg(float xDeg, float yDeg, float zDeg)
    {
        this.host.setR(null, xDeg, yDeg, zDeg);
    }

    @Override
    public void writeRotationQuat(Quaternionf quat)
    {
        this.host.setRQuat(quat);
    }

    private class AcceptRejectOverlay extends UIElement
    {
        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (TransformGesture.this.editing)
            {
                if (context.mouseButton == 0)
                {
                    TransformGesture.this.accept();

                    return true;
                }
                else if (context.mouseButton == 1)
                {
                    TransformGesture.this.reject();

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
            if (TransformGesture.this.scroll(context))
            {
                return true;
            }

            UITrackpad.updateAmplifier(context);

            return true;
        }
    }
}
