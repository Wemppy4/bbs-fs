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
    @com.github.bsideup.jabel.Desugar
    private record Handle(ShapeController controller, int axis, Vector3f screen, Vector3f direction) {}
    private final List<Handle> handles = new ArrayList<>();
    private UIShapeControllers controls;
    private Handle drag;
    private int mouseX, mouseY;
    private float initial, last;
    private Runnable begin = () -> {}, end = () -> {};
    private Area viewport;
    private boolean draggingAxis(ShapeController c, int axis)
    {
        return c.bindings.getAllTyped().stream().anyMatch(b -> (axis == 0 ? b.height.get() : b.tilt.get()) != 0F);
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
        this.initial = this.last = best.controller.read(this.controls.shapeKeys())[best.axis];
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
            else
            {
                Vector3f d = this.drag.direction;
                float length = d.x * d.x + d.y * d.y;
                if (length > 0.01F)
                {
                    float value = this.initial + ((context.mouseX - this.mouseX) * d.x + (context.mouseY - this.mouseY) * d.y) / length;
                    if (Math.abs(value - this.last) > 0.0001F) { controls.setAxis(this.drag.axis, value); this.last = value; }
                }
            }
        }
        context.batcher.clip(viewport, context);
        context.batcher.beginBatch();
        for (ShapeController c : controls.list.getList())
        {
            Matrix4f frame = frames.apply(c);
            if (frame == null || c.bindings.getAllTyped().isEmpty()) continue;
            Matrix4f matrix = new Matrix4f(projection).mul(frame);
            float[] value = c.read(controls.shapeKeys());
            Vector3f local = new Vector3f(c.position.get()).add(0, value[0], 0).div(16F);
            Vector3f center = project(matrix, viewport, local);
            Vector3f up = project(matrix, viewport, new Vector3f(local).add(0, 1F / 16, 0));
            if (center == null || up == null) continue;
            this.addHandle(viewport, c, 0, center, new Vector3f(up).sub(center));
            if (this.draggingAxis(c, 1))
            {
                Vector3f edge = project(matrix, viewport, new Vector3f(local).add(1F / 16, value[1] / 16, 0));
                Vector3f edgeUp = project(matrix, viewport, new Vector3f(local).add(1F / 16, (value[1] + 1) / 16, 0));
                if (edge != null && edgeUp != null)
                {
                    this.connector(context, center, edge, c == controls.selected() ? SELECTED : IDLE);
                    this.addHandle(viewport, c, 1, edge, new Vector3f(edgeUp).sub(edge));
                }
            }
        }
        Handle hovered = viewport.isInside(context) ? this.pick(context.mouseX, context.mouseY) : null;
        for (Handle handle : this.handles)
        {
            boolean active = this.drag != null && this.drag.controller == handle.controller && this.drag.axis == handle.axis;
            boolean lit = handle == hovered || active;
            boolean selected = handle.controller == controls.selected();
            int color = selected ? (lit ? SELECTED_HOVER : SELECTED) : (lit ? HOVER : IDLE);
            this.outlineHandle(context, handle, color, 0.65F);
        }
        context.batcher.endBatch();
        context.batcher.unclip(context);
        if (hovered != null)
            context.batcher.textCard(hovered.controller.name.get() + ": " + UIShapeControllers.key(hovered.axis == 0 ? "height" : "tilt").get(),
                context.mouseX + 12, context.mouseY + 8);
    }

    private void addHandle(Area viewport, ShapeController c, int axis, Vector3f point, Vector3f direction)
    {
        if (!viewport.isInside((int) point.x, (int) point.y)) return;
        this.handles.add(new Handle(c, axis, point, direction));
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
