package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Quaternionf;

public class FormRenderingContext
{
    public FormRenderType type;
    public IEntity entity;
    public MatrixStack stack;
    public MatrixStack world;
    public int light;
    public int overlay;
    public float transition;
    public final Camera camera = new Camera();
    public StencilMap stencilMap;
    public boolean ui;
    public int color;
    public boolean modelRenderer;
    public long modelRendererTick;

    public FormRenderingContext()
    {}

    public FormRenderingContext set(FormRenderType type, IEntity entity, MatrixStack stack, int light, int overlay, float transition)
    {
        this.type = type == null ? FormRenderType.ENTITY : type;
        this.entity = entity;
        this.stack = stack;

        /* Reused, not reallocated: set() runs per replay per frame, and nothing retains the
         * stack itself (readers snapshot matrices). The drain guards against a render that
         * left it unbalanced. */
        if (this.world == null)
        {
            this.world = new MatrixStack();
        }
        else
        {
            while (!this.world.isEmpty())
            {
                this.world.pop();
            }

            this.world.loadIdentity();
        }
        this.light = light;
        this.overlay = overlay;
        this.transition = transition;
        this.stencilMap = null;
        this.ui = false;
        this.color = 0xffffffff;
        this.modelRenderer = false;
        this.modelRendererTick = 0L;

        if (entity != null && (this.type == FormRenderType.ENTITY || this.type == FormRenderType.MODEL_BLOCK))
        {
            double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
            double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition);
            double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

            float bodyYaw = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition);

            this.world.translate(x, y, z);
            this.world.multiply(new Quaternionf().rotationY(MathUtils.toRad(-bodyYaw)));
        }

        return this;
    }

    public FormRenderingContext camera(Camera camera)
    {
        this.camera.copy(camera);

        return this;
    }

    /** Camera pose supplied by the real Forge view entity. */
    public FormRenderingContext camera(net.minecraft.entity.Entity view)
    {
        this.camera.position.set(view.posX, view.posY + view.getEyeHeight(), view.posZ);
        this.camera.rotation.set(MathUtils.toRad(-view.rotationPitch), MathUtils.toRad(view.rotationYaw), 0F);
        this.camera.fov = MathUtils.toRad(Minecraft.getMinecraft().gameSettings.fovSetting);
        this.camera.updateView();
        return this;
    }

    public FormRenderingContext stencilMap(StencilMap stencilMap)
    {
        this.stencilMap = stencilMap;

        return this;
    }

    public FormRenderingContext inUI()
    {
        this.ui = true;

        return this;
    }

    public FormRenderingContext color(int color)
    {
        this.color = color;

        return this;
    }

    /**
     * Mark this as a {@link mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer}
     * viewport and hand it that viewport's clock.
     *
     * <p>The tick is here because form ticking in an editor is opt-in
     * ({@link mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer#updatable()})
     * and off in the plain form editor. Anything that has to keep moving in a
     * preview regardless &mdash; particles, say &mdash; cannot wait to be
     * ticked and has to run off this instead.
     */
    public FormRenderingContext modelRenderer(long tick)
    {
        this.modelRenderer = true;
        this.modelRendererTick = tick;

        return this;
    }

    public float getTransition()
    {
        return this.transition;
    }

    public boolean isPicking()
    {
        return this.stencilMap != null;
    }

    public int getPickingIndex()
    {
        return this.stencilMap == null ? -1 : this.stencilMap.objectIndex;
    }
}
