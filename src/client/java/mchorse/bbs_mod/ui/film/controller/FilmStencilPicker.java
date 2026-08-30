package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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

    public FilmStencilPicker(UIFilmController controller)
    {
        this.controller = controller;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
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

        boolean altPressed = Window.isAltPressed();

        RenderSystem.depthFunc(GL11.GL_LESS);

        /* Cache the global stuff */
        MatrixStackUtils.cacheMatrices();

        RenderSystem.setProjectionMatrix(this.controller.panel.lastProjection, VertexSorter.BY_Z);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(this.controller.panel.lastView).invert());

        /* Render the stencil */
        MatrixStack worldStack = this.controller.worldRenderContext().matrixStack();

        worldStack.push();
        worldStack.loadIdentity();
        MatrixStackUtils.multiply(worldStack, this.controller.panel.lastView);
        this.renderStencil(this.controller.worldRenderContext(), this.controller.getContext(), altPressed);
        worldStack.pop();

        /* Return back to orthographic projection */
        MatrixStackUtils.restoreMatrices();

        RenderSystem.depthFunc(GL11.GL_ALWAYS);

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
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        ShaderProgram previewProgram = BBSShaders.getPickerPreviewProgram();
        Supplier<ShaderProgram> getPickerPreviewProgram = BBSShaders::getPickerPreviewProgram;
        GlUniform target = previewProgram.getUniform("Target");

        if (target != null)
        {
            target.set(index);
        }

        GlUniform highlight = previewProgram.getUniform("HighlightColor");

        if (highlight != null)
        {
            int color = BBSSettings.stencilHighlightColor.get();
            highlight.set(Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        RenderSystem.enableBlend();
        context.batcher.texturedBox(getPickerPreviewProgram, texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, h, w, 0, w, h);

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

        if ((entity == null || (this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && entity == this.controller.getCurrentEntity())) && !altPressed)
        {
            this.lastPickMouseX = Integer.MIN_VALUE;

            return;
        }

        if (!this.needsRepick(context, altPressed))
        {
            return;
        }

        this.ensureFramebuffer();

        /* Match the visual gizmo's on-screen size compensation (see
         * Gizmo#setViewportScale) so the pick handles line up with what is drawn. */
        Gizmo.INSTANCE.setViewportScale(context.menu.height / (float) viewport.h);

        boolean isPlaying = this.controller.isPlaying();
        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();

        this.stencilMap.setup();
        this.stencil.apply();

        if (altPressed)
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
                        .gizmoView(this.controller.getGizmoView());
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
                .gizmoView(this.controller.getGizmoView()));
        }

        int x = (int) ((context.mouseX - viewport.x) / (float) viewport.w * mainTexture.width);
        int y = (int) ((1F - (context.mouseY - viewport.y) / (float) viewport.h) * mainTexture.height);
        int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * mainTexture.width / (float) viewport.w);

        this.stencil.pick(x, y, radius, Gizmo.STENCIL_MAX);
        this.stencil.unbind(this.stencilMap);

        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
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
