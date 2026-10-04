package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.util.*;

/** Original BBS text layout and anchors, using the native modern bitmap/TTF font service. */
public class LabelFormRenderer extends NativeGeometryFormRenderer<LabelForm>
{
    public LabelFormRenderer(LabelForm form){super(form);}
    private FontRenderer font(float detail)
    {
        FontRenderer font=BBSModClient.getFonts().get(form.font.get(),form.fontSize.get(),detail);
        return font==null?Batcher2D.getDefaultTextRenderer():font;
    }
    @Override public boolean isPreviewCameraFacing(){return form.billboard.get();}
    private List<String> lines(FontRenderer font)
    {
        String text=StringUtils.processColoredText(form.text.get());
        List<String> lines=form.max.get()>10?new ArrayList<>(font.wrap(text,form.max.get())):new ArrayList<>(Collections.singletonList(text));
        if(lines.size()>1)for(int i=0;i<lines.size();i++)lines.set(i,lines.get(i).trim());
        return lines;
    }
    private int width(FontRenderer font,List<String> lines){int width=0;for(String line:lines)width=Math.max(width,font.getWidth(line)-1);return width;}
    private int lineHeight(FontRenderer font){return form.lineHeight.get()>0?form.lineHeight.get():font.getLineHeight();}
    @Override public AABB getPreviewBounds()
    {
        FontRenderer f=font(FontManager.MAX_DETAIL);List<String> lines=lines(f);int w=width(f,lines),h=f.getHeight()+Math.max(0,lines.size()-1)*lineHeight(f);
        return new AABB(-w*form.anchorX.get()/16F,-h*(1-form.anchorY.get())/16F,0,w/16F,h/16F,.01F);
    }
    @Override protected void renderInUI(UIContext context,int x1,int y1,int x2,int y2)
    {
        FontRenderer font=font(BBSModClient.getGUIScale()),previous=context.batcher.setFont(font);
        try
        {
            List<String> lines=font.wrap(StringUtils.processColoredText(form.text.get()),Math.max(1,x2-x1-4));
            int height=font.getHeight(),step=height+4,y=(y1+y2-height-(lines.size()-1)*step)/2;
            for(String line:lines){context.batcher.textShadow(line,x1+2,y,form.color.get().getARGBColor());y+=step;}
        }
        finally{context.batcher.setFont(previous);}
    }
    @Override protected void render3D(FormRenderingContext context)
    {
        FontRenderer font=font(FontManager.MAX_DETAIL);List<String> lines=lines(font);
        int w=width(font,lines),h=font.getHeight()+Math.max(0,lines.size()-1)*lineHeight(font);
        int x=(int)(-w*form.anchorX.get()),y=(int)(-h*form.anchorY.get());
        float padding=Math.max(0,form.offset.get());
        int left=(int)Math.floor(Math.min(x-padding,x+form.shadowX.get())),top=(int)Math.floor(Math.min(y-padding,y+form.shadowY.get()));
        int right=(int)Math.ceil(Math.max(x+w+padding,x+w+form.shadowX.get()+2)),bottom=(int)Math.ceil(Math.max(y+h+padding,y+h+form.shadowY.get()+2));
        int width=Math.max(2,Math.min(4096,right-left)),height=Math.max(2,Math.min(4096,bottom-top));
        /* Rendering text to a transient transparent buffer preserves texture-alpha picking, even
         * for native font providers which intentionally select fixed-function GL internally. */
        try(NativeOffscreen off=new NativeOffscreen(width,height,left,left+width,top+height,top))
        {
            GL20.glUseProgram(0);GlStateManager.disableLighting();GlStateManager.disableCull();
            net.minecraft.client.Minecraft.getMinecraft().entityRenderer.disableLightmap();
            Color bg=form.background.get();
            if(bg.a>0){GlStateManager.disableTexture2D();GlStateManager.color(bg.r,bg.g,bg.b,bg.a);quad(x-padding,y-padding,x+w+padding,y+h+padding,-.2F);GlStateManager.enableTexture2D();}
            Color shadow=form.shadowColor.get();
            if(shadow.a>0)drawLines(font,lines,x,y,w,shadow.getARGBColor(),form.shadowX.get(),form.shadowY.get(),-.1F);
            drawLines(font,lines,x,y,w,form.color.get().getARGBColor(),0,0,0);
            mchorse.bbs_mod.graphics.texture.Texture texture=off.straightTexture();
            off.restore();
            context.stack.push();
            try
            {
                if(form.billboard.get())
                {
                    org.joml.Matrix4f m=context.stack.peek().getPositionMatrix();org.joml.Vector3f scale=m.getScale(new org.joml.Vector3f());
                    m.m00(1).m01(0).m02(0).m10(0).m11(1).m12(0).m20(0).m21(0).m22(1).scale(scale);
                }
                Color tint=Color.white();tint.mul(context.color);
                if(!context.isPicking()&&mchorse.bbs_mod.forms.FormTranslucentQueue.isActive())
                {
                    off.retain();
                    mchorse.bbs_mod.forms.FormTranslucentQueue.add(new NativeFormCommand(context,tint,form.overlayColor.get(),false,false,false,true,()->
                    {texture.bind();texturedQuad(left/16F,-top/16F,(left+width)/16F,-(top+height)/16F);})
                    {@Override public void release(){off.release();}});
                }
                else try(NativeFormDraw draw=new NativeFormDraw(context,tint,form.overlayColor.get(),false))
                {
                    GlStateManager.disableCull();texture.bind();texturedQuad(left/16F,-top/16F,(left+width)/16F,-(top+height)/16F);
                }
            }
            finally{context.stack.pop();}
        }
    }
    private void drawLines(FontRenderer font,List<String> lines,int x,int y,int w,int color,float dx,float dy,float z)
    {
        GlStateManager.pushMatrix();GlStateManager.translate(0,0,z);
        try{for(String line:lines){float px=x+(form.anchorLines.get()?(int)((w-font.getWidth(line))*form.anchorX.get()):0);font.getRenderer().drawString(line,px+dx,y+dy,color,false);y+=lineHeight(font);}}
        finally{GlStateManager.popMatrix();}
    }
    static void quad(float x1,float y1,float x2,float y2,float z)
    {GL11.glBegin(GL11.GL_QUADS);GL11.glVertex3f(x1,y1,z);GL11.glVertex3f(x2,y1,z);GL11.glVertex3f(x2,y2,z);GL11.glVertex3f(x1,y2,z);GL11.glEnd();}
    static void texturedQuad(float x1,float y1,float x2,float y2)
    {GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,1);GL11.glVertex3f(x1,y1,0);GL11.glTexCoord2f(1,1);GL11.glVertex3f(x2,y1,0);GL11.glTexCoord2f(1,0);GL11.glVertex3f(x2,y2,0);GL11.glTexCoord2f(0,0);GL11.glVertex3f(x1,y2,0);GL11.glEnd();}
}
