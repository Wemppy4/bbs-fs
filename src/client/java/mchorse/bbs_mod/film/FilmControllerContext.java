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

    public String bone;
    public boolean local;

    /** The reference frame the bone gizmo is drawn in, and the film camera's
     *  world&rarr;camera rotation used to reorient it (null keeps LOCAL). */
    public TransformSpace space = TransformSpace.LOCAL;
    public Matrix4f gizmoView;

    public String bone2;
    public boolean local2;

    /** Draw the editing gizmo at the entity's resolved {@code form.anchor} matrix. */
    public boolean anchorGizmo;
    public boolean anchorLocal;

    public String nameTag = "";
    public boolean relative;

    private FilmControllerContext()
    {}

    private void reset()
    {
        this.map = null;
        this.shadowRadius = 0F;
        this.color = Colors.WHITE;
        this.bone = null;
        this.local = false;
        this.space = TransformSpace.LOCAL;
        this.gizmoView = null;
        this.bone2 = null;
        this.local2 = false;
        this.anchorGizmo = false;
        this.anchorLocal = false;
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
        this.transition = context.tickDelta();

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

    public FilmControllerContext bone(String bone, boolean local)
    {
        this.bone = bone;
        this.local = local;

        return this;
    }

    /** Set the space and camera view used to reorient the bone gizmo (Phase C). */
    public FilmControllerContext gizmoSpace(TransformSpace space, Matrix4f view)
    {
        this.space = space;
        this.gizmoView = view;

        return this;
    }

    public FilmControllerContext bone2(String bone, boolean local)
    {
        this.bone2 = bone;
        this.local2 = local;

        return this;
    }

    public FilmControllerContext anchorGizmo(boolean anchorGizmo, boolean anchorLocal)
    {
        this.anchorGizmo = anchorGizmo;
        this.anchorLocal = anchorLocal;

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