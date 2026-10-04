package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.spline.SplineCurve;
import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.cubic.spline.SplineSource;
import mchorse.bbs_mod.cubic.spline.SplinePoint;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.l10n.L10n;

/** Screen-space spline handles shared by the form, state and film viewports. */
public class SplineOverlay
{
    @com.github.bsideup.jabel.Desugar
    public record Hit(SplineSource chain, SplinePoint point, float x, float y, float depth) implements SplineEditorUtils.Hit {}
    @com.github.bsideup.jabel.Desugar
    public record CurveHit(SplineSource chain, int after, Vector3f position, float x, float y, float depth, float distance) {}

    private final List<Hit> handles = new ArrayList<>();
    private CurveHit curveHit;

    /** Actual path geometry in the palette's ordinary form-preview frame, without picking. */
    public static void drawPreview(UIContext context, SplineSource source, Matrix4f matrix)
    {
        List<Vector3f> points = new ArrayList<>();
        for (SplinePoint point : source.points().getAllTyped())
            points.add(new Vector3f(source.position(point.getId()).translate));
        if (points.isEmpty()) return;

        Vector3f previous = null;
        for (Vector3f sample : SplineCurve.sample(points, 20, source.closed()))
        {
            Vector3f current = matrix.transformPosition(new Vector3f(sample));
            if (previous != null)
            {
                line(context, previous, current, 0xAA111820, 2F);
                line(context, previous, current, 0xFF79BBFF, 0.85F);
            }
            previous = current;
        }
        for (Vector3f point : points)
        {
            Vector3f position = matrix.transformPosition(new Vector3f(point));
            if (!position.isFinite()) continue;
            context.batcher.box(position.x - 3, position.y - 3, position.x + 3, position.y + 3, 0xFF15212B);
            context.batcher.box(position.x - 2, position.y - 2, position.x + 2, position.y + 2, 0xFF79BBFF);
        }
    }

    /** Clear pick data and share the axes visibility switch across editor viewports. */
    public boolean begin()
    {
        this.handles.clear();
        this.curveHit = null;
        return UIBaseMenu.shouldRenderAxes();
    }

    public Hit pick(int x, int y)
    {
        /* The shortcut can hide the overlay between its last draw and the next click. */
        if (!UIBaseMenu.shouldRenderAxes()) return null;
        Hit best = null;
        float distance = 100;
        for (Hit hit : this.handles)
        {
            if (hit.chain instanceof SplineIK && !BBSSettings.ikDebug.enabled.get()) continue;
            float d = (hit.x - x) * (hit.x - x) + (hit.y - y) * (hit.y - y);
            if (d < distance || (d == distance && best != null && hit.depth < best.depth))
            {
                best = hit;
                distance = d;
            }
        }
        return best;
    }

    public SplineSource pickSource(int x, int y)
    {
        Hit hit = this.pick(x, y);
        if (hit != null) return hit.chain;
        CurveHit curve = this.insertionAt(x, y);
        return curve == null ? null : curve.chain;
    }

    /** Called in the ordinary GUI pass, after the viewport restores its projection. */
    public void draw(UIContext context, Matrix4f parentModelView, Matrix4f projection, Area viewport, SplineSource chain,
        Collection<String> selectedPointIds, String hoveredPointId)
    {
        if (chain instanceof SplineIK && !BBSSettings.ikDebug.enabled.get()) return;
        Matrix4f matrix = new Matrix4f(projection).mul(parentModelView);
        List<Vector3f> positions = new ArrayList<>();
        for (SplinePoint point : chain.points().getAllTyped()) positions.add(new Vector3f(chain.position(point.getId()).translate));
        if (positions.isEmpty()) return;
        int color = selectedPointIds.isEmpty() ? 0xFF6599CF : 0xFF79BBFF;
        context.batcher.clip(viewport, context);
        Vector3f previous = null;
        List<Vector3f> sampled = SplineCurve.sample(positions, 20, chain.closed());
        for (Vector3f point : sampled)
        {
            Vector3f current = project(matrix, viewport, point);
            if (previous != null && current != null)
            {
                line(context, previous, current, 0xAA111820, 2F);
                line(context, previous, current, color, 0.85F);
            }
            previous = current;
        }
        if (viewport.isInside(context) && positions.size() > 1)
        {
            CurveHit hit = findCurveHit(chain, positions, matrix, viewport, context.mouseX, context.mouseY);
            if (hit != null && (this.curveHit == null || hit.distance < this.curveHit.distance
                || (Math.abs(hit.distance - this.curveHit.distance) < 0.5F && hit.depth < this.curveHit.depth))) this.curveHit = hit;
            if (hit != null)
            {
                int start = hit.after * 20;
                for (int i = start + 1; i <= Math.min(start + 20, sampled.size() - 1); i++)
                {
                    Vector3f a = project(matrix, viewport, sampled.get(i - 1));
                    Vector3f b = project(matrix, viewport, sampled.get(i));
                    if (a != null && b != null) line(context, a, b, 0xFFB7DCFF, 1.25F);
                }
            }
        }
        for (SplinePoint point : chain.points().getAllTyped())
        {
            Vector3f p = project(matrix, viewport, chain.position(point.getId()).translate);
            if (p == null || !viewport.isInside((int) p.x, (int) p.y)) continue;
            boolean selected = selectedPointIds.contains(point.getId());
            boolean hover = point.getId().equals(hoveredPointId);
            float radius = selected ? 4 : 3;
            context.batcher.box(p.x - radius - 1, p.y - radius - 1, p.x + radius + 1, p.y + radius + 1, 0xFF15212B);
            context.batcher.box(p.x - radius, p.y - radius, p.x + radius, p.y + radius, selected ? Colors.WHITE : color);
            if (selected || hover) context.batcher.outline(p.x - radius - 3, p.y - radius - 3,
                p.x + radius + 3, p.y + radius + 3, hover ? Colors.WHITE : 0xFF5599FF);
            this.handles.add(new Hit(chain, point, p.x, p.y, p.z));
        }
        context.batcher.unclip(context);
    }

    /** All chains have been collected: label the closest handle only. */
    public void finish(UIContext context, Area viewport)
    {
        if (!viewport.isInside(context)) return;
        Hit hit = this.pick(context.mouseX, context.mouseY);
        if (hit == null) return;
        context.batcher.clip(viewport, context);
        context.batcher.outline(hit.x - 8, hit.y - 8, hit.x + 8, hit.y + 8, Colors.WHITE);
        String label = SplinePoint.displayName(hit.chain.points().getAllTyped().indexOf(hit.point) + 1);
        float x = Math.min(hit.x + 13, viewport.ex() - context.batcher.getFont().getWidth(label) - 6);
        float y = Math.max(viewport.y + 4, hit.y - 18);
        context.batcher.textShadow(label, x, y, Colors.WHITE);
        context.batcher.unclip(context);
    }

    public CurveHit insertionAt(int x, int y)
    {
        if (!UIBaseMenu.shouldRenderAxes()) return null;
        if (this.curveHit == null || (this.curveHit.chain instanceof SplineIK && !BBSSettings.ikDebug.enabled.get()) || this.pick(x, y) != null) return null;
        float dx = this.curveHit.x - x, dy = this.curveHit.y - y;
        return dx * dx + dy * dy <= 64F ? this.curveHit : null;
    }

    public void drawInsertionPreview(UIContext context, Area viewport)
    {
        if (!viewport.isInside(context)) return;
        CurveHit hit = this.insertionAt(context.mouseX, context.mouseY);
        if (hit == null) return;
        context.batcher.clip(viewport, context);
        context.batcher.box(hit.x - 7, hit.y - 7, hit.x + 7, hit.y + 7, 0xD015212B);
        context.batcher.outline(hit.x - 7, hit.y - 7, hit.x + 7, hit.y + 7, 0xFF79BBFF);
        context.batcher.box(hit.x - 4, hit.y - 1, hit.x + 4, hit.y + 1, Colors.WHITE);
        context.batcher.box(hit.x - 1, hit.y - 4, hit.x + 1, hit.y + 4, Colors.WHITE);
        String label = L10n.lang("bbs.ui.forms.editors.model.spline.insert_point").get();
        float x = Math.max(viewport.x + 4, Math.min(hit.x + 13, viewport.ex() - context.batcher.getFont().getWidth(label) - 6));
        float y = Math.max(viewport.y + 4, hit.y - 18);
        context.batcher.textShadow(label, x, y, Colors.WHITE);
        context.batcher.unclip(context);
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
        Vector3f previous = project(matrix, viewport, points.get(0));
        for (int i = 1; i <= steps; i++)
        {
            Vector3f current = project(matrix, viewport, SplineCurve.evaluate(points, i / (float) steps, closed));
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
            Vector3f pa = project(matrix, viewport, SplineCurve.evaluate(points, a, closed));
            Vector3f pb = project(matrix, viewport, SplineCurve.evaluate(points, b, closed));
            if (pa == null || pb == null) return null;
            if (screenDistance(pa, x, y) < screenDistance(pb, x, y)) high = b; else low = a;
        }
        float t = (low + high) * 0.5F;
        Vector3f position = SplineCurve.evaluate(points, t, closed);
        Vector3f screen = project(matrix, viewport, position);
        if (screen == null || !viewport.isInside((int) screen.x, (int) screen.y)) return null;
        return new CurveHit(chain, Math.min(segments - 1, (int) (t * segments)),
            position, screen.x, screen.y, screen.z, screenDistance(screen, x, y));
    }

    private static float screenDistance(Vector3f point, int x, int y)
    {
        float dx = point.x - x, dy = point.y - y;
        return dx * dx + dy * dy;
    }

    private static Vector3f project(Matrix4f matrix, Area viewport, Vector3f point)
    {
        Vector4f clip = matrix.transform(new Vector4f(point, 1));
        if (!Float.isFinite(clip.w) || clip.w <= 0.00001F) return null;
        clip.div(clip.w);
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || clip.z < -1 || clip.z > 1) return null;
        return new Vector3f(viewport.x + (clip.x + 1) * viewport.w * 0.5F, viewport.y + (1 - clip.y) * viewport.h * 0.5F, clip.z);
    }

    private static void line(UIContext context, Vector3f from, Vector3f to, int color, float width)
    {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (!Float.isFinite(length) || length < 0.01F) return;
        var matrices = context.batcher.getContext().getMatrices();
        matrices.push();
        matrices.translate(from.x, from.y, 0);
        matrices.multiply(new Quaternionf().rotationZ((float) Math.atan2(dy, dx)));
        context.batcher.box(0, -width, length, width, color);
        matrices.pop();
    }
}
