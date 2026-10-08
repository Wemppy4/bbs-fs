package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.renderers.EmptyStateRenderer;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class UIKeyframeGraph implements IUIKeyframeGraph
{
    protected UIKeyframes keyframes;

    private final Area plotArea = new Area();
    private boolean initialized;
    private double pendingMin = Double.NaN;
    private double pendingMax;

    protected final Scale yAxis;

    public UIKeyframeGraph(UIKeyframes keyframes)
    {
        this.keyframes = keyframes;

        this.yAxis = new Scale(this.plotArea, ScrollDirection.VERTICAL).inverse();
    }

    @Override
    public UIKeyframes getKeyframes()
    {
        return this.keyframes;
    }

    @Override
    public void updateZoom()
    {
        this.yAxis.updateZoom();
    }

    @Override
    public void stopZoom()
    {
        this.yAxis.stopZoom();
    }

    /* Graphing */

    public int toGraphY(double value)
    {
        return (int) this.yAxis.to(value);
    }

    public double fromGraphY(int mouseY)
    {
        return this.yAxis.from(mouseY);
    }

    public void initializeView()
    {
        if (!this.initialized && this.plotArea.w > 0 && this.plotArea.h > 1 && !this.getSheets().isEmpty())
        {
            this.fitValues(false);
            this.initialized = true;
        }
    }

    public void copyViewport(UIKeyframeGraph previous)
    {
        this.yAxis.copy(previous.yAxis);
        this.initialized = previous.initialized;
        this.pendingMin = this.initialized ? (Double.isNaN(previous.pendingMin) ? previous.yAxis.getMinValue() : previous.pendingMin) : Double.NaN;
        this.pendingMax = Double.isNaN(previous.pendingMin) ? previous.yAxis.getMaxValue() : previous.pendingMax;
    }

    private void fitValues(boolean selected)
    {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            List<Keyframe> keys = selected ? sheet.selection.getSelected() : sheet.channel.getKeyframes();
            for (Keyframe key : keys)
            {
                min = Math.min(min, key.getY());
                max = Math.max(max, key.getY());
                if (this.leftHandle(sheet, key))
                {
                    min = Math.min(min, key.getY() + key.ly);
                    max = Math.max(max, key.getY() + key.ly);
                }
                if (this.rightHandle(sheet, key))
                {
                    min = Math.min(min, key.getY() + key.ry);
                    max = Math.max(max, key.getY() + key.ry);
                }
            }
            if (!selected)
            {
                /* Sample each interval so a short overshoot isn't lost in a long animation. */
                for (int index = 0; index < Math.max(1, keys.size()); index++)
                {
                    float start = keys.isEmpty() ? this.keyframes.getTick() : keys.get(index).getTick();
                    float end = index + 1 < keys.size() ? keys.get(index + 1).getTick() : start;
                    for (int i = 0; i <= 32; i++)
                    {
                        double value = sheet.channel.getFactory().getY(sheet.sample(start + (end - start) * i / 32F));
                        if (!Double.isFinite(value)) continue;
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                    }
                }
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) { min = -1; max = 1; }
        if (max - min < 0.01D) { min -= 1; max += 1; }
        this.yAxis.viewOffset(min, max, Math.max(1, this.plotArea.h), Math.min(30, this.plotArea.h / 4));
    }

    private void fitTime(boolean selected)
    {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            List<Keyframe> keys = selected ? sheet.selection.getSelected() : sheet.channel.getKeyframes();
            for (Keyframe key : keys)
            {
                min = Math.min(min, key.getTick());
                max = Math.max(max, key.getTick());
            }
            if (!selected && !sheet.channel.getLoops().isEmpty()) max = Math.max(max, sheet.channel.getLength());
        }
        if (!Double.isFinite(min)) { min = 0; max = Math.max(20, this.keyframes.getDuration()); }
        if (min == max) { min -= 5; max += 5; }
        this.keyframes.getXAxis().viewOffset(min, max, this.plotArea.w, Math.min(30, this.plotArea.w / 4));
    }

    public void fitSelection()
    {
        if (this.getSelected() == null) return;
        this.fitTime(true);
        this.fitValues(true);
    }

    @Override
    public void resetView()
    {
        this.fitTime(false);
        this.fitValues(false);
        this.initialized = true;
    }

    @Override
    public UIKeyframeSheet getLastSheet()
    {
        List<UIKeyframeSheet> sheets = this.getSheets();
        UIKeyframeSheet picked = this.keyframes.getPickedSheet();
        if (sheets.contains(picked)) return picked;
        return sheets.isEmpty() ? null : sheets.get(0);
    }

    @Override
    public List<UIKeyframeSheet> getSheets()
    {
        List<UIKeyframeSheet> sheets = new ArrayList<>();
        for (UIKeyframeSheet sheet : this.keyframes.getSheets())
        {
            if (this.keyframes.isGraphTrack(sheet) && KeyframeFactories.isNumeric(sheet.channel.getFactory())) sheets.add(sheet);
        }
        if (!sheets.isEmpty()) return sheets;
        for (UIKeyframeSheet sheet : this.keyframes.getDopeSheet().getInteractiveSheets())
        {
            if (KeyframeFactories.isNumeric(sheet.channel.getFactory())) sheets.add(sheet);
        }
        return sheets;
    }

    /** The picked key's curve first for equal-distance hits. */
    private List<UIKeyframeSheet> hitOrder()
    {
        List<UIKeyframeSheet> sheets = this.getSheets();
        UIKeyframeSheet picked = this.keyframes.getPickedSheet();
        if (sheets.remove(picked)) sheets.add(0, picked);
        return sheets;
    }

    @Override
    public void selectByX(int mouseX)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            for (Object entry : sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) entry;
                if (Math.abs(this.keyframes.toGraphX(key.getTick()) - mouseX) < 5) sheet.selection.add(key);
            }
        }
        this.pickSelected();
    }

    @Override
    public void selectInArea(Area area)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            for (Object entry : sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) entry;
                if (area.isInside(this.keyframes.toGraphX(key.getTick()), this.toGraphY(key.getY()))) sheet.selection.add(key);
            }
        }
        this.pickSelected();
    }

    @Override
    public UIKeyframeSheet getSheet(int mouseX, int mouseY)
    {
        if (mouseX < this.keyframes.graphArea.x) return this.keyframes.getDopeSheet().getSheet(mouseY);
        Pair<Keyframe, KeyframeType> hit = this.findKeyframe(mouseX, mouseY);
        if (hit != null) return this.getSheet(hit.a);
        UIKeyframeSheet curve = this.findCurve(mouseX, mouseY);
        return curve == null ? this.getLastSheet() : curve;
    }

    /** The actual sampled curve, in pixels, including vertical steps and steep segments. */
    public UIKeyframeSheet findCurve(int mouseX, int mouseY)
    {
        if (!this.plotArea.isInside(mouseX, mouseY)) return null;
        UIKeyframeSheet result = null;
        double best = 36D;
        for (UIKeyframeSheet sheet : this.hitOrder())
        {
            double previousY = this.sampleY(sheet, mouseX - 7);
            for (int x = mouseX - 6; x <= mouseX + 6; x++)
            {
                double y = this.sampleY(sheet, x);
                double dy = y - previousY;
                double t = Math.max(0D, Math.min(1D, ((mouseX - x + 1) + (mouseY - previousY) * dy) / (1D + dy * dy)));
                double dx = x - 1 + t - mouseX, distanceY = previousY + dy * t - mouseY;
                double distance = dx * dx + distanceY * distanceY;
                if (distance < best - 0.01D) { best = distance; result = sheet; }
                previousY = y;
            }
        }
        return result;
    }

    private double sampleY(UIKeyframeSheet sheet, int x)
    {
        return this.yAxis.to(sheet.channel.getFactory().getY(sheet.sample((float) this.keyframes.fromGraphX(x))));
    }

    @Override
    public boolean addKeyframe(int mouseX, int mouseY)
    {
        UIKeyframeSheet curve = this.findCurve(mouseX, mouseY);
        return this.addKeyframeAt(curve == null ? this.getLastSheet() : curve, this.keyframes.fromGraphCursor(mouseX), mouseY);
    }

    @Override
    public boolean addKeyframeAt(float tick, int mouseY)
    {
        return this.addKeyframeAt(this.getLastSheet(), tick, mouseY);
    }

    private boolean addKeyframeAt(UIKeyframeSheet sheet, float tick, int mouseY)
    {
        if (sheet == null) return false;
        this.addKeyframeManually(sheet, tick, sheet.channel.getFactory().yToValue(this.fromGraphY(mouseY)));
        return true;
    }

    private boolean leftHandle(UIKeyframeSheet sheet, Keyframe key)
    {
        if (!key.isEnabled() || sheet != this.keyframes.getPickedSheet() && !sheet.selection.has(key)) return false;
        Keyframe previous = sheet.channel.get(sheet.channel.previousEnabledIndex(sheet.channel.indexOf(key) - 1));
        return previous != null && previous.getInterpolation().getInterp() == Interpolations.BEZIER;
    }

    private boolean rightHandle(UIKeyframeSheet sheet, Keyframe key)
    {
        return key.isEnabled() && (sheet == this.keyframes.getPickedSheet() || sheet.selection.has(key))
            && key.getInterpolation().getInterp() == Interpolations.BEZIER;
    }

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY)
    {
        if (!this.plotArea.isInside(mouseX, mouseY)) return null;
        Pair<Keyframe, KeyframeType> result = null;
        double best = 36D;
        for (UIKeyframeSheet sheet : this.hitOrder())
        {
            for (Object entry : sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) entry;
                for (KeyframeType type : KeyframeType.values())
                {
                    if (type == KeyframeType.SUMMARY) continue;
                    if (type == KeyframeType.LEFT_HANDLE && !this.leftHandle(sheet, key)) continue;
                    if (type == KeyframeType.RIGHT_HANDLE && !this.rightHandle(sheet, key)) continue;
                    double time = key.getTick() + (type == KeyframeType.LEFT_HANDLE ? -key.lx : type == KeyframeType.RIGHT_HANDLE ? key.rx : 0);
                    double value = key.getY() + (type == KeyframeType.LEFT_HANDLE ? key.ly : type == KeyframeType.RIGHT_HANDLE ? key.ry : 0);
                    double dx = this.keyframes.toGraphX(time) - mouseX, dy = this.toGraphY(value) - mouseY;
                    double distance = dx * dx + dy * dy;
                    if (distance < best - 0.01D) { best = distance; result = new Pair<>(key, type); }
                }
            }
        }
        return result;
    }

    @Override
    public void selectKeyframe(Keyframe keyframe)
    {
        this.clearSelection();

        UIKeyframeSheet sheet = this.getSheet(keyframe);

        if (sheet != null)
        {
            sheet.selection.add(keyframe);
            this.pickKeyframe(keyframe);

            double x = keyframe.getTick();
            this.keyframes.getXAxis().shiftIntoMiddle(x);
            this.yAxis.shiftIntoMiddle(keyframe.getY());
        }
    }

    @Override
    public void resize()
    {
        double min = Double.isNaN(this.pendingMin) ? this.yAxis.getMinValue() : this.pendingMin;
        double max = Double.isNaN(this.pendingMin) ? this.yAxis.getMaxValue() : this.pendingMax;
        this.pendingMin = Double.NaN;
        this.plotArea.copy(this.keyframes.graphArea);
        this.plotArea.y += TOP_MARGIN;
        this.plotArea.h = Math.max(1, this.plotArea.h - TOP_MARGIN);
        if (this.initialized && max > min) this.yAxis.view(min, max);
        this.keyframes.getDopeSheet().resize();
        this.initializeView();
    }

    @Override
    public boolean mouseClicked(UIContext context)
    {
        return context.mouseX < this.keyframes.graphArea.x && this.keyframes.getDopeSheet().mouseClicked(context);
    }

    @Override
    public void mouseReleased(UIContext context)
    {
        this.keyframes.getDopeSheet().mouseReleased(context);
    }

    @Override
    public void mouseScrolled(UIContext context)
    {
        if (context.mouseX < this.keyframes.graphArea.x)
        {
            this.keyframes.getDopeSheet().getYAxis().mouseScroll(context);
        }
        else if (context.mouseWheelHorizontal != 0)
        {
            this.keyframes.panTime(context.mouseWheelHorizontal);
        }
        else if (Window.isAltPressed() && context.mouseWheel != 0D && this.getSelected() != null)
        {
            float delta = (float) (context.mouseWheel * 1F);
            this.moveSelectedBy(delta, true);
        }
        else if (context.mouseWheel != 0D)
        {
            boolean shift = Window.isShiftPressed();
            boolean ctrl = Window.isCtrlPressed();

            /* Shift isolates time, Ctrl isolates values, both accelerate the two axes. */
            if (!ctrl || shift)
            {
                this.keyframes.zoomTimeAt(context, context.mouseWheel);
            }

            if (!shift || ctrl)
            {
                this.yAxis.animateZoom(Scale.getAnchorY(context, this.plotArea), context.mouseWheel, this.keyframes.getZoomSpeed());
            }
        }
    }

    @Override
    public void handleMouse(UIContext context, int lastX, int lastY)
    {
        this.keyframes.getDopeSheet().getYAxis().drag(context);
        if (this.keyframes.isNavigating())
        {
            this.keyframes.dragTimeBy(context.mouseX - lastX);
            this.yAxis.setShift(this.yAxis.getShift() + (context.mouseY - lastY) / this.yAxis.getZoom());
        }
    }

    @Override
    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV)
    {
        if (type == null)
        {
            return;
        }

        IKeyframeFactory factory = type.a.getFactory();
        Keyframe keyframe = type.a;

        if (type.b == KeyframeType.REGULAR)
        {
            float offsetX = (float) this.keyframes.fromGraphX(originalX) - originalT;
            double offsetY = this.fromGraphY(originalY) - factory.getY(originalV);

            float fx = (float) this.keyframes.fromGraphX(context.mouseX) - offsetX;

            if (this.keyframes.isSnappingToTicks())
            {
                fx = Math.round(this.keyframes.fromGraphX(context.mouseX) - offsetX);
            }

            this.keyframes.moveSelectedKeys(fx - originalT, this.fromGraphY(context.mouseY) - offsetY - factory.getY(originalV));
        }
        else if (type.b == KeyframeType.LEFT_HANDLE)
        {
            keyframe.lx = -(float) ((this.keyframes.fromGraphX(context.mouseX)) - keyframe.getTick());
            keyframe.ly = (float) (this.fromGraphY(context.mouseY) - factory.getY(originalV));

            if (!Window.isShiftPressed())
            {
                keyframe.rx = keyframe.lx;
                keyframe.ry = -keyframe.ly;
            }
        }
        else if (type.b == KeyframeType.RIGHT_HANDLE)
        {
            keyframe.rx = (float) ((this.keyframes.fromGraphX(context.mouseX)) - keyframe.getTick());
            keyframe.ry = (float) (this.fromGraphY(context.mouseY) - factory.getY(originalV));

            if (!Window.isShiftPressed())
            {
                keyframe.lx = keyframe.rx;
                keyframe.ly = -keyframe.ry;
            }
        }

        this.keyframes.triggerChange();
    }

    @Override
    public void render(UIContext context)
    {
        this.initializeView();
        this.keyframes.getDopeSheet().renderGrid(context);
        context.batcher.clip(this.plotArea, context);
        this.renderGrid(context);
        List<UIKeyframeSheet> sheets = this.hitOrder();
        Collections.reverse(sheets);
        for (UIKeyframeSheet sheet : sheets) this.renderGraph(context, sheet);
        this.renderPreviews(context);
        context.batcher.unclip(context);
        if (sheets.isEmpty()) EmptyStateRenderer.renderHint(context, this.plotArea, UIKeys.KEYFRAMES_GRAPH_EMPTY);
    }

    private void renderGrid(UIContext context)
    {
        /* Decimal steps remain readable for small influences as well as large coordinates. */
        double span = this.yAxis.getMaxValue() - this.yAxis.getMinValue();
        double raw = span * 60D / Math.max(1, this.plotArea.h);
        double magnitude = Math.pow(10D, Math.floor(Math.log10(Math.max(raw, 1E-12D))));
        double unit = raw / magnitude;
        double step = (unit > 5 ? 10 : unit > 2 ? 5 : unit > 1 ? 2 : 1) * magnitude;
        double first = Math.ceil(this.yAxis.getMinValue() / step) * step;
        for (int i = 0; i < 100; i++)
        {
            double value = first + step * i;
            if (value > this.yAxis.getMaxValue()) break;
            int y = this.toGraphY(value);
            context.batcher.box(this.plotArea.x, y, this.plotArea.ex(), y + 1, Colors.setA(Colors.WHITE, value == 0D ? 0.3F : 0.1F));
            String label = String.format(java.util.Locale.ROOT, "%.6g", value);
            context.batcher.text(label, this.plotArea.x + 4, y + 3, Colors.setA(Colors.WHITE, 0.6F));
        }
        int stepX = this.keyframes.getXAxis().getMult();
        double firstTick = Math.ceil(this.keyframes.fromGraphX(this.plotArea.x) / stepX) * stepX;
        for (int i = 0; i < 2000; i++)
        {
            int x = this.keyframes.toGraphX(firstTick + stepX * i);
            if (x >= this.plotArea.ex()) break;
            context.batcher.box(x, this.plotArea.y, x + 1, this.plotArea.ey(), Colors.setA(Colors.WHITE, 0.1F));
        }
    }

    private void renderPreviews(UIContext context)
    {
        if (!this.plotArea.isInside(context)) return;
        float currentTick = this.keyframes.getDuplicationTick(context);
        if (Window.isCtrlPressed() && !this.keyframes.isDuplicatingAtPlayhead())
        {
            UIKeyframeSheet sheet = this.getLastSheet();
            if (sheet != null) this.renderPreviewKeyframe(context, sheet, this.keyframes.getCreationTick(context), context.mouseY, Colors.WHITE);
            return;
        }
        if (!this.keyframes.isStacking() && !Window.isAltPressed()) return;
        float first = Float.POSITIVE_INFINITY;
        for (UIKeyframeSheet sheet : this.getSheets())
            for (Keyframe key : sheet.selection.getSelected()) first = Math.min(first, key.getTick());
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            List<Keyframe> selected = sheet.selection.getSelected();
            if (selected.isEmpty()) continue;
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (Keyframe key : selected) { min = Math.min(min, key.getTick()); max = Math.max(max, key.getTick()); }
            float length = max - min + this.keyframes.getStackOffset();
            int times = this.keyframes.isStacking() ? (int) Math.min(2000, Math.max(1, Math.ceil((currentTick - max) / length))) : 1;
            for (int i = 0; i < times; i++)
            {
                for (Keyframe key : selected)
                {
                    float tick = this.keyframes.isStacking() ? max + this.keyframes.getStackOffset() + key.getTick() - min + length * i : currentTick + key.getTick() - first;
                    this.renderPreviewKeyframe(context, sheet, tick, this.toGraphY(key.getY()), Colors.YELLOW);
                }
            }
        }
    }

    private void renderPreviewKeyframe(UIContext context, UIKeyframeSheet sheet, double tick, int y, int color)
    {
        int x = this.keyframes.toGraphX(tick);
        float a = (float) Math.sin(context.getTickTransition() / 2D) * 0.1F + 0.5F;

        context.batcher.box(x - 4, y - 4, x + 4, y + 4, Colors.setA(color, a));
    }

    /**
     * Render the graph
     */
    @SuppressWarnings({"rawtypes", "IntegerDivisionInFloatingPointContext"})
    protected void renderGraph(UIContext context, UIKeyframeSheet sheet)
    {
        if (!sheet.channel.getLoops().isEmpty())
        {
            this.renderLoopGraph(context, sheet);
            return;
        }

        List keyframes = sheet.channel.getKeyframes();
        boolean filtered = sheet.channel.hasDisabledKeyframes();
        if (filtered)
        {
            keyframes = new ArrayList(keyframes);
            keyframes.removeIf(key -> !((Keyframe) key).isEnabled());
        }
        KeyframeSegment segment = new KeyframeSegment();

        /* Render graph */
        LineBuilder lineBuilder = new LineBuilder(sheet == this.keyframes.getPickedSheet() ? 1.2F : 0.7F);

        if (keyframes.isEmpty())
        {
            int y = this.toGraphY(sheet.channel.getFactory().getY(sheet.sample(this.keyframes.getTick())));
            lineBuilder.add(this.plotArea.x, y);
            lineBuilder.add(this.plotArea.ex(), y);
        }
        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe frame = (Keyframe) keyframes.get(i);
            Keyframe prev = i > 0 ? (Keyframe) keyframes.get(i - 1) : null;
            int x = this.keyframes.toGraphX(frame.getTick());
            int y = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()));

            if (i == 0 && x > this.plotArea.x)
            {
                lineBuilder.add(this.plotArea.x, y);
            }

            if (prev != null)
            {
                IInterp interp = prev.getInterpolation().getInterp();
                int px = this.keyframes.toGraphX(prev.getTick());
                int py = this.toGraphY(sheet.channel.getFactory().getY(prev.getValue()));

                if (interp == Interpolations.CONST)
                {
                    lineBuilder.add(x, py);
                    lineBuilder.push();
                }
                else if (interp != Interpolations.LINEAR || prev.getMotionShift() != 0F)
                {
                    /* Sampling a curve nobody can see is the whole cost of a dense channel, and a
                     * pixel-wide segment needs no more points than it has pixels. The straight
                     * stand-in an offscreen segment gets is behind the scissor either way. */
                    boolean visible = Math.max(px, x) >= this.plotArea.x - 20 && Math.min(px, x) <= this.plotArea.ex() + 20;

                    if (visible)
                    {
                        float steps = Math.min(50, Math.max(2, Math.abs(x - px)));

                        /* prev sits at i - 1 by construction — no need to re-find it per sample. */
                        segment.fill(prev, frame, filtered ? sheet.channel.indexOf(prev) : i - 1);

                        for (int j = 1; j <= steps; j++)
                        {
                            float a = j / steps;

                            segment.setup(prev.getTick() + a * (frame.getTick() - prev.getTick()));

                            float interpolate = this.toGraphY(frame.getFactory().getY(segment.createInterpolated(sheet.property == null ? null : sheet.property.getOriginalValue())));

                            lineBuilder.add(Lerps.lerp(px, x, a), interpolate);
                        }
                    }
                }
            }

            lineBuilder.add(x, y);

            if (i == keyframes.size() - 1 && x < this.plotArea.ex())
            {
                lineBuilder.add(this.plotArea.ex(), y);
            }

            boolean add = false;

            if (this.rightHandle(sheet, frame))
            {
                int rx = this.keyframes.toGraphX(frame.getTick() + frame.rx);
                int ry = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ry);

                lineBuilder.push();
                lineBuilder.add(x, y);
                lineBuilder.add(rx, ry);

                add = true;
            }

            if (this.leftHandle(sheet, frame))
            {
                int lx = this.keyframes.toGraphX(frame.getTick() - frame.lx);
                int ly = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ly);

                lineBuilder.push();
                lineBuilder.add(x, y);
                lineBuilder.add(lx, ly);

                add = true;
            }

            if (add)
            {
                lineBuilder.push();
                lineBuilder.add(x, y);
            }
        }

        lineBuilder.render(context.batcher, SolidColorLineRenderer.get(Colors.COLOR.set(Colors.setA(sheet.color, sheet == this.keyframes.getPickedSheet() ? 1F : 0.65F))));

    }

    /** Sample the actual finite-loop playback instead of drawing a fictitious line from the
     * source's last key straight to the next ordinary key. Work is bounded by visible pixels. */
    private void renderLoopGraph(UIContext context, UIKeyframeSheet sheet)
    {
        LineBuilder line = new LineBuilder(sheet == this.keyframes.getPickedSheet() ? 1.2F : 0.7F);
        float previousSource = -Float.MAX_VALUE;
        for (int x = this.keyframes.graphArea.x; x <= this.keyframes.graphArea.ex(); x += 2)
        {
            float tick = (float) this.keyframes.fromGraphX(x);
            float source = sheet.channel.getSourceTick(tick);
            if (source < previousSource) line.push();
            Object value = sheet.sample(tick);
            if (value != null) line.add(x, this.toGraphY(sheet.channel.getFactory().getY(value)));
            previousSource = source;
        }

        /* Original Bezier handles remain editable in the same place. */
        List<Keyframe> originals = sheet.channel.getKeyframes();
        for (int index = 0; index < originals.size(); index++)
        {
            Keyframe key = originals.get(index);
            if (!key.isEnabled()) continue;
            int x = this.keyframes.toGraphX(key.getTick()), y = this.toGraphY(key.getY());
            if (x < this.keyframes.graphArea.x - 20 || x > this.keyframes.graphArea.ex() + 20) continue;
            if (this.rightHandle(sheet, key))
            {
                line.push(); line.add(x, y);
                line.add(this.keyframes.toGraphX(key.getTick() + key.rx), this.toGraphY(key.getY() + key.ry));
            }
            if (this.leftHandle(sheet, key))
            {
                line.push(); line.add(x, y);
                line.add(this.keyframes.toGraphX(key.getTick() - key.lx), this.toGraphY(key.getY() + key.ly));
            }
        }
        line.render(context.batcher, SolidColorLineRenderer.get(Colors.COLOR.set(Colors.setA(sheet.color, sheet == this.keyframes.getPickedSheet() ? 1F : 0.65F))));
    }

    protected void renderGraphPointShapes(UIContext context, BufferBuilder builder, Matrix4f matrix, UIKeyframeSheet sheet, List keyframes, Pair<Keyframe, KeyframeType> hit)
    {
        /* Draw keyframe handles (outer) */
        int forcedIndex = 0;
        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe frame = (Keyframe) keyframes.get(i);
            float tick = frame.getTick();
            int x1 = this.keyframes.toGraphX(tick);
            int x2 = this.keyframes.toGraphX(tick + frame.getDuration());
            int y = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()));

            /* Render custom duration markers */
            if (x1 != x2)
            {
                int y1 = y - 8 + (forcedIndex % 2 == 1 ? -4 : 0);
                int color = sheet.selection.has(i) ? Colors.WHITE :  Colors.setA(Colors.mulRGB(sheet.color, 0.9F), 0.75F);

                context.batcher.fillRect(builder, matrix, x1, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x2, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x1 + 1, y1, x2 - x1, 1, color, color, color, color);

                forcedIndex += 1;
            }

            boolean isPointHover = hit != null && hit.a == frame && hit.b == KeyframeType.REGULAR;
            boolean toRemove = this.keyframes.isRemovingKeyframe() && isPointHover;

            if (this.keyframes.isSelecting())
            {
                isPointHover = isPointHover || this.keyframes.getGrabbingArea(context).isInside(x1, y);
            }

            int kc = UIKeyframeDopeSheet.keyframeColor(frame, sheet);
            int c = (sheet.selection.has(i) || isPointHover ? Colors.WHITE : kc) | Colors.A100;

            if (toRemove)
            {
                c = Colors.RED | Colors.A100;
            }

            int offset = toRemove ? 4 : 3;

            UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, x1, y, offset, c);

            if (this.rightHandle(sheet, frame))
            {
                int rx = this.keyframes.toGraphX(frame.getTick() + frame.rx);
                int ry = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ry);

                UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, rx, ry, 3, c);
            }

            if (this.leftHandle(sheet, frame))
            {
                int lx = this.keyframes.toGraphX(frame.getTick() - frame.lx);
                int ly = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ly);

                UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, lx, ly, 3, c);
            }
        }

        /* Render keyframe handles (inner) */
        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            int y = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()));

            int c = sheet.selection.has(j) ? Colors.ACTIVE : 0;
            int mx = this.keyframes.toGraphX(frame.getTick());
            int mc = UIKeyframeDopeSheet.keyframeCoreColor(frame, sheet, sheet.selection.has(j));
            IKeyframeShapeRenderer shapeResult = UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, mx, y, 2, mc);

            shapeResult.renderKeyframeBackground(context, builder, matrix, mx, y, 2, mc);

            if (this.rightHandle(sheet, frame))
            {
                int rx = this.keyframes.toGraphX(frame.getTick() + frame.rx);
                int ry = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ry);

                shapeResult = UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, rx, ry, 2, c | Colors.A100);
                shapeResult.renderKeyframeBackground(context, builder, matrix, rx, ry, 2, c | Colors.A100);
            }

            if (this.leftHandle(sheet, frame))
            {
                int lx = this.keyframes.toGraphX(frame.getTick() - frame.lx);
                int ly = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ly);

                shapeResult = UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, lx, ly, 2, c | Colors.A100);
                shapeResult.renderKeyframeBackground(context, builder, matrix, lx, ly, 2, c | Colors.A100);
            }
        }
    }

    @Override
    public void renderTopmostKeyframes(UIContext context)
    {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        List<UIKeyframeSheet> sheets = this.hitOrder();
        Collections.reverse(sheets);
        context.batcher.clip(this.plotArea, context);
        Pair<Keyframe, KeyframeType> hit = this.findKeyframe(context.mouseX, context.mouseY);
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (UIKeyframeSheet sheet : sheets) this.renderGraphPointShapes(context, builder, matrix, sheet, sheet.channel.getKeyframes(), hit);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        context.batcher.unclip(context);
    }

    @Override
    public void postRender(UIContext context)
    {
        if (this.keyframes.graphArea.x > this.keyframes.area.x) this.keyframes.getDopeSheet().postRender(context);
    }

    @Override
    public void saveState(MapType extra)
    {
        extra.putBool("graph_initialized", this.initialized);
        extra.putDouble("y_min", Double.isNaN(this.pendingMin) ? this.yAxis.getMinValue() : this.pendingMin);
        extra.putDouble("y_max", Double.isNaN(this.pendingMin) ? this.yAxis.getMaxValue() : this.pendingMax);
    }

    @Override
    public void restoreState(MapType extra)
    {
        this.initialized = extra.getBool("graph_initialized");
        this.pendingMin = Double.NaN;
        if (this.initialized)
        {
            this.pendingMin = extra.getDouble("y_min");
            this.pendingMax = extra.getDouble("y_max");
        }
    }
}
