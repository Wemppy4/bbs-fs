package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.util.math.MatrixStack;
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
    private static final List<Postponed> postponed = new ArrayList<>();
    private static boolean active;

    /**
     * Whether forms skip their turn right now: inside an open scope, and never in the Iris
     * shadow pass — the shadow map wants every form where it stands, order means nothing there.
     */
    public static boolean isActive()
    {
        return active && !BBSRendering.isIrisShadowPass();
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
        if (!form.renderLast.get() || !isActive() || context.isPicking())
        {
            return false;
        }

        postponed.add(new Postponed(form, context));

        return true;
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
