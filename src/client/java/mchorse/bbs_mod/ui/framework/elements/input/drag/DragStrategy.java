package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * One live transform gesture: a single operation in a single control style (axis ray,
 * screen plane, ring sweep, trackball...), owning all of its per-gesture state. The host
 * builds one when an edit starts, feeds it the cursor each frame and drops it at the end,
 * so no state leaks between gestures.
 *
 * <p>🔴 {@link #begin} is re-invoked on every cursor wrap, so implementations must be
 * idempotent about their accumulated amounts — never reset the user's progress.
 */
public abstract class DragStrategy
{
    /** Cursor-speed multiplier for a drag gesture while Shift is held (precision drag). */
    public static final float FINE_DRAG_FACTOR = 0.1F;

    /** Factor the modifier keys apply to a gizmo step: Ctrl coarsens, Alt refines. */
    protected static final float STEP_MODIFIER = 5F;

    /** Base degrees of view-axis roll per mouse-wheel notch while sphere-dragging. */
    protected static final float TRACKBALL_WHEEL_DEG = 5F;

    /* ── Drag debug logging ─────────────────────────────────────────────────
     * A throttled dump of the live gesture for diagnosing transform/gizmo bugs, written
     * to {@link #DRAG_LOG_FILE} in the game folder (not the console, which drowns in
     * per-frame spam) and TRUNCATED at the start of every gesture, so the file always
     * holds exactly the last drag. The thresholds below keep it compact. */

    /** Master switch for the per-drag dump. */
    private static final boolean LOG_DRAG = false;

    /** File in the game folder the gesture dump is written to (overwritten per gesture). */
    private static final String DRAG_LOG_FILE = "drag-log.txt";

    /** Minimum rotation (degrees) between two consecutive drag debug logs. */
    private static final float LOG_STEP_DEG = 5F;

    /** Minimum translation (channel units) between two consecutive drag debug logs. */
    private static final float LOG_STEP_TRANSLATE = 0.25F;

    /** Minimum per-axis scale change between two consecutive drag debug logs. */
    private static final float LOG_STEP_SCALE = 0.05F;

    protected final DragContext ctx;
    protected final TransformOp op;
    protected final Axis axis;
    protected final Axis axis2;

    /** Whether {@link #begin} anchored successfully and {@link #update} may run. */
    protected boolean hasStart;

    /* Snapshot of the last state that was logged, so {@link #logDrag} can throttle
     * by how far the gesture has moved since. Fresh per gesture (the host builds a
     * new strategy each edit), so it needs no explicit reset. */
    private boolean logInitialized;
    private int logCounter;
    private final Quaternionf logLastRotation = new Quaternionf();
    private final Vector3f logLastTranslate = new Vector3f();
    private final Vector3f logLastScale = new Vector3f();

    protected DragStrategy(DragContext ctx, TransformOp op, Axis axis, Axis axis2)
    {
        this.ctx = ctx;
        this.op = op;
        this.axis = axis;
        this.axis2 = axis2;
    }

    public final TransformOp op()
    {
        return this.op;
    }

    /** Anchor the gesture at the cursor. Re-invoked on cursor wraps. */
    public abstract void begin(int mouseX, int mouseY);

    /** Advance the gesture from the cursor position. */
    public abstract void update(int mouseX, int mouseY);

    /** Apply a typed numeric amount on top of the cached start transform. */
    public abstract void applyNumeric(double value);

    /**
     * Whether the host should feed the Shift-slowed virtual cursor. The
     * additive fallback damps Shift through its own factor instead, so it
     * reads the raw cursor.
     */
    public boolean usesFineCursor()
    {
        return true;
    }

    /** Consume a mouse-wheel event (depth move, sphere roll). */
    public boolean scroll(UIContext context)
    {
        return false;
    }

    /** On-screen summary of what the gesture changed so far, or {@code null}. */
    public String readout()
    {
        return null;
    }

    public boolean isSphere()
    {
        return false;
    }

    public boolean isView()
    {
        return false;
    }

    public boolean isScreenTranslate()
    {
        return false;
    }

    public boolean isScaleAll()
    {
        return false;
    }

    /** Special label for the editing chip row, or {@code null} for axis letters. */
    public String editingTargetLabel()
    {
        return null;
    }

    /** Whether typed numeric input may drive this gesture. */
    public boolean acceptsNumeric()
    {
        return true;
    }

    /** Aim the typed angle while on the sphere (X/Y keys); {@code false} elsewhere. */
    public boolean handleNumericAxisKey(int key)
    {
        return false;
    }

    /** Prefix of the numeric card while typing on the sphere. */
    public String numericPrefix()
    {
        return "";
    }

    /* Pie-preview data the gizmo renders during a rotation gesture. Default
     * to "nothing swept" so only the gestures that own a pie override. */

    /** Swept angle (degrees) of the rotation pie; {@code 0} when the gesture has none. */
    public float accumulatedRotateDeg()
    {
        return 0F;
    }

    /** Ring direction the pie starts from, or {@code null} when the gesture has none. */
    public Vector3f initialRingVec()
    {
        return null;
    }

    /**
     * The world axis the gesture ACTUALLY rotates about, for the pie's sweep
     * direction — the ring drag's own anchored axis, so the pie can never
     * disagree with the rotation (the drawn frame axis and the real turn axis
     * differ on the channel path: cubic models flip the channels' X/Z response).
     * {@code null} when the gesture has no fixed axis.
     */
    public Vector3f ringAxisDir()
    {
        return null;
    }

    /** Screen-space start edge of the view sweep pie (radians, Y-down convention). */
    public float viewGrabScreenAngle()
    {
        return 0F;
    }

    /** Signed screen-space span of the view sweep, in radians. */
    public float viewScreenSweepRad()
    {
        return 0F;
    }

    /**
     * Apply the shared modifier keys to a gizmo step amount: Alt makes it fine
     * (÷{@value #STEP_MODIFIER}), Ctrl makes it coarse (×{@value #STEP_MODIFIER}),
     * both/neither leave it as-is.
     */
    protected static float applyStepModifiers(float step)
    {
        if (Window.isAltPressed()) step /= STEP_MODIFIER;
        if (Window.isCtrlPressed()) step *= STEP_MODIFIER;

        return step;
    }

    protected static double snap(double value, float step)
    {
        return step <= 0F ? value : Math.round(value / step) * (double) step;
    }

    protected static void appendAxis(StringBuilder builder, String label, float value)
    {
        if (builder.length() > 0)
        {
            builder.append("  ");
        }

        builder.append(label).append(' ').append(String.format("%+.3f", value));
    }

    /** Per-axis delta readout filtered to this gesture's axes (or all of them). */
    protected String axisDeltaReadout(Vector3f delta, boolean allAxes)
    {
        StringBuilder builder = new StringBuilder();

        if (allAxes || this.axis == Axis.X || this.axis2 == Axis.X) appendAxis(builder, "X", delta.x);
        if (allAxes || this.axis == Axis.Y || this.axis2 == Axis.Y) appendAxis(builder, "Y", delta.y);
        if (allAxes || this.axis == Axis.Z || this.axis2 == Axis.Z) appendAxis(builder, "Z", delta.z);

        return builder.length() == 0 ? null : builder.toString();
    }

    /** Per-axis rotation deltas (degrees) of a free rotation, measured from the cache. */
    protected String freeRotateReadout()
    {
        Transform transform = this.ctx.transform();

        /* On a quaternion bone the channels are stale (the drag writes the quat),
         * so measure off the live rotations — the current one decomposed nearest
         * the grab base, so the displayed deltas stay small and continuous. */
        Vector3f start = RotationDragMath.sourceEuler(this.ctx.cache());
        Vector3f now = transform.rotationMode == Transform.RotationMode.QUATERNION
            ? Matrices.toCompatibleEulerZYXRadians(transform.quat, start, new Vector3f())
            : transform.rotate;

        return String.format("X %+.1f°  Y %+.1f°  Z %+.1f°",
            MathUtils.toDeg(now.x - start.x),
            MathUtils.toDeg(now.y - start.y),
            MathUtils.toDeg(now.z - start.z));
    }

    /** Whether this gesture must refuse because an enabled IK chain owns the rotation:
     *  the render follows the solve, so the gesture would sweep while the bone ignores it.
     *  🔴 Checked in BOTH {@code begin} and {@code applyNumeric} — typed degrees on a
     *  refused gesture must not write either. */
    protected boolean refuseConstrainedRotation()
    {
        return this.op == TransformOp.ROTATE && this.ctx.rotationConstrained();
    }

    /* Typed numeric amounts, applied on top of the cached start transform.
     * Shared here because the ray strategies and the additive fallback use
     * the exact same semantics: an offset for translate (units) and rotate
     * (degrees), a factor for scale. */

    /** The typed offset of a translate gesture: {@code value} WORLD units along the
     *  active frame's drawn axes, through {@link #spaceTranslateOffset} — the exact basis
     *  the ray drag slides along. Without a snapshot there is no frame to map through, and
     *  the fallbacks take over (analytic local vector for LOCAL, raw channels otherwise). */
    protected Vector3f numericTranslateOffset(double value)
    {
        Vector3f offset = this.spaceTranslateOffset(value, this.axis, this.axis2);

        if (offset != null)
        {
            return offset;
        }

        if (this.ctx.space().isLocal())
        {
            offset = this.ctx.localTranslateVector(value, this.axis);

            if (this.axis2 != null)
            {
                offset.add(this.ctx.localTranslateVector(value, this.axis2));
            }

            return offset;
        }

        offset = new Vector3f();

        if (this.axis == Axis.X || this.axis2 == Axis.X) offset.x = (float) value;
        if (this.axis == Axis.Y || this.axis2 == Axis.Y) offset.y = (float) value;
        if (this.axis == Axis.Z || this.axis2 == Axis.Z) offset.z = (float) value;

        return offset;
    }

    /** Channel offset of {@code value} world units along the frame's axes —
     *  {@code J⁻¹ · frameBasis(space)}, the same mapping the ray drag builds, kept in one
     *  place so typed input, the additive lever and the cursor drag can never disagree
     *  about what a frame's axis means. {@code null} without a snapshot or an axis. */
    protected Vector3f spaceTranslateOffset(double value, Axis axis, Axis axis2)
    {
        GizmoDrag drag = this.ctx.drag();

        if (drag == null || axis == null)
        {
            return null;
        }

        Matrix3f translateBasis = TranslateDrag.invertedJacobian(drag.translateJacobian).mul(drag.frameBasis(this.ctx.space()));
        Vector3f offset = translateBasis.getColumn(axis.ordinal(), new Vector3f()).mul((float) value);

        if (axis2 != null)
        {
            offset.add(translateBasis.getColumn(axis2.ordinal(), new Vector3f()).mul((float) value));
        }

        return offset;
    }

    protected void numericTranslate(double value)
    {
        Transform cache = this.ctx.cache();
        Vector3f offset = this.numericTranslateOffset(value);

        this.ctx.writeTranslate(
            cache.translate.x + offset.x,
            cache.translate.y + offset.y,
            cache.translate.z + offset.z
        );
    }

    protected void numericScale(double value, boolean all)
    {
        Transform cache = this.ctx.cache();
        Vector3f s = new Vector3f(cache.scale);

        if (all || this.axis == Axis.X || this.axis2 == Axis.X) s.x = (float) (cache.scale.x * value);
        if (all || this.axis == Axis.Y || this.axis2 == Axis.Y) s.y = (float) (cache.scale.y * value);
        if (all || this.axis == Axis.Z || this.axis2 == Axis.Z) s.z = (float) (cache.scale.z * value);

        this.ctx.writeScale(s.x, s.y, s.z);
    }

    protected void numericRotate(double value)
    {
        boolean quatMode = this.ctx.transform().rotationMode == Transform.RotationMode.QUATERNION;

        /* In quaternion mode the euler channels are stale, so read the base off
         * the cache quaternion (its ZYX equivalent), bump the axis, and store
         * the result back as a quaternion — the single-axis add is exact, so
         * this stays gimbal-safe. */
        Vector3f source = quatMode
            ? Matrices.toEulerZYXRadians(this.ctx.cache().quat, new Vector3f())
            : this.ctx.cache().rotate;

        float rx = MathUtils.toDeg(source.x);
        float ry = MathUtils.toDeg(source.y);
        float rz = MathUtils.toDeg(source.z);

        if (this.axis == Axis.X || this.axis2 == Axis.X) rx += value;
        if (this.axis == Axis.Y || this.axis2 == Axis.Y) ry += value;
        if (this.axis == Axis.Z || this.axis2 == Axis.Z) rz += value;

        if (quatMode) this.ctx.writeRotationQuat(Matrices.toQuaternionZYXDegrees(rx, ry, rz));
        else this.ctx.writeRotateDeg(rx, ry, rz);
    }

    /**
     * Premultiply the start orientation (the cache) by a turn of the typed
     * degrees about a fixed parent-frame axis, then read the euler angles
     * back — the same composition the cursor-driven view/trackball drags
     * use, but from a single exact angle.
     */
    protected void numericAxisRotation(double degrees, Vector3f localAxis)
    {
        if (localAxis.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        Vector3f source = this.ctx.cache().rotate;

        RotationDragMath.applyLocalDelta(
            this.ctx,
            new Matrix3f().rotation(MathUtils.toRad((float) degrees), localAxis),
            source
        );
    }

    /* ── Drag debug logging ──────────────────────────────────────────────── */

    /** Dump the live gesture to {@link #DRAG_LOG_FILE}, throttled by the LOG_STEP_*
     *  thresholds. Called once per drag frame right after {@link #update}; a no-op unless
     *  {@link #LOG_DRAG} is on. Truncated on the gesture's first dump. */
    public final void logDrag()
    {
        if (!LOG_DRAG)
        {
            return;
        }

        Transform now = this.ctx.transform();
        Quaternionf rotation = now.createRotation();

        boolean firstOfGesture = !this.logInitialized;
        float rotStep = firstOfGesture ? Float.MAX_VALUE : quatAngleDeg(this.logLastRotation, rotation);
        float transStep = firstOfGesture ? Float.MAX_VALUE : now.translate.distance(this.logLastTranslate);
        float scaleStep = firstOfGesture ? Float.MAX_VALUE : now.scale.distance(this.logLastScale);

        String reason;

        if (firstOfGesture) reason = "grab";
        else if (rotStep >= LOG_STEP_DEG) reason = String.format("Δrot %.1f°", rotStep);
        else if (transStep >= LOG_STEP_TRANSLATE) reason = String.format("Δpos %.3f", transStep);
        else if (scaleStep >= LOG_STEP_SCALE) reason = String.format("Δscale %.3f", scaleStep);
        else return; /* Below every threshold — stay quiet this frame. */

        this.logLastRotation.set(rotation);
        this.logLastTranslate.set(now.translate);
        this.logLastScale.set(now.scale);
        this.logInitialized = true;
        this.logCounter++;

        File file = BBSMod.getGamePath(DRAG_LOG_FILE);

        writeDragLog(file, this.buildDragLog(reason, rotation), firstOfGesture);

        /* One console breadcrumb per gesture so the console stays quiet but it's
         * obvious logging is live and where to look. */
        if (firstOfGesture)
        {
            System.out.println("[gizmo drag] logging gesture to " + file.getAbsolutePath());
        }
    }

    /** Append (or overwrite, when {@code truncate}) one dump block to the log file. */
    private static void writeDragLog(File file, String text, boolean truncate)
    {
        try (FileWriter writer = new FileWriter(file, !truncate))
        {
            writer.write(text);
        }
        catch (IOException e)
        {
            System.out.println("[gizmo drag] failed to write " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private String buildDragLog(String reason, Quaternionf nowQuat)
    {
        Transform cache = this.ctx.cache();
        Transform now = this.ctx.transform();
        TransformSpace space = this.ctx.space();

        Vector3f cacheEuler = cache.getEulerRotation(new Vector3f());
        Vector3f nowEuler = now.getEulerRotation(new Vector3f());
        float turnedDeg = quatAngleDeg(cache.createRotation(), nowQuat);

        StringBuilder b = new StringBuilder();

        b.append("\n──────── gizmo drag #").append(this.logCounter).append("  (").append(reason).append(") ────────\n");
        b.append("  op=").append(this.op)
            .append("  style=").append(this.getClass().getSimpleName())
            .append("  axis=").append(this.axis).append(this.axis2 != null ? ("+" + this.axis2) : "")
            .append("  space=").append(space)
            .append("  mode=").append(now.rotationMode)
            .append("  target=").append(this.ctx.targetName()).append('\n');
        b.append("  flags: model=").append(this.ctx.isModel())
            .append(" sphere=").append(this.isSphere())
            .append(" view=").append(this.isView())
            .append(" screenT=").append(this.isScreenTranslate())
            .append(" sphereR=").append(fmt(this.ctx.sphereWorldRadius())).append('\n');

        b.append("  translate: ").append(fmtVec(cache.translate)).append(" -> ").append(fmtVec(now.translate))
            .append("   Δ ").append(fmtVec(new Vector3f(now.translate).sub(cache.translate))).append('\n');
        b.append("  scale:     ").append(fmtVec(cache.scale)).append(" -> ").append(fmtVec(now.scale))
            .append("   Δ ").append(fmtVec(new Vector3f(now.scale).sub(cache.scale))).append('\n');
        b.append("  euler°:    ").append(fmtVecDeg(cacheEuler)).append(" -> ").append(fmtVecDeg(nowEuler))
            .append("   Δ ").append(fmtVecDeg(new Vector3f(nowEuler).sub(cacheEuler))).append('\n');
        b.append("  quat:      ").append(fmtQuat(nowQuat))
            .append("   turned ").append(fmt(turnedDeg)).append("° from grab\n");

        GizmoDrag drag = this.ctx.drag();

        if (drag != null)
        {
            b.append("  rotateAxes     ").append(fmtBasis(drag.rotateAxes))
                .append(drag.hasRotateAxes ? "  [measured]" : "  [UNSET - identity default]").append('\n');
            b.append("  frameAxes set  ").append(drag.hasFrameAxes()).append('\n');
            b.append("  gizmoWorldAxes ").append(fmtBasis(drag.gizmoWorldAxes)).append('\n');
            b.append("  frameBasis     ").append(fmtBasis(drag.frameBasis(space))).append('\n');
        }
        else
        {
            b.append("  gizmo frame: <no ray drag>\n");
        }

        float pieDeg = this.accumulatedRotateDeg();
        String readout = this.readout();

        if (pieDeg != 0F)
        {
            b.append("  pie=").append(fmt(pieDeg)).append("°  viewSweep=").append(fmt(this.viewScreenSweepRad())).append(" rad\n");
        }

        if (readout != null)
        {
            b.append("  readout: ").append(readout).append('\n');
        }

        return b.toString();
    }

    /** Shortest-arc angle between two rotations, in degrees. */
    private static float quatAngleDeg(Quaternionf a, Quaternionf b)
    {
        Quaternionf delta = new Quaternionf(a).conjugate().mul(b).normalize();

        return (float) Math.toDegrees(2.0 * Math.acos(Math.min(1F, Math.abs(delta.w))));
    }

    private static String fmt(float value)
    {
        return String.format("%.3f", value);
    }

    private static String fmtVec(Vector3f v)
    {
        return String.format("(%+.3f, %+.3f, %+.3f)", v.x, v.y, v.z);
    }

    private static String fmtVecDeg(Vector3f radians)
    {
        return String.format("(%+.2f, %+.2f, %+.2f)", MathUtils.toDeg(radians.x), MathUtils.toDeg(radians.y), MathUtils.toDeg(radians.z));
    }

    private static String fmtQuat(Quaternionf q)
    {
        return String.format("(w%+.4f x%+.4f y%+.4f z%+.4f)", q.w, q.x, q.y, q.z);
    }

    private static String fmtBasis(Matrix3f m)
    {
        return String.format("X%s Y%s Z%s",
            fmtVec(m.getColumn(0, new Vector3f())),
            fmtVec(m.getColumn(1, new Vector3f())),
            fmtVec(m.getColumn(2, new Vector3f())));
    }
}
