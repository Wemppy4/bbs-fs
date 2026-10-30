package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.spline.SplineCurve;
import mchorse.bbs_mod.cubic.spline.SplinePoint;
import mchorse.bbs_mod.cubic.spline.SplineSource;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spline handles on the editors' markup ({@link ScreenOverlay}), shared by the form, state and film
 * viewports: the curve as a line and its points as small squares, the picked ones ringed and the one
 * under the cursor lighter; with Ctrl over the curve, a square the same as a point marks where a
 * click puts a new one. The markup's spline switch hides them all.
 */
public class SplineOverlay
{
    private static final int COLOR = 0xFF79BBFF;

    public record Hit(SplineSource chain, SplinePoint point, float x, float y, float depth) {}
    public record CurveHit(SplineSource chain, int after, Vector3f position, float x, float y, float depth, float distance) {}

    private ScreenOverlay overlay;
    private CurveHit curveHit;

    /** Actual path geometry in the palette's ordinary form-preview frame, without picking. */
    public static void drawPreview(UIContext context, SplineSource source, Matrix4f matrix, Area area)
    {
        List<Vector3f> points = new ArrayList<>();
        for (SplinePoint point : source.points().getAllTyped())
            points.add(new Vector3f(source.position(point.getId()).translate));
        if (points.isEmpty()) return;

        ScreenOverlay overlay = new ScreenOverlay();
        overlay.begin(area);
        Vector3f previous = null;
        for (Vector3f sample : SplineCurve.sample(points, 20, source.closed()))
        {
            Vector3f current = matrix.transformPosition(new Vector3f(sample));
            if (previous != null) overlay.line(previous, current, COLOR);
            previous = current;
        }
        for (Vector3f point : points)
        {
            Vector3f position = matrix.transformPosition(new Vector3f(point));
            if (position.isFinite()) overlay.marker(position.x, position.y, ScreenOverlay.Shape.SQUARE, ScreenOverlay.HANDLE_SMALL, COLOR);
        }
        overlay.flush(context);
    }

    /** Start the frame on the viewport's markup: false when the splines don't show (the markup hidden, or its spline switch off). */
    public boolean begin(ScreenOverlay overlay)
    {
        this.overlay = overlay;
        this.curveHit = null;
        return overlay.isShown() && BBSSettings.editorMarkup.splines.get();
    }

    /** The spline point nearest to the point, if the nearest handle of the markup is one. */
    public Hit pick(int x, int y)
    {
        if (this.overlay == null) return null;
        ScreenOverlay.Handle handle = this.overlay.pick(x, y);
        return handle != null && handle.data instanceof Hit hit ? hit : null;
    }

    public SplineSource pickSource(int x, int y)
    {
        Hit hit = this.pick(x, y);
        if (hit != null) return hit.chain;
        CurveHit curve = this.insertionAt(x, y);
        return curve == null ? null : curve.chain;
    }

    /** Called in the ordinary GUI pass, after the viewport restores its projection. */
    public void draw(UIContext context, Matrix4f parentModelView, Matrix4f projection, SplineSource chain,
        Collection<String> selectedPointIds, String hoveredPointId)
    {
        Area viewport = this.overlay.getViewport();
        Matrix4f matrix = new Matrix4f(projection).mul(parentModelView);
        List<Vector3f> positions = new ArrayList<>();
        for (SplinePoint point : chain.points().getAllTyped()) positions.add(new Vector3f(chain.position(point.getId()).translate));
        if (positions.isEmpty()) return;
        Vector3f previous = null;
        for (Vector3f point : SplineCurve.sample(positions, 20, chain.closed()))
        {
            Vector3f current = this.overlay.project(matrix, point);
            if (previous != null && current != null) this.overlay.line(previous, current, COLOR);
            previous = current;
        }
        if (viewport.isInside(context) && positions.size() > 1)
        {
            CurveHit hit = findCurveHit(chain, positions, matrix, viewport, context.mouseX, context.mouseY);
            if (hit != null && (this.curveHit == null || hit.distance < this.curveHit.distance
                || (Math.abs(hit.distance - this.curveHit.distance) < 0.5F && hit.depth < this.curveHit.depth))) this.curveHit = hit;
        }
        int index = 0;
        for (SplinePoint point : chain.points().getAllTyped())
        {
            index++;
            Vector3f p = this.overlay.project(matrix, chain.position(point.getId()).translate);
            if (p == null) continue;
            this.overlay.handle(p, ScreenOverlay.Shape.SQUARE, ScreenOverlay.HANDLE_SMALL, COLOR, new Hit(chain, point, p.x, p.y, p.z))
                .label(UIKeys.MARKUP_SPLINE_POINT.format(index).get())
                .selected(selectedPointIds.contains(point.getId()))
                .lit(point.getId().equals(hoveredPointId));
        }
    }

    public CurveHit insertionAt(int x, int y)
    {
        if (this.overlay == null || !this.overlay.isShown() || !BBSSettings.editorMarkup.splines.get()) return null;
        if (this.curveHit == null || this.overlay.pick(x, y) != null) return null;
        float dx = this.curveHit.x - x, dy = this.curveHit.y - y;
        return dx * dx + dy * dy <= 64F ? this.curveHit : null;
    }

    public void drawInsertionPreview(UIContext context)
    {
        if (!this.overlay.getViewport().isInside(context)) return;
        CurveHit hit = this.insertionAt(context.mouseX, context.mouseY);
        if (hit == null) return;
        this.overlay.marker(hit.x, hit.y, ScreenOverlay.Shape.SQUARE, ScreenOverlay.HANDLE_SMALL, COLOR);
    }

    /** Search in screen space, but evaluate the returned point on the actual 3D curve. */
    public static CurveHit findCurveHit(SplineSource chain, List<Vector3f> points, Matrix4f matrix, Area viewport, int x, int y)
    {
        if (points.size() < 2) return null;
        boolean closed = chain != null && chain.closed();
        int segments = closed ? points.size() : points.size() - 1;
        int steps = segments * 20;
        float best = 64F;
        float bestDepth = Float.POSITIVE_INFINITY;
        int segment = -1;
        Vector3f previous = ScreenOverlay.project(matrix, viewport, points.get(0));
        for (int i = 1; i <= steps; i++)
        {
            Vector3f current = ScreenOverlay.project(matrix, viewport, SplineCurve.evaluate(points, i / (float) steps, closed));
            if (previous != null && current != null)
            {
                float dx = current.x - previous.x, dy = current.y - previous.y;
                float length = dx * dx + dy * dy;
                float fraction = length < 1E-6F ? 0F : Math.max(0F, Math.min(1F, ((x - previous.x) * dx + (y - previous.y) * dy) / length));
                float offsetX = x - previous.x - dx * fraction, offsetY = y - previous.y - dy * fraction;
                float distance = offsetX * offsetX + offsetY * offsetY;
                float depth = previous.z + (current.z - previous.z) * fraction;
                if (distance < 64F && (distance < best - 0.25F || (Math.abs(distance - best) <= 0.25F && depth < bestDepth)))
                {
                    best = distance;
                    bestDepth = depth;
                    segment = i - 1;
                }
            }
            previous = current;
        }
        if (segment < 0) return null;
        float low = segment / (float) steps, high = (segment + 1F) / steps;
        for (int i = 0; i < 12; i++)
        {
            float a = low + (high - low) / 3F, b = high - (high - low) / 3F;
            Vector3f pa = ScreenOverlay.project(matrix, viewport, SplineCurve.evaluate(points, a, closed));
            Vector3f pb = ScreenOverlay.project(matrix, viewport, SplineCurve.evaluate(points, b, closed));
            if (pa == null || pb == null) return null;
            if (screenDistance(pa, x, y) < screenDistance(pb, x, y)) high = b; else low = a;
        }
        float t = (low + high) * 0.5F;
        Vector3f position = SplineCurve.evaluate(points, t, closed);
        Vector3f screen = ScreenOverlay.project(matrix, viewport, position);
        if (screen == null || !viewport.isInside((int) screen.x, (int) screen.y)) return null;
        return new CurveHit(chain, Math.min(segments - 1, (int) (t * segments)),
            position, screen.x, screen.y, screen.z, screenDistance(screen, x, y));
    }

    private static float screenDistance(Vector3f point, int x, int y)
    {
        float dx = point.x - x, dy = point.y - y;
        return dx * dx + dy * dy;
    }
}
