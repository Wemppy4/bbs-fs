package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.joml.Matrix4f;

/**
 * A viewport that hosts the rotate-mode gizmo. Each editor (film, form,
 * animation state) implements this so {@link GizmoInteraction} can drive
 * gizmo-handle dragging, the 3D rotate sphere and the deferred
 * bone-vs-sphere pick uniformly, without re-implementing that logic per
 * viewport.
 */
public interface GizmoViewport
{
    StencilFormFramebuffer getGizmoStencil();

    /** Projection paired with the matrix captured in {@link Gizmo#render}
     *  this frame, so origin/radius project into this viewport's pixels. */
    Matrix4f getGizmoProjection();

    Area getGizmoArea();

    /** Build the editable transform + drag and start {@link Gizmo} on the
     *  given stencil index (handle or {@link Gizmo#STENCIL_TRACKBALL} trackball).
     *  Returns whether a drag actually began. */
    boolean startGizmo(UIContext context, int stencilIndex);

    /** Resolve a deferred sphere-disc click as a plain bone selection. */
    void pickGizmoForm(UIContext context, Form form, String bone);
}
