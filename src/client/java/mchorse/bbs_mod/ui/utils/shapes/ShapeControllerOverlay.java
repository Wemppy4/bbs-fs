package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** One projection/picking/drag implementation for model, form, state and film previews. */
public class ShapeControllerOverlay
{
    private static final float PICK_RADIUS = 9F;
    private static final int IDLE = 0xFFABB4C0;
    private static final int HOVER = 0xFFFFFFFF;
    private static final int SELECTED = 0xFFED0D5B;
    private static final int SELECTED_HOVER = 0xFFFF70A0;
    /** {@code side}: the screen step of one pixel sideways, on a handle that also travels along {@link ShapeController#SHIFT}. */
    private record Handle(ShapeController controller, int axis, Vector3f screen, Vector3f direction, Vector3f side) {}
    private final List<Handle> handles = new ArrayList<>();
    private UIShapeControllers controls;
    private Handle drag;
    private int mouseX, mouseY;
    private float[] initial, last;
    private Runnable begin = () -> {}, end = () -> {};
    private Area viewport;
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
        if ((context.mouseButton != 0 && context.mouseButton != 1) || this.controls == null || this.viewport == null
            || !this.viewport.isInside(context) || !UIBaseMenu.shouldRenderAxes()) return false;
        Handle best = this.pick(context.mouseX, context.mouseY);
        if (best == null) return false;
        this.controls.select(best.controller);
        if (context.mouseButton == 1)
        {
            this.controls.openPresets(context);
            return true;
        }
        if (selectOnly) return true;
        this.drag = best;
        this.mouseX = context.mouseX; this.mouseY = context.mouseY;
        this.initial = best.controller.read(this.controls.shapeKeys());
        this.last = this.initial.clone();
        this.begin.run();
        return true;
    }

    private Handle pick(int x, int y)
    {
        Handle best = null;
        float distance = PICK_RADIUS * PICK_RADIUS;
        for (Handle handle : this.handles)
        {
            if (!this.draggingAxis(handle.controller, handle.axis)) continue;
            float dx = x - handle.screen.x, dy = y - handle.screen.y;
            float d = dx * dx + dy * dy;
            if (d < distance) { best = handle; distance = d; }
        }
        return best;
    }

    public void preview(UIContext context, UIModelRenderer renderer, ModelForm form, UIShapeControllers controls,
        Runnable begin, Runnable end)
    {
        if (form == null || controls == null) { this.clear(); return; }
        var root = FormUtils.getRoot(form);
        var entity = renderer instanceof mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer pickable ? pickable.getTargetEntity() : renderer.getEntity();
        var matrices = FormUtilsClient.getRenderer(root).collectMatrices(entity, context.getTransition());
        Matrix4f view = new Matrix4f(renderer.camera.view).translate((float) -renderer.camera.position.x,
            (float) -renderer.camera.position.y, (float) -renderer.camera.position.z);
        this.draw(context, controls, renderer.area, renderer.camera.projection, c ->
        {
            String path = StringUtils.combinePaths(FormUtils.getPath(form), c.bone.get());
            Matrix4f frame = matrices.get(path).matrix();
            return frame == null ? null : new Matrix4f(view).mul(renderer.toSceneMatrix(frame));
        }, begin, end);
    }

    public void draw(UIContext context, UIShapeControllers controls, Area viewport, Matrix4f projection,
        Function<ShapeController, Matrix4f> frames, Runnable begin, Runnable end)
    {
        if (this.controls != controls) this.release();
        this.controls = controls; this.begin = begin; this.end = end; this.viewport = viewport;
        this.handles.clear();
        if (!UIBaseMenu.shouldRenderAxes() || controls == null) { this.release(); return; }
        if (this.drag != null)
        {
            if (!Window.isMouseButtonPressed(0) || !controls.list.getList().contains(this.drag.controller)) this.release();
            else this.dragTo(controls, context.mouseX - this.mouseX, context.mouseY - this.mouseY);
        }
        context.batcher.clip(viewport, context);
        context.batcher.beginBatch();
        for (ShapeController c : controls.list.getList())
        {
            Matrix4f frame = frames.apply(c);
            if (frame == null || c.bindings.getAllTyped().isEmpty()) continue;
            Matrix4f matrix = new Matrix4f(projection).mul(frame);
            float[] value = c.read(controls.shapeKeys());
            Vector3f local = new Vector3f(c.position.get()).add(value[ShapeController.SHIFT], value[ShapeController.HEIGHT], 0).div(16F);
            Vector3f center = project(matrix, viewport, local);
            Vector3f up = project(matrix, viewport, new Vector3f(local).add(0, 1F / 16, 0));
            if (center == null || up == null) continue;
            int color = c == controls.selected() ? SELECTED : IDLE;
            Vector3f side = null;
            if (c.hasAxis(ShapeController.SHIFT))
            {
                Vector3f right = project(matrix, viewport, new Vector3f(local).add(1F / 16, 0, 0));
                if (right != null) side = right.sub(center);
                if (c.hasAxis(ShapeController.HEIGHT)) this.frame(context, matrix, viewport, c.position.get(), color);
            }
            this.addHandle(viewport, c, ShapeController.HEIGHT, center, new Vector3f(up).sub(center), side);
            if (this.draggingAxis(c, ShapeController.TILT))
            {
                Vector3f edge = project(matrix, viewport, new Vector3f(local).add(1F / 16, value[ShapeController.TILT] / 16, 0));
                Vector3f edgeUp = project(matrix, viewport, new Vector3f(local).add(1F / 16, (value[ShapeController.TILT] + 1) / 16, 0));
                if (edge != null && edgeUp != null)
                {
                    this.connector(context, center, edge, color);
                    this.addHandle(viewport, c, ShapeController.TILT, edge, new Vector3f(edgeUp).sub(edge), null);
                }
            }
        }
        Handle hovered = viewport.isInside(context) ? this.pick(context.mouseX, context.mouseY) : null;
        ShapeController mirror = this.drag == null ? null : controls.mirrorOf(this.drag.controller);
        for (Handle handle : this.handles)
        {
            boolean active = this.drag != null && this.drag.axis == handle.axis
                && (this.drag.controller == handle.controller || mirror == handle.controller);
            boolean lit = handle == hovered || active;
            boolean selected = handle.controller == controls.selected();
            int color = selected ? (lit ? SELECTED_HOVER : SELECTED) : (lit ? HOVER : IDLE);
            this.outlineHandle(context, handle, color, 0.65F);
        }
        context.batcher.endBatch();
        context.batcher.unclip(context);
        if (hovered != null)
            context.batcher.textCard(hovered.controller.name.get() + ": " + UIShapeControllers.key(axisName(hovered)).get(),
                context.mouseX + 12, context.mouseY + 8);
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
    private void frame(UIContext context, Matrix4f matrix, Area viewport, Vector3f position, int color)
    {
        Vector3f[] corners = new Vector3f[4];
        for (int i = 0; i < 4; i++)
        {
            float x = i == 0 || i == 3 ? -1 : 1, y = i < 2 ? -1 : 1;
            corners[i] = project(matrix, viewport, new Vector3f(position).add(x, y, 0).div(16F));
            if (corners[i] == null) return;
        }
        for (int i = 0; i < 4; i++)
        {
            Vector3f a = corners[i], b = corners[(i + 1) % 4];
            line(context, a.x, a.y, b.x, b.y, color, 0.5F);
        }
    }

    private void addHandle(Area viewport, ShapeController c, int axis, Vector3f point, Vector3f direction, Vector3f side)
    {
        if (!viewport.isInside((int) point.x, (int) point.y)) return;
        this.handles.add(new Handle(c, axis, point, direction, side));
    }

    private void connector(UIContext context, Vector3f from, Vector3f to, int color)
    {
        float dx = to.x - from.x, dy = to.y - from.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 11F) return;
        /* Keep the line outside the hollow handles. */
        float x1 = from.x + dx * 6F / length, y1 = from.y + dy * 6F / length;
        float x2 = to.x - dx * 5F / length, y2 = to.y - dy * 5F / length;
        line(context, x1, y1, x2, y2, color, 0.55F);
    }

    private void outlineHandle(UIContext context, Handle handle, int color, float width)
    {
        int segments = handle.axis == 0 ? 4 : 16;
        float radius = handle.axis == 0 ? 4.5F : 3.5F;
        float x = handle.screen.x + radius, y = handle.screen.y;
        for (int i = 1; i <= segments; i++)
        {
            double angle = i * Math.PI * 2 / segments;
            float nextX = handle.screen.x + (float) Math.cos(angle) * radius;
            float nextY = handle.screen.y + (float) Math.sin(angle) * radius;
            line(context, x, y, nextX, nextY, color, width);
            x = nextX; y = nextY;
        }
    }

    private static void line(UIContext context, float x1, float y1, float x2, float y2, int color, float width)
    {
        float dx = x2 - x1, dy = y2 - y1;
        var matrices = context.batcher.getContext().getMatrices();
        matrices.push();
        matrices.translate(x1, y1, 0);
        matrices.multiply(new Quaternionf().rotationZ((float) Math.atan2(dy, dx)));
        context.batcher.box(0, -width, (float) Math.sqrt(dx * dx + dy * dy), width, color);
        matrices.pop();
    }

    private static Vector3f project(Matrix4f matrix, Area viewport, Vector3f point)
    {
        Vector4f clip = matrix.transform(new Vector4f(point, 1));
        if (!Float.isFinite(clip.w) || clip.w <= 0.00001F) return null;
        clip.div(clip.w);
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || clip.z < -1 || clip.z > 1) return null;
        return new Vector3f(viewport.x + (clip.x + 1) * viewport.w * 0.5F, viewport.y + (1 - clip.y) * viewport.h * 0.5F, clip.z);
    }
}
