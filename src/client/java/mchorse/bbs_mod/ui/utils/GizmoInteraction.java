package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Pair;
import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * Shared rotate-mode gizmo interaction for any {@link GizmoViewport}:
 * gizmo-handle dragging, the screen-projected 3D rotate sphere (hover
 * highlight + trackball), and the deferred bone-vs-sphere pick (a press on
 * a bone inside the sphere disc starts a trackball if dragged, or selects
 * the bone if released without dragging).
 *
 * <p>One instance per viewport. The host forwards {@link #mouseClicked},
 * {@link #mouseReleased} and a per-frame {@link #update} (after the gizmo
 * has rendered), and calls {@link #stop} when the interaction ends.
 */
public class GizmoInteraction
{
    private static final int SPHERE_PICK_MIN_RADIUS_PX = 12;
    private static final int BONE_VS_SPHERE_DRAG_THRESHOLD_PX = 4;

    private final GizmoViewport viewport;
    private final Vector2f sphereScreenCenter = new Vector2f();

    private boolean sphereHovered;
    private boolean gizmoActive;
    private int pendingDownX;
    private int pendingDownY;
    private Form pendingPickForm;
    private String pendingPickBone;

    public GizmoInteraction(GizmoViewport viewport)
    {
        this.viewport = viewport;
    }

    public boolean isSphereHovered()
    {
        return this.sphereHovered;
    }

    public boolean mouseClicked(UIContext context)
    {
        if (context.mouseButton != 0)
        {
            return false;
        }

        StencilFormFramebuffer stencil = this.viewport.getGizmoStencil();

        if (stencil.hasPicked())
        {
            int index = stencil.getIndex();

            if (index >= Gizmo.STENCIL_X && index <= Gizmo.STENCIL_MAX)
            {
                return this.startGizmo(context, index);
            }

            if (this.sphereHovered && Gizmo.INSTANCE.isSphereInteractive())
            {
                Pair<Form, String> pair = stencil.getPicked();

                if (pair != null && pair.a != null)
                {
                    this.pendingDownX = context.mouseX;
                    this.pendingDownY = context.mouseY;
                    this.pendingPickForm = pair.a;
                    this.pendingPickBone = pair.b == null ? "" : pair.b;

                    return true;
                }
            }
        }
        else if (this.sphereHovered && Gizmo.INSTANCE.isSphereInteractive())
        {
            return this.startGizmo(context, Gizmo.STENCIL_TRACKBALL);
        }

        return false;
    }

    public boolean mouseReleased(UIContext context)
    {
        if (this.pendingPickForm != null && context.mouseButton == 0)
        {
            Form form = this.pendingPickForm;
            String bone = this.pendingPickBone;

            this.clearPending();
            this.viewport.pickGizmoForm(context, form, bone);

            return true;
        }

        return false;
    }

    /**
     * Per-frame upkeep: promote a deferred pick to a trackball once the
     * cursor drags past the threshold, then refresh the sphere hover. Call
     * after the gizmo has rendered this frame (so its render matrix and
     * radius are current).
     */
    public void update(UIContext context)
    {
        this.promotePendingPick(context);
        this.updateSphereHover(context);
    }

    public void stop()
    {
        if (this.gizmoActive)
        {
            Gizmo.INSTANCE.stop();
            this.gizmoActive = false;
        }

        this.clearPending();
    }

    private boolean startGizmo(UIContext context, int index)
    {
        if (this.viewport.startGizmo(context, index))
        {
            this.gizmoActive = true;

            return true;
        }

        return false;
    }

    private void promotePendingPick(UIContext context)
    {
        if (this.pendingPickForm == null)
        {
            return;
        }

        int dx = context.mouseX - this.pendingDownX;
        int dy = context.mouseY - this.pendingDownY;

        if (dx * dx + dy * dy > BONE_VS_SPHERE_DRAG_THRESHOLD_PX * BONE_VS_SPHERE_DRAG_THRESHOLD_PX)
        {
            this.clearPending();
            this.startGizmo(context, Gizmo.STENCIL_TRACKBALL);
        }
    }

    private void updateSphereHover(UIContext context)
    {
        boolean hover = false;

        if (Gizmo.INSTANCE.isSphereInteractive())
        {
            if (Gizmo.INSTANCE.isTrackballDragging())
            {
                hover = true;
            }
            else if (!this.stencilWouldWinSpherePick())
            {
                Matrix4f projection = this.viewport.getGizmoProjection();
                Area area = this.viewport.getGizmoArea();

                if (projection != null && area != null
                    && Gizmo.INSTANCE.computeScreenCenter(projection, area.x, area.y, area.w, area.h, this.sphereScreenCenter))
                {
                    float radius = Math.max(SPHERE_PICK_MIN_RADIUS_PX, Gizmo.INSTANCE.computeScreenRadius(projection, area.x, area.y, area.w, area.h));
                    float dx = context.mouseX - this.sphereScreenCenter.x;
                    float dy = context.mouseY - this.sphereScreenCenter.y;

                    hover = dx * dx + dy * dy <= radius * radius;
                }
            }
        }

        this.sphereHovered = hover;
        Gizmo.INSTANCE.setSphereHovered(hover);
    }

    private boolean stencilWouldWinSpherePick()
    {
        StencilFormFramebuffer stencil = this.viewport.getGizmoStencil();

        if (!stencil.hasPicked())
        {
            return false;
        }

        int index = stencil.getIndex();

        return index >= Gizmo.STENCIL_X && index <= Gizmo.STENCIL_MAX;
    }

    private void clearPending()
    {
        this.pendingPickForm = null;
        this.pendingPickBone = null;
    }
}
