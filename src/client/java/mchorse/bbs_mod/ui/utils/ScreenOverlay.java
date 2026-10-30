package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The editors' markup: one screen layer every viewport overlay draws through — the motion path,
 * the rig's IK goals, poles and controllers, spline points, shape key handles. Points are
 * projected onto the viewport and drawn flat over the frame: lines one screen pixel wide, and one
 * look for every handle — filled with its system's colour inside a dark outline, lighter under the
 * cursor, a white ring when selected, its name in a card at the cursor.
 *
 * <p>A frame is {@link #begin}, the overlays adding their lines, marks and handles, then
 * {@link #flush}. Picks answer from the handles of the last flush, nearest to the cursor first,
 * whichever overlay a handle belongs to, so two overlays never fight over one click. The axes
 * switch (F8) hides the whole layer, as it hides the gizmo. How solid it draws and how big its
 * handles are is {@link BBSSettings#editorMarkup}.</p>
 */
public class ScreenOverlay
{
    public enum Shape
    {
        DIAMOND, SQUARE, CIRCLE
    }

    /** How near the cursor (interface pixels) a handle answers to it. */
    public static final float PICK_RADIUS = 12F;

    /** Handle sizes in interface pixels: a diamond's diagonal, a square's side, a circle's diameter. */
    public static final float HANDLE_LARGE = 10F;
    public static final float HANDLE = 9F;
    public static final float HANDLE_SMALL = 6F;

    private static final int OUTLINE = 0xff0d1118;
    private static final int RING = Colors.WHITE;

    /* Dashes in screen pixels */
    private static final float DASH = 4F;
    private static final float GAP = 3F;

    private static final float DOT = 1.5F;
    private static final float KEY = 5F;
    private static final float CURRENT = 6F;
    private static final int SEGMENTS = 16;
    private static final float SQRT2 = 1.4142135F;

    public static class Handle
    {
        public final float x;
        public final float y;
        public final float depth;
        public final Shape shape;
        public final float size;
        public final int color;
        public final Object data;

        private final boolean pickable;
        private String label = "";
        private boolean selected;
        private boolean lit;

        private Handle(float x, float y, float depth, Shape shape, float size, int color, Object data, boolean pickable)
        {
            this.x = x;
            this.y = y;
            this.depth = depth;
            this.shape = shape;
            this.size = size;
            this.color = color;
            this.data = data;
            this.pickable = pickable;
        }

        /** The name the card at the cursor shows while the handle is hovered. */
        public Handle label(String label)
        {
            this.label = label == null ? "" : label;

            return this;
        }

        public Handle selected(boolean selected)
        {
            this.selected = selected;

            return this;
        }

        /** Lit as if hovered: dragged, or pointed at from somewhere else (a list row, a mirror twin). */
        public Handle lit(boolean lit)
        {
            this.lit = lit;

            return this;
        }
    }

    private record Line(float x1, float y1, float x2, float y2, int color, boolean dashed)
    {}

    private enum MarkKind
    {
        DOT, KEY, RING
    }

    private record Mark(MarkKind kind, float x, float y, int color)
    {}

    private Area viewport = new Area();
    private final List<Line> lines = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    private List<Handle> handles = new ArrayList<>();
    private List<Handle> flushed = new ArrayList<>();
    private Handle hovered;

    /** Start a frame over the viewport: the overlays add to it, {@link #flush} draws it. */
    public void begin(Area viewport)
    {
        this.viewport = viewport;
        this.lines.clear();
        this.marks.clear();
        this.handles = new ArrayList<>();
    }

    public Area getViewport()
    {
        return this.viewport;
    }

    /** Whether the layer shows at all: the axes switch (F8) hides it with the gizmo. */
    public boolean isShown()
    {
        return UIBaseMenu.shouldRenderAxes();
    }

    /* Projection */

    /** The point under {@code mvp} on the viewport: screen x, y and the clip depth; null off the depth range or behind the eye. */
    public Vector3f project(Matrix4f mvp, Vector3f point)
    {
        return project(mvp, this.viewport, point);
    }

    public static Vector3f project(Matrix4f mvp, Area viewport, Vector3f point)
    {
        Vector4f clip = mvp.transform(new Vector4f(point, 1));

        if (!Float.isFinite(clip.w) || clip.w <= 0.00001F)
        {
            return null;
        }

        clip.div(clip.w);

        return fromNDC(viewport, clip.x, clip.y, clip.z);
    }

    /** A normalized device point (already divided) on the viewport, as {@link #project} returns it. */
    public Vector3f fromNDC(Vector3f ndc)
    {
        return fromNDC(this.viewport, ndc.x, ndc.y, ndc.z);
    }

    private static Vector3f fromNDC(Area viewport, float x, float y, float z)
    {
        if (!Float.isFinite(x) || !Float.isFinite(y) || z < -1 || z > 1)
        {
            return null;
        }

        return new Vector3f(viewport.x + (x + 1) * viewport.w * 0.5F, viewport.y + (1 - y) * viewport.h * 0.5F, z);
    }

    /* Adding */

    public void line(Vector3f a, Vector3f b, int color)
    {
        if (a != null && b != null)
        {
            this.line(a.x, a.y, b.x, b.y, color);
        }
    }

    /** A line one screen pixel wide. */
    public void line(float x1, float y1, float x2, float y2, int color)
    {
        this.lines.add(new Line(x1, y1, x2, y2, color, false));
    }

    /** A relationship line: one pixel wide, dashed. */
    public void dashed(Vector3f a, Vector3f b, int color)
    {
        if (a != null && b != null)
        {
            this.lines.add(new Line(a.x, a.y, b.x, b.y, color, true));
        }
    }

    /** A frame's dot on a path. */
    public void dot(float x, float y, int color)
    {
        this.marks.add(new Mark(MarkKind.DOT, x, y, color));
    }

    /** A keyframe on a path, as the timeline draws one: a frame in the colour around a dark middle. */
    public void key(float x, float y, int color)
    {
        this.marks.add(new Mark(MarkKind.KEY, x, y, color));
    }

    /** The current frame's place on a path: a ring. */
    public void ring(float x, float y, int color)
    {
        this.marks.add(new Mark(MarkKind.RING, x, y, color));
    }

    /**
     * A handle the cursor can pick: drawn and answering {@link #pick} until the next flush.
     * {@code data} is the overlay's own record of it, to recognise its handles by.
     */
    public Handle handle(Vector3f screen, Shape shape, float size, int color, Object data)
    {
        return this.add(new Handle(screen.x, screen.y, screen.z, shape, size, color, data, true));
    }

    /** Looks like a handle but takes no clicks: a handle that can't be dragged, a preview of one. */
    public Handle marker(float x, float y, Shape shape, float size, int color)
    {
        return this.add(new Handle(x, y, 0F, shape, size, color, null, false));
    }

    private Handle add(Handle handle)
    {
        if (this.viewport.isInside((int) handle.x, (int) handle.y))
        {
            this.handles.add(handle);
        }

        return handle;
    }

    /* Picking */

    /** The handle nearest to the point among the last flush's, within {@link #PICK_RADIUS}; null over none. */
    public Handle pick(int x, int y)
    {
        if (!this.isShown())
        {
            return null;
        }

        return nearest(this.flushed, x, y);
    }

    /** The handle under the cursor as of the last flush. */
    public Handle getHovered()
    {
        return this.hovered;
    }

    private static Handle nearest(List<Handle> handles, int x, int y)
    {
        Handle best = null;
        float distance = PICK_RADIUS * PICK_RADIUS;

        for (Handle handle : handles)
        {
            if (!handle.pickable)
            {
                continue;
            }

            float dx = handle.x - x;
            float dy = handle.y - y;
            float d = dx * dx + dy * dy;

            if (d < distance || (d == distance && best != null && handle.depth < best.depth))
            {
                best = handle;
                distance = d;
            }
        }

        return best;
    }

    /* Drawing */

    /** Draw the frame: lines, then marks, then handles (the selected and lit ones on top), then the hovered handle's name. */
    public void flush(UIContext context)
    {
        List<Handle> drawn = this.handles;

        this.handles = new ArrayList<>();
        this.flushed = this.isShown() ? drawn : new ArrayList<>();
        this.hovered = this.viewport.isInside(context) ? nearest(this.flushed, context.mouseX, context.mouseY) : null;

        if (this.isShown() && (!this.lines.isEmpty() || !this.marks.isEmpty() || !drawn.isEmpty()))
        {
            this.draw(context, drawn);
        }

        this.lines.clear();
        this.marks.clear();

        if (this.hovered != null && !this.hovered.label.isEmpty())
        {
            context.batcher.textCard(this.hovered.label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    private void draw(UIContext context, List<Handle> drawn)
    {
        float px = 1F / BBSModClient.getGUIScale();
        float scale = BBSSettings.editorMarkup.size.get();
        float opacity = BBSSettings.editorMarkup.opacity.get();

        context.batcher.flush();
        context.batcher.clip(this.viewport, context);

        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Painter painter = new Painter(builder, matrix, opacity);

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (Line line : this.lines)
        {
            if (line.dashed)
            {
                painter.dashed(line.x1, line.y1, line.x2, line.y2, px, DASH * px, GAP * px, line.color);
            }
            else
            {
                painter.line(line.x1, line.y1, line.x2, line.y2, px, line.color);
            }
        }

        for (Mark mark : this.marks)
        {
            if (mark.kind == MarkKind.DOT)
            {
                painter.square(mark.x, mark.y, DOT * scale * 0.5F, mark.color);
            }
            else if (mark.kind == MarkKind.KEY)
            {
                float half = KEY * scale * 0.5F;

                painter.square(mark.x, mark.y, half, mark.color);
                painter.square(mark.x, mark.y, half - 2 * px, OUTLINE);
            }
            else
            {
                float half = CURRENT * scale * 0.5F;

                painter.ring(mark.x, mark.y, half, half - 2 * px, mark.color);
            }
        }

        for (Handle handle : drawn)
        {
            if (!this.onTop(handle))
            {
                this.drawHandle(painter, handle, px, scale);
            }
        }

        for (Handle handle : drawn)
        {
            if (this.onTop(handle))
            {
                this.drawHandle(painter, handle, px, scale);
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.enableCull();

        context.batcher.unclip(context);
    }

    private boolean onTop(Handle handle)
    {
        return handle.selected || handle.lit || handle == this.hovered;
    }

    private void drawHandle(Painter painter, Handle handle, float px, float scale)
    {
        float half = handle.size * scale * 0.5F;
        /* An outline of the same width all round: a diamond's corners stand further out. */
        float step = handle.shape == Shape.DIAMOND ? px * SQRT2 : px;
        int color = handle.lit || handle == this.hovered ? lighten(handle.color) : handle.color;

        if (handle.selected)
        {
            painter.shape(handle.shape, handle.x, handle.y, half + step * 2, RING);
        }

        painter.shape(handle.shape, handle.x, handle.y, half + step, OUTLINE);
        painter.shape(handle.shape, handle.x, handle.y, half, color);
    }

    /** The colour under the cursor: towards white, alpha kept. */
    private static int lighten(int color)
    {
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;

        r += (int) ((255 - r) * 0.45F);
        g += (int) ((255 - g) * 0.45F);
        b += (int) ((255 - b) * 0.45F);

        return (color & 0xff000000) | (r << 16) | (g << 8) | b;
    }

    /** Quads into one buffer, every colour's alpha scaled by the layer's opacity. */
    private static class Painter
    {
        private final BufferBuilder builder;
        private final Matrix4f matrix;
        private final float opacity;

        Painter(BufferBuilder builder, Matrix4f matrix, float opacity)
        {
            this.builder = builder;
            this.matrix = matrix;
            this.opacity = opacity;
        }

        void line(float x1, float y1, float x2, float y2, float width, int color)
        {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);

            if (!Float.isFinite(length) || length < 0.0001F)
            {
                return;
            }

            float nx = -dy / length * width * 0.5F;
            float ny = dx / length * width * 0.5F;

            this.quad(x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, color);
        }

        void dashed(float x1, float y1, float x2, float y2, float width, float dash, float gap, int color)
        {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);

            if (!Float.isFinite(length) || length < 0.0001F)
            {
                return;
            }

            for (float t = 0; t < length; t += dash + gap)
            {
                float a = t / length;
                float b = Math.min(t + dash, length) / length;

                this.line(x1 + dx * a, y1 + dy * a, x1 + dx * b, y1 + dy * b, width, color);
            }
        }

        void square(float x, float y, float half, int color)
        {
            this.quad(x - half, y - half, x - half, y + half, x + half, y + half, x + half, y - half, color);
        }

        void shape(Shape shape, float x, float y, float half, int color)
        {
            if (shape == Shape.DIAMOND)
            {
                this.quad(x, y - half, x - half, y, x, y + half, x + half, y, color);
            }
            else if (shape == Shape.SQUARE)
            {
                this.square(x, y, half, color);
            }
            else
            {
                for (int i = 0; i < SEGMENTS; i++)
                {
                    double a = i * Math.PI * 2 / SEGMENTS;
                    double b = (i + 1) * Math.PI * 2 / SEGMENTS;
                    float ax = x + (float) Math.cos(a) * half;
                    float ay = y + (float) Math.sin(a) * half;
                    float bx = x + (float) Math.cos(b) * half;
                    float by = y + (float) Math.sin(b) * half;

                    this.quad(x, y, ax, ay, bx, by, bx, by, color);
                }
            }
        }

        void ring(float x, float y, float outer, float inner, int color)
        {
            for (int i = 0; i < SEGMENTS; i++)
            {
                double a = i * Math.PI * 2 / SEGMENTS;
                double b = (i + 1) * Math.PI * 2 / SEGMENTS;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float cb = (float) Math.cos(b), sb = (float) Math.sin(b);

                this.quad(x + ca * inner, y + sa * inner, x + ca * outer, y + sa * outer, x + cb * outer, y + sb * outer, x + cb * inner, y + sb * inner, color);
            }
        }

        private void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color)
        {
            int alpha = (int) (((color >>> 24) & 0xff) * this.opacity);
            int argb = (alpha << 24) | (color & 0xffffff);

            this.builder.vertex(this.matrix, x1, y1, 0F).color(argb).next();
            this.builder.vertex(this.matrix, x2, y2, 0F).color(argb).next();
            this.builder.vertex(this.matrix, x3, y3, 0F).color(argb).next();
            this.builder.vertex(this.matrix, x4, y4, 0F).color(argb).next();
        }
    }
}
