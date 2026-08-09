package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.BBSShaders.ModelVariant;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Deferred translucency for forms.
 *
 * <p>Form renderers draw immediately, in entity iteration order, with depth writes on — so a
 * semi-transparent pixel drawn early would occlude anything drawn later behind it. Instead, draws
 * whose texture (or colour) has semi-transparency run twice: an immediate opaque pass (the shader
 * keeps only opaque texels, which write depth), and a command enqueued here. At the end of the
 * frame — after every form has drawn — {@link #flush()} replays the commands sorted far-to-near, so
 * translucent pixels blend over everything without ever hiding it. It is the sort, not the depth
 * mask, that keeps forms behind them visible: solid geometry keeps writing depth in the replay, so a
 * model's own semi-transparent texels still occlude the ones behind them. Only flat forms
 * (billboards, framebuffer screens, label parts) drop the depth write — they have no self-occlusion
 * to preserve.
 *
 * <p>Commands replay finished draw calls with the geometry captured at enqueue time — they never
 * re-run animation, IK or physics.
 *
 * <h2>What the 1.21.11 port changed</h2>
 * The mechanism is the 1.21.1 one; the three things it used to lean on are gone:
 * <ul>
 *   <li>{@code PassMode} was a mutable {@code GlUniform}. It is a compile-time shader define now, so
 *       each pass is a separate {@link ModelVariant} of the model pipeline.</li>
 *   <li>Depth-write and cull were global GL toggles flipped per command inside the flush loop. They
 *       are immutable pipeline state now, so they moved into the variant too and a command carries
 *       nothing but its layer — the flush loop touches no GL state at all.</li>
 *   <li>Retention was a {@code VertexBuffer} uploaded per command. The geometry is kept as a CPU
 *       copy ({@link FormRenderCapture.Captured}, the same one the item path already uses) and
 *       re-emitted into the layer's buffer at flush.</li>
 * </ul>
 */
public class FormTranslucentQueue
{
    public static final int PASS_SINGLE = 0;
    public static final int PASS_OPAQUE = 1;
    public static final int PASS_TRANSLUCENT = 2;

    private static final List<DrawCommand> commands = new ArrayList<>();
    private static boolean active;

    /**
     * Camera-space origin of the form currently being drawn through the buffered vertex consumer path
     * (blocks, items) — its translucent layers can't know their position from the camera-space
     * vertices alone. Non-null also acts as the opt-in for deferring those layers; picking and UI
     * paths never set it.
     */
    private static Vector3f sortOrigin;

    /** Non-null while a group is being recorded: added commands collect here instead of the queue. */
    private static GroupCommand group;

    public static void setSortOrigin(Vector3f origin)
    {
        sortOrigin = origin;
    }

    public static Vector3f getSortOrigin()
    {
        return sortOrigin;
    }

    public static boolean isGroupOpen()
    {
        return group != null;
    }

    /**
     * Start recording a group: until {@link #endGroup()}, added commands collect into one composite
     * command that replays them in insertion order at flush. For forms whose parts depend on each
     * other's depth (a label's text against its background) — the group sorts against other forms as
     * a whole, while its internals keep their original draw order.
     */
    public static void beginGroup(Vector3f cameraSpaceOrigin, boolean cull)
    {
        group = new GroupCommand(cameraSpaceOrigin);
        sortOrigin = new Vector3f(cameraSpaceOrigin);
    }

    public static void endGroup()
    {
        GroupCommand finished = group;

        group = null;
        sortOrigin = null;

        if (finished != null && !finished.children.isEmpty())
        {
            add(finished);
        }
    }

    public static boolean isActive()
    {
        /* The Iris shadow pass re-renders the scene into the shadow map mid-frame: forms there must
         * draw immediately (the shadow map needs their full geometry), and nothing may enqueue — the
         * queue belongs to the main pass. */
        return active && !BBSRendering.isIrisShadowPass();
    }

    /**
     * Whether a draw splits into an immediate opaque pass (writes depth) + a deferred translucent
     * pass. Only when translucency is <em>intrinsic to the texture</em> and the colour is not faded:
     * the solid texels draw now and write depth, the see-through ones defer. A uniform colour fade
     * (alpha &lt; 1) instead takes {@link #needsWholeDefer}, because it drops every texel below the
     * opaque threshold — the split's immediate pass would draw nothing at all, leaving a pointless
     * empty draw call. False outside an active queue scope (UI previews, first-person arm) and during
     * picking (the stencil needs every pixel).
     */
    public static boolean needsSplit(StencilMap stencilMap, Texture texture, float alpha)
    {
        return alpha >= 1F && texture != null && texture.hasTranslucency()
            && isActive() && stencilMap == null;
    }

    /**
     * Whether the whole draw defers as one unit with depth writes kept on, sorted between models: a
     * uniform colour fade. The faded model keeps writing depth so it still self-occludes instead of
     * collapsing into an unordered translucent blob, and the transition out of alpha == 1 stays
     * continuous (no pop).
     */
    public static boolean needsWholeDefer(StencilMap stencilMap, float alpha)
    {
        return alpha < 1F && isActive() && stencilMap == null;
    }

    /**
     * The entry point for every BBS-drawn mesh that goes through the model pipeline: decide between a
     * plain immediate draw, the opaque/translucent split, and a whole deferred draw, then issue it.
     *
     * <p>{@code variant} is the caller's own choice of depth-write and cull (see {@link ModelVariant});
     * this method only ever overrides the pass. The layer is resolved from the currently bound BBS
     * texture, exactly as the immediate path does.
     *
     * @param origin camera-space origin used as the far-to-near sort key
     */
    public static void submit(BuiltBuffer built, ModelVariant variant, Texture texture, float alpha, StencilMap stencilMap, Vector3f origin)
    {
        if (built == null)
        {
            return;
        }

        if (needsSplit(stencilMap, texture, alpha))
        {
            /* Opaque texels now (they write depth and occlude properly), see-through ones at flush.
             * The layer must be resolved HERE, while this form's texture is still the bound one — at
             * flush time the binding belongs to whoever drew last. */
            FormRenderCapture.Captured captured = FormRenderCapture.copy(built);
            RenderLayer deferred = BBSShaders.getBoundModelLayer(variant.withPass(PASS_TRANSLUCENT));

            /* The opaque half always writes depth, even for a flat form whose deferred half does not:
             * writing depth is the entire point of drawing it now — it is what lets the solid part of
             * the texture occlude properly instead of waiting for the sort. On 1.21.1 this fell out of
             * the global depth mask being on during the immediate draw; the flag only ever applied to
             * the replay. */
            BBSShaders.getBoundModelLayer(variant.withPass(PASS_OPAQUE).withDepthWrite(true)).draw(built);
            add(new BufferCommand(deferred, captured, origin));
        }
        else if (needsWholeDefer(stencilMap, alpha))
        {
            add(new BufferCommand(BBSShaders.getBoundModelLayer(variant), FormRenderCapture.copy(built), origin));
            built.close();
        }
        else
        {
            BBSShaders.getBoundModelLayer(variant).draw(built);
        }
    }

    public static void add(DrawCommand command)
    {
        if (group != null && command != group)
        {
            group.children.add(command);
        }
        else if (active)
        {
            commands.add(command);
        }
        else
        {
            /* No scope to defer into — draw right away so no pixels are lost. */
            command.draw();
        }
    }

    public static void begin()
    {
        release();

        active = true;
    }

    /**
     * Temporarily deactivate the queue: nested offscreen renders (framebuffer forms) run under their
     * own projection mid-frame and must not enqueue into the world's flush. Returns the previous
     * state for {@link #restore(boolean)}.
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
     * Draw all deferred commands, far to near. Deactivates the queue — later draws (the first-person
     * hand) fall back to single-pass rendering.
     *
     * <p>Unlike 1.21.1 this loop sets no GL state: depth-write and cull ride each command's layer, and
     * the lightmap/overlay samplers are bound by the layer too.
     */
    public static void flush()
    {
        active = false;

        if (commands.isEmpty())
        {
            return;
        }

        commands.sort((a, b) -> Float.compare(b.distanceSq, a.distanceSq));

        for (DrawCommand command : commands)
        {
            try
            {
                command.draw();
            }
            catch (Exception e)
            {
                /* One malformed command must not take the rest of the frame's translucency with it. */
            }
        }

        commands.clear();
    }

    /** Drop any leftover commands (e.g. a frame whose flush point never ran). */
    private static void release()
    {
        commands.clear();

        /* A group left open by an aborted render must not leak into the next frame. */
        group = null;
        sortOrigin = null;
    }

    public static abstract class DrawCommand
    {
        public final float distanceSq;

        /** The origin must be camera-space (a captured model-view translation) — it's the sort key. */
        protected DrawCommand(Vector3f cameraSpaceOrigin)
        {
            this.distanceSq = cameraSpaceOrigin.lengthSquared();
        }

        public abstract void draw();
    }

    /**
     * Replays captured geometry through a render layer. The layer carries the pass, the depth-write,
     * the cull and the texture, so the replay is a plain re-emit — the vertices were already fully
     * transformed when they were captured.
     */
    public static class BufferCommand extends DrawCommand
    {
        private final RenderLayer layer;
        private final FormRenderCapture.Captured captured;

        public BufferCommand(RenderLayer layer, FormRenderCapture.Captured captured, Vector3f cameraSpaceOrigin)
        {
            super(cameraSpaceOrigin);

            this.layer = layer;
            this.captured = captured;
        }

        @Override
        public void draw()
        {
            BufferBuilder builder = Tessellator.getInstance().begin(this.captured.params().mode(), this.captured.params().format());

            /* Same mode in and out, so emit() copies the vertices straight through; it is shared with
             * the item path, which does need the rewrite. */
            FormRenderCapture.emit(this.captured, this.captured.params().mode(), builder);

            BuiltBuffer built = builder.endNullable();

            if (built != null)
            {
                this.layer.draw(built);
            }
        }
    }

    /** Replays its children in insertion order; sorts against other commands as one unit. */
    public static class GroupCommand extends DrawCommand
    {
        private final List<DrawCommand> children = new ArrayList<>();

        public GroupCommand(Vector3f cameraSpaceOrigin)
        {
            super(cameraSpaceOrigin);
        }

        @Override
        public void draw()
        {
            for (DrawCommand child : this.children)
            {
                child.draw();
            }
        }
    }
}
