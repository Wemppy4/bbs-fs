package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Original sampled ribbon: paused history, stops, length, looping UVs and repeat displacement. */
public class TrailFormRenderer extends NativeGeometryFormRenderer<TrailForm> implements ITickable
{
    private int tick;
    private final Map<FormRenderType,ArrayDeque<Trail>> record=new EnumMap<>(FormRenderType.class);
    public TrailFormRenderer(TrailForm form){super(form);}
    @Override public void tick(IEntity entity){tick++;}
    public int getTrailSamples(){int count=0;for(ArrayDeque<Trail> trail:record.values())count+=trail.size();return count;}
    @Override public AABB getPreviewBounds(){return new AABB(-.02,-1,-.02,.04,2,.04);}
    @Override protected void renderInUI(UIContext context,int x1,int y1,int x2,int y2)
    {
        Texture texture=context.render.getTextures().getTexture(form.texture.get());if(texture==null)return;
        float fit=Math.min((x2-x1-4)/(float)texture.width,(y2-y1-4)/(float)texture.height);
        int w=Math.round(texture.width*fit),h=Math.round(texture.height*fit);
        context.batcher.fullTexturedBox(texture,(x1+x2-w)/2,(y1+y2-h)/2,w,h);
    }
    @Override protected void render3D(FormRenderingContext context)
    {
        if(context.type==FormRenderType.ITEM_INVENTORY||mchorse.bbs_mod.graphics.OptiFineShaders.isShadowPass())return;
        if(context.ui||context.modelRenderer)
        {
            try(NativeFormDraw draw=new NativeFormDraw(context,Color.white(),new Color(0,0,0,0),false))
            {
                GlStateManager.disableTexture2D();org.lwjgl.opengl.GL20.glUseProgram(0);GlStateManager.disableLighting();GlStateManager.color(0,1,0,1);
                GL11.glBegin(GL11.GL_QUADS);GL11.glVertex3f(-.015F,-1,0);GL11.glVertex3f(.015F,-1,0);GL11.glVertex3f(.015F,1,0);GL11.glVertex3f(-.015F,1,0);GL11.glEnd();
            }
            return;
        }
        if(!mchorse.bbs_mod.client.BBSRendering.isRenderingWorld())return;
        float current=tick+context.transition,length=form.length.get();
        ArrayDeque<Trail> trails=record.computeIfAbsent(context.type,k->new ArrayDeque<>());
        Matrix4f world=context.world==null?new Matrix4f(context.camera.view).invert().mul(context.stack.peek().getPositionMatrix()):new Matrix4f(context.world.peek().getPositionMatrix());
        RepeatedFormRender repeated=RepeatedFormRender.current();
        Matrix4f displacement=repeated==null?null:repeated.displacement(form,world);
        if(!form.paused.get()&&!context.isPicking()&&(repeated==null||repeated.first(form)))
        {
            Vector3f top=world.transformPosition(new Vector3f(0,1,0)),bottom=world.transformPosition(new Vector3f(0,-1,0));
            Trail sample=new Trail();sample.tick=current;sample.top=new Vector3d(top);sample.bottom=new Vector3d(bottom);sample.stop=top.distanceSquared(bottom)<1.0E-4;
            if(!trails.isEmpty()&&trails.getLast().tick>current)trails.clear();
            trails.add(sample);
        }
        while(!trails.isEmpty()&&trails.getFirst().tick<current-length)trails.removeFirst();
        if(trails.size()<2||length<=.001F)return;
        Texture texture=BBSModClient.getTextures().getTexture(form.texture.get());if(texture==null)return;
        context.stack.push();int previousLight=context.light;
        try
        {
            /* Original position/texture ribbons do not sample environmental light. */
            context.light=0x00f000f0;
            Matrix4f view=context.stack.peek().getPositionMatrix().set(context.camera.view).translate((float)-context.camera.position.x,(float)-context.camera.position.y,(float)-context.camera.position.z);
            if(displacement!=null)view.mul(displacement);
            try(NativeFormDraw draw=new NativeFormDraw(context,Color.white(),form.overlayColor.get(),false))
            {
                texture.bind();GlStateManager.disableCull();Trail previous=null;float segmentLength=length;GL11.glBegin(GL11.GL_QUADS);
                try
                {
                    for(Trail sample:trails)
                    {
                        if(previous!=null&&!previous.stop&&!sample.stop)
                        {
                            float u1=form.loop.get()?sample.tick/segmentLength:(current-sample.tick)/segmentLength,u2=form.loop.get()?previous.tick/segmentLength:(current-previous.tick)/segmentLength;
                            vertex(sample.top,u1,0);vertex(sample.bottom,u1,1);vertex(previous.bottom,u2,1);vertex(previous.top,u2,0);
                        }
                        else segmentLength=Math.max(.001F,current-sample.tick);
                        previous=sample;
                    }
                }
                finally{GL11.glEnd();}
            }
        }
        finally{context.light=previousLight;context.stack.pop();}
    }
    private static void vertex(Vector3d v,float u,float y){GL11.glTexCoord2f(u,y);GL11.glVertex3d(v.x,v.y,v.z);}
    private static class Trail{float tick;Vector3d top,bottom;boolean stop;}
}
