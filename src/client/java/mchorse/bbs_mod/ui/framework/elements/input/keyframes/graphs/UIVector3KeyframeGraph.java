package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class UIVector3KeyframeGraph extends UIKeyframeGraph
{
    private static final int[] AXIS_COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE};
    private int draggingAxis = -1;

    public UIVector3KeyframeGraph(UIKeyframes keyframes, UIKeyframeSheet sheet)
    {
        super(keyframes, sheet);
    }

    @Override
    public void resetViewY(UIKeyframeSheet current)
    {
        this.yAxis.set(0, 2);

        KeyframeChannel channel = current.channel;
        List<Keyframe> keyframes = channel.getKeyframes();
        int c = keyframes.size();

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        if (c > 1)
        {
            for (int i = 0; i < c; i++)
            {
                Vector3f v = (Vector3f) keyframes.get(i).getValue();

                minY = Math.min(minY, Math.min(v.x, Math.min(v.y, v.z)));
                maxY = Math.max(maxY, Math.max(v.x, Math.max(v.y, v.z)));
            }
        }
        else
        {
            minY = -10;
            maxY = 10;

            if (c == 1)
            {
                Vector3f v = (Vector3f) channel.get(0).getValue();
                minY = maxY = v.x;
            }
        }

        if (Math.abs(maxY - minY) < 0.01F)
        {
            this.yAxis.setShift(minY);
            this.yAxis.anchor(0.5F);
        }
        else
        {
            this.yAxis.viewOffset(minY, maxY, this.keyframes.area.h, 30);
        }
    }

    @Override
    public void render(UIContext context)
    {
        this.renderGrid(context);
        this.renderGraph(context);
    }

    @Override
    protected void renderGraph(UIContext context)
    {
        KeyframeSegment segment = new KeyframeSegment();

        /* Render graph lines */
        for (int axis = 0; axis < 3; axis++)
        {
            this.renderGraphLine(context, axis, segment);
        }

        /* Render track bars (horizontal lines) */
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int axis = 0; axis < 3; axis++)
        {
            this.renderGraphPoints(context, builder, matrix, axis);
        }
        
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    @Override
    public void renderTopmostKeyframes(UIContext context)
    {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        Area area = this.keyframes.graphArea;

        context.batcher.clip(area, context);
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int axis = 0; axis < 3; axis++)
        {
            this.renderGraphPoints(context, builder, matrix, axis);
        }

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        context.batcher.unclip(context);
    }

    private void renderGraphLine(UIContext context, int axis, KeyframeSegment segment)
    {
        List<Keyframe> keyframes = this.sheet.channel.getKeyframes();
        LineBuilder lineBuilder = new LineBuilder(0.7F);

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe frame = keyframes.get(i);
            Keyframe prev = i > 0 ? keyframes.get(i - 1) : null;
            int x = this.keyframes.toGraphX(frame.getTick());
            int y = this.toGraphY(this.getValue(frame.getValue(), axis));

            if (i == 0 && x > this.keyframes.area.x)
            {
                lineBuilder.add(this.keyframes.area.x, y);
            }

            if (prev != null)
            {
                this.renderInterpolation(lineBuilder, segment, prev, frame, x, axis);
            }

            lineBuilder.add(x, y);

            if (i == keyframes.size() - 1 && x < this.keyframes.area.ex())
            {
                lineBuilder.add(this.keyframes.area.ex(), y);
            }

            this.renderBezierHandles(lineBuilder, prev, frame, x, y, axis);
        }

        lineBuilder.render(context.batcher, SolidColorLineRenderer.get(Colors.COLOR.set(AXIS_COLORS[axis] | Colors.A100)));
    }

    private void renderInterpolation(LineBuilder lineBuilder, KeyframeSegment segment, Keyframe prev, Keyframe frame, int x, int axis)
    {
        IInterp interp = prev.getInterpolation().getInterp();
        int px = this.keyframes.toGraphX(prev.getTick());
        int py = this.toGraphY(this.getValue(prev.getValue(), axis));

        if (interp == Interpolations.CONST)
        {
            lineBuilder.add(x, py);
            lineBuilder.push();
        }
        else if (interp != Interpolations.LINEAR)
        {
            float steps = 50F;

            for (int j = 1; j <= steps; j++)
            {
                float a = j / steps;

                segment.setup(prev, frame, prev.getTick() + a * (frame.getTick() - prev.getTick()));
                float interpolate = this.toGraphY(this.getValue(segment.createInterpolated(), axis));

                lineBuilder.add(Lerps.lerp(px, x, a), interpolate);
            }
        }
    }

    private void renderBezierHandles(LineBuilder lineBuilder, Keyframe prev, Keyframe frame, int x, int y, int axis)
    {
        boolean add = false;

        if (frame.getInterpolation().getInterp() == Interpolations.BEZIER)
        {
            int rx = this.keyframes.toGraphX(frame.getTick() + frame.getRx(axis));
            int ry = this.toGraphY(this.getValue(frame.getValue(), axis) + frame.getRy(axis));

            lineBuilder.push();
            lineBuilder.add(x, y);
            lineBuilder.add(rx, ry);

            add = true;
        }

        if (prev != null && prev.getInterpolation().getInterp() == Interpolations.BEZIER)
        {
            int lx = this.keyframes.toGraphX(frame.getTick() - frame.getLx(axis));
            int ly = this.toGraphY(this.getValue(frame.getValue(), axis) + frame.getLy(axis));

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

    private void renderGraphPoints(UIContext context, BufferBuilder builder, Matrix4f matrix, int axis)
    {
        List<Keyframe> keyframes = this.sheet.channel.getKeyframes();

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe frame = keyframes.get(i);
            Keyframe prev = i > 0 ? keyframes.get(i - 1) : null;
            float tick = frame.getTick();
            int x1 = this.keyframes.toGraphX(tick);
            int y = this.toGraphY(this.getValue(frame.getValue(), axis));

            boolean isPointHover = this.isNear(this.keyframes.toGraphX(frame.getTick()), y, context.mouseX, context.mouseY);
            boolean toRemove = Window.isCtrlPressed() && isPointHover;

            if (this.keyframes.isSelecting())
            {
                isPointHover = isPointHover || this.keyframes.getGrabbingArea(context).isInside(x1, y);
            }

            int c = (this.sheet.selection.has(i) || isPointHover ? Colors.WHITE : AXIS_COLORS[axis]) | Colors.A100;

            if (toRemove)
            {
                c = Colors.RED | Colors.A100;
            }

            int offset = toRemove ? 4 : 3;

            UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, x1, y, offset, c);

            if (frame.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int rx = this.keyframes.toGraphX(frame.getTick() + frame.getRx(axis));
                int ry = this.toGraphY(this.getValue(frame.getValue(), axis) + frame.getRy(axis));

                UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, rx, ry, 3, c);
            }

            if (prev != null && prev.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int lx = this.keyframes.toGraphX(frame.getTick() - frame.getLx(axis));
                int ly = this.toGraphY(this.getValue(frame.getValue(), axis) + frame.getLy(axis));

                UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, lx, ly, 3, c);
            }
        }
    }

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY)
    {
        List<Keyframe> keyframes = this.sheet.channel.getKeyframes();

        for (Keyframe keyframe : keyframes)
        {
            int x = this.keyframes.toGraphX(keyframe.getTick());

            for (int axis = 0; axis < 3; axis++)
            {
                if (this.checkKeyframeHit(keyframe, x, mouseX, mouseY, axis))
                {
                    return new Pair<>(keyframe, this.getLastHitType());
                }
            }
        }

        return null;
    }

    private KeyframeType lastHitType;

    private KeyframeType getLastHitType()
    {
        return this.lastHitType;
    }

    private boolean checkKeyframeHit(Keyframe keyframe, int x, int mouseX, int mouseY, int axis)
    {
        int y = this.toGraphY(this.getValue(keyframe.getValue(), axis));

        if (this.isNear(x, y, mouseX, mouseY))
        {
            this.draggingAxis = axis;
            this.lastHitType = KeyframeType.REGULAR;
            return true;
        }

        int lx = this.keyframes.toGraphX(keyframe.getTick() - keyframe.getLx(axis));
        int ly = this.toGraphY(this.getValue(keyframe.getValue(), axis) + keyframe.getLy(axis));

        if (this.isNear(lx, ly, mouseX, mouseY))
        {
            this.draggingAxis = axis;
            this.lastHitType = KeyframeType.LEFT_HANDLE;
            return true;
        }

        int rx = this.keyframes.toGraphX(keyframe.getTick() + keyframe.getRx(axis));
        int ry = this.toGraphY(this.getValue(keyframe.getValue(), axis) + keyframe.getRy(axis));

        if (this.isNear(rx, ry, mouseX, mouseY))
        {
            this.draggingAxis = axis;
            this.lastHitType = KeyframeType.RIGHT_HANDLE;
            return true;
        }

        return false;
    }

    @Override
    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV)
    {
        if (type == null)
        {
            return;
        }

        Keyframe keyframe = type.a;
        Vector3f v = (Vector3f) originalV;
        float originalValue = this.getValue(v, this.draggingAxis);

        if (type.b == KeyframeType.REGULAR)
        {
            float offsetX = (float) this.keyframes.fromGraphX(originalX) - originalT;
            double offsetY = this.fromGraphY(originalY) - originalValue;

            float fx = (float) this.keyframes.fromGraphX(context.mouseX) - offsetX;
            double fy = this.fromGraphY(context.mouseY) - offsetY;

            if (!Window.isShiftPressed())
            {
                fx = Math.round(this.keyframes.fromGraphX(context.mouseX) - offsetX);
            }

            this.setTick(fx, false);
            this.setValue(fy, false);
        }
        else if (type.b == KeyframeType.LEFT_HANDLE)
        {
            float lx = -(float) ((this.keyframes.fromGraphX(context.mouseX)) - keyframe.getTick());
            float ly = (float) (this.fromGraphY(context.mouseY) - originalValue);

            keyframe.setLx(this.draggingAxis, lx);
            keyframe.setLy(this.draggingAxis, ly);

            if (!Window.isShiftPressed())
            {
                keyframe.setRx(this.draggingAxis, lx);
                keyframe.setRy(this.draggingAxis, -ly);
            }
        }
        else if (type.b == KeyframeType.RIGHT_HANDLE)
        {
            float rx = (float) ((this.keyframes.fromGraphX(context.mouseX)) - keyframe.getTick());
            float ry = (float) (this.fromGraphY(context.mouseY) - originalValue);

            keyframe.setRx(this.draggingAxis, rx);
            keyframe.setRy(this.draggingAxis, ry);

            if (!Window.isShiftPressed())
            {
                keyframe.setLx(this.draggingAxis, rx);
                keyframe.setLy(this.draggingAxis, -ry);
            }
        }

        this.keyframes.triggerChange();
    }
    
    @Override
    public void setValue(Object value, boolean unmergeable)
    {
        Keyframe selected = this.getSelected();
        IKeyframeFactory factory = selected.getFactory();
        Vector3f keyframe = (Vector3f) factory.copy(selected.getValue());
        
        if (value instanceof Vector3f)
        {
            keyframe.set((Vector3f) value);
        }
        else
        {
            double val = value instanceof Number ? ((Number) value).doubleValue() : 0;

            if (this.draggingAxis == 0) keyframe.x = (float) val;
            else if (this.draggingAxis == 1) keyframe.y = (float) val;
            else if (this.draggingAxis == 2) keyframe.z = (float) val;
        }
        
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.channel.getFactory() == factory)
            {
                sheet.setValue(keyframe, factory.copy(selected.getValue()), unmergeable);
            }
        }
    }

    private float getValue(Object value, int axis)
    {
        Vector3f v = (Vector3f) value;

        if (axis == 0) return v.x;
        if (axis == 1) return v.y;
        
        return v.z;
    }

    @Override
    public boolean addKeyframe(int mouseX, int mouseY)
    {
        float tick = (float) this.keyframes.fromGraphX(mouseX);
        UIKeyframeSheet sheet = this.sheet;

        if (!Window.isShiftPressed())
        {
            tick = Math.round(tick);
        }

        if (sheet != null)
        {
            float y = (float) this.fromGraphY(mouseY);
            Vector3f v = null;
            
            KeyframeSegment segment = sheet.channel.find(tick);
            
            if (segment != null)
            {
                v = (Vector3f) sheet.channel.getFactory().copy(segment.createInterpolated());
                this.updateClosestAxis(v, y);
            }
            else
            {
                v = new Vector3f(y, y, y);
            }
            
            this.addKeyframeManually(sheet, tick, v);
        }

        return sheet != null;
    }

    private void updateClosestAxis(Vector3f v, float y)
    {
        int closestAxis = 0;
        float closestDist = Float.MAX_VALUE;
        
        for (int i = 0; i < 3; i++)
        {
            float val = this.getValue(v, i);
            float dist = Math.abs(val - y);
            
            if (dist < closestDist)
            {
                closestDist = dist;
                closestAxis = i;
            }
        }
        
        if (closestAxis == 0) v.x = y;
        else if (closestAxis == 1) v.y = y;
        else if (closestAxis == 2) v.z = y;
    }
}
