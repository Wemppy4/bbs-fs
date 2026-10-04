package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * "Render last": a form with {@link Form#renderLast} set skips its turn in the draw order and
 * draws after every other form of the same pass. Forms draw immediately, in list order, with
 * depth writes on — so a semi-transparent pixel drawn early hides whatever draws later behind
 * it, and the cure used to be reordering the replay list by hand. This is that reorder as a
 * per-form switch, the Blockbuster "render last".
 *
 * <p>Unlike {@link FormTranslucentQueue} it never moves a form into another phase of the frame:
 * the postponed forms go through the ordinary {@link FormUtilsClient#render} at the end of the
 * very pass that skipped them — same programs, same frame state, only later than their
 * neighbours. That is what keeps it sound under shader packs, where the queue's end-of-frame
 * replay lands past a deferred pack's lighting composite. What it cannot do is order surfaces
 * within one object, or two "last" forms against each other — those keep the list order.</p>
 *
 * <p>A pass wraps its form drawing in {@link #open()} / {@link #close(boolean)}; close draws
 * the postponed forms in the order they were skipped. Scopes nest: opening inside an open
 * scope opens nothing, and the forms wait for the outer close. What is captured is the form's
 * frame — matrices, light, camera — not its geometry: the replay runs the renderer again, so
 * animation, IK and physics evaluate at replay time, still within the same frame. A postponed
 * form takes its body parts with it, they draw inside its renderer.</p>
 */
public class FormRenderLast
{
    private static List<Postponed> postponed = new ArrayList<>();
    private static boolean active;

    /** Repeated compositions order their parts without draining another scene's pending draws. */
    public static final class LocalScope implements AutoCloseable
    {
        private final List<Postponed> previous = postponed;
        private final boolean wasActive = active;

        public LocalScope()
        {
            postponed = new ArrayList<>();
            active = true;
        }

        @Override
        public void close()
        {
            boolean draws = !postponed.isEmpty();
            boolean depth = draws && org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            boolean blend = draws && org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            try { FormRenderLast.close(true); }
            finally
            {
                postponed = this.previous;
                active = this.wasActive;
                if (draws)
                {
                    if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                    if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                }
            }
        }
    }

    /**
     * Whether forms skip their turn right now: inside an open scope, and never in the Iris
     * shadow pass — the shadow map wants every form where it stands, order means nothing there.
     */
    public static boolean isActive()
    {
        return active && !mchorse.bbs_mod.graphics.OptiFineShaders.isShadowPass();
    }

    /**
     * Open a scope for a pass about to draw forms. Returns whether this call opened it — hand
     * that to {@link #close(boolean)}; a call inside an already open scope opens nothing.
     */
    public static boolean open()
    {
        if (active)
        {
            return false;
        }

        active = true;

        return true;
    }

    /** Close the scope this {@link #open()} call opened and draw the forms it postponed. */
    public static void close(boolean opened)
    {
        if (!opened)
        {
            return;
        }

        active = false;

        flush();
    }

    /**
     * Deactivate for a nested render that must draw where it stands — a framebuffer form's
     * parts go into its own buffer, postponing them would move them into the world. Returns the
     * previous state for {@link #restore(boolean)}.
     */
    public static boolean suspend()
    {
        boolean wasActive = active;

        active = false;

        return wasActive;
    }

    public static void restore(boolean wasActive)
    {
        active = wasActive;
    }

    /**
     * Skip the form's turn if it asks to render last: captures its frame for the replay and
     * returns true, and the caller draws nothing now. Picking draws right away — the stencil
     * needs every form in place and order is irrelevant to it.
     */
    public static boolean postpone(Form form, FormRenderingContext context)
    {
        if (form == null || !isActive() || context.isPicking() || !rendersLast(form))
        {
            return false;
        }

        postponed.add(new Postponed(form, context));

        return true;
    }

    private static boolean rendersLast(Form form)
    {
        if (form.renderLast.get()) return true;
        if (form instanceof mchorse.bbs_mod.forms.forms.SplineForm spline && spline.repeatEnabled.get())
        {
            // A repeated composition shares one evaluation. Defer it as a unit when any source
            // asks to draw last; its own local pass then orders those parts within the composition.
            return hasLastPart(form);
        }
        return false;
    }

    private static boolean hasLastPart(Form form)
    {
        for (var part : form.parts.getAllTyped())
        {
            Form child = part.getForm();
            if (child != null && (child.renderLast.get() || hasLastPart(child))) return true;
        }
        return false;
    }

    private static void flush()
    {
        if (postponed.isEmpty())
        {
            return;
        }

        /* Drained before drawing: the scope is closed, so the replay draws straight away and
         * nothing postpones again mid-flush — the list must simply be empty for the next pass. */
        List<Postponed> forms = new ArrayList<>(postponed);

        postponed.clear();

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();

        for (Postponed form : forms)
        {
            form.render();
        }

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    /** A form's frame at the moment it was skipped — enough to run its renderer later. */
    private static class Postponed
    {
        private final Form form;
        private final FormRenderType type;
        private final IEntity entity;
        private final Matrix4f position;
        private final Matrix3f normal;
        private final Matrix4f worldPosition;
        private final Matrix3f worldNormal;
        private final int light;
        private final int overlay;
        private final int color;
        private final float transition;
        private final boolean ui;
        private final boolean modelRenderer;
        private final long modelRendererTick;
        private final Camera camera = new Camera();

        private Postponed(Form form, FormRenderingContext context)
        {
            MatrixStack.Entry entry = context.stack.peek();
            MatrixStack.Entry world = context.world == null ? null : context.world.peek();

            this.form = form;
            this.type = context.type;
            this.entity = context.entity;
            this.position = new Matrix4f(entry.getPositionMatrix());
            this.normal = new Matrix3f(entry.getNormalMatrix());
            this.worldPosition = world == null ? null : new Matrix4f(world.getPositionMatrix());
            this.worldNormal = world == null ? null : new Matrix3f(world.getNormalMatrix());
            this.light = context.light;
            this.overlay = context.overlay;
            this.color = context.color;
            this.transition = context.transition;
            this.ui = context.ui;
            this.modelRenderer = context.modelRenderer;
            this.modelRendererTick = context.modelRendererTick;
            this.camera.copy(context.camera);
        }

        private void render()
        {
            MatrixStack stack = new MatrixStack();

            stack.peek().getPositionMatrix().set(this.position);
            stack.peek().getNormalMatrix().set(this.normal);

            FormRenderingContext context = new FormRenderingContext()
                .set(this.type, this.entity, stack, this.light, this.overlay, this.transition)
                .camera(this.camera)
                .color(this.color);
            context.ui = this.ui;
            context.modelRenderer = this.modelRenderer;
            context.modelRendererTick = this.modelRendererTick;

            /* set() rebuilt the world stack from the entity alone; the capture holds what the
             * pass really had there — a body part's slot on its parent, a film's anchor. */
            if (this.worldPosition != null)
            {
                context.world.peek().getPositionMatrix().set(this.worldPosition);
                context.world.peek().getNormalMatrix().set(this.worldNormal);
            }

            FormUtilsClient.render(this.form, context);
        }
    }
}
