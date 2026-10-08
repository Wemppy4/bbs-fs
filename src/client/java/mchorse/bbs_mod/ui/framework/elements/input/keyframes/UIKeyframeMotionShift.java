package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** Midpoint controls shared by film, state and camera timelines; hidden when the keys are too close. */
public class UIKeyframeMotionShift
{
    private final UIKeyframes view;
    private final DoubleClick<Keyframe<?>> doubleClick = new DoubleClick<>(true);
    private Hit dragging;
    private float original;
    private int pressX;

    private record Hit(UIKeyframeSheet sheet, Keyframe<?> key, float start, float duration, int y) {}

    public UIKeyframeMotionShift(UIKeyframes view)
    {
        this.view = view;
    }

    public boolean isDragging()
    {
        return this.dragging != null;
    }

    private int handleX(Hit hit)
    {
        return this.view.toGraphX(hit.start + hit.duration * (0.5F + hit.key.getMotionShift()));
    }

    private boolean onHandle(UIContext context, Hit hit)
    {
        return Math.abs(context.mouseX - this.handleX(hit)) <= 5 && Math.abs(context.mouseY - this.handleY(hit)) <= 5;
    }

    private int handleY(Hit hit)
    {
        if (this.view.getGraph() instanceof UIKeyframeGraph graph)
        {
            float tick = hit.start + hit.duration * (0.5F + hit.key.getMotionShift());
            Object value = hit.sheet.sample(tick);
            return graph.toGraphY(hit.sheet.channel.getFactory().getY(value));
        }
        return hit.y;
    }

    private Hit hit(UIContext context)
    {
        if (!BBSSettings.showMotionShiftHandles.get() || this.view.isInteracting()
            || !this.view.graphArea.isInside(context) || context.mouseY < this.view.area.y + IUIKeyframeGraph.TOP_MARGIN
            || Window.isCtrlPressed() || Window.isAltPressed() || Window.isShiftPressed()) return null;

        UIKeyframeSheet sheet = this.view.getGraph().getSheet(context.mouseX, context.mouseY);
        if (this.view.isEditing())
        {
            List<UIKeyframeSheet> candidates = new ArrayList<>(this.view.getOperationSheets());
            UIKeyframeSheet active = this.view.getPickedSheet();
            if (candidates.remove(active)) candidates.add(0, active);
            double best = 51D;
            for (UIKeyframeSheet candidate : candidates)
            {
                KeyframeSegment<?> segment = candidate.channel.find((float) this.view.fromGraphX(context.mouseX));
                if (segment == null || segment.isSame() || segment.timeOffset != 0F) continue;
                Hit handle = this.visibleHandle(candidate, segment.a, segment.b);
                if (handle == null || !this.onHandle(context, handle)) continue;
                double dx = context.mouseX - this.handleX(handle), dy = context.mouseY - this.handleY(handle);
                double distance = dx * dx + dy * dy;
                if (distance < best - 0.01D) { best = distance; sheet = candidate; }
            }
        }
        if (sheet == null) return null;
        float tick = (float) this.view.fromGraphX(context.mouseX);
        KeyframeSegment<?> segment = sheet.channel.find(tick);
        /* Repeated keys belong to the source pass. Edit there, where their handles are drawn. */
        if (segment == null || segment.isSame() || segment.timeOffset != 0F
            || !segment.a.supportsMotionShift() || sheet.channel.indexOf(segment.a) < 0) return null;

        Hit hit = this.visibleHandle(sheet, segment.a, segment.b);
        if (hit == null) return null;

        boolean dope = this.view.getGraph() == this.view.getDopeSheet();
        int rowY = dope ? this.view.getDopeSheet().getDopeSheetY(sheet) : this.view.area.y + IUIKeyframeGraph.TOP_MARGIN;
        int height = dope ? this.view.getDopeSheet().getTrackHeight(sheet) : 16;
        /* Loop boundaries retain their existing move/resize targets. */
        for (Object entry : sheet.channel.getLoops())
        {
            KeyframeLoop loop = (KeyframeLoop) entry;
            int loopStart = this.view.toGraphX(loop.start()), loopEnd = this.view.toGraphX(sheet.channel.getLoopEnd(loop));
            if ((dope || sheet == this.view.getPickedSheet()) && context.mouseY >= rowY && context.mouseY < rowY + height
                && context.mouseX >= loopStart - 3 && context.mouseX <= loopEnd + 5
                && (context.mouseY < rowY + 4 || context.mouseY >= rowY + height - 3 || Math.abs(context.mouseX - loopEnd) <= 5)) return null;
        }

        if (this.view.getGraph().findKeyframe(context.mouseX, context.mouseY) != null) return null;
        return hit;
    }

    /** Drawing and picking use exactly the same screen-space visibility rules. */
    private Hit visibleHandle(UIKeyframeSheet sheet, Keyframe<?> key, Keyframe<?> next)
    {
        if (!key.isEnabled() || !key.supportsMotionShift() || next == null) return null;
        float start = key.getTick();
        float duration = key.getDuration() > 0F ? key.getDuration() : next.getTick() - start;
        int left = this.view.toGraphX(start), right = this.view.toGraphX(Math.min(start + duration, next.getTick()));
        if (duration <= 0F || right - left < 28 || right < this.view.graphArea.x || left > this.view.graphArea.ex()) return null;

        boolean dope = this.view.getGraph() == this.view.getDopeSheet();
        int y = dope ? this.view.getDopeSheet().getDopeSheetY(sheet) + this.view.getDopeSheet().getTrackHeight(sheet) / 2 : 0;
        if (dope && this.view.getDopeSheet().getSheet(y) != sheet) return null;
        Hit hit = new Hit(sheet, key, start, duration, y);
        int x = this.handleX(hit);
        if (x <= left + 6 || x >= right - 6 || x < this.view.graphArea.x + 5 || x > this.view.graphArea.ex() - 5) return null;
        /* Hidden source intervals under a loop must not gain independent controls. */
        KeyframeSegment<?> actual = sheet.channel.find(start + duration * (0.5F + key.getMotionShift()));
        if (actual == null || actual.a != key || actual.timeOffset != 0F) return null;
        y = this.handleY(hit);
        if (y < this.view.area.y + IUIKeyframeGraph.TOP_MARGIN + 4 || y >= this.view.area.ey() - 4) return null;
        return hit;
    }

    public boolean mouseClicked(UIContext context)
    {
        if (context.mouseButton != 0) return false;
        Hit hit = this.hit(context);
        if (hit == null || !this.onHandle(context, hit))
        {
            this.doubleClick.hit(null);
            return false;
        }

        this.view.getGraph().clearSelection();
        hit.sheet.selection.add(hit.key);
        this.view.pickKeyframe(hit.key);
        if (this.doubleClick.hit(hit.key))
        {
            if (hit.key.getMotionShift() != 0F)
            {
                hit.sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
                hit.key.setMotionShift(0F, false);
                hit.sheet.channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
                this.view.triggerChange();
            }
            return true;
        }

        this.dragging = hit;
        this.original = hit.key.getMotionShift();
        this.pressX = context.mouseX;
        return true;
    }

    public void handleMouse(UIContext context)
    {
        if (!this.isDragging()) return;
        Hit hit = this.dragging;
        float delta = (float) (this.view.fromGraphX(context.mouseX) - this.view.fromGraphX(this.pressX));
        float shift = this.original + delta / hit.duration;
        double pixels = Math.abs(this.view.toGraphX(hit.start + hit.duration * (0.5F + shift))
            - this.view.toGraphX(hit.start + hit.duration * 0.5F));
        if (context.mouseX != this.pressX && pixels <= 3) shift = 0F;
        if (context.mouseX != this.pressX) this.doubleClick.hit(null);
        hit.key.setMotionShift(shift, false);
        this.view.triggerChange();
    }

    public boolean release(boolean cancel)
    {
        if (!this.isDragging()) return false;
        Hit hit = this.dragging;
        float value = hit.key.getMotionShift();
        hit.key.setMotionShift(this.original, false);
        if (!cancel && value != this.original)
        {
            hit.sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
            hit.key.setMotionShift(value, false);
            hit.sheet.channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }
        this.dragging = null;
        this.view.triggerChange();
        return true;
    }

    public boolean keyPressed(UIContext context)
    {
        if (!this.isDragging()) return false;
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE)) this.release(true);
        return true;
    }

    public void render(UIContext context)
    {
        if (!BBSSettings.showMotionShiftHandles.get()) return;

        Hit hovered = this.isDragging() ? this.dragging : this.hit(context);
        int top = this.view.area.y + IUIKeyframeGraph.TOP_MARGIN;
        context.batcher.clip(new Area(this.view.graphArea.x, top, this.view.graphArea.w, this.view.area.ey() - top), context);
        for (UIKeyframeSheet sheet : this.view.getOperationSheets())
        {
            if (this.view.getGraph() == this.view.getDopeSheet())
            {
                int y = this.view.getDopeSheet().getDopeSheetY(sheet) + this.view.getDopeSheet().getTrackHeight(sheet) / 2;
                if (y < top + 4 || y >= this.view.area.ey() - 4 || this.view.getDopeSheet().getSheet(y) != sheet) continue;
            }
            for (int i = 0; i + 1 < sheet.channel.getKeyframes().size(); i++)
            {
                Keyframe<?> key = sheet.channel.get(i);
                if (this.isDragging() && key == this.dragging.key) continue;
                Hit hit = this.visibleHandle(sheet, key, sheet.channel.get(sheet.channel.nextEnabledIndex(i + 1)));
                if (hit != null) this.renderHandle(context, hit, hovered != null && hovered.key == hit.key);
            }
        }
        if (this.isDragging()) this.renderHandle(context, this.dragging, true);
        context.batcher.unclip(context);
        this.renderStatus(context);
    }

    private void renderHandle(UIContext context, Hit hit, boolean highlighted)
    {
        int x = this.handleX(hit), y = this.handleY(hit);
        int left = Math.max(this.view.graphArea.x, this.view.toGraphX(hit.start));
        int right = Math.min(this.view.graphArea.ex(), this.view.toGraphX(Math.min(hit.start + hit.duration,
            hit.sheet.channel.get(hit.sheet.channel.nextEnabledIndex(hit.sheet.channel.indexOf(hit.key) + 1)).getTick())));
        boolean dragging = this.dragging != null && this.dragging.key == hit.key;
        boolean active = dragging || (highlighted && this.onHandle(context, hit));
        int color = active ? Colors.WHITE : Colors.setA(hit.sheet.color, highlighted ? 1F : 0.5F);
        if (highlighted && this.view.getGraph() instanceof UIKeyframeGraph graph)
        {
            LineBuilder line = new LineBuilder(1F);
            int steps = Math.max(2, (right - left) / 3);
            for (int i = 0; i <= steps; i++)
            {
                int px = left + (right - left) * i / steps;
                Object value = hit.sheet.channel.interpolate((float) this.view.fromGraphX(px));
                line.add(px, graph.toGraphY(hit.sheet.channel.getFactory().getY(value)));
            }
            line.render(context.batcher, SolidColorLineRenderer.get(Colors.COLOR.set(Colors.setA(hit.sheet.color, 0.65F))));
        }
        else if (highlighted) context.batcher.box(left, y, right, y + 1, Colors.setA(hit.sheet.color, 0.45F));
        if (dragging)
        {
            int center = this.view.toGraphX(hit.start + hit.duration * 0.5F);
            context.batcher.box(center, y - 4, center + 1, y + 5, Colors.setA(Colors.WHITE, 0.3F));
        }
        if (x >= this.view.graphArea.x && x < this.view.graphArea.ex())
        {
            context.batcher.box(x, y - 3, x + 1, y + 4, color);
            context.batcher.box(x - 2, y - 3, x + 3, y - 2, color);
            context.batcher.box(x - 2, y + 3, x + 3, y + 4, color);
        }
        if (active) context.requestCursor(GLFW.GLFW_HRESIZE_CURSOR);
    }

    private void renderStatus(UIContext context)
    {
        if (this.isDragging())
        {
            Hit hit = this.dragging;
            String label = String.format(Locale.ROOT, "%+.1f%%", hit.key.getMotionShift() * 100F);
            if (hit.key.getMotionShift() == 0F) label = "0%";
            int width = context.batcher.getFont().getWidth(label);
            int height = context.batcher.getFont().getHeight();
            int tx = Math.max(this.view.area.x + 4, Math.min(context.mouseX + 12, this.view.area.ex() - width - 4));
            int ty = Math.max(this.view.area.y + 4, Math.min(context.mouseY + 12, this.view.area.ey() - height - 4));
            context.batcher.textCard(label, tx, ty, Colors.WHITE, 0xE6181818);
        }
    }
}
