package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.TransformSpace;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class UIPropTransform extends UITransform
{
    private static final double[] CURSOR_X = new double[1];
    private static final double[] CURSOR_Y = new double[1];

    /** Fraction of the camera-to-gizmo distance the object moves in depth per wheel notch
     *  during a screen-space grab (Alt divides it by 5, Ctrl multiplies it by 5). */
    private static final float DEPTH_WHEEL_FACTOR = 0.05F;
    /** Base degrees of view-axis (arcball) roll per mouse-wheel notch while
     *  trackball-dragging; Alt divides it by 5 (fine), Ctrl multiplies by 5 (coarse). */
    private static final float TRACKBALL_WHEEL_DEG = 5F;
    /** Factor the modifier keys apply to a gizmo step: Ctrl coarsens, Alt refines. */
    private static final float STEP_MODIFIER = 5F;
    /** Cursor-speed multiplier for a ray gesture while Shift is held (precision drag). */
    private static final float FINE_DRAG_FACTOR = 0.1F;

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
    /** World→translate map (inverse of the translate Jacobian) captured for the active
     *  screen-space grab, so the mouse-wheel depth move can convert a world step into
     *  {@code transform.translate} units. */
    private final Matrix3f dragScreenInverseJacobian = new Matrix3f();
    private final Vector3f dragPlaneNormal = new Vector3f();
    private final Vector3d dragStartHit = new Vector3d();
    private final Vector3f dragStartTranslate = new Vector3f();

    /* Per-mode start state. Translate uses dragStartTranslate above; scale gets its own snapshot
     * so each mode is self-contained. */
    private final Vector3f dragStartScale = new Vector3f();
    /** World-space direction of the active handle, captured at drag start. */
    private final Vector3f dragAxisDir = new Vector3f();
    /** Axis the rotation composes about, expressed in the bone's parent frame and
     *  captured at drag start: the view axis for VIEW, the grabbed ring's axis for
     *  LOCAL-space ring drags. */
    private final Vector3f rotateParentAxis = new Vector3f();
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
    /** Net cursor offset (pixels) since the trackball drag began, carried across
     *  cursor wraps. The rotation is rebuilt from this absolute offset and the
     *  cached start orientation every frame — never from the previous frame — so a
     *  looping drag returns to the exact starting rotation instead of accumulating
     *  roll (the classic incremental-trackball drift). */
    private float trackballAccumX;
    private float trackballAccumY;
    /** View axis (toward the camera) in the bone's parent frame, captured at
     *  trackball-drag start — the mouse wheel rolls about this for arcball motion. */
    private final Vector3f trackballViewLocal = new Vector3f();
    /** Accumulated wheel-driven view-axis roll (degrees) for the current trackball drag. */
    private float trackballRollDeg;
    /** World-space unit axis from the sphere centre toward the camera, captured at arcball start. */
    private final Vector3f arcballViewWorld = new Vector3f();
    /** World→parent-frame rotation captured at arcball start (constant for the drag). */
    private final Matrix3f arcballParentInverse = new Matrix3f();
    /** Grab direction on the ball (parent frame) at the current anchor. */
    private final Vector3f arcballStartLocal = new Vector3f();
    /** Cursor's current direction on the ball (parent frame). */
    private final Vector3f arcballCurrentLocal = new Vector3f();
    /** Arc segments folded in by re-anchors (cursor wraps), parent frame. Within
     *  one anchor the rotation stays a pure function of the cursor position. */
    private final Quaternionf arcballAccum = new Quaternionf();
    /** World-space radius of the rendered sphere, captured at arcball start. */
    private float arcballRadius;
    /** Whether the arcball anchor state is valid (guards the re-anchor fold). */
    private boolean arcballAnchored;
    private boolean dragHasStart;
    private RotateKind rotateKind = RotateKind.AXIS;
    /**
     * Screen-space (view-plane) translate: movement is camera-relative — along the
     * camera's right/up axes, in the plane facing the camera — rather than along a
     * gizmo axis. Lives only on the translate mode; cleared as soon as the drag ends
     * or the user constrains to an axis with X/Y/Z.
     */
    private boolean translateScreen;
    private boolean hotkeyMode;
    private Supplier<GizmoDrag> hotkeyDragSupplier;

    /* Blender-style numeric input captured while a hotkey-driven G/S/R operation
     * is live. {@link #numericInput} holds the typed digits and decimal point;
     * the sign lives in {@link #numericNegative} so '-' can flip it at any moment.
     * While {@link #numericActive} the cursor drives nothing — the transform is
     * recomputed purely from {@link #cache} plus the typed amount (an offset for
     * translate/rotate, a factor for scale). */
    private final StringBuilder numericInput = new StringBuilder();
    private boolean numericNegative;
    private boolean numericActive;
    /** Optional divisor typed after '/'. While {@link #numericDividing} the typing
     *  feeds this buffer and the applied value is {@code numericInput / numericDivisor};
     *  pressing '/' again drops it and editing returns to {@link #numericInput}. */
    private final StringBuilder numericDivisor = new StringBuilder();
    private boolean numericDividing;
    /** Trackball numeric target: {@link Axis#X} = horizontal (screen-up axis),
     *  {@link Axis#Y} = vertical (screen-right axis). */
    private Axis trackballAxis = Axis.X;

    /* Fine-drag (Shift) precision: a virtual cursor that lags the real one,
     * advancing at {@link #FINE_DRAG_FACTOR} speed while Shift is held, so every
     * ray gesture slows uniformly without per-mode code. The lag is the
     * accumulated offset between the two. */
    private float fineOffsetX;
    private float fineOffsetY;
    private int fineLastX;
    private int fineLastY;
    private boolean fineHasLast;

    private UITransformHandler handler;

    /* The spaces bar: one square icon per editing space, the active one carries
     * the dashboard-style primary highlight. */
    private UIElement spacesBar;
    private UIIcon spaceParent;
    private UIIcon spaceLocal;
    private UIIcon spaceWorld;
    private boolean spacesBarBackground;

    /* Right-hand toggles of the spaces bar, only on editors that support it (see
     * {@link #supportsMirror()}): mirror edit reflects a pose change onto each
     * bone's left/right counterpart; alternate-invert flips the rotation of every
     * second selected bone. Both are shared, persisted settings. */
    private UIIcon mirror;
    private UIIcon invert;

    public UIPropTransform()
    {
        this.handler = new UITransformHandler(this);

        this.spaceParent = new UIIcon(Icons.ALL_DIRECTIONS, (b) -> this.setSpace(TransformSpace.PARENT));
        this.spaceParent.tooltip(UIKeys.TRANSFORMS_CONTEXT_SWITCH_GLOBAL);
        this.spaceLocal = new UIIcon(Icons.MINIMIZE, (b) -> this.setSpace(TransformSpace.LOCAL));
        this.spaceLocal.tooltip(UIKeys.TRANSFORMS_CONTEXT_SWITCH_LOCAL);
        this.spaceWorld = new UIIcon(Icons.GLOBE, (b) -> this.setSpace(TransformSpace.WORLD));
        this.spaceWorld.tooltip(UIKeys.TRANSFORMS_CONTEXT_SWITCH_WORLD);

        this.spacesBar = new UIElement();
        this.spacesBar.h(UIConstants.CONTROL_HEIGHT).row(0).resize();
        this.spacesBar.add(this.spaceParent, this.spaceLocal, this.spaceWorld);

        if (this.supportsMirror())
        {
            this.mirror = new UIIcon(Icons.CONVERT, (b) -> this.toggleMirrorEdit());
            this.mirror.tooltip(UIKeys.TRANSFORMS_MIRROR_EDIT);
            this.invert = new UIIcon(Icons.REVERSE, (b) -> this.toggleAlternateInvert());
            this.invert.tooltip(UIKeys.TRANSFORMS_ALTERNATE_INVERT);

            /* An empty, width-less spacer eats the leftover row width so the
             * toggles are pushed to the far (right) end of the bar. */
            this.spacesBar.add(new UIElement(), this.mirror, this.invert);
        }

        this.prepend(this.spacesBar);
        this.h(4 * UIConstants.CONTROL_HEIGHT);

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

    public UIPropTransform hotkeyDrag(Supplier<GizmoDrag> supplier)
    {
        this.hotkeyDragSupplier = supplier;

        return this;
    }

    /** Give the spaces bar a backdrop — for hosts where the panel floats over the preview. */
    public UIPropTransform barBackground()
    {
        this.spacesBarBackground = true;

        return this;
    }

    /** Whether this editor offers the mirror-edit toggle (pose multi-bone editors do). */
    protected boolean supportsMirror()
    {
        return false;
    }

    public boolean isMirrorEdit()
    {
        return BBSSettings.poseMirrorEdit.get();
    }

    private void toggleMirrorEdit()
    {
        BBSSettings.poseMirrorEdit.set(!BBSSettings.poseMirrorEdit.get());
        UIUtils.playClick();
    }

    public boolean isAlternateInvert()
    {
        return BBSSettings.poseAlternateInvert.get();
    }

    private void toggleAlternateInvert()
    {
        BBSSettings.poseAlternateInvert.set(!BBSSettings.poseAlternateInvert.get());
        UIUtils.playClick();
    }

    public boolean isLocal()
    {
        return this.getSpace() == TransformSpace.LOCAL;
    }

    /** The editing space is shared between every transform panel and persists with the settings. */
    public TransformSpace getSpace()
    {
        TransformSpace[] values = TransformSpace.values();

        return values[MathUtils.clamp(BBSSettings.transformSpace.get(), 0, values.length - 1)];
    }

    private UIIcon activeSpaceIcon()
    {
        TransformSpace space = this.getSpace();

        if (space == TransformSpace.LOCAL) return this.spaceLocal;
        if (space == TransformSpace.WORLD) return this.spaceWorld;

        return this.spaceParent;
    }

    private void setSpace(TransformSpace space)
    {
        BBSSettings.transformSpace.set(space.ordinal());
    }

    private void toggleLocal()
    {
        this.setSpace(this.getSpace().next());
    }

    /**
     * Unit direction (in {@code transform.translate} units) one unit of input
     * moves along the given axis in the active space. The anchored ray basis is
     * preferred when one is live (so typed amounts match the handles, renderer
     * conventions included); without it LOCAL falls back to the transform's own
     * rotation and WORLD to a freshly built drag's Jacobian. PARENT is the
     * plain channel axis, which is also the last-resort fallback — exact
     * wherever the parent frame is the world itself (e.g. model blocks).
     */
    private Vector3f spaceTranslateDir(Axis axis)
    {
        Vector3f dir = new Vector3f();
        TransformSpace space = this.getSpace();

        if (space != TransformSpace.PARENT && this.transform != null)
        {
            if (this.drag != null && this.dragHasStart && this.mode == 0)
            {
                this.dragTranslateBasis.getColumn(axis.ordinal(), dir);
            }

            if (dir.lengthSquared() < 1.0E-12F)
            {
                if (space == TransformSpace.LOCAL)
                {
                    this.transform.createRotationMatrix().getColumn(axis.ordinal(), dir);
                }
                else
                {
                    this.worldTranslateDir(axis, dir);
                }
            }

            if (dir.lengthSquared() >= 1.0E-12F)
            {
                return dir.normalize();
            }
        }

        return dir.set(
            axis == Axis.X ? 1F : 0F,
            axis == Axis.Y ? 1F : 0F,
            axis == Axis.Z ? 1F : 0F
        );
    }

    /** World axis mapped into translate units via a freshly built drag's Jacobian. */
    private void worldTranslateDir(Axis axis, Vector3f dir)
    {
        GizmoDrag drag = this.getHotkeyDrag();

        if (drag == null)
        {
            return;
        }

        Matrix3f inverse = new Matrix3f(drag.translateJacobian);

        if (Math.abs(inverse.determinant()) < 1.0E-8F)
        {
            return;
        }

        inverse.invert().getColumn(axis.ordinal(), dir);
    }

    public UIPropTransform enableHotkeys()
    {
        return this.enableHotkeys(() -> true);
    }

    /**
     * Same as {@link #enableHotkeys()}, but every registered hotkey is gated by {@code enabled}. When
     * it returns {@code false} the keybinds report inactive, so input propagation flows past this
     * transform to whichever element is checked next &mdash; this is how two transforms that coexist in
     * the tree (e.g. the body part transform in the sidebar alongside the active form panel's transform)
     * avoid both grabbing on G/S/R: only the one whose gate is on responds.
     */
    public UIPropTransform enableHotkeys(Supplier<Boolean> enabled)
    {
        IKey category = UIKeys.TRANSFORMS_KEYS_CATEGORY;
        Supplier<Boolean> active = () -> enabled.get() && this.editing;

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, () -> this.enableMode(0)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SCALE, () -> this.enableMode(1)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATE, () -> this.enableMode(2)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_COMBINED, () -> Gizmo.INSTANCE.toggleCombined()).strict().active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_X, () -> this.setEditingAxis(Axis.X)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Y, () -> this.setEditingAxis(Axis.Y)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Z, () -> this.setEditingAxis(Axis.Z)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_TOGGLE_LOCAL, () ->
        {
            this.toggleLocal();
            UIUtils.playClick();
        }).strict().active(enabled).category(category);

        if (this.supportsMirror())
        {
            this.keys().register(Keys.TRANSFORMATIONS_MIRROR_EDIT, this::toggleMirrorEdit).active(enabled).category(category);
        }

        return this;
    }

    public Transform getTransform()
    {
        return this.transform;
    }

    @Override
    protected Transform getEditedTransform()
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

    public Axis getAxis2()
    {
        return this.axis2;
    }

    public int getMode()
    {
        return this.mode;
    }

    /** Whether the active translate is the screen-space (view-plane) grab. */
    public boolean isScreenTranslate()
    {
        return this.translateScreen;
    }

    /** Whether the active rotation is one of the sphere's kinds (trackball or arcball). */
    public boolean isSphereRotate()
    {
        return this.rotateKind == RotateKind.TRACKBALL || this.rotateKind == RotateKind.ARCBALL;
    }

    public boolean isViewRotate()
    {
        return this.rotateKind == RotateKind.VIEW;
    }

    public Vector3f getInitialDragRingVec()
    {
        return this.initialDragRingVec;
    }

    /** World-space axis of the active ring drag, as captured at its start. */
    public Vector3f getDragAxisDir()
    {
        return this.dragAxisDir;
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
        if (!this.editing || this.translateScreen)
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
    }

    public void enableMode(int mode)
    {
        GizmoDrag drag = this.getHotkeyDrag();
        boolean ray = BBSSettings.transformHotkeys3dRay.get() && drag != null;

        /* G/S/R walk their handles in the user-configured order (the
         * *_hotkey_order settings), wrapping past the end back to the first
         * step. Steps whose handle is unavailable drop out: the ray-driven
         * ones without a rendered gizmo, the sphere when it's turned off. */
        HotkeyTarget target = this.nextHotkeyTarget(mode, ray);

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
        else
        {
            this.enableHotkeyAxis(mode, target.axis, drag);
        }
    }

    /** The walk step the active edit corresponds to ({@code null} when not editing this mode). */
    private HotkeyTarget currentHotkeyTarget(int mode)
    {
        if (!this.editing || this.mode != mode)
        {
            return null;
        }

        if (this.rotateKind == RotateKind.VIEW) return HotkeyTarget.VIEW;
        if (this.isSphereRotate()) return HotkeyTarget.SPHERE;
        if (this.translateScreen) return HotkeyTarget.SCREEN;
        if (this.axis == Axis.Y) return HotkeyTarget.Y;
        if (this.axis == Axis.Z) return HotkeyTarget.Z;

        return HotkeyTarget.X;
    }

    private HotkeyTarget nextHotkeyTarget(int mode, boolean ray)
    {
        ValueOrder order = mode == 0 ? BBSSettings.translateHotkeyOrder : (mode == 1 ? BBSSettings.scaleHotkeyOrder : BBSSettings.rotateHotkeyOrder);
        List<HotkeyTarget> steps = new ArrayList<>();

        for (String token : order.get())
        {
            HotkeyTarget target = HotkeyTarget.byToken(token);

            if (target == null || (target.needsRay && !ray))
            {
                continue;
            }

            if (target == HotkeyTarget.SPHERE && !BBSSettings.rotate3dSphere.get())
            {
                continue;
            }

            steps.add(target);
        }

        if (steps.isEmpty())
        {
            return HotkeyTarget.X;
        }

        int index = steps.indexOf(this.currentHotkeyTarget(mode));

        return steps.get((index + 1) % steps.size());
    }

    /**
     * Start (or switch to) a hotkey-driven operation along a specific axis.
     * Unlike the mouse path through {@link #enableMode(int, Axis, Axis, GizmoDrag)}
     * this keeps the hotkey semantics (numeric input, accept/reject overlay,
     * the display-mode switch on the first press); the axis comes from the
     * configured hotkey order rather than a fixed cycle.
     */
    private void enableHotkeyAxis(int mode, Axis axis, GizmoDrag drag)
    {
        if (Gizmo.INSTANCE.getMode() != Gizmo.Mode.COMBINED && Gizmo.INSTANCE.setMode(Gizmo.Mode.values()[mode]))
        {
            return;
        }

        UIContext context = this.getContext();
        if (context == null || this.transform == null)
        {
            return;
        }

        this.clearNumericInput();

        if (this.editing)
        {
            this.restore(true);
        }

        this.editing = true;
        this.mode = mode;
        this.rotateKind = RotateKind.AXIS;
        this.translateScreen = false;
        this.axis = axis;
        this.axis2 = null;
        this.hotkeyMode = true;
        this.drag = drag;
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

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

    public void enableMode(int mode, Axis axis)
    {
        this.enableMode(mode, axis, null, null);
    }

    public void enableMode(int mode, Axis axis, Axis axis2)
    {
        this.enableMode(mode, axis, axis2, null);
    }

    /**
     * Start an operation from a mouse handle pick: the axes come straight
     * from the picked handle, so this never cycles and never switches the
     * gizmo's display mode. The keyboard path goes through
     * {@link #enableMode(int)} and the configured hotkey orders instead.
     */
    public void enableMode(int mode, Axis axis, Axis axis2, GizmoDrag drag)
    {
        UIContext context = this.getContext();
        if (context == null)
        {
            return;
        }

        if (this.transform == null)
        {
            return;
        }

        this.clearNumericInput();

        if (this.editing)
        {
            this.restore(true);
        }

        this.translateScreen = false;
        this.rotateKind = RotateKind.AXIS;
        this.axis = axis == null ? Axis.X : axis;
        this.axis2 = axis2;
        this.drag = drag;
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

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
        if (hotkeyMode && Gizmo.INSTANCE.getMode() != Gizmo.Mode.COMBINED && Gizmo.INSTANCE.setMode(Gizmo.Mode.ROTATE))
        {
            return;
        }

        UIContext context = this.getContext();
        if (context == null || this.transform == null)
        {
            return;
        }

        this.clearNumericInput();

        if (this.editing)
        {
            this.restore(true);
        }

        this.editing = true;
        this.rotateKind = RotateKind.TRACKBALL;
        this.trackballAxis = Axis.X;
        this.translateScreen = false;
        this.mode = 2; // ROTATE
        this.axis = null;
        this.axis2 = null;
        this.hotkeyMode = hotkeyMode;
        this.drag = drag;
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        this.cache.copy(this.transform);
        Gizmo.INSTANCE.trackTransform(this);

        this.trackballAccumX = 0F;
        this.trackballAccumY = 0F;
        this.trackballRollDeg = 0F;
        this.beginRayRotateTrackball(context.mouseX, context.mouseY);

        if (!this.handler.hasParent())
        {
            context.menu.overlay.add(this.handler);
        }
    }

    public void enableArcball(GizmoDrag drag)
    {
        this.enableArcball(drag, false);
    }

    public void enableArcball(GizmoDrag drag, boolean hotkeyMode)
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

        this.clearNumericInput();

        if (this.editing)
        {
            this.restore(true);
        }

        this.editing = true;
        this.rotateKind = RotateKind.ARCBALL;
        this.trackballAxis = Axis.X;
        this.translateScreen = false;
        this.mode = 2; // ROTATE
        this.axis = null;
        this.axis2 = null;
        this.hotkeyMode = hotkeyMode;
        this.drag = drag;
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        this.cache.copy(this.transform);
        Gizmo.INSTANCE.trackTransform(this);

        this.trackballRollDeg = 0F;
        this.arcballAccum.identity();
        this.arcballAnchored = false;
        this.beginRayRotateArcball(context.mouseX, context.mouseY);

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

        this.clearNumericInput();

        if (this.editing)
        {
            this.restore(true);
        }

        this.editing = true;
        this.rotateKind = RotateKind.VIEW;
        this.translateScreen = false;
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

    public void enableScreenTranslate(GizmoDrag drag)
    {
        this.enableScreenTranslate(drag, false);
    }

    /**
     * Start a screen-space (view-plane) translate: the object moves along the
     * camera's right/up axes in the plane facing the camera. Grabbing the
     * centre cube with the mouse never switches the gizmo's display mode
     * (like the other handle picks); as a hotkey walk step the first press
     * switches it like the rest of the hotkey starters.
     */
    public void enableScreenTranslate(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && Gizmo.INSTANCE.getMode() != Gizmo.Mode.COMBINED && Gizmo.INSTANCE.setMode(Gizmo.Mode.TRANSLATE))
        {
            return;
        }

        UIContext context = this.getContext();
        if (context == null || this.transform == null)
        {
            return;
        }

        this.clearNumericInput();

        if (this.editing)
        {
            this.restore(true);
        }

        this.editing = true;
        this.mode = 0;
        this.rotateKind = RotateKind.AXIS;
        this.translateScreen = true;
        this.axis = Axis.X;
        this.axis2 = Axis.Y;
        this.hotkeyMode = hotkeyMode;
        this.drag = drag;
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

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
        this.translateScreen = false;

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

        /* Re-route an in-progress typed amount onto the freshly picked axis. */
        if (this.numericActive)
        {
            this.applyNumericInput();
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
            else if (this.rotateKind == RotateKind.ARCBALL) this.applyRayRotateArcball(mouseX, mouseY);
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
     * Push the object toward or away from the camera with the mouse wheel during a
     * screen-space grab. One notch scales its distance from the camera by a fraction
     * (Alt divides the step by 5 for fine control, Ctrl multiplies it by 5 for coarse).
     *
     * <p>The object moves along its own camera-to-object ray, so its on-screen position
     * is preserved (only the depth/size changes) — moving along the camera forward axis
     * instead would drift the object across the screen. The drag plane is slid with the
     * object and re-anchored, so the in-plane drag keeps tracking the cursor at the new
     * depth without the perspective mismatch that otherwise makes it overshoot/lag.
     *
     * @return {@code true} when the wheel was consumed as depth (i.e. a screen-space
     *         grab is live), so callers can fall back to their default scroll handling.
     */
    /**
     * Apply the shared modifier keys to a gizmo step amount: Alt makes it fine
     * (÷5), Ctrl makes it coarse (×5), both/neither leave it as-is. Keeps the
     * wheel-driven depth and arcball roll consistent.
     */
    private static float applyStepModifiers(float step)
    {
        if (Window.isAltPressed()) step /= STEP_MODIFIER;
        if (Window.isCtrlPressed()) step *= STEP_MODIFIER;

        return step;
    }

    public boolean scrollDepth(UIContext context)
    {
        if (!this.editing || !this.translateScreen || !this.dragHasStart || this.transform == null)
        {
            return false;
        }

        /* Re-acquire the drag from the freshly rendered gizmo (as if the grab were just
         * triggered): its origin is then the object's authoritative current world
         * position, instead of a snapshot reconstruction that drifts. Rebuild the
         * screen-drag basis/anchors on it so the depth ray and the in-plane plane stay
         * aligned with where the object actually is. */
        GizmoDrag fresh = this.getHotkeyDrag();

        if (fresh != null)
        {
            this.drag = fresh;
        }

        if (this.drag == null)
        {
            return true;
        }

        this.beginRayTranslateScreen(context.mouseX, context.mouseY);

        Vector3d ray = new Vector3d(this.drag.gizmoOrigin).sub(this.drag.cameraOrigin);
        double distance = ray.length();

        if (distance < 1.0E-4)
        {
            return true;
        }

        ray.div(distance);

        float step = applyStepModifiers((float) (context.mouseWheel * distance * DEPTH_WHEEL_FACTOR));

        /* Move along the camera->object ray (preserves screen position), in translate units. */
        Vector3f translateStep = this.dragScreenInverseJacobian.transform(
            new Vector3f((float) (ray.x * step), (float) (ray.y * step), (float) (ray.z * step))
        );

        this.setT(null,
            this.transform.translate.x + translateStep.x,
            this.transform.translate.y + translateStep.y,
            this.transform.translate.z + translateStep.z
        );

        /* Slide the drag plane to the new depth and re-anchor so the in-plane drag continues. */
        this.drag.gizmoOrigin.add(ray.x * step, ray.y * step, ray.z * step);
        this.dragStartTranslate.set(this.transform.translate);
        this.drag.intersectPlane(context.mouseX, context.mouseY, this.dragPlaneNormal, this.dragStartHit);

        this.setTransform(this.transform);

        return true;
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

        if (this.shouldSnap(1))
        {
            float step = BBSSettings.snapScale.get();

            if (all || this.axis == Axis.X || this.axis2 == Axis.X) s.x = (float) snap(s.x, step);
            if (all || this.axis == Axis.Y || this.axis2 == Axis.Y) s.y = (float) snap(s.y, step);
            if (all || this.axis == Axis.Z || this.axis2 == Axis.Z) s.z = (float) snap(s.z, step);
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
     * either direction.
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

        /* Every space composes the whole accumulated sweep about the grabbed ring's parent-frame
         * axis on top of the cached start orientation — including PARENT, which turns about the
         * parent frame's fixed axes (the rings as drawn), not the bone's own like LOCAL. The snap
         * lands on whole degrees of the sweep, since after the decomposition no single euler channel
         * corresponds to it. */
        this.applyAxisRotation(this.snapGizmoValue(this.accumulatedRotateDeg), this.rotateParentAxis);
    }

    /**
     * Cursor angle (radians) around the projected gizmo center, in the viewport
     * pixel convention (Y down) so it lines up with {@link GizmoDrag#projectToScreen}.
     */
    private float screenAngle(int mouseX, int mouseY)
    {
        return (float) Math.atan2(mouseY - this.dragScreenCenter.y, mouseX - this.dragScreenCenter.x);
    }

    private static float unwrapDeg(float valueDeg, float referenceDeg)
    {
        return valueDeg + Math.round((referenceDeg - valueDeg) / 360F) * 360F;
    }

    /**
     * Decompose {@code matrix} into ZYX Euler degrees, returning whichever of the
     * two equivalent Euler solutions sits nearest the reference angles. At the
     * Y=&plusmn;90 gimbal pole {@link Matrix3f#getEulerAnglesZYX} pours the whole
     * turn into X and Z (both near &plusmn;180); the dual solution
     * {@code (x+180, 180-y, z+180)} keeps them near the reference instead. Both
     * rebuild the identical rotation, so this never changes the orientation &mdash;
     * it only keeps the Euler channels continuous frame to frame, which stops
     * keyframes from landing on alternating branches and tearing on playback.
     */
    private Vector3f continuousEulerZYXDeg(Matrix3f matrix, Vector3f reference)
    {
        float refX = MathUtils.toDeg(reference.x);
        float refY = MathUtils.toDeg(reference.y);
        float refZ = MathUtils.toDeg(reference.z);

        Vector3f euler = matrix.getEulerAnglesZYX(new Vector3f());
        float ax = MathUtils.toDeg(euler.x);
        float ay = MathUtils.toDeg(euler.y);
        float az = MathUtils.toDeg(euler.z);

        float px = unwrapDeg(ax, refX);
        float py = unwrapDeg(ay, refY);
        float pz = unwrapDeg(az, refZ);

        float dx = unwrapDeg(ax + 180F, refX);
        float dy = unwrapDeg(180F - ay, refY);
        float dz = unwrapDeg(az + 180F, refZ);

        float primary = Math.abs(px - refX) + Math.abs(py - refY) + Math.abs(pz - refZ);
        float dual = Math.abs(dx - refX) + Math.abs(dy - refY) + Math.abs(dz - refZ);

        return dual < primary ? new Vector3f(dx, dy, dz) : new Vector3f(px, py, pz);
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

        /* Snap the distance moved along the handle in translate units, so the
         * step means the same thing whatever frame the handle works in. */
        if (this.shouldSnap(0))
        {
            float length = translateAxis.length();

            if (length > 1.0E-8F)
            {
                t = (float) (snap(t * length, BBSSettings.snapTranslate.get()) / length);
            }
        }

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
                else if (this.rotateKind == RotateKind.ARCBALL)
                {
                    this.beginRayRotateArcball(mouseX, mouseY);
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
        if (this.translateScreen)
        {
            this.beginRayTranslateScreen(mouseX, mouseY);

            return;
        }

        /* The handles drag along the axes the gizmo is rendered with — the
         * viewport orients those by the active space (parent frame in GLOBAL,
         * the bone's own frame in LOCAL), so the math here is space-agnostic.
         * Pushing them through the inverse Jacobian recovers the matching
         * change in translate-space, whatever units translate is in. */
        Matrix3f inverse = new Matrix3f(this.drag.translateJacobian);

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
     * Anchor a screen-space translate. The drag plane is the one facing the camera
     * (normal = camera forward), and the two move directions are the camera's world
     * right/up axes recovered from the rotation-only view matrix. Pushed through the
     * inverse translate Jacobian they map cursor motion in the view plane straight
     * onto {@code transform.translate}, regardless of the local/global toggle (screen
     * space is camera-relative by definition). Reuses {@link #applyRayTranslate} with
     * {@code axis = X}, {@code axis2 = Y} addressing those two camera directions.
     */
    private void beginRayTranslateScreen(int mouseX, int mouseY)
    {
        Matrix3f invView = this.drag.view.get3x3(new Matrix3f());

        if (Math.abs(invView.determinant()) < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        invView.invert();

        Vector3f right = invView.getColumn(0, new Vector3f());
        Vector3f up = invView.getColumn(1, new Vector3f());
        Vector3f forward = invView.getColumn(2, new Vector3f());

        if (right.lengthSquared() < 1.0E-8F || up.lengthSquared() < 1.0E-8F || forward.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        right.normalize();
        up.normalize();
        forward.normalize();

        Matrix3f cameraBasis = new Matrix3f();

        cameraBasis.setColumn(0, right);
        cameraBasis.setColumn(1, up);
        cameraBasis.setColumn(2, forward);

        Matrix3f inverse = new Matrix3f(this.drag.translateJacobian);

        if (Math.abs(inverse.determinant()) < 1.0E-8F)
        {
            inverse.identity();
        }
        else
        {
            inverse.invert();
        }

        this.dragTranslateBasis.set(inverse).mul(cameraBasis);
        this.dragScreenInverseJacobian.set(inverse);
        this.dragWorldBasis.set(cameraBasis);
        this.dragPlaneNormal.set(forward);

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
        /* Axes come from the cached start orientation, not the live one, so the
         * screen right/up directions stay fixed for the whole drag. This also
         * makes the call idempotent: a cursor wrap re-invokes it, and rebuilding
         * from the unchanged cache yields the same axes (and never disturbs the
         * accumulated offset). */
        Matrix3f parentInverse = this.computeParentInverse(this.cache.rotate);

        if (parentInverse == null)
        {
            this.dragHasStart = false;

            return;
        }

        Matrix3f invView = this.drag.view.get3x3(new Matrix3f()).invert();

        parentInverse.transform(invView.getColumn(0, new Vector3f()).normalize(), this.trackballRightLocal);
        parentInverse.transform(invView.getColumn(1, new Vector3f()).normalize(), this.trackballUpLocal);
        parentInverse.transform(invView.getColumn(2, new Vector3f()).normalize(), this.trackballViewLocal);

        if (this.trackballRightLocal.lengthSquared() < 1.0E-8F || this.trackballUpLocal.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        this.trackballRightLocal.normalize();
        this.trackballUpLocal.normalize();
        this.trackballViewLocal.normalize();
        this.trackballLastX = mouseX;
        this.trackballLastY = mouseY;
        this.dragHasStart = true;
    }

    /**
     * Roll the bone by the cursor's frame-to-frame motion: horizontal travel
     * turns it about the screen's vertical axis, vertical travel about the
     * screen's horizontal axis, like spinning a ball under the fingertip. The
     * turns are rebuilt from the accumulated offset in
     * {@link #updateTrackballRotation} (then read back as Euler), so the drag
     * stays smooth through gimbal lock; roll comes only from the mouse wheel.
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

        /* Track the net cursor offset since the drag began (telescoping the
         * per-frame deltas keeps it correct across cursor wraps). */
        this.trackballAccumX += dx;
        this.trackballAccumY += dy;

        this.updateTrackballRotation();
    }

    /**
     * Rebuild the trackball rotation from the FIXED start orientation plus the
     * absolute cursor offset (yaw/pitch) and the wheel-driven view-axis roll,
     * rather than nudging the previous frame's result. Because the outcome is a
     * pure function of those accumulated amounts — and addition commutes, unlike
     * rotation composition — a back-and-forth drag returns to the exact starting
     * rotation, eliminating the trackball roll drift. Shared by the cursor drag
     * and the mouse-wheel arcball roll.
     */
    private void updateTrackballRotation()
    {
        Vector3f source = this.cache.rotate;

        Matrix3f startRotation = new Matrix3f()
            .rotationZ(source.z)
            .rotateY(source.y)
            .rotateX(source.x);

        float sensitivity = BBSSettings.trackballSensitivity.get();
        float yaw = MathUtils.toRad(this.trackballAccumX * sensitivity);
        float pitch = MathUtils.toRad(this.trackballAccumY * sensitivity);
        float roll = MathUtils.toRad(this.trackballRollDeg);

        Matrix3f rotation = new Matrix3f()
            .rotation(roll, this.trackballViewLocal)
            .rotate(yaw, this.trackballUpLocal.x, this.trackballUpLocal.y, this.trackballUpLocal.z)
            .rotate(pitch, this.trackballRightLocal.x, this.trackballRightLocal.y, this.trackballRightLocal.z)
            .mul(startRotation);

        Vector3f live = this.transform.rotate;
        Vector3f euler = this.continuousEulerZYXDeg(rotation, live);

        this.setR(null, euler.x, euler.y, euler.z);
    }

    /**
     * Mouse-wheel roll while sphere-dragging: each notch rolls the object
     * about the view axis (toward the camera), with Alt for fine (÷5) and
     * Ctrl for coarse (×5) steps. Returns whether the wheel was consumed.
     */
    public boolean scrollTrackballRoll(UIContext context)
    {
        if (!this.editing || !this.isSphereRotate() || !this.dragHasStart || this.transform == null)
        {
            return false;
        }

        float step = applyStepModifiers((float) (context.mouseWheel * TRACKBALL_WHEEL_DEG));

        this.trackballRollDeg += step;

        if (this.rotateKind == RotateKind.ARCBALL) this.updateArcballRotation();
        else this.updateTrackballRotation();

        return true;
    }

    /**
     * Anchor an arcball drag: capture the sphere's world placement, the
     * world&rarr;parent map and the grab direction under the cursor. A cursor
     * wrap re-invokes this; the arc swept so far is folded into
     * {@link #arcballAccum} first, so the fresh anchor continues from the
     * current orientation instead of jumping back to the start.
     */
    private void beginRayRotateArcball(int mouseX, int mouseY)
    {
        if (this.arcballAnchored)
        {
            this.arcballAccum.premul(new Quaternionf().rotationTo(this.arcballStartLocal, this.arcballCurrentLocal));
            this.arcballAnchored = false;
        }

        Matrix3f parentInverse = this.computeParentInverse(this.cache.rotate);
        float radius = Gizmo.INSTANCE.getSphereWorldRadius();

        if (parentInverse == null || radius <= 0F)
        {
            this.dragHasStart = false;

            return;
        }

        Vector3f view = new Vector3f(
            (float) (this.drag.cameraOrigin.x - this.drag.gizmoOrigin.x),
            (float) (this.drag.cameraOrigin.y - this.drag.gizmoOrigin.y),
            (float) (this.drag.cameraOrigin.z - this.drag.gizmoOrigin.z)
        );

        if (view.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        this.arcballViewWorld.set(view.normalize());
        this.arcballRadius = radius;
        this.arcballParentInverse.set(parentInverse);

        /* Screen axes in the parent frame, for the typed-angle input and the
         * wheel roll — captured exactly like the trackball's. */
        Matrix3f invView = this.drag.view.get3x3(new Matrix3f()).invert();

        parentInverse.transform(invView.getColumn(0, new Vector3f()).normalize(), this.trackballRightLocal);
        parentInverse.transform(invView.getColumn(1, new Vector3f()).normalize(), this.trackballUpLocal);
        parentInverse.transform(invView.getColumn(2, new Vector3f()).normalize(), this.trackballViewLocal);

        if (this.trackballRightLocal.lengthSquared() < 1.0E-8F || this.trackballUpLocal.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        this.trackballRightLocal.normalize();
        this.trackballUpLocal.normalize();
        this.trackballViewLocal.normalize();

        Vector3f grab = this.mapArcball(mouseX, mouseY, new Vector3f());

        if (grab == null)
        {
            this.dragHasStart = false;

            return;
        }

        this.arcballParentInverse.transform(grab, this.arcballStartLocal).normalize();
        this.arcballCurrentLocal.set(this.arcballStartLocal);
        this.arcballAnchored = true;
        this.dragHasStart = true;
    }

    /**
     * Map the cursor to a unit direction from the sphere's centre, in world
     * space. The cursor ray is dropped onto the camera-facing plane through
     * the centre; inside the ball the offset lifts onto the sphere's surface,
     * outside it follows Bell's hyperbola, so the turn degrades into a
     * view-axis roll smoothly &mdash; no seam at the silhouette. Returns
     * {@code null} when the ray misses the plane entirely.
     */
    private Vector3f mapArcball(int mouseX, int mouseY, Vector3f out)
    {
        Vector3d hit = new Vector3d();

        if (!this.drag.intersectPlane(mouseX, mouseY, this.arcballViewWorld, hit))
        {
            return null;
        }

        float px = (float) (hit.x - this.drag.gizmoOrigin.x);
        float py = (float) (hit.y - this.drag.gizmoOrigin.y);
        float pz = (float) (hit.z - this.drag.gizmoOrigin.z);
        float r = this.arcballRadius;
        float dSq = px * px + py * py + pz * pz;
        float z = dSq < r * r / 2F
            ? (float) Math.sqrt(r * r - dSq)
            : r * r / (2F * (float) Math.sqrt(dSq));

        out.set(this.arcballViewWorld).mul(z).add(px, py, pz);

        return out.normalize();
    }

    /**
     * Turn the grabbed point to follow the cursor: the point picked on the
     * ball at anchor time stays under the fingertip as it drags.
     */
    private void applyRayRotateArcball(int mouseX, int mouseY)
    {
        Vector3f current = this.mapArcball(mouseX, mouseY, new Vector3f());

        if (current == null)
        {
            return;
        }

        this.arcballParentInverse.transform(current, this.arcballCurrentLocal).normalize();
        this.updateArcballRotation();
    }

    /**
     * Rebuild the arcball rotation from the cached start orientation plus the
     * single shortest arc from the anchored grab direction to the cursor's
     * current one (the folded {@link #arcballAccum} segments and the wheel
     * roll on top). Within one anchor the result is a pure function of the
     * cursor position, so backtracking the drag restores the exact starting
     * rotation. Shared by the cursor drag and the mouse-wheel roll.
     */
    private void updateArcballRotation()
    {
        Vector3f source = this.cache.rotate;

        Matrix3f startRotation = new Matrix3f()
            .rotationZ(source.z)
            .rotateY(source.y)
            .rotateX(source.x);

        Quaternionf arc = new Quaternionf()
            .rotationTo(this.arcballStartLocal, this.arcballCurrentLocal)
            .mul(this.arcballAccum);

        Matrix3f rotation = new Matrix3f()
            .rotation(MathUtils.toRad(this.trackballRollDeg), this.trackballViewLocal)
            .rotate(arc)
            .mul(startRotation);

        Vector3f live = this.transform.rotate;
        Vector3f euler = this.continuousEulerZYXDeg(rotation, live);

        this.setR(null, euler.x, euler.y, euler.z);
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

        /* Express the view axis once in the bone's parent frame; it stays
         * constant for the whole drag, while applyRayRotateView premultiplies
         * the live rotation by a turn about it. */
        Matrix3f parentInverse = this.computeParentInverse(this.transform.rotate);

        if (parentInverse == null)
        {
            this.dragHasStart = false;

            return;
        }

        parentInverse.transform(this.dragAxisDir, this.rotateParentAxis);

        if (this.rotateParentAxis.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        this.rotateParentAxis.normalize();
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
     * invisible. The spin axis is the constant parent-frame {@link #rotateParentAxis}.
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

        Vector3f source = this.transform.rotate;

        Matrix3f rotation = new Matrix3f()
            .rotation(angle, this.rotateParentAxis)
            .mul(new Matrix3f().rotationZ(source.z).rotateY(source.y).rotateX(source.x));

        Vector3f euler = this.continuousEulerZYXDeg(rotation, source);

        this.setR(null, euler.x, euler.y, euler.z);
    }

    private void beginRayRotate(int mouseX, int mouseY)
    {
        /* Every space composes the sweep about the ring the user actually grabbed, so the rendered
         * gizmo axis is the right one by definition — whether the gizmo is drawn in the bone's own
         * frame (LOCAL), the parent's (PARENT) or world-aligned (WORLD). */
        Vector3f axisDir = this.drag.gizmoWorldAxes.getColumn(this.axis.ordinal(), new Vector3f());

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

        Matrix3f parentInverse = this.computeParentInverse(this.transform.rotate);

        if (parentInverse == null)
        {
            this.dragHasStart = false;

            return;
        }

        parentInverse.transform(axisDir, this.rotateParentAxis);

        if (this.rotateParentAxis.lengthSquared() < 1.0E-8F)
        {
            this.dragHasStart = false;

            return;
        }

        this.rotateParentAxis.normalize();

        /* PARENT space turns about the parent frame's own axes, which in that frame
         * ARE the canonical basis vectors at any pose — so snap to the exact axis and
         * drop the numeric rotateAxes round-trip, whose ~1-2° tilt explodes into X/Z
         * runaway at the gimbal pole. The sign is kept from the rendered axis so a
         * cubic model's Ry(180) flip doesn't invert the X/Z drag direction. */
        if (this.getSpace() == TransformSpace.PARENT)
        {
            Vector3f canonical = new Vector3f(
                this.axis == Axis.X ? 1F : 0F,
                this.axis == Axis.Y ? 1F : 0F,
                this.axis == Axis.Z ? 1F : 0F
            );

            if (this.rotateParentAxis.dot(canonical) < 0F)
            {
                canonical.negate();
            }

            this.rotateParentAxis.set(canonical);
        }
        else if (this.getSpace() == TransformSpace.LOCAL)
        {
            /* LOCAL turns about the bone's OWN axes: the canonical basis vector
             * carried into the parent frame by the bone's start rotation
             * (Rstart · e_axis, Rstart = the cached start euler the drag composes
             * onto). At rest that's canonical; for a turned bone it follows the
             * bone. Exact, unlike the numeric estimate whose tilt blows X/Z up at
             * the pole. Sign kept from the rendered axis (cubic Ry(180) flip). */
            Vector3f boneAxis = new Matrix3f()
                .rotationZ(this.cache.rotate.z)
                .rotateY(this.cache.rotate.y)
                .rotateX(this.cache.rotate.x)
                .transform(new Vector3f(
                    this.axis == Axis.X ? 1F : 0F,
                    this.axis == Axis.Y ? 1F : 0F,
                    this.axis == Axis.Z ? 1F : 0F
                ));

            if (boneAxis.lengthSquared() > 1.0E-8F)
            {
                boneAxis.normalize();

                if (this.rotateParentAxis.dot(boneAxis) < 0F)
                {
                    boneAxis.negate();
                }

                this.rotateParentAxis.set(boneAxis);
            }
        }

        /* Freeze the ring at this orientation so it stays put while we drag,
         * instead of writhing as the live rotation is recomposed each frame. */
        Gizmo.INSTANCE.bakeRotation();

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
            return this.transform.rotate;
        }

        return this.transform.translate;
    }

    private void restore(boolean fully)
    {
        if (this.mode == 0 || fully) this.setT(null, this.cache.translate.x, this.cache.translate.y, this.cache.translate.z);
        if (this.mode == 1 || fully) this.setS(null, this.cache.scale.x, this.cache.scale.y, this.cache.scale.z);
        if (this.mode == 2 || fully) this.setR(null, MathUtils.toDeg(this.cache.rotate.x), MathUtils.toDeg(this.cache.rotate.y), MathUtils.toDeg(this.cache.rotate.z));
    }

    private void disable()
    {
        this.editing = false;
        this.axis2 = null;
        this.hotkeyMode = false;
        this.rotateKind = RotateKind.AXIS;
        this.translateScreen = false;
        this.drag = null;
        this.dragHasStart = false;
        this.arcballAnchored = false;
        this.fineHasLast = false;
        this.clearNumericInput();
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

    /* Numeric (keyboard) input for hotkey-driven transforms */

    /**
     * Numeric input only rides on the GSR keyboard operations ({@link #hotkeyMode}),
     * never on a mouse handle drag. Axis rotation additionally needs a picked ring
     * to apply the typed angle to; the screen-space view (arcball) and trackball
     * turns take the angle directly. Translate and scale always run on an axis
     * (X by default).
     */
    private boolean acceptsNumericInput()
    {
        if (!this.editing || !this.hotkeyMode || this.transform == null)
        {
            return false;
        }

        /* Screen-space grab spreads one drag across two camera axes, so a single typed
         * scalar is ambiguous — numeric input resumes once X/Y/Z constrains to an axis. */
        if (this.translateScreen)
        {
            return false;
        }

        if (this.mode != 2)
        {
            return true;
        }

        return this.rotateKind != RotateKind.AXIS || this.axis != null;
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
         * and constrain to a ring, the same as Z — otherwise they read as
         * dead keys. */
        if (this.mode == 2 && this.isSphereRotate() && this.numericActive
            && (key == GLFW.GLFW_KEY_X || key == GLFW.GLFW_KEY_Y))
        {
            this.trackballAxis = key == GLFW.GLFW_KEY_Y ? Axis.Y : Axis.X;
            this.applyNumericInput();

            return true;
        }

        int digit = numericDigit(key);

        if (digit >= 0)
        {
            this.activeNumericBuffer().append((char) ('0' + digit));
            this.numericActive = true;
            this.applyNumericInput();

            return true;
        }

        if (key == GLFW.GLFW_KEY_PERIOD || key == GLFW.GLFW_KEY_KP_DECIMAL)
        {
            StringBuilder buffer = this.activeNumericBuffer();

            if (buffer.indexOf(".") < 0)
            {
                if (buffer.length() == 0)
                {
                    buffer.append('0');
                }

                buffer.append('.');
                this.numericActive = true;
                this.applyNumericInput();
            }

            return true;
        }

        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT)
        {
            this.numericNegative = !this.numericNegative;
            this.numericActive = true;
            this.applyNumericInput();

            return true;
        }

        if (key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_KP_DIVIDE)
        {
            /* First '/' opens a divisor that subsequent typing feeds; a second
             * '/' drops it and returns to editing the dividend as usual. */
            if (this.numericDividing)
            {
                this.numericDivisor.setLength(0);
                this.numericDividing = false;
            }
            else
            {
                this.numericDividing = true;
            }

            this.numericActive = true;
            this.applyNumericInput();

            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE)
        {
            if (!this.numericActive)
            {
                return false;
            }

            if (this.numericDividing)
            {
                /* Editing the divisor: trim it, and once it is empty drop the
                 * '/' so editing falls back to the dividend. */
                if (this.numericDivisor.length() > 0)
                {
                    this.numericDivisor.deleteCharAt(this.numericDivisor.length() - 1);
                }
                else
                {
                    this.numericDividing = false;
                }

                this.applyNumericInput();

                return true;
            }

            if (this.numericInput.length() > 0)
            {
                this.numericInput.deleteCharAt(this.numericInput.length() - 1);
            }
            else
            {
                this.numericNegative = false;
            }

            if (this.numericInput.length() == 0 && !this.numericNegative)
            {
                this.stopNumericInput(context);
            }
            else
            {
                this.applyNumericInput();
            }

            return true;
        }

        return false;
    }

    /** The buffer keystrokes currently feed: the divisor while dividing, else the dividend. */
    private StringBuilder activeNumericBuffer()
    {
        return this.numericDividing ? this.numericDivisor : this.numericInput;
    }

    private static int numericDigit(int key)
    {
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
        {
            return key - GLFW.GLFW_KEY_0;
        }

        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9)
        {
            return key - GLFW.GLFW_KEY_KP_0;
        }

        return -1;
    }

    private double getNumericValue()
    {
        double value = parseNumeric(this.numericInput);

        if (this.numericNegative)
        {
            value = -value;
        }

        /* A zero or empty divisor leaves the dividend untouched, so the value
         * stays sensible until a non-zero divisor is typed. */
        if (this.numericDividing)
        {
            double divisor = parseNumeric(this.numericDivisor);

            if (divisor != 0D)
            {
                value /= divisor;
            }
        }

        return value;
    }

    private static double parseNumeric(CharSequence buffer)
    {
        if (buffer.length() == 0)
        {
            return 0D;
        }

        try
        {
            return Double.parseDouble(buffer.toString());
        }
        catch (NumberFormatException e)
        {
            return 0D;
        }
    }

    private String numericInputDisplay()
    {
        String display = (this.numericNegative ? "-" : "") + (this.numericInput.length() > 0 ? this.numericInput.toString() : "0");

        if (this.numericDividing)
        {
            display += " / " + this.numericDivisor;
        }

        return display;
    }

    private void clearNumericInput()
    {
        this.numericInput.setLength(0);
        this.numericDivisor.setLength(0);
        this.numericNegative = false;
        this.numericDividing = false;
        this.numericActive = false;
    }

    /**
     * Erasing the whole buffer cancels numeric mode: rewind to the operation's
     * start and re-anchor the cursor drag at the current pointer so mouse
     * control resumes without a jump.
     */
    private void stopNumericInput(UIContext context)
    {
        this.clearNumericInput();
        this.restore(true);

        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        /* The cursor was free to roam while typing; re-anchor the precision
         * tracking here so the resumed drag doesn't inherit a stale lag. */
        this.resetFineCursor(context.mouseX, context.mouseY);

        if (this.useRayDrag())
        {
            this.beginRayDrag(context.mouseX, context.mouseY);
        }

        this.setTransform(this.transform);
    }

    /**
     * Recompute the transform from {@link #cache} plus the typed amount. The
     * amount is an offset for translate (units) and rotate (degrees) and a
     * factor for scale, applied to the active axis (and {@link #axis2}, or every
     * axis when Ctrl scales uniformly).
     */
    private void applyNumericInput()
    {
        if (this.transform == null)
        {
            return;
        }

        double value = this.getNumericValue();

        switch (this.mode)
        {
            case 0:
                this.applyNumericTranslate(value);
                break;
            case 1:
                this.applyNumericScale(value);
                break;
            case 2:
                switch (this.rotateKind)
                {
                    case VIEW:
                        this.applyNumericView(value);
                        break;
                    case TRACKBALL:
                    case ARCBALL:
                        this.applyNumericTrackball(value);
                        break;
                    default:
                        this.applyNumericRotate(value);
                        break;
                }
                break;
        }

        this.setTransform(this.transform);
    }

    private void applyNumericTranslate(double value)
    {
        Vector3f offset = this.spaceTranslateDir(this.axis).mul((float) value);

        if (this.axis2 != null)
        {
            offset.add(this.spaceTranslateDir(this.axis2).mul((float) value));
        }

        this.setT(null,
            this.cache.translate.x + offset.x,
            this.cache.translate.y + offset.y,
            this.cache.translate.z + offset.z
        );
    }

    private void applyNumericScale(double value)
    {
        boolean all = Window.isCtrlPressed();
        Vector3f s = new Vector3f(this.cache.scale);

        if (all || this.axis == Axis.X || this.axis2 == Axis.X) s.x = (float) (this.cache.scale.x * value);
        if (all || this.axis == Axis.Y || this.axis2 == Axis.Y) s.y = (float) (this.cache.scale.y * value);
        if (all || this.axis == Axis.Z || this.axis2 == Axis.Z) s.z = (float) (this.cache.scale.z * value);

        this.setS(null, s.x, s.y, s.z);
    }

    private void applyNumericRotate(double value)
    {
        /* With a live ray anchor every space composes about the grabbed ring's axis (PARENT and
         * WORLD included — the parent/world fixed axes, not the bone's own). */
        if (this.useRayDrag() && this.dragHasStart)
        {
            this.applyAxisRotation(value, this.rotateParentAxis);

            return;
        }

        if (this.getSpace() == TransformSpace.LOCAL)
        {
            this.applyLocalBodyRotation(value, this.cache.rotate);

            return;
        }

        /* PARENT/WORLD without a ray anchor have no axis in reach; the channel add below stands
         * in — exact wherever parent == world. */

        Vector3f source = this.cache.rotate;

        float rx = MathUtils.toDeg(source.x);
        float ry = MathUtils.toDeg(source.y);
        float rz = MathUtils.toDeg(source.z);

        if (this.axis == Axis.X || this.axis2 == Axis.X) rx += value;
        if (this.axis == Axis.Y || this.axis2 == Axis.Y) ry += value;
        if (this.axis == Axis.Z || this.axis2 == Axis.Z) rz += value;

        this.setR(null, rx, ry, rz);
    }

    private void applyNumericView(double value)
    {
        this.applyAxisRotation(value, this.rotateParentAxis);
    }

    private void applyNumericTrackball(double value)
    {
        this.applyAxisRotation(value, this.trackballAxis == Axis.Y ? this.trackballRightLocal : this.trackballUpLocal);
    }

    /**
     * Premultiply the start orientation ({@link #cache}) by a turn of the given
     * degrees about a fixed parent-frame axis, then read the Euler angles back —
     * the composition shared by the view ring, the sphere's typed angles and the
     * LOCAL-space ring (cursor-driven and typed alike). {@code parentAxis} is
     * captured at drag start and stays constant; unwrapping against the live
     * values keeps the channels continuous across decomposition seams.
     */
    private void applyAxisRotation(double degrees, Vector3f parentAxis)
    {
        if (parentAxis.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        Vector3f source = this.cache.rotate;
        Vector3f live = this.transform.rotate;

        Matrix3f rotation = new Matrix3f()
            .rotation(MathUtils.toRad((float) degrees), parentAxis)
            .mul(new Matrix3f().rotationZ(source.z).rotateY(source.y).rotateX(source.x));

        Vector3f euler = this.continuousEulerZYXDeg(rotation, live);

        this.setR(null, euler.x, euler.y, euler.z);
    }

    /**
     * Post-multiply a base orientation by a turn about the bone's own axis (and
     * the optional second one) and write the Euler angles back. Without a ray
     * anchor this is the exact meaning of "rotate in local space" — it needs no
     * renderer or parent context at all, so it serves the classic cursor drag
     * (base = live, the single-axis composition telescopes exactly) and typed
     * input (base = the cached start).
     */
    private void applyLocalBodyRotation(double degrees, Vector3f baseRotate)
    {
        float rad = MathUtils.toRad((float) degrees);
        Matrix3f rotation = new Matrix3f()
            .rotationZ(baseRotate.z)
            .rotateY(baseRotate.y)
            .rotateX(baseRotate.x);

        rotateBodyAxis(rotation, rad, this.axis);

        if (this.axis2 != null)
        {
            rotateBodyAxis(rotation, rad, this.axis2);
        }

        Vector3f live = this.transform.rotate;
        Vector3f euler = this.continuousEulerZYXDeg(rotation, live);

        this.setR(null, euler.x, euler.y, euler.z);
    }

    private static void rotateBodyAxis(Matrix3f rotation, float rad, Axis axis)
    {
        if (axis == Axis.X) rotation.rotateX(rad);
        else if (axis == Axis.Y) rotation.rotateY(rad);
        else rotation.rotateZ(rad);
    }

    /* Blender-style snapping: every gesture is free by default and snaps to the
     * configured step only while Ctrl is held. Typed numeric input is exact
     * already, so it never snaps. */

    private boolean shouldSnap(int mode)
    {
        return this.editing && this.mode == mode && Window.isCtrlPressed() && !this.numericActive;
    }

    private static double snap(double value, float step)
    {
        return step <= 0F ? value : Math.round(value / step) * (double) step;
    }

    private double snapGizmoValue(double value)
    {
        if (this.rotateKind != RotateKind.AXIS || !this.shouldSnap(2))
        {
            return value;
        }

        return snap(value, BBSSettings.snapRotate.get());
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
        if (this.translateScreen)
        {
            return UIKeys.TRANSFORMS_TARGET_SCREEN.get();
        }

        if (this.mode == 2)
        {
            if (this.rotateKind == RotateKind.TRACKBALL) return UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_TRACKBALL.get();
            if (this.rotateKind == RotateKind.ARCBALL) return UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_ARCBALL.get();
            if (this.rotateKind == RotateKind.VIEW) return UIKeys.TRANSFORMS_TARGET_VIEW.get();
        }

        if (this.mode == 1 && Window.isCtrlPressed())
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
        boolean singleAxis = !this.translateScreen
            && this.axis != null && this.axis2 == null
            && (this.mode != 2 || this.rotateKind == RotateKind.AXIS)
            && !(this.mode == 1 && Window.isCtrlPressed());

        if (!singleAxis)
        {
            return Colors.WHITE;
        }

        if (this.axis == Axis.X) return Colors.A100 | Colors.RED;
        if (this.axis == Axis.Y) return Colors.A100 | Colors.GREEN;

        return Colors.A100 | Colors.BLUE;
    }

    /** Local/global chip; scale ignores the space toggle and the view/sphere
     *  rotations are screen-driven, so neither gets one. */
    private String editingSpaceLabel()
    {
        if (this.mode == 1 || (this.mode == 2 && this.rotateKind != RotateKind.AXIS))
        {
            return null;
        }

        TransformSpace space = this.getSpace();
        IKey label = space == TransformSpace.LOCAL ? UIKeys.TRANSFORMS_SPACE_LOCAL
            : space == TransformSpace.WORLD ? UIKeys.TRANSFORMS_SPACE_WORLD
            : UIKeys.TRANSFORMS_SPACE_GLOBAL;

        return label.get();
    }

    /**
     * Maintain the virtual cursor for the current frame. While Shift is held it
     * advances at {@link #FINE_DRAG_FACTOR} of the real cursor — the rest of the
     * motion piles into the lag offset; released, it tracks the cursor 1:1 again
     * with no jump. Ray gestures read {@link #fineX}/{@link #fineY} so they all
     * slow uniformly without any per-mode code.
     */
    private void updateFineCursor(int mouseX, int mouseY)
    {
        if (!this.fineHasLast)
        {
            this.resetFineCursor(mouseX, mouseY);

            return;
        }

        if (Window.isShiftPressed())
        {
            float keep = 1F - FINE_DRAG_FACTOR;

            this.fineOffsetX += (mouseX - this.fineLastX) * keep;
            this.fineOffsetY += (mouseY - this.fineLastY) * keep;
        }

        this.fineLastX = mouseX;
        this.fineLastY = mouseY;
    }

    private void resetFineCursor(int mouseX, int mouseY)
    {
        this.fineOffsetX = 0F;
        this.fineOffsetY = 0F;
        this.fineLastX = mouseX;
        this.fineLastY = mouseY;
        this.fineHasLast = true;
    }

    private int fineX(int mouseX)
    {
        return Math.round(mouseX - this.fineOffsetX);
    }

    private int fineY(int mouseY)
    {
        return Math.round(mouseY - this.fineOffsetY);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.editing && !this.numericActive && this.checker.isTime())
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

            this.updateFineCursor(context.mouseX, context.mouseY);

            if (rawX <= border)
            {
                Window.moveCursor(w - borderPadding, (int) mc.mouse.getY());

                this.lastX = context.menu.width - (int) (borderPadding / fx);
                this.checker.mark();

                /* The wrap re-anchors the drag at the teleported position, so the
                 * virtual cursor resets there too — no lag carries across the seam. */
                this.resetFineCursor(this.lastX, context.mouseY);

                if (this.useRayDrag()) this.beginRayDrag(this.lastX, context.mouseY);
            }
            else if (rawX >= w - border)
            {
                Window.moveCursor(borderPadding, (int) mc.mouse.getY());

                this.lastX = (int) (borderPadding / fx);
                this.checker.mark();

                this.resetFineCursor(this.lastX, context.mouseY);

                if (this.useRayDrag()) this.beginRayDrag(this.lastX, context.mouseY);
            }
            else if (this.useRayDrag())
            {
                this.applyRayDrag(this.fineX(context.mouseX), this.fineY(context.mouseY));
            }
            else
            {
                int dx = context.mouseX - this.lastX;
                Vector3f vector = this.getValue();
                boolean all = this.mode == 1 && Window.isCtrlPressed();
                UITrackpad reference = this.mode == 0 ? this.tx : (this.mode == 1 ? this.sx : this.rx);
                float factor = (float) reference.getValueModifier() * (Window.isShiftPressed() ? FINE_DRAG_FACTOR : 1F);

                if (this.mode == 0)
                {
                    Vector3f offset = this.spaceTranslateDir(this.axis).mul(factor * dx);

                    if (this.axis2 != null)
                    {
                        offset.add(this.spaceTranslateDir(this.axis2).mul(factor * dx));
                    }

                    this.setT(null, vector.x + offset.x, vector.y + offset.y, vector.z + offset.z);
                }
                else if (this.mode == 2 && this.getSpace() == TransformSpace.LOCAL)
                {
                    this.applyLocalBodyRotation(factor * dx, this.transform.rotate);
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

                    if (this.mode == 1) this.setS(null, vector3f.x, vector3f.y, vector3f.z);
                    if (this.mode == 2) this.setR(null, vector3f.x, vector3f.y, vector3f.z);
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

        if (this.spacesBarBackground)
        {
            this.spacesBar.area.render(context.batcher, Colors.A50);
        }

        UIDashboardPanels.renderHighlight(context.batcher, this.activeSpaceIcon().area, Direction.BOTTOM);

        if (this.mirror != null && BBSSettings.poseMirrorEdit.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.mirror.area, Direction.BOTTOM);
        }

        if (this.invert != null && BBSSettings.poseAlternateInvert.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.invert.area, Direction.BOTTOM);
        }

        super.render(context);

        if (this.editing)
        {
            FontRenderer font = context.batcher.getFont();
            String op = (this.mode == 0 ? UIKeys.TRANSFORMS_TRANSLATE : this.mode == 1 ? UIKeys.TRANSFORMS_SCALE : UIKeys.TRANSFORMS_ROTATE).get();
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

                if (this.mode == 2)
                {
                    val = MathUtils.toDeg(val);
                }

                String valueLabel = String.format(Locale.US, "%.2f", val);

                if (this.axis2 != null)
                {
                    float val2 = this.axis2 == Axis.X ? v.x : (this.axis2 == Axis.Y ? v.y : v.z);

                    if (this.mode == 2)
                    {
                        val2 = MathUtils.toDeg(val2);
                    }

                    valueLabel += ", " + String.format(Locale.US, "%.2f", val2);
                }

                /* While typing, lead with the raw input so the user sees exactly
                 * what they've entered, with the resulting value in parentheses. */
                String cursorLabel = this.numericActive
                    ? this.numericInputDisplay() + " (" + valueLabel + ")"
                    : valueLabel;

                if (this.numericActive)
                {
                    numericLabel = cursorLabel;
                }

                context.batcher.textCard(cursorLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
            }
            else if (this.numericActive)
            {
                /* The view ring and the sphere have no single axis component to
                 * echo, so show the typed angle, plus the aimed direction. */
                String prefix = this.isSphereRotate() ? (this.trackballAxis == Axis.Y ? "X" : "Y") : "";

                numericLabel = prefix + this.numericInputDisplay() + "°";

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
     * Which flavour of rotation a drag performs: around a single gizmo ring
     * ({@link #AXIS}), freely by cursor motion ({@link #TRACKBALL}), by
     * gripping a point on the ball ({@link #ARCBALL}), or in the screen plane
     * about the view axis ({@link #VIEW}) &mdash; the ring shared by all three
     * axes. The sphere starts trackball or arcball per the user's setting.
     */
    public enum RotateKind
    {
        AXIS, TRACKBALL, ARCBALL, VIEW;
    }

    /**
     * A step of a transform hotkey's walk. Tokens match the entries of the
     * translate/scale/rotate hotkey order settings.
     */
    public enum HotkeyTarget
    {
        VIEW("view", null, true),
        SPHERE("sphere", null, true),
        SCREEN("screen", null, true),
        X("x", Axis.X, false),
        Y("y", Axis.Y, false),
        Z("z", Axis.Z, false);

        public final String token;
        public final Axis axis;
        /** Whether the step is driven by the 3D ray and so needs a rendered gizmo. */
        public final boolean needsRay;

        HotkeyTarget(String token, Axis axis, boolean needsRay)
        {
            this.token = token;
            this.axis = axis;
            this.needsRay = needsRay;
        }

        public static HotkeyTarget byToken(String token)
        {
            for (HotkeyTarget target : values())
            {
                if (target.token.equals(token))
                {
                    return target;
                }
            }

            return null;
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
            /* While trackball-dragging the wheel rolls about the view axis (arcball);
             * during a screen-space grab it drives depth; otherwise it keeps adjusting
             * the drag sensitivity amplifier as before. */
            if (this.transform.scrollTrackballRoll(context))
            {
                return true;
            }

            if (this.transform.scrollDepth(context))
            {
                return true;
            }

            UITrackpad.updateAmplifier(context);

            return true;
        }
    }
}
