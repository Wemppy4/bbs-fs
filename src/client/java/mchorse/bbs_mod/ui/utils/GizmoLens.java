package mchorse.bbs_mod.ui.utils;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The lens the gizmo is drawn through: the scene camera swung to look straight
 * at the gizmo, framing it through a frustum of the gizmo's own fixed angle.
 *
 * <p>A gizmo is a screen tool &mdash; its rings want to read as circles and its
 * bars as equal sticks &mdash; but it is drawn as a scene object, so the camera's
 * perspective shears it the further it sits from the middle of the frame, and the
 * wider the camera's lens the worse it gets. Measured on the billboard ring, nine
 * tenths of the way out to the frame's edge: 43% oval at a 50&deg; FOV, 97% at
 * 70&deg;, 485% at 110&deg;. That is the whole "the gizmo warps when I widen the FOV".
 *
 * <p>Two swaps cancel exactly that, and nothing else:
 *
 * <ul>
 * <li>{@link #viewDelta} turns view space until the gizmo sits on the eye axis.
 * A tool in the middle of a frustum has no perspective left to shear it, wherever
 * the camera itself happens to point.</li>
 * <li>{@link #projection} re-frames it at a fixed narrow angle
 * ({@link #FOV}) through an off-centre aperture &mdash; the third
 * column carries the gizmo's own NDC &mdash; which lands it back on the exact pixel
 * the camera had it on, so the tool keeps sitting on the thing it edits.</li>
 * </ul>
 *
 * <p>A narrow frustum is also a zoom, so {@link #scale} shrinks the gizmo by the
 * ratio of the two half-angle tangents and its on-screen size comes out unchanged
 * (with "fixed axes size" off as well as on, since that ratio is applied to
 * whatever scale the setting produced).
 *
 * <p>Everything that projects the gizmo has to go through the same lens &mdash;
 * the visual, the pick stencil, the sphere highlight and the drag's mouse rays
 * &mdash; or they part ways at the edge of the frame. That is why this is a plain
 * value rebuilt from (camera projection, gizmo model-view) wherever it is needed
 * rather than a per-frame cache someone can read at the wrong moment.
 */
public class GizmoLens
{
    /** The lens's angle. Narrow enough that what perspective is left across the
     *  gizmo's own width is a sliver, wide enough that the rings still read as rings
     *  and not as a flat overlay. Deliberately a constant rather than a setting: it
     *  is a property of how the tool is drawn, and the whole point of the lens is
     *  that this number never changes, whatever the camera does. */
    public final static float FOV = (float) Math.toRadians(30);

    /** Below this a projected point is on or behind the camera plane and there is
     *  nothing to frame; the gizmo isn't drawn at all in that case. */
    private final static float CLIP_EPSILON = 1.0E-4F;

    /** The gizmo would have to sit straight above or below the camera for the
     *  up vector to go degenerate — off any frame narrower than 180&deg;, but the
     *  helpers are called for off-screen gizmos too, so it is checked. */
    private final static float UP_EPSILON = 1.0E-4F;

    /** Fixed-angle frustum with the gizmo dead centre, aperture shifted so it
     *  still projects onto its camera pixel. Identity-swap: the camera's own
     *  projection while the lens is inactive. */
    public final Matrix4f projection = new Matrix4f();

    /** Rotation prepended to the gizmo's model-view, putting it on the eye axis.
     *  Identity while the lens is inactive. */
    public final Matrix4f viewDelta = new Matrix4f();

    /** Factor the gizmo's distance scale is multiplied by to undo the narrow
     *  frustum's zoom. {@code 1} while the lens is inactive. */
    public float scale = 1F;

    /** Whether the swaps above are anything but identity. */
    public boolean active;

    /**
     * Whether a gizmo at {@code gizmoViewPosition} (its model-view's translation,
     * i.e. its place in view space) can be framed by a lens under
     * {@code cameraProjection}. The single predicate everything asks, so the drawn
     * frame, the {@link mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace#VIEW}
     * basis and the drag agree on whether the lens is on this frame.
     *
     * <p>An orthographic camera is deliberately excluded: it has no perspective to
     * shear the gizmo in the first place, and swinging the view under it would only
     * turn a tool that already reads square to the screen.
     */
    public static boolean canFrame(Matrix4f cameraProjection, Vector3f gizmoViewPosition)
    {
        if (cameraProjection == null || gizmoViewPosition == null)
        {
            return false;
        }

        /* m33 != 0 is an orthographic projection; m11 is 1 / tan(fov / 2). */
        if (cameraProjection.m33() != 0F || cameraProjection.m11() == 0F || cameraProjection.m00() == 0F)
        {
            return false;
        }

        float distance = gizmoViewPosition.length();

        if (distance < UP_EPSILON)
        {
            return false;
        }

        float sideways = (float) Math.sqrt(
            gizmoViewPosition.x * gizmoViewPosition.x + gizmoViewPosition.z * gizmoViewPosition.z);

        if (sideways / distance < UP_EPSILON)
        {
            return false;
        }

        Vector4f clip = cameraProjection.transform(new Vector4f(gizmoViewPosition, 1F));

        return clip.w > CLIP_EPSILON;
    }

    /**
     * The view rotation alone, for the callers that place the gizmo's frame before
     * anything is drawn ({@code Gizmo.reorientForSpace}). Guard it with
     * {@link #canFrame} first &mdash; this only fails on a degenerate direction.
     */
    public static boolean viewDelta(Vector3f gizmoViewPosition, Matrix4f out)
    {
        float distance = gizmoViewPosition.length();

        if (distance < UP_EPSILON)
        {
            return false;
        }

        out.setLookAlong(gizmoViewPosition.x, gizmoViewPosition.y, gizmoViewPosition.z, 0F, 1F, 0F);

        return true;
    }

    /**
     * Build the lens for a gizmo drawn with {@code gizmoModelView} under
     * {@code cameraProjection}. Returns whether it came out active; when it did not,
     * the fields hold the identity swap (the camera's own projection, no rotation,
     * no rescale) so callers can use them unconditionally.
     */
    public boolean set(Matrix4f cameraProjection, Matrix4f gizmoModelView)
    {
        this.projection.set(cameraProjection);
        this.viewDelta.identity();
        this.scale = 1F;
        this.active = false;

        Vector3f position = gizmoModelView.getTranslation(new Vector3f());

        if (!canFrame(cameraProjection, position) || !viewDelta(position, this.viewDelta))
        {
            this.viewDelta.identity();

            return false;
        }

        Vector4f clip = cameraProjection.transform(new Vector4f(position, 1F));
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float distance = position.length();
        float tanCamera = 1F / cameraProjection.m11();
        float tanLens = (float) Math.tan(FOV / 2F);

        /* The aperture shift is exact for a point on the eye axis at any depth, so
         * the gizmo's origin lands on its camera pixel to the float; the rest of it
         * is off by its own radius over the distance, which is the same sliver at
         * every FOV and screen position — that constancy is the point.
         *
         * The far plane has to clear the constraint guide's 10000-block bar, and the
         * near plane rides the distance so the depth range stays sane at any scale;
         * neither matters much, since the visual pass draws with the depth test off
         * and the stencil owns its buffer outright. */
        this.projection
            .setPerspective(FOV, cameraProjection.m11() / cameraProjection.m00(),
                Math.max(0.01F, distance * 0.01F), distance * 4F + 20000F)
            .m20(-ndcX)
            .m21(-ndcY);

        this.scale = tanLens / tanCamera;
        this.active = true;

        return true;
    }
}
