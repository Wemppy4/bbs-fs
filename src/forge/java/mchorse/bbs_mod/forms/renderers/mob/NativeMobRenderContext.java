package mchorse.bbs_mod.forms.renderers.mob;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.jem.VanillaRigs;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forge.studio.NativePickingShader;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** A scoped pose over actual animated ModelRenderers; never writes into shared native model fields. */
public final class NativeMobRenderContext implements AutoCloseable
{
    private static NativeMobRenderContext current;
    private static int bypass;
    private static final java.lang.reflect.Field OWNER=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ModelRenderer.class,"baseModel","field_78810_s");
    private final NativeMobRenderContext previous=current;
    private final MobForm form;
    private final VanillaRigs.Rig rig;
    private final Pose pose;
    private final FormRenderingContext context;
    private final MatrixCache matrices;
    private final Matrix4f baseInverse;
    private final Map<ModelRenderer,String> names=new IdentityHashMap<>();
    private final Set<ModelRenderer> primary=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ModelBase> layers=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String,Integer> indices=new HashMap<>();
    private final java.nio.FloatBuffer nativeColor=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private boolean captureOnly;
    public NativeMobRenderContext(MobForm form,VanillaRigs.Rig rig,Pose pose,FormRenderingContext context,MatrixCache matrices)
    {
        this.form=form;this.rig=rig;this.pose=pose;this.context=context;this.matrices=matrices;
        this.baseInverse=new Matrix4f(context.stack.peek().getPositionMatrix()).invert();
        if(rig!=null)for(Map.Entry<String,ModelRenderer> e:rig.parts().entrySet()){names.put(e.getValue(),e.getKey());primary.add(e.getValue());}
        int index=context.getPickingIndex()+1;
        if(rig!=null)for(String bone:rig.parts().keySet())indices.put(bone,context.stencilMap!=null&&!context.stencilMap.increment?context.getPickingIndex():index++);
        current=this;
    }
    public NativeMobRenderContext captureOnly(){this.captureOnly=true;return this;}
    public static boolean isActive(){return current!=null;}
    public static boolean renderPart(ModelRenderer part,float scale,int mode)
    {
        NativeMobRenderContext active=current;
        if(active==null||bypass>0)return false;
        if(!active.names.containsKey(part))active.layer(part);
        if(!active.names.containsKey(part))return active.captureOnly;
        active.draw(part,scale,mode);return true;
    }
    private void layer(ModelRenderer part)
    {
        if(captureOnly||rig==null||!(rig.model() instanceof ModelBiped))return;
        try
        {
            ModelBase owner=(ModelBase)OWNER.get(part);
            /* Armour models use the same bone convention; skull/held-item models are already
             * inside a posed postRender frame and must not receive the pose twice. */
            if(owner instanceof ModelBiped&&layers.add(owner))
                for(Map.Entry<String,ModelRenderer> e:VanillaRigs.inspect(form.mobID.get().replace("minecraft:",""),owner).parts().entrySet())names.put(e.getValue(),e.getKey());
        }
        catch(IllegalAccessException e){throw new IllegalStateException("Cannot inspect mob render layer",e);}
    }
    public static String overlayParent(String bone)
    {
        switch(bone)
        {
            case "hat":return "head";
            case "jacket":return "body";
            case "left_sleeve":return "left_arm";
            case "right_sleeve":return "right_arm";
            case "left_pants":return "left_leg";
            case "right_pants":return "right_leg";
            default:return null;
        }
    }
    private void draw(ModelRenderer part,float scale,int mode)
    {
        if(part.isHidden||!part.showModel)return;
        String bone=names.get(part);PoseTransform posed=pose.transforms.get(bone);
        Matrix4f parent=NativeTextureRenderer.currentMatrix();
        Matrix4f inherited=new Matrix4f();
        String parentBone=overlayParent(bone);
        if(primary.contains(part)&&parentBone!=null&&rig.parts().containsKey(parentBone))
        {
            ModelRenderer nativeParent=rig.parts().get(parentBone);PoseTransform parentPose=pose.transforms.get(parentBone);
            if(parentPose!=null)inherited.set(local(nativeParent,parentPose,scale,mode)).mul(local(nativeParent,null,scale,mode).invert());
        }
        Matrix4f local=new Matrix4f(inherited).mul(local(part,posed,scale,mode)),origin=new Matrix4f(parent).mul(inherited);
        if(mode==0)origin.translate(part.offsetX,part.offsetY,part.offsetZ);
        origin.translate((part.rotationPointX+(posed==null?0:posed.translate.x))*scale,
            (part.rotationPointY+(posed==null?0:posed.translate.y))*scale,(part.rotationPointZ+(posed==null?0:posed.translate.z))*scale);
        Matrix4f full=new Matrix4f(parent).mul(local);
        if(mode==2){NativeTextureRenderer.loadMatrix(full);return;}
        if(primary.contains(part))matrices.put(bone,new Matrix4f(baseInverse).mul(full).scale(-1,-1,1),new Matrix4f(baseInverse).mul(origin).scale(-1,-1,1));
        GlStateManager.pushMatrix();
        try
        {
            if(!captureOnly&&(posed==null||posed.visible))
            {
                /* Reuse vanilla's existing compiled geometry, including OptiFine model sprites.
                 * renderWithRotation draws this part only; its native rotation is cancelled here. */
                Matrix4f nativeRotation=local(part,null,scale,1);
                NativeTextureRenderer.loadMatrix(new Matrix4f(full).mul(nativeRotation.invert()));
                if(primary.contains(part)&&form.texture.get()!=null)BBSModClient.getTextures().bindTexture(form.texture.get());
                int id=context.isPicking()?(primary.contains(part)?indices.getOrDefault(bone,context.getPickingIndex()):context.getPickingIndex()):-1;
                nativeColor.clear();GL11.glGetFloat(GL11.GL_CURRENT_COLOR,nativeColor);
                float cr=nativeColor.get(0),cg=nativeColor.get(1),cb=nativeColor.get(2),ca=nativeColor.get(3);
                try(NativePickingShader.Scope pick=NativePickingShader.open(id))
                {
                    float r=primary.contains(part)?1:cr,g=primary.contains(part)?1:cg,b=primary.contains(part)?1:cb,a=primary.contains(part)?1:ca;
                    if(!context.ui&&!context.isPicking()&&mchorse.bbs_mod.graphics.OptiFineShaders.isWorldPass())
                    {
                        mchorse.bbs_mod.utils.colors.Color tint=mchorse.bbs_mod.utils.colors.Color.white();tint.mul(context.color);
                        r*=tint.r;g*=tint.g;b*=tint.b;a*=tint.a;
                    }
                    color(r,g,b,a);
                    bypass++;
                    /* OptiFine adds child traversal to renderWithRotation; we traverse below
                     * with a separate pose and picking ID, so ask native code for this box only. */
                    List<ModelRenderer> children=part.childModels;part.childModels=null;
                    try{part.renderWithRotation(scale);}finally{part.childModels=children;bypass--;}
                }
                finally{color(cr,cg,cb,ca);}
            }
            if(mode==0&&part.childModels!=null)
            {
                NativeTextureRenderer.loadMatrix(full);
                for(ModelRenderer child:part.childModels)child.render(scale);
            }
        }
        finally{GlStateManager.popMatrix();}
    }
    private static void color(float r,float g,float b,float a)
    {
        GlStateManager.color(r,g,b,a);
        /* Native array uploads can invalidate current color without updating MC's cache. */
        GL11.glColor4f(r,g,b,a);
    }
    public static Matrix4f local(ModelRenderer part,PoseTransform pose,float scale,int mode)
    {
        Matrix4f matrix=new Matrix4f();
        if(mode==0)matrix.translate(part.offsetX,part.offsetY,part.offsetZ);
        matrix.translate((part.rotationPointX+(pose==null?0:pose.translate.x))*scale,
            (part.rotationPointY+(pose==null?0:pose.translate.y))*scale,(part.rotationPointZ+(pose==null?0:pose.translate.z))*scale);
        Vector3f r=pose==null?new Vector3f():pose.getEulerRotation(new Vector3f());r.add(part.rotateAngleX,part.rotateAngleY,part.rotateAngleZ);
        if(mode==1)matrix.rotateY(r.y).rotateX(r.x).rotateZ(r.z);else matrix.rotateZ(r.z).rotateY(r.y).rotateX(r.x);
        if(pose!=null)matrix.scale(pose.scale);
        return matrix;
    }
    public void close(){current=previous;}
}
