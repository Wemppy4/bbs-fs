package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.IWorldTransformProvider;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.WorldTransformClipboard;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * Transformation editor GUI (compact layout: CONTROL_HEIGHT rows, MARGIN between).
 */
public abstract class UITransform extends UIElement
{
    public UITrackpad tx;
    public UITrackpad ty;
    public UITrackpad tz;
    public UITrackpad sx;
    public UITrackpad sy;
    public UITrackpad sz;
    public UITrackpad rx;
    public UITrackpad ry;
    public UITrackpad rz;

    protected UIIcon iconT;
    protected UIIcon iconS;
    protected UIIcon iconR;

    protected UIElement scaleRow;

    private boolean uniformDrag;
    private boolean uniformScale;

    /** Refinement passes for the non-linear (rotation/scale) part of a world paste. */
    private static final int WORLD_PASTE_ITERATIONS = 6;

    /** Host hook for world-space copy/paste; when null those context actions don't appear. */
    private IWorldTransformProvider worldProvider;

    public UITransform()
    {
        super();

        IKey raw = IKey.constant("%s (%s)");

        this.tx = new UITrackpad((value) -> this.internalSetT(value, Axis.X)).block().onlyNumbers();
        this.tx.tooltip(raw.format(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_X));
        this.tx.textbox.setColor(Colors.RED);
        this.ty = new UITrackpad((value) -> this.internalSetT(value, Axis.Y)).block().onlyNumbers();
        this.ty.tooltip(raw.format(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Y));
        this.ty.textbox.setColor(Colors.GREEN);
        this.tz = new UITrackpad((value) -> this.internalSetT(value, Axis.Z)).block().onlyNumbers();
        this.tz.tooltip(raw.format(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Z));
        this.tz.textbox.setColor(Colors.BLUE);

        this.sx = new UITrackpad((value) ->
        {
            this.internalSetS(value, Axis.X);
            this.syncScale(value);
        }).disableCanceling();
        this.sx.onlyNumbers().tooltip(raw.format(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_X));
        this.sx.textbox.setColor(Colors.RED);
        this.sy = new UITrackpad((value) ->
        {
            this.internalSetS(value, Axis.Y);
            this.syncScale(value);
        }).disableCanceling();
        this.sy.onlyNumbers().tooltip(raw.format(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Y));
        this.sy.textbox.setColor(Colors.GREEN);
        this.sz = new UITrackpad((value) ->
        {
            this.internalSetS(value, Axis.Z);
            this.syncScale(value);
        }).disableCanceling();
        this.sz.onlyNumbers().tooltip(raw.format(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Z));
        this.sz.textbox.setColor(Colors.BLUE);

        this.rx = new UITrackpad((value) -> this.internalSetR(value, Axis.X)).degrees().onlyNumbers();
        this.rx.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_X));
        this.rx.textbox.setColor(Colors.RED);
        this.ry = new UITrackpad((value) -> this.internalSetR(value, Axis.Y)).degrees().onlyNumbers();
        this.ry.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Y));
        this.ry.textbox.setColor(Colors.GREEN);
        this.rz = new UITrackpad((value) -> this.internalSetR(value, Axis.Z)).degrees().onlyNumbers();
        this.rz.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Z));
        this.rz.textbox.setColor(Colors.BLUE);

        this.w(1F).column(2).stretch().vertical();

        this.iconT = new UIIcon(Icons.ALL_DIRECTIONS, null);
        this.iconS = new UIIcon(Icons.SCALE, (b) -> this.toggleUniformScale());
        this.iconS.tooltip(UIKeys.TRANSFORMS_UNIFORM_SCALE);
        this.iconR = new UIIcon(Icons.REFRESH, null);

        this.iconT.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        this.iconS.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        this.iconR.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);

        this.iconT.disabledColor = this.iconS.disabledColor = this.iconR.disabledColor = Colors.WHITE;
        this.iconT.hoverColor = this.iconS.hoverColor = this.iconR.hoverColor = Colors.WHITE;

        this.iconT.setEnabled(false);
        this.iconR.setEnabled(false);

        this.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.iconT, this.tx, this.ty, this.tz));
        this.add(this.scaleRow = UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.iconS, this.sx, this.sy, this.sz));
        this.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.iconR, this.rx, this.ry, this.rz));

        this.context((menu) ->
        {
            ListType transforms = Window.getClipboardList();

            if (transforms != null && transforms.size() < 9)
            {
                transforms = null;
            }

            menu.autoKeys().action(Icons.COPY, UIKeys.TRANSFORMS_CONTEXT_COPY, this::copyTransformations);

            if (transforms != null)
            {
                final ListType innerList = transforms;

                menu.action(Icons.PASTE, UIKeys.TRANSFORMS_CONTEXT_PASTE, () -> this.pasteAll(innerList));
                menu.action(Icons.ALL_DIRECTIONS, UIKeys.TRANSFORMS_CONTEXT_PASTE_TRANSLATION, () -> this.pasteTranslation(this.getVector(innerList, 0)));
                menu.action(Icons.MAXIMIZE, UIKeys.TRANSFORMS_CONTEXT_PASTE_SCALE, () -> this.pasteScale(this.getVector(innerList, 3)));
                menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_PASTE_ROTATION, () -> this.pasteRotation(this.getVector(innerList, 6)));
            }

            if (this.worldProvider != null && this.worldProvider.getWorldMatrix(new Matrix4f()))
            {
                menu.action(Icons.GLOBE, UIKeys.TRANSFORMS_CONTEXT_COPY_WORLD, this::copyWorldTransform);

                if (WorldTransformClipboard.has())
                {
                    menu.action(Icons.GLOBE, UIKeys.TRANSFORMS_CONTEXT_PASTE_WORLD, this::pasteWorldTransform);
                }
            }

            menu.action(Icons.CLOSE, UIKeys.TRANSFORMS_CONTEXT_RESET, this::reset);
        });

        this.w(190).h(3 * UIConstants.CONTROL_HEIGHT);

        this.keys().register(Keys.COPY, this::copyTransformations).inside().label(UIKeys.TRANSFORMS_CONTEXT_COPY);
        this.keys().register(Keys.PASTE, () ->
        {
            ListType transforms = Window.getClipboardList();

            if (transforms != null && transforms.size() < 9)
            {
                transforms = null;
            }

            if (transforms != null)
            {
                this.pasteAll(transforms);
            }
        }).inside().label(UIKeys.TRANSFORMS_CONTEXT_PASTE);
    }

    protected void toggleUniformScale()
    {
        this.uniformScale = !this.uniformScale;

        this.scaleRow.removeAll();

        if (this.uniformScale)
        {
            this.scaleRow.add(this.iconS, this.sx);
        }
        else
        {
            this.scaleRow.add(this.iconS, this.sx, this.sy, this.sz);
        }

        UIElement parentContainer = this.getParentContainer();

        if (parentContainer != null)
        {
            parentContainer.resize();
        }
    }

    protected boolean isUniformScale()
    {
        return this.uniformDrag || Window.isKeyPressed(GLFW.GLFW_KEY_SPACE);
    }

    private void syncScale(double value)
    {
        if (this.isUniformScale())
        {
            this.fillS(value, value, value);
            this.setS(null, value, value, value);
        }
    }

    public void fillSetT(double x, double y, double z)
    {
        this.fillT(x, y, z);
        this.setT(null, x, y, z);
    }

    public void fillSetS(double x, double y, double z)
    {
        this.fillS(x, y, z);
        this.setS(null, x, y, z);
    }

    public void fillSetR(double x, double y, double z)
    {
        this.fillR(x, y, z);
        this.setR(null, x, y, z);
    }

    public void fillT(double x, double y, double z)
    {
        this.tx.setValue(x);
        this.ty.setValue(y);
        this.tz.setValue(z);
    }

    public void fillS(double x, double y, double z)
    {
        this.sx.setValue(x);
        this.sy.setValue(y);
        this.sz.setValue(z);
    }

    public void fillR(double x, double y, double z)
    {
        this.rx.setValue(x);
        this.ry.setValue(y);
        this.rz.setValue(z);
    }

    protected void internalSetT(double x, Axis axis)
    {
        try
        {
            this.setT(axis,
                axis == Axis.X ? x : this.tx.value,
                axis == Axis.Y ? x : this.ty.value,
                axis == Axis.Z ? x : this.tz.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void internalSetS(double x, Axis axis)
    {
        try
        {
            if (this.uniformScale && axis == Axis.X)
            {
                this.setS(axis, x, x, x);
                this.sy.setValue(x);
                this.sz.setValue(x);

                return;
            }

            this.setS(axis,
                axis == Axis.X ? x : this.sx.value,
                axis == Axis.Y ? x : this.sy.value,
                axis == Axis.Z ? x : this.sz.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void internalSetR(double x, Axis axis)
    {
        try
        {
            this.setR(axis,
                axis == Axis.X ? x : this.rx.value,
                axis == Axis.Y ? x : this.ry.value,
                axis == Axis.Z ? x : this.rz.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public abstract void setT(Axis axis, double x, double y, double z);

    public abstract void setS(Axis axis, double x, double y, double z);

    public abstract void setR(Axis axis, double x, double y, double z);

    private void copyTransformations()
    {
        ListType list = new ListType();

        list.addDouble(this.tx.value);
        list.addDouble(this.ty.value);
        list.addDouble(this.tz.value);
        list.addDouble(this.sx.value);
        list.addDouble(this.sy.value);
        list.addDouble(this.sz.value);
        list.addDouble(this.rx.value);
        list.addDouble(this.ry.value);
        list.addDouble(this.rz.value);

        Window.setClipboard(list);
    }

    public void pasteAll(ListType list)
    {
        this.pasteTranslation(this.getVector(list, 0));
        this.pasteScale(this.getVector(list, 3));
        this.pasteRotation(this.getVector(list, 6));
    }

    public void pasteTranslation(Vector3d translation)
    {
        this.fillSetT(translation.x, translation.y, translation.z);
    }

    public void pasteScale(Vector3d scale)
    {
        this.fillSetS(scale.x, scale.y, scale.z);
    }

    public void pasteRotation(Vector3d rotation)
    {
        this.fillSetR(rotation.x, rotation.y, rotation.z);
    }

    private Vector3d getVector(ListType list, int offset)
    {
        Vector3d result = new Vector3d();

        if (list.get(offset).isNumeric() && list.get(offset + 1).isNumeric() && list.get(offset + 2).isNumeric())
        {
            result.x = list.get(offset).asNumeric().doubleValue();
            result.y = list.get(offset + 1).asNumeric().doubleValue();
            result.z = list.get(offset + 2).asNumeric().doubleValue();
        }

        if (offset == 0)
        {
            result.x *= Window.isShiftPressed() ? -1 : 1;
        }

        if (offset >= 6)
        {
            result.y *= Window.isShiftPressed() ? -1 : 1;
            result.z *= Window.isShiftPressed() ? -1 : 1;
        }

        return result;
    }

    /**
     * Give this editor a way to read its element's world matrices, which enables the world-space
     * copy/paste actions in the context menu. Hosts without a hierarchy/tick context leave it unset.
     */
    public UITransform worldTransform(IWorldTransformProvider provider)
    {
        this.worldProvider = provider;

        return this;
    }

    /** Capture the element's current full world matrix into the shared world clipboard. */
    private void copyWorldTransform()
    {
        if (this.worldProvider == null)
        {
            return;
        }

        Matrix4f world = new Matrix4f();

        if (this.worldProvider.getWorldMatrix(world))
        {
            WorldTransformClipboard.set(world);
        }
    }

    /** The transform this editor mutates, exposed for the world-space solve. {@code null} when the
     *  host doesn't back the editor with one — then world paste does nothing. */
    protected Transform getEditedTransform()
    {
        return null;
    }

    /**
     * Drive this element's transform until its world matrix matches the captured one. An analytic
     * decompose can't be trusted here: the renderer composes a bone's pose in its own frame (the
     * cubic /16 pixel scale, a post-applied {@code Ry(180)} that flips local X/Z, parent chains),
     * so the matrix the editor would decompose lives in a different frame than the channels it
     * writes. Instead we solve numerically against a live world-matrix sampler, the same
     * finite-difference approach the gizmo drag uses, which measures those quirks instead of
     * assuming them away.
     *
     * <p>Translation is linear in {@code translate} and independent of rotation/scale, so it solves
     * exactly in one step through the local→world Jacobian. Rotation and scale are non-linear, so
     * they are refined over a few passes around the current pose using the renderer's true
     * per-channel rotation axes.
     */
    private void pasteWorldTransform()
    {
        Matrix4f cached = WorldTransformClipboard.get();
        Transform transform = this.getEditedTransform();

        if (cached == null || this.worldProvider == null || transform == null)
        {
            return;
        }

        if (!this.worldProvider.getWorldMatrix(new Matrix4f()))
        {
            return;
        }

        Supplier<Matrix4f> sampler = () ->
        {
            Matrix4f matrix = new Matrix4f();

            this.worldProvider.getWorldMatrix(matrix);

            return matrix;
        };

        Vector3f startTranslate = new Vector3f(transform.translate);
        Vector3f startRotate = new Vector3f(transform.rotate);
        Vector3f startScale = new Vector3f(transform.scale);

        Vector3f targetScale = new Vector3f();
        Matrix3f targetRotation = orthonormalize(cached.get3x3(new Matrix3f()), targetScale);
        Vector3f targetPosition = cached.getTranslation(new Vector3f());

        Matrix3f jacobian = GizmoDrag.computeTranslateJacobian(transform, () -> sampler.get().getTranslation(new Vector3f()));

        if (Math.abs(jacobian.determinant()) > 1.0E-9F)
        {
            Vector3f error = targetPosition.sub(sampler.get().getTranslation(new Vector3f()), new Vector3f());

            jacobian.invert().transform(error);
            transform.translate.add(error);
        }

        for (int i = 0; i < WORLD_PASTE_ITERATIONS; i++)
        {
            Matrix3f axes = GizmoDrag.computeRotateAxes(transform, sampler);

            if (Math.abs(axes.determinant()) > 1.0E-9F)
            {
                Matrix3f current = orthonormalize(sampler.get().get3x3(new Matrix3f()), new Vector3f());
                Matrix3f delta = new Matrix3f(targetRotation).mul(current.invert());
                AxisAngle4f axisAngle = new AxisAngle4f().set(delta);
                Vector3f error = new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z).mul(axisAngle.angle);

                axes.invert().transform(error);
                transform.rotate.add(error);
            }

            this.solveScale(transform, sampler, targetScale);
        }

        Vector3f finalTranslate = new Vector3f(transform.translate);
        Vector3f finalRotate = new Vector3f(transform.rotate);
        Vector3f finalScale = new Vector3f(transform.scale);

        /* The solve used the live transform as scratch; hand the net result to the editor's own
         * apply path (undo, notify, multi-bone) from the original values. */
        transform.translate.set(startTranslate);
        transform.rotate.set(startRotate);
        transform.scale.set(startScale);

        this.fillSetT(finalTranslate.x, finalTranslate.y, finalTranslate.z);
        this.fillSetS(finalScale.x, finalScale.y, finalScale.z);
        this.fillSetR(MathUtils.toDeg(finalRotate.x), MathUtils.toDeg(finalRotate.y), MathUtils.toDeg(finalRotate.z));
    }

    /**
     * Nudge each scale channel so the world basis column lengths reach {@code targetScale},
     * measuring each channel's effect on its world column numerically (one extra sample per axis).
     */
    private void solveScale(Transform transform, Supplier<Matrix4f> sampler, Vector3f targetScale)
    {
        float epsilon = 0.05F;
        Vector3f current = new Vector3f();

        orthonormalize(sampler.get().get3x3(new Matrix3f()), current);

        for (int axis = 0; axis < 3; axis++)
        {
            float saved = transform.scale.get(axis);

            transform.scale.setComponent(axis, saved + epsilon);

            Vector3f perturbed = new Vector3f();

            orthonormalize(sampler.get().get3x3(new Matrix3f()), perturbed);
            transform.scale.setComponent(axis, saved);

            float slope = (perturbed.get(axis) - current.get(axis)) / epsilon;

            if (Math.abs(slope) > 1.0E-6F)
            {
                transform.scale.setComponent(axis, saved + (targetScale.get(axis) - current.get(axis)) / slope);
            }
        }
    }

    /**
     * Normalize a basis's columns to unit length, returning the lengths in {@code scaleOut} and
     * leaving the orthonormal rotation in {@code basis}. Assumes a rigid-plus-scale basis (no shear).
     */
    private static Matrix3f orthonormalize(Matrix3f basis, Vector3f scaleOut)
    {
        Vector3f column = new Vector3f();

        float sx = basis.getColumn(0, column).length();
        float sy = basis.getColumn(1, column).length();
        float sz = basis.getColumn(2, column).length();

        scaleOut.set(sx, sy, sz);

        if (sx > 1.0E-6F) basis.setColumn(0, basis.getColumn(0, column).div(sx));
        if (sy > 1.0E-6F) basis.setColumn(1, basis.getColumn(1, column).div(sy));
        if (sz > 1.0E-6F) basis.setColumn(2, basis.getColumn(2, column).div(sz));

        return basis;
    }

    protected void reset()
    {
        this.fillSetT(0, 0, 0);
        this.fillSetS(1, 1, 1);
        this.fillSetR(0, 0, 0);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.sx.area.isInside(context) || this.sy.area.isInside(context) || this.sz.area.isInside(context))
        {
            if (context.mouseButton == 1 && (this.sx.isDragging() || this.sy.isDragging() || this.sz.isDragging()))
            {
                this.uniformDrag = true;

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 1)
        {
            this.uniformDrag = false;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.sx.isDragging() || this.sy.isDragging() || this.sz.isDragging())
        {
            if (context.isHeld(GLFW.GLFW_KEY_SPACE))
            {
                return true;
            }
        }

        return super.subKeyPressed(context);
    }
}