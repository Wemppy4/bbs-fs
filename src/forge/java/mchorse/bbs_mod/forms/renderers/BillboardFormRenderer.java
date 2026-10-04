package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forge.studio.*;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;

/** Extensible billboard backend; VideoForm supplies its decoded texture through getTexture(). */
public class BillboardFormRenderer<T extends BillboardForm> extends NativeGeometryFormRenderer<T>
{
    public BillboardFormRenderer(T form){super(form);}
    protected Texture getTexture(){return BBSModClient.getTextures().getTexture(form.texture.get());}
    @Override public AABB getPreviewBounds(){Texture t=getTexture();return t==null?null:NativeBillboardGeometry.bounds(form,t.width,t.height);}
    @Override public boolean isPreviewCameraFacing(){return form.billboard.get();}
    @Override protected void render3D(FormRenderingContext context)
    {
        Texture texture=getTexture();if(texture==null)return;
        try(NativeFormDraw.State state=new NativeFormDraw.State();NativePickingShader.Scope pick=NativePickingShader.open(context.isPicking()?context.getPickingIndex():-1))
        {
            GlStateManager.pushMatrix();
            try
            {
                NativeTextureRenderer.loadMatrix(context.stack.peek().getPositionMatrix());
                if(form.billboard.get())NativeTextureRenderer.faceCamera();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,context.light&65535,context.light>>>16&65535);
                Color color=form.color.get().copy();color.mul(context.color);
                NativeTextureRenderer.render(NativeBillboardGeometry.create(form,texture.width,texture.height),texture,form,color,context.ui||form.shading.get(),form.linear.get(),form.mipmap.get(),context.ui);
            }
            finally{GlStateManager.popMatrix();}
        }
    }
}
