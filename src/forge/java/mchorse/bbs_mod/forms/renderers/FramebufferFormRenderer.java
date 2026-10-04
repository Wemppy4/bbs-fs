package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.util.Map;

/** Flatten nested forms into the original -1..1 orthographic box, retaining nested pick IDs. */
public class FramebufferFormRenderer extends NativeGeometryFormRenderer<FramebufferForm>
{
    public FramebufferFormRenderer(FramebufferForm form){super(form);}
    private int width(){return Math.max(2,Math.min(4096,form.width.get()));}
    private int height(){return Math.max(2,Math.min(4096,form.height.get()));}
    private float sx(){return form.scale.get()*Math.min(1F,width()/(float)height());}
    private float sy(){return form.scale.get()*Math.min(1F,height()/(float)width());}
    @Override public AABB getPreviewBounds(){return new AABB(-sx(),-sy(),0,sx()*2,sy()*2,.01F);}
    @Override public void renderBodyParts(FormRenderingContext context)
    {
        if(form.parts.getAllTyped().isEmpty())return;
        int light=context.light;
        boolean ui=context.ui;
        boolean renderingWorld=mchorse.bbs_mod.client.BBSRendering.renderingWorld;
        try(NativeOffscreen off=new NativeOffscreen(width(),height(),-1,1,1,-1))
        {
            context.stack.push();
            try
            {
                context.stack.loadIdentity();context.light=0x00f000f0;context.ui=true;
                mchorse.bbs_mod.client.BBSRendering.renderingWorld=false;
                GL11.glCullFace(GL11.GL_FRONT);
                super.renderBodyParts(context);
            }
            finally{context.stack.pop();context.light=light;context.ui=ui;mchorse.bbs_mod.client.BBSRendering.renderingWorld=renderingWorld;}
            off.restore();
            /* Draw while checked out: same-size forms cannot reuse a deferred texture. Child
             * pick colours pass through unchanged rather than becoming the framebuffer's ID. */
            Color tint=Color.white();tint.mul(context.color);
            try(NativeFormDraw draw=new NativeFormDraw(context,tint,form.overlayColor.get(),false))
            {
                off.framebuffer.getMainTexture().bind();GlStateManager.disableCull();
                if(context.isPicking())
                {
                    GL20.glUseProgram(0);net.minecraft.client.Minecraft.getMinecraft().entityRenderer.disableLightmap();
                    GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();GlStateManager.enableAlpha();
                }
                LabelFormRenderer.texturedQuad(-sx(),sy(),sx(),-sy());
            }
        }
    }
    @Override public void collectMatrices(IEntity entity,MatrixStack stack,MatrixCache matrices,String prefix,float transition)
    {
        stack.push();this.applyTransforms(stack,true,transition);Matrix4f origin=new Matrix4f(stack.peek().getPositionMatrix());stack.pop();
        stack.push();this.applyTransforms(stack,false,transition);Matrix4f parent=new Matrix4f(stack.peek().getPositionMatrix());matrices.put(prefix,parent,origin);stack.pop();
        MatrixStack childStack=new MatrixStack();MatrixCache children=new MatrixCache();
        for(BodyPart part:form.parts.getAllTyped())
        {
            Form child=part.getForm();if(child==null)continue;
            childStack.push();MatrixStackUtils.applyTransform(childStack,part.transform.get());
            FormUtilsClient.getRenderer(child).collectMatrices(part.getRenderEntity(entity),childStack,children,StringUtils.combinePaths(prefix,part.getId()),transition);childStack.pop();
        }
        for(Map.Entry<String,MatrixCacheEntry> entry:children.entrySet())
        {MatrixCacheEntry child=entry.getValue();matrices.put(entry.getKey(),project(parent,child.matrix()),project(parent,child.origin()));}
    }
    private Matrix4f project(Matrix4f parent,Matrix4f child)
    {return child==null?null:new Matrix4f(parent).mul(new Matrix4f(child).setTranslation(child.m30()*sx(),child.m31()*sy(),0));}
}
