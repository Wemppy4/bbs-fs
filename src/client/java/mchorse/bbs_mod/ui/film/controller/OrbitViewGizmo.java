package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.graphics.GuiQuadMesh;
import org.joml.Matrix3x2fc;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Navigation ball in the bottom left corner of the film editor's preview
 * (like in Blender and other DCC apps). Available only in the orbit camera
 * mode: dragging the ball rotates the orbit, clicking an axis ball snaps
 * the camera to look along that world axis (and switches the projection to
 * orthographic, see {@link OrbitFilmCameraController#snapToAxis}).
 *
 * The ball is drawn from {@link UIFilmPanel#lastView}, so it always matches
 * the rendered frame, including the camera smoothing.
 *
 * The axes live in the orbit's anchor space: detached that is the world, but
 * when the orbit is attached to a replay, they follow the replay's body yaw —
 * so the axis balls mean "in front of/behind/beside the record", and both the
 * axes and the attached camera turn together with it.
 */
public class OrbitViewGizmo
{
    public static final int RADIUS = 30;
    public static final int PADDING = 6;

    private static final float BALL_RADIUS = 7F;
    private static final int SEGMENTS = 24;
    private static final int DRAG_THRESHOLD = 3;

    private static final int[][] AXES = {
        {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
        {-1, 0, 0}, {0, -1, 0}, {0, 0, -1}
    };
    private static final String[] LABELS = {"X", "Y", "Z"};
    private static final int[] COLORS = {
        Colors.A100 | Colors.RED,
        Colors.A100 | Colors.GREEN,
        Colors.A100 | Colors.BLUE
    };

    private final UIFilmController controller;

    private boolean pressed;
    private int pressedAxis = -1;
    private boolean dragged;
    private int lastX;
    private int lastY;
    private int pressX;
    private int pressY;

    public OrbitViewGizmo(UIFilmController controller)
    {
        this.controller = controller;
    }

    public boolean isActive()
    {
        return BBSSettings.editorOrbitGizmo.get()
            && this.controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT
            && !this.controller.panel.isFlying()
            && !this.controller.isControlling();
    }

    public boolean mouseClicked(UIContext context, Area area)
    {
        if (!this.isActive() || context.mouseButton != 0)
        {
            return false;
        }

        float dx = context.mouseX - this.centerX(area);
        float dy = context.mouseY - this.centerY(area);
        float r = this.radius() + 2F;

        if (dx * dx + dy * dy > r * r)
        {
            return false;
        }

        this.pressed = true;
        this.dragged = false;
        this.pressedAxis = this.getHoveredAxis(this.getBalls(area), context.mouseX, context.mouseY);
        this.lastX = this.pressX = context.mouseX;
        this.lastY = this.pressY = context.mouseY;

        return true;
    }

    public boolean mouseReleased(UIContext context)
    {
        if (!this.pressed)
        {
            return false;
        }

        boolean snap = !this.dragged && this.pressedAxis >= 0;
        int axis = this.pressedAxis;

        this.reset();

        if (snap)
        {
            this.controller.orbit.snapToAxis(AXES[axis][0], AXES[axis][1], AXES[axis][2]);
            UIUtils.playClick();
        }

        return true;
    }

    public void render(UIContext context, Area area)
    {
        if (!this.isActive())
        {
            this.reset();

            return;
        }

        this.handleDragging(context);

        float cx = this.centerX(area);
        float cy = this.centerY(area);
        float mdx = context.mouseX - cx;
        float mdy = context.mouseY - cy;
        float hoverRadius = this.radius() + 2F;
        boolean hover = this.pressed || mdx * mdx + mdy * mdy <= hoverRadius * hoverRadius;

        Ball[] balls = this.getBalls(area);
        int hoveredAxis = hover && !this.dragged ? this.getHoveredAxis(balls, context.mouseX, context.mouseY) : -1;
        Batcher2D batcher = context.batcher;

        if (hover)
        {
            this.fillCircle(batcher, cx, cy, hoverRadius, Colors.setA(Colors.WHITE, 0.2F));
        }

        Ball[] sorted = balls.clone();

        Arrays.sort(sorted, Comparator.comparingDouble(Ball::z));

        FontRenderer font = batcher.getFont();

        for (Ball ball : sorted)
        {
            int axis = ball.index() % 3;
            boolean positive = ball.index() < 3;
            boolean hovered = ball.index() == hoveredAxis;

            /* Depth cue: balls on the far hemisphere are smaller and dimmer. */
            float t = (ball.z() + 1F) * 0.5F;
            float r = this.ballRadius() * Lerps.lerp(0.85F, 1.05F, t);
            float ring = Math.max(1.5F, r * 0.22F);
            int color = Colors.lerp(0xFF333333, COLORS[axis], Lerps.lerp(0.45F, 1F, t));

            if (positive)
            {
                this.line(batcher, cx, cy, ball.x(), ball.y(), Math.max(1.5F, r * 0.28F), color);
                this.fillCircle(batcher, ball.x(), ball.y(), r, color);
            }
            else
            {
                this.annulus(batcher, ball.x(), ball.y(), r - ring, r, color);
                this.fillCircle(batcher, ball.x(), ball.y(), r - ring, Colors.setA(color, hovered ? 0.6F : 0.25F));
            }

            if (positive || hovered)
            {
                String label = LABELS[axis];

                /* 0xFFFEFEFE reads as white, but dodges the light theme's
                 * white -> black text remap (the ball sits on the video, not
                 * on a themed UI background). */
                int labelColor = hovered ? 0xFFFEFEFE : 0xFF1D1D1D;

                batcher.text(label, ball.x() - font.getWidth(label) / 2F + 0.5F, ball.y() - 3F, labelColor, false);
            }
        }
    }

    private void reset()
    {
        this.pressed = false;
        this.pressedAxis = -1;
        this.dragged = false;
    }

    private void handleDragging(UIContext context)
    {
        if (!this.pressed)
        {
            return;
        }

        int mx = context.mouseX;
        int my = context.mouseY;

        if (!this.dragged && (Math.abs(mx - this.pressX) > DRAG_THRESHOLD || Math.abs(my - this.pressY) > DRAG_THRESHOLD))
        {
            this.dragged = true;
        }

        if (this.dragged)
        {
            this.controller.orbit.rotate(mx - this.lastX, my - this.lastY);
        }

        this.lastX = mx;
        this.lastY = my;
    }

    private float scale()
    {
        return BBSSettings.editorOrbitGizmoScale.get();
    }

    private float radius()
    {
        return RADIUS * this.scale();
    }

    private float ballRadius()
    {
        return BALL_RADIUS * this.scale();
    }

    private float centerX(Area area)
    {
        return area.x + PADDING + this.radius();
    }

    private float centerY(Area area)
    {
        return area.ey() - PADDING - this.radius();
    }

    private Ball[] getBalls(Area area)
    {
        Matrix3f view = new Matrix3f(this.controller.panel.lastView);
        float anchorYaw = this.controller.orbit.getAnchorYaw();
        float cx = this.centerX(area);
        float cy = this.centerY(area);
        float arm = this.radius() - this.ballRadius();
        Ball[] balls = new Ball[AXES.length];
        Vector3f vector = new Vector3f();

        for (int i = 0; i < AXES.length; i++)
        {
            /* Anchor space -> world space -> view space */
            vector.set(AXES[i][0], AXES[i][1], AXES[i][2]).rotateY(anchorYaw);
            view.transform(vector);

            /* View space is y-up, screen space is y-down. */
            balls[i] = new Ball(i, cx + vector.x * arm, cy - vector.y * arm, vector.z);
        }

        return balls;
    }

    private int getHoveredAxis(Ball[] balls, int mouseX, int mouseY)
    {
        int result = -1;
        float bestZ = -Float.MAX_VALUE;
        float r = this.ballRadius() + 1F;

        for (Ball ball : balls)
        {
            float dx = mouseX - ball.x();
            float dy = mouseY - ball.y();

            if (dx * dx + dy * dy <= r * r && ball.z() > bestZ)
            {
                bestZ = ball.z();
                result = ball.index();
            }
        }

        return result;
    }

    /* Drawing primitives (winding matches Batcher2D's quads) */

    private void fillCircle(Batcher2D batcher, float x, float y, float radius, int color)
    {
        Matrix3x2fc matrix = batcher.getContext().getMatrices();
        GuiQuadMesh builder = new GuiQuadMesh();

        /* The deferred GUI composites everything through the hard-wired QUADS index buffer, so the
         * old TRIANGLE_FAN becomes one degenerate quad (last corner doubled) per fan triangle. */
        for (int i = 0; i < SEGMENTS; i++)
        {
            double a1 = i / (double) SEGMENTS * Math.PI * 2D;
            double a2 = (i + 1) / (double) SEGMENTS * Math.PI * 2D;
            float x1 = (float) (x - Math.cos(a1) * radius);
            float y1 = (float) (y + Math.sin(a1) * radius);
            float x2 = (float) (x - Math.cos(a2) * radius);
            float y2 = (float) (y + Math.sin(a2) * radius);

            builder.vertex(matrix, x, y).color(color);
            builder.vertex(matrix, x1, y1).color(color);
            builder.vertex(matrix, x2, y2).color(color);
            builder.vertex(matrix, x2, y2).color(color);
        }

        this.draw(batcher, builder);
    }

    private void annulus(Batcher2D batcher, float x, float y, float inner, float outer, int color)
    {
        Matrix3x2fc matrix = batcher.getContext().getMatrices();
        GuiQuadMesh builder = new GuiQuadMesh();

        for (int i = 0; i < SEGMENTS; i++)
        {
            double a1 = i / (double) SEGMENTS * Math.PI * 2D;
            double a2 = (i + 1) / (double) SEGMENTS * Math.PI * 2D;
            float ix1 = (float) (x - Math.cos(a1) * inner);
            float iy1 = (float) (y + Math.sin(a1) * inner);
            float ix2 = (float) (x - Math.cos(a2) * inner);
            float iy2 = (float) (y + Math.sin(a2) * inner);
            float ox1 = (float) (x - Math.cos(a1) * outer);
            float oy1 = (float) (y + Math.sin(a1) * outer);
            float ox2 = (float) (x - Math.cos(a2) * outer);
            float oy2 = (float) (y + Math.sin(a2) * outer);

            /* One quad per segment — the same two triangles the explicit emission built. */
            builder.vertex(matrix, ix2, iy2).color(color);
            builder.vertex(matrix, ix1, iy1).color(color);
            builder.vertex(matrix, ox1, oy1).color(color);
            builder.vertex(matrix, ox2, oy2).color(color);
        }

        this.draw(batcher, builder);
    }

    private void line(Batcher2D batcher, float x1, float y1, float x2, float y2, float width, int color)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length <= 0.0001F)
        {
            return;
        }

        float nx = -dy / length * width / 2F;
        float ny = dx / length * width / 2F;

        Matrix3x2fc matrix = batcher.getContext().getMatrices();
        GuiQuadMesh builder = new GuiQuadMesh();

        builder.vertex(matrix, x1 - nx, y1 - ny).color(color);
        builder.vertex(matrix, x1 + nx, y1 + ny).color(color);
        builder.vertex(matrix, x2 + nx, y2 + ny).color(color);
        builder.vertex(matrix, x2 - nx, y2 - ny).color(color);

        this.draw(batcher, builder);
    }

    /**
     * 1.21.11: the 1.21.6+ GUI records draws into a GuiRenderState and composites them afterwards, so an
     * immediate mid-frame draw is overpainted. The recorded quad mesh is submitted as one deferred GUI
     * element instead (blend and the program come from its pipeline).
     */
    private void draw(Batcher2D batcher, GuiQuadMesh builder)
    {
        if (!builder.isEmpty())
        {
            batcher.drawQuadMesh(builder);
        }
    }

    private record Ball(int index, float x, float y, float z)
    {}
}
