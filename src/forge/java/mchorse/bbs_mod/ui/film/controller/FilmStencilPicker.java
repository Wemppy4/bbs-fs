package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.graphics.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;

import java.nio.IntBuffer;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Hit-testing the film preview: the scene is drawn again into an off-screen buffer where every
 * pickable thing writes its id instead of its colour, and the pixel under the cursor says what
 * is hovered — a bone, a gizmo handle, or (with Alt) another replay.
 *
 * <p>The id space is shared with the gizmo, which owns the low ids ({@link Gizmo#STENCIL_MAX});
 * replays begin right after, so a pixel is never ambiguous about which of the two it is.
 */
public class FilmStencilPicker
{
    /** Where replay ids begin, past the ids the gizmo's own handles claim. */
    private static final int REPLAY_STENCIL_OFFSET = Gizmo.STENCIL_MAX + 1;

    private final UIFilmController controller;

    private final StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private final StencilMap stencilMap = new StencilMap();
    private final IntBuffer savedViewport = BufferUtils.createIntBuffer(16);
    private final IntBuffer savedScissor = BufferUtils.createIntBuffer(16);

    /** The replay under the cursor while Alt is held, or -1. */
    private int hoveredReplayIndex = -1;

    /* The pick is a pure function of these inputs; while none of them change, the previous
     * pass's buffer and pick result stand, and the whole scene re-render is skipped. The
     * heartbeat below bounds staleness from anything this key does not see (an undo from the
     * keyboard, physics settling) to a fraction of a second. */
    private final Matrix4f lastPickView = new Matrix4f();
    private final Matrix4f lastPickProjection = new Matrix4f();
    private int lastPickMouseX = Integer.MIN_VALUE;
    private int lastPickMouseY;
    private boolean lastPickAlt;
    private int lastPickCursor;
    private int lastPickReplayIndex;
    private FilmTarget lastPickTarget;
    private int lastPickReplayCount;
    private int framesSincePick;

    /** Repick at least this often (in frames) even when no tracked input changed. */
    private static final int PICK_HEARTBEAT = 15;

    /** One-shot target selection, scoped to the visible control that armed it. */
    private UIElement pickOwner;
    private String excludedReplay;
    private BiConsumer<String, Pair<Form, String>> pickCallback;

    public FilmStencilPicker(UIFilmController controller)
    {
        this.controller = controller;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public void toggleTargetPick(UIElement owner, String excludedReplay, BiConsumer<String, Pair<Form, String>> callback)
    {
        boolean cancel = this.pickOwner == owner;

        this.cancelTargetPick();

        if (!cancel)
        {
            this.pickOwner = owner;
            this.excludedReplay = excludedReplay;
            this.pickCallback = callback;
        }
    }

    public void cancelTargetPick()
    {
        this.pickOwner = null;
        this.pickCallback = null;
        this.excludedReplay = null;
        this.hoveredReplayIndex = -1;
        this.stencil.clearPicking();
        this.lastPickMouseX = Integer.MIN_VALUE;
    }

    public boolean isPickingTarget()
    {
        if (this.pickOwner != null && !this.pickOwner.canBeSeen())
        {
            this.cancelTargetPick();
        }

        return this.pickCallback != null;
    }

    public boolean isPickingTarget(UIElement owner)
    {
        return this.isPickingTarget() && this.pickOwner == owner;
    }

    public boolean pickTarget(UIContext context)
    {
        if (!this.isPickingTarget() || !this.controller.panel.preview.getViewport().isInside(context))
        {
            return false;
        }

        if (context.mouseButton == 1)
        {
            this.cancelTargetPick();
        }
        else if (context.mouseButton == 0)
        {
            Form spline = this.controller.pickSplineForm(context);
            Pair<Form, String> pair = spline == null ? this.stencil.getPicked() : new Pair<>(spline, "");

            if (pair != null && pair.a != null)
            {
                Form root = FormUtils.getRoot(pair.a);

                for (var entry : this.controller.getEntities().entrySet())
                {
                    if (entry.getValue().getForm() == root && !entry.getKey().equals(this.excludedReplay))
                    {
                        BiConsumer<String, Pair<Form, String>> callback = this.pickCallback;

                        this.cancelTargetPick();
                        callback.accept(entry.getKey(), pair);
                        break;
                    }
                }
            }
        }

        return true;
    }

    public int getHoveredReplayIndex()
    {
        return this.hoveredReplayIndex;
    }

    /**
     * The picking pass of a rendered frame: draw the scene into the pick buffer, read what is
     * under the cursor, then paint the hover highlight and its label back over the viewport.
     */
    public void renderPreview(UIContext context, Area area)
    {
        if (this.controller.panel.isFlying() || this.controller.worldRenderContext() == null)
        {
            return;
        }

        boolean altPressed = Window.isAltPressed() && !this.isPickingTarget();

        /* Finish GUI batches before changing their projection or render target. Picking only
         * runs on changed inputs/its heartbeat, so leaking FBO 0 here makes the GUI flicker. */
        context.batcher.flush();
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        this.savedViewport.clear();
        this.savedScissor.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, this.savedViewport);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, this.savedScissor);
        MatrixStackUtils.cacheMatrices();
        MatrixStack worldStack = this.controller.worldRenderContext().matrixStack();
        worldStack.push();
        try
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LESS);
            MatrixStackUtils.loadProjection(this.controller.panel.lastProjection);
            worldStack.loadIdentity();
            MatrixStackUtils.multiply(worldStack, this.controller.panel.lastView);
            this.renderStencil(this.controller.worldRenderContext(), this.controller.getContext(), altPressed);
        }
        finally
        {
            worldStack.pop();
            MatrixStackUtils.restoreMatrices();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager.viewport(this.savedViewport.get(0), this.savedViewport.get(1), this.savedViewport.get(2), this.savedViewport.get(3));
            GL11.glScissor(this.savedScissor.get(0), this.savedScissor.get(1), this.savedScissor.get(2), this.savedScissor.get(3));
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.depthFunc(depthFunction);
            GlStateManager.depthMask(depthMask);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }

        this.hoveredReplayIndex = -1;

        if (this.controller.canShowGizmo())
        {
            this.controller.gizmo().update(context);
            this.controller.gizmo().renderSphereHighlight(context);
            this.controller.gizmo().renderReadout(context);
        }

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Pair<Form, String> pair = this.stencil.getPicked();

        this.stencil.renderPreview(context, area);

        if (altPressed)
        {
            int selectedReplayIndex = this.controller.getCurrentReplayIndex();
            int stencilIndex = index - REPLAY_STENCIL_OFFSET;

            if (stencilIndex >= 0 && stencilIndex < this.controller.panel.getData().replays.getList().size() && stencilIndex != selectedReplayIndex)
            {
                this.hoveredReplayIndex = stencilIndex;

                String label = this.controller.panel.getData().replays.getList().get(stencilIndex).getName();

                context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
            }
            else if (pair != null && pair.a != null)
            {
                String label = pair.a.getFormIdOrName();

                if (!pair.b.isEmpty())
                {
                    label += " - " + pair.b;
                }

                context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
            }
        }
        else if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    private void renderStencil(WorldRenderContext renderContext, UIContext context, boolean altPressed)
    {
        Area viewport = this.controller.panel.preview.getViewport();

        if (!viewport.isInside(context) || this.controller.getControlled() != null)
        {
            this.stencil.clearPicking();
            this.lastPickMouseX = Integer.MIN_VALUE;

            return;
        }

        IEntity entity = this.controller.getCurrentEntity();

        if ((entity == null || (this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && entity == this.controller.getCurrentEntity())) && !altPressed && !this.isPickingTarget())
        {
            this.lastPickMouseX = Integer.MIN_VALUE;

            return;
        }

        if (!this.needsRepick(context, altPressed))
        {
            return;
        }

        BBSProfiler.count(BBSProfiler.Section.STENCIL_PASS);
        BBSProfiler.begin(BBSProfiler.Timer.STENCIL_PASS);

        this.ensureFramebuffer();

        /* Match the visual gizmo's on-screen size compensation (see
         * Gizmo#setViewportHeight) so the pick handles line up with what is drawn. */
        Gizmo.INSTANCE.setViewportHeight(viewport.h);

        boolean isPlaying = this.controller.isPlaying();
        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();

        this.stencilMap.setup();
        this.stencil.apply();

        if (this.isPickingTarget())
        {
            this.stencilMap.setIncrement(true);

            for (Replay replay : this.controller.panel.getData().replays.getList())
            {
                IEntity replayEntity = this.controller.getEntities().get(replay.getId());

                if (replayEntity == null || replay.getId().equals(this.excludedReplay))
                {
                    continue;
                }

                FilmEntityRenderer.renderEntity(FilmControllerContext.instance
                    .setup(this.controller.getEntities(), replayEntity, replay, renderContext)
                    .transition(isPlaying ? renderContext.tickDelta() : 0)
                    .stencil(this.stencilMap)
                    .relative(replay.relative.get()));
            }
        }
        else if (altPressed)
        {
            List<Replay> replays = this.controller.panel.getData().replays.getList();
            int selectedReplayIndex = this.controller.getCurrentReplayIndex();
            FilmTarget target = this.controller.getEditTarget();

            /* Walked by list position, not by the entity map: the stencil object index IS the
             * replay's position in the film, which is what the pick reads back. */
            for (int i = 0; i < replays.size(); i++)
            {
                Replay replay = replays.get(i);
                IEntity replayEntity = this.controller.getEntities().get(replay.getId());

                if (replayEntity == null)
                {
                    continue;
                }

                FilmControllerContext filmContext = FilmControllerContext.instance
                    .setup(this.controller.getEntities(), replayEntity, replay, renderContext)
                    .transition(isPlaying ? renderContext.tickDelta() : 0)
                    .stencil(this.stencilMap)
                    .relative(replay.relative.get());

                if (i == selectedReplayIndex)
                {
                    this.stencilMap.objectIndex = replays.size() + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(true);

                    filmContext
                        .gizmoTarget(target)
                        .gizmoView(renderContext.matrixStack().peek().getPositionMatrix());
                }
                else
                {
                    this.stencilMap.objectIndex = i + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(false);
                }

                FilmEntityRenderer.renderEntity(filmContext);
            }
        }
        else
        {
            Replay replay = this.controller.panel.replayEditor.getReplay();

            this.stencilMap.setIncrement(true);

            FilmEntityRenderer.renderEntity(FilmControllerContext.instance
                .setup(this.controller.getEntities(), entity, replay, renderContext)
                .transition(isPlaying ? renderContext.tickDelta() : 0)
                .stencil(this.stencilMap)
                .relative(replay.relative.get())
                .gizmoTarget(this.controller.getEditTarget())
                .gizmoView(renderContext.matrixStack().peek().getPositionMatrix()));
        }

        int x = (int) ((context.mouseX - viewport.x) / (float) viewport.w * mainTexture.width);
        int y = (int) ((1F - (context.mouseY - viewport.y) / (float) viewport.h) * mainTexture.height);
        int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * mainTexture.width / (float) viewport.w);

        this.stencil.pick(x, y, radius, Gizmo.STENCIL_MAX);
        this.stencil.unbind(this.stencilMap);


        BBSProfiler.end(BBSProfiler.Timer.STENCIL_PASS);
    }

    /**
     * True when any input the pick depends on changed since the buffer was last drawn — or when
     * the heartbeat expires. Doubt resolves toward repicking: a spare pass costs what every
     * frame used to cost, a missed one costs a stale highlight.
     */
    private boolean needsRepick(UIContext context, boolean altPressed)
    {
        this.framesSincePick += 1;

        int cursor = this.controller.panel.getCursor();
        int replayIndex = this.controller.getCurrentReplayIndex();
        FilmTarget target = this.controller.getEditTarget();
        int replayCount = this.controller.panel.getData().replays.getList().size();

        boolean unchanged = !this.controller.isPlaying()
            && this.framesSincePick < PICK_HEARTBEAT
            && context.mouseX == this.lastPickMouseX
            && context.mouseY == this.lastPickMouseY
            && altPressed == this.lastPickAlt
            && cursor == this.lastPickCursor
            && replayIndex == this.lastPickReplayIndex
            && target.equals(this.lastPickTarget)
            && replayCount == this.lastPickReplayCount
            && this.controller.panel.lastView.equals(this.lastPickView)
            && this.controller.panel.lastProjection.equals(this.lastPickProjection);

        if (unchanged)
        {
            return false;
        }

        this.framesSincePick = 0;
        this.lastPickMouseX = context.mouseX;
        this.lastPickMouseY = context.mouseY;
        this.lastPickAlt = altPressed;
        this.lastPickCursor = cursor;
        this.lastPickReplayIndex = replayIndex;
        this.lastPickTarget = target;
        this.lastPickReplayCount = replayCount;
        this.lastPickView.set(this.controller.panel.lastView);
        this.lastPickProjection.set(this.controller.panel.lastProjection);

        return true;
    }

    private void ensureFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_film"));

        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
        int w = BBSRendering.getVideoWidth();
        int h = BBSRendering.getVideoHeight();

        if (mainTexture.width != w || mainTexture.height != h)
        {
            this.stencil.resizeGUI(w, h);
        }
    }
}
