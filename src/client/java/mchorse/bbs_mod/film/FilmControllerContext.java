package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Matrix4f;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Map;

public class FilmControllerContext
{
    public final static FilmControllerContext instance = new FilmControllerContext();

    /** The film's entities keyed by their replay's stable id, in replay-list order. */
    public Map<String, IEntity> entities;
    public IEntity entity;
    public Replay replay;
    public Camera camera;
    public MatrixStack stack;
    public VertexConsumerProvider consumers;
    public StencilMap map;

    public float transition;
    public int color;
    public float shadowRadius;

    /** What the gizmo is on this frame — bone, anchor or the replay's own placement — and
     *  the frame it is placed and drawn in. ONE field for the whole cascade: the passes that
     *  draw the gizmo and the pass that picks it are built from the same answer, so a target
     *  cannot reach one of them and not the other. {@link FilmTarget#NONE} draws nothing. */
    public FilmTarget gizmoTarget = FilmTarget.NONE;

    /** The film camera's world&rarr;camera rotation, used to reorient the gizmo. */
    public Matrix4f gizmoView;

    public String bone2;

    /** The preview axes' frame; always LOCAL today — the preview shows the bone's own axes. */
    public TransformSpace space2 = TransformSpace.LOCAL;

    public String nameTag = "";
    public boolean relative;

    private FilmControllerContext()
    {}

    private void reset()
    {
        this.map = null;
        this.shadowRadius = 0F;
        this.color = Colors.WHITE;
        this.gizmoTarget = FilmTarget.NONE;
        this.gizmoView = null;
        this.bone2 = null;
        this.space2 = TransformSpace.LOCAL;
        this.nameTag = "";
        this.relative = false;
    }

    public FilmControllerContext setup(Map<String, IEntity> entities, IEntity entity, Replay replay, WorldRenderContext context)
    {
        this.reset();

        this.entities = entities;
        this.entity = entity;
        this.replay = replay;
        this.camera = context.camera();
        this.stack = context.matrixStack();
        this.consumers = context.consumers();
        this.transition = context.tickCounter().getTickDelta(false);

        return this;
    }

    public FilmControllerContext setup(Map<String, IEntity> entities, IEntity entity, Replay replay, Camera camera, MatrixStack stack, VertexConsumerProvider consumers, float transition)
    {
        this.reset();

        this.entities = entities;
        this.entity = entity;
        this.replay = replay;
        this.camera = camera;
        this.stack = stack;
        this.consumers = consumers;
        this.transition = transition;

        return this;
    }

    public FilmControllerContext transition(float transition)
    {
        this.transition = transition;

        return this;
    }

    public FilmControllerContext stencil(StencilMap map)
    {
        this.map = map;

        return this;
    }

    public FilmControllerContext shadow(boolean shadow, float shadowRadius)
    {
        this.shadowRadius = shadow ? shadowRadius : 0F;

        return this;
    }

    public FilmControllerContext shadow(float shadowRadius)
    {
        this.shadowRadius = shadowRadius;

        return this;
    }

    public FilmControllerContext color(int overlayColor)
    {
        this.color = overlayColor;

        return this;
    }

    /** What the gizmo edits and the frame it is edited in, as one answer. */
    public FilmControllerContext gizmoTarget(FilmTarget target)
    {
        this.gizmoTarget = target == null ? FilmTarget.NONE : target;

        return this;
    }

    /** The film camera's view rotation, needed to reorient the gizmo. */
    public FilmControllerContext gizmoView(Matrix4f view)
    {
        this.gizmoView = view;

        return this;
    }

    public FilmControllerContext bone2(String bone, TransformSpace space)
    {
        this.bone2 = bone;
        this.space2 = space == null ? TransformSpace.LOCAL : space;

        return this;
    }

    public FilmControllerContext nameTag(String nameTag)
    {
        this.nameTag = nameTag;

        return this;
    }

    public FilmControllerContext relative(boolean relative)
    {
        this.relative = relative;

        return this;
    }
}