package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forge.studio.NativeFormRenderer;
import mchorse.bbs_mod.forge.studio.NativePickingShader;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Typed API bridge to the single existing native geometry backend, not a second model engine. */
public final class NativeFormRendererAdapter<T extends Form> extends FormRenderer<T> implements ITickable
{
    private final NativeFormRenderer backend;
    private String hierarchyKey;
    private IBoneHierarchy hierarchy;
    public NativeFormRendererAdapter(T form) { super(form); this.backend = NativeFormRenderer.of(form); }
    public NativeFormRenderer getBackend() { return this.backend; }
    @Override public IBoneHierarchy getBoneHierarchy()
    {
        if (!(this.form instanceof mchorse.bbs_mod.forms.forms.MobForm)) return null;
        mchorse.bbs_mod.forms.forms.MobForm mob = (mchorse.bbs_mod.forms.forms.MobForm) this.form;
        String key = mob.mobID.get() + "|" + mob.slim.get();
        if (!key.equals(this.hierarchyKey))
        {
            this.hierarchy = mchorse.bbs_mod.cubic.jem.NativeMobHierarchy.create(mob);
            this.hierarchyKey = key;
        }
        return this.hierarchy;
    }
    @Override public List<String> getBones()
    {
        IBoneHierarchy rig = this.getBoneHierarchy();
        return rig == null ? Collections.emptyList() : rig.getGroupKeysInHierarchyOrder();
    }
    @Override public void tick(IEntity entity) { this.backend.tick(entity); }
    @Override public AABB getPreviewBounds() { return this.backend.getPreviewBounds(); }
    @Override public boolean isPreviewCameraFacing() { return this.backend.isPreviewCameraFacing(); }
    @Override protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.backend.renderPreview(context, x1, y1, x2, y2);
    }

    @Override protected void render3D(FormRenderingContext context)
    {
        if (this.isPreviewCameraFacing())
        {
            Matrix4f matrix = context.stack.peek().getPositionMatrix();
            Vector3f scale = matrix.getScale(new Vector3f());
            matrix.m00(1).m01(0).m02(0).m10(0).m11(1).m12(0).m20(0).m21(0).m22(1);
            matrix.scale(scale);
            context.stack.peek().getNormalMatrix().identity();
        }
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try
        {
            NativeTextureRenderer.loadMatrix(context.stack.peek().getPositionMatrix());
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, context.light & 0xffff, context.light >> 16 & 0xffff);
            try (NativePickingShader.Scope picking = NativePickingShader.open(context.isPicking() ? context.getPickingIndex() : -1))
            {
                this.backend.renderGeometry(context.entity, context.transition, context.ui, context.color);
            }
        }
        finally
        {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GlStateManager.popMatrix();
        }
    }

}
