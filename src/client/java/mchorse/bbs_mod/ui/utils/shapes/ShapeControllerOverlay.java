package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.ui.utils.ScreenOverlay;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One projection/picking/drag implementation for model, form, state and film previews, drawn on the
 * viewport's markup ({@link ScreenOverlay}): a rounded diamond for height (and shift), a dot for tilt.
 */
public class ShapeControllerOverlay
{
    private static final int COLOR = 0xFFABB4C0;
    /** {@code side}: the screen step of one pixel sideways, on a handle that also travels along {@link ShapeController#SHIFT}. */
    private record Handle(ShapeController controller, int axis, Vector3f screen, Vector3f direction, Vector3f side) {}
    private final List<Handle> handles = new ArrayList<>();
    private ScreenOverlay overlay;
    private UIShapeControllers controls;
    private Handle drag;
    private int mouseX, mouseY;
    private float[] initial, last;
    private Runnable begin = () -> {}, end = () -> {};
    private final DoubleClick<String> doubleClick = new DoubleClick<>(false);
    private boolean draggingAxis(ShapeController c, int axis)
    {
        return axis == ShapeController.TILT ? c.hasAxis(ShapeController.TILT)
            : c.hasAxis(ShapeController.HEIGHT) || c.hasAxis(ShapeController.SHIFT);
    }

    public boolean release()
    {
        if (this.drag == null) return false;
        this.drag = null;
        this.end.run();
        return true;
    }

    public void clear()
    {
        this.release(); this.handles.clear(); this.controls = null;
    }

    public boolean click(UIContext context)
    {
        return this.click(context, false);
    }

    /** Placement mode selects a handle without deforming the preview. */
    public boolean click(UIContext context, boolean selectOnly)
    {
        if ((context.mouseButton != 0 && context.mouseButton != 1) || this.controls == null || this.overlay == null
            || !this.overlay.getViewport().isInside(context)) return false;
        Handle best = this.pick(context.mouseX, context.mouseY);
        if (best == null) return false;
        this.controls.select(best.controller);
        if (context.mouseButton == 1)
        {
            this.controls.openPresets(context);
            return true;
        }
        if (selectOnly) return true;
        if (this.doubleClick.hit(System.identityHashCode(best.controller) + ":" + best.axis))
        {
            /* Double click: the handle's own axes back to rest, as one undo step. */
            this.begin.run();
            if (best.axis == ShapeController.TILT) this.controls.reset(best.controller, ShapeController.TILT);
            else this.controls.reset(best.controller, ShapeController.HEIGHT, ShapeController.SHIFT);
            this.end.run();
            return true;
        }
        this.drag = best;
        this.mouseX = context.mouseX; this.mouseY = context.mouseY;
        this.initial = best.controller.read(this.controls.shapeKeys());
        this.last = this.initial.clone();
        this.begin.run();
        return true;
    }

    /** This overlay's handle, if it is the markup's nearest to the point. */
    private Handle pick(int x, int y)
    {
        ScreenOverlay.Handle hit = this.overlay.pick(x, y);
        return hit != null && hit.data instanceof Handle handle && this.handles.contains(handle) ? handle : null;
    }

    public void preview(UIContext context, ScreenOverlay overlay, UIModelRenderer renderer, ModelForm form, UIShapeControllers controls,
        Runnable begin, Runnable end)
    {
        if (form == null || controls == null) { this.clear(); return; }
        var root = FormUtils.getRoot(form);
        var entity = renderer instanceof mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer pickable ? pickable.getTargetEntity() : renderer.getEntity();
        var matrices = FormUtilsClient.getRenderer(root).collectMatrices(entity, context.getTransition());
        Matrix4f view = new Matrix4f(renderer.camera.view).translate((float) -renderer.camera.position.x,
            (float) -renderer.camera.position.y, (float) -renderer.camera.position.z);
        this.draw(context, overlay, controls, renderer.camera.projection, c ->
        {
            String path = StringUtils.combinePaths(FormUtils.getPath(form), c.bone.get());
            Matrix4f frame = matrices.get(path).matrix();
            return frame == null ? null : new Matrix4f(view).mul(renderer.toSceneMatrix(frame));
        }, begin, end);
    }

    public void draw(UIContext context, ScreenOverlay overlay, UIShapeControllers controls, Matrix4f projection,
        Function<ShapeController, Matrix4f> frames, Runnable begin, Runnable end)
    {
        if (this.controls != controls) this.release();
        this.overlay = overlay; this.controls = controls; this.begin = begin; this.end = end;
        this.handles.clear();
        if (!overlay.isShown() || controls == null) { this.release(); return; }
        if (this.drag != null)
        {
            if (!Window.isMouseButtonPressed(0) || !controls.list.getList().contains(this.drag.controller)) this.release();
            else this.dragTo(controls, context.mouseX - this.mouseX, context.mouseY - this.mouseY);
        }
        ShapeController mirror = this.drag == null ? null : controls.mirrorOf(this.drag.controller);
        for (ShapeController c : controls.list.getList())
        {
            Matrix4f frame = frames.apply(c);
            if (frame == null || c.bindings.getAllTyped().isEmpty()) continue;
            Matrix4f matrix = new Matrix4f(projection).mul(frame);
            float[] value = c.read(controls.shapeKeys());
            Vector3f local = new Vector3f(c.position.get()).add(value[ShapeController.SHIFT], value[ShapeController.HEIGHT], 0).div(16F);
            Vector3f center = overlay.project(matrix, local);
            Vector3f up = overlay.project(matrix, new Vector3f(local).add(0, 1F / 16, 0));
            if (center == null || up == null) continue;
            Vector3f side = null;
            if (c.hasAxis(ShapeController.SHIFT))
            {
                Vector3f right = overlay.project(matrix, new Vector3f(local).add(1F / 16, 0, 0));
                if (right != null) side = right.sub(center);
                if (c.hasAxis(ShapeController.HEIGHT)) this.frame(overlay, matrix, c.position.get());
            }
            this.addHandle(c, ShapeController.HEIGHT, center, new Vector3f(up).sub(center), side, mirror);
            if (this.draggingAxis(c, ShapeController.TILT))
            {
                Vector3f edge = overlay.project(matrix, new Vector3f(local).add(1F / 16, value[ShapeController.TILT] / 16, 0));
                Vector3f edgeUp = overlay.project(matrix, new Vector3f(local).add(1F / 16, (value[ShapeController.TILT] + 1) / 16, 0));
                if (edge != null && edgeUp != null)
                {
                    this.connector(overlay, center, edge);
                    this.addHandle(c, ShapeController.TILT, edge, new Vector3f(edgeUp).sub(edge), null, mirror);
                }
            }
        }
    }

    private static String axisName(Handle handle)
    {
        if (handle.axis == ShapeController.TILT) return "tilt";
        if (handle.side == null) return "height";
        return handle.controller.hasAxis(ShapeController.HEIGHT) ? "height_shift" : "shift";
    }

    /** Screen motion back into the controller's axes; a handle with a side step moves both ways at once. */
    private void dragTo(UIShapeControllers controls, float dx, float dy)
    {
        Vector3f up = this.drag.direction;
        Vector3f side = this.drag.side;
        float[] values = this.last.clone();
        if (side == null)
        {
            float length = up.x * up.x + up.y * up.y;
            if (length <= 0.01F) return;
            values[this.drag.axis] = this.initial[this.drag.axis] + (dx * up.x + dy * up.y) / length;
        }
        else
        {
            float det = up.x * side.y - up.y * side.x;
            if (Math.abs(det) <= 0.01F) return;
            if (this.drag.controller.hasAxis(ShapeController.HEIGHT))
                values[ShapeController.HEIGHT] = this.initial[ShapeController.HEIGHT] + (dx * side.y - dy * side.x) / det;
            values[ShapeController.SHIFT] = this.initial[ShapeController.SHIFT] + (up.x * dy - up.y * dx) / det;
        }
        for (int axis = 0; axis < ShapeController.AXES; axis++)
        {
            if (Math.abs(values[axis] - this.last[axis]) > 0.0001F)
            {
                controls.setAxis(axis, values[axis]);
                this.last[axis] = values[axis];
            }
        }
    }

    /** The square a two-way handle travels in: one pixel each way of its rest point. */
    private void frame(ScreenOverlay overlay, Matrix4f matrix, Vector3f position)
    {
        Vector3f[] corners = new Vector3f[4];
        for (int i = 0; i < 4; i++)
        {
            float x = i == 0 || i == 3 ? -1 : 1, y = i < 2 ? -1 : 1;
            corners[i] = overlay.project(matrix, new Vector3f(position).add(x, y, 0).div(16F));
            if (corners[i] == null) return;
        }
        for (int i = 0; i < 4; i++) overlay.line(corners[i], corners[(i + 1) % 4], COLOR);
    }

    private void addHandle(ShapeController c, int axis, Vector3f point, Vector3f direction, Vector3f side, ShapeController mirror)
    {
        Handle handle = new Handle(c, axis, point, direction, side);
        boolean active = this.drag != null && this.drag.axis == axis && (this.drag.controller == c || mirror == c);
        ScreenOverlay.Shape shape = axis == ShapeController.TILT ? ScreenOverlay.Shape.CIRCLE : ScreenOverlay.Shape.DIAMOND;
        float size = axis == ShapeController.TILT ? ScreenOverlay.POINT : ScreenOverlay.SHAPE_KEY;
        ScreenOverlay.Handle drawn = this.draggingAxis(c, axis)
            ? this.overlay.handle(point, shape, size, COLOR, handle)
            : this.overlay.marker(point.x, point.y, shape, size, COLOR, true);

        drawn.selected(c == this.controls.selected()).lit(active)
            .label(c.name.get() + ": " + UIShapeControllers.key(axisName(handle)).get());
        this.handles.add(handle);
    }

    private void connector(ScreenOverlay overlay, Vector3f from, Vector3f to)
    {
        float dx = to.x - from.x, dy = to.y - from.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 12F) return;
        /* Keep the line outside the handles. */
        overlay.line(from.x + dx * 6F / length, from.y + dy * 6F / length, to.x - dx * 5F / length, to.y - dy * 5F / length, COLOR);
    }
}
