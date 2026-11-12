package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.OutlineMask;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A thin outline around the film's selected actor, the way 3D programs mark what is selected.
 * The actor alone is drawn again into a mask the size of the preview, the way the pick pass
 * draws it (its silhouette and its depth); a pass over the preview then rings what of the mask
 * is seen — the parts the world's own depth ({@link mchorse.bbs_mod.graphics.SceneDepth}) puts behind a block or another
 * actor get no outline. It is one selection: while a bone is edited, its part is ringed instead
 * of the whole actor, in the same colour — the mask holds each part in its pick id's colour, so
 * the bone is found in it without another draw. The mask is only redrawn when something it
 * depends on changed.
 */
public class SelectionOutline
{
    /** Redraw at least this often (in frames), for what the key below doesn't see (a pose edit from a field). */
    private static final int HEARTBEAT = 10;

    private final UIFilmController controller;
    private final OutlineMask mask = new OutlineMask();

    /* What the mask was drawn for */
    private final Matrix4f lastView = new Matrix4f();
    private final Matrix4f lastProjection = new Matrix4f();
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY;
    private int lastCursor;
    private int lastReplay;
    private FilmTarget lastTarget;
    private int lastWidth;
    private int lastHeight;
    private int framesSince;

    public SelectionOutline(UIFilmController controller)
    {
        this.controller = controller;
    }

    public void render(UIContext context)
    {
        UIFilmController controller = this.controller;
        WorldRenderContext world = controller.worldRenderContext();
        IEntity entity = controller.getCurrentEntity();
        Replay replay = controller.panel.replayEditor.getReplay();

        /* The axes switch (F8) hides it along with the gizmo and the markup */
        if (!BBSSettings.editorSelectionOutline.get() || !UIBaseMenu.renderAxes || !OutlineMask.isAvailable() || world == null
            || entity == null || replay == null || controller.panel.isFlying() || controller.getControlled() != null
            || controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON)
        {
            this.mask.invalidate();

            return;
        }

        Area viewport = controller.panel.preview.getViewport();
        float scale = BBSModClient.getGUIScale();
        int w = Math.max(1, Math.round(viewport.w * scale));
        int h = Math.max(1, Math.round(viewport.h * scale));

        BBSProfiler.begin(BBSProfiler.Timer.OUTLINE);

        if (this.needsRedraw(context, w, h) && this.mask.begin(w, h))
        {
            this.drawMask(world, entity, replay);
            this.mask.end();
        }

        /* One selection: the edited bone's part when it has one, else the whole actor */
        this.mask.draw(context, viewport, this.selectedIndex(), controller.panel.lastProjection);

        BBSProfiler.end(BBSProfiler.Timer.OUTLINE);
    }

    private boolean needsRedraw(UIContext context, int w, int h)
    {
        UIFilmController controller = this.controller;
        int cursor = controller.panel.getCursor();
        int replay = controller.getCurrentReplayIndex();
        FilmTarget target = controller.getEditTarget();

        this.framesSince += 1;

        boolean unchanged = this.mask.isDrawn()
            && !controller.isPlaying()
            && this.framesSince < HEARTBEAT
            && w == this.lastWidth
            && h == this.lastHeight
            && context.mouseX == this.lastMouseX
            && context.mouseY == this.lastMouseY
            && cursor == this.lastCursor
            && replay == this.lastReplay
            && target.equals(this.lastTarget)
            && controller.panel.lastView.equals(this.lastView)
            && controller.panel.lastProjection.equals(this.lastProjection);

        if (unchanged)
        {
            return false;
        }

        this.framesSince = 0;
        this.lastWidth = w;
        this.lastHeight = h;
        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;
        this.lastCursor = cursor;
        this.lastReplay = replay;
        this.lastTarget = target;
        this.lastView.set(controller.panel.lastView);
        this.lastProjection.set(controller.panel.lastProjection);

        return true;
    }

    /** The selected actor alone into the mask, through the pick pass's own path and matrices. */
    private void drawMask(WorldRenderContext world, IEntity entity, Replay replay)
    {
        UIFilmController controller = this.controller;

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(controller.panel.lastProjection, VertexSorter.BY_Z);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(controller.panel.lastView).invert());

        MatrixStack stack = world.matrixStack();

        stack.push();
        stack.loadIdentity();
        MatrixStackUtils.multiply(stack, controller.panel.lastView);

        FilmEntityRenderer.renderEntity(FilmControllerContext.instance
            .setup(controller.getEntities(), entity, replay, world)
            .transition(controller.isPlaying() ? world.tickDelta() : 0)
            .stencil(this.mask.stencilMap)
            .relative(replay.relative.get()));

        stack.pop();
        MatrixStackUtils.restoreMatrices();
    }

    /** The pick id the mask drew the edited bone with, or 0 when no bone is edited or it wasn't drawn (a controller without cubes). */
    private int selectedIndex()
    {
        Pair<String, TransformSpace> bone = this.controller.getBone();

        if (bone == null || bone.a == null || bone.a.isEmpty())
        {
            return 0;
        }

        return this.mask.indexOf((form, id) -> StringUtils.combinePaths(FormUtils.getPath(form), id).equals(bone.a));
    }
}
