package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.FilteredLink;
import mchorse.bbs_mod.utils.resources.MultiLink;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import java.util.*;

/** Native normal/specular units for BBS textures, including original per-material LabPBR sliders. */
public final class OptiFinePbr
{
    private static final Map<Integer,Entry> TRACKED=new HashMap<>();
    private static int scopes,neutralNormal,neutralSpecular;
    private static final Deque<OptiFineTextureSize> TEXTURE_SIZES=new ArrayDeque<>();
    private static boolean resolving;
    private OptiFinePbr() {}

    static void begin(int normal,int specular)
    {neutralNormal=normal;neutralSpecular=specular;scopes++;TEXTURE_SIZES.push(new OptiFineTextureSize());updateSize();}
    static void end(){TEXTURE_SIZES.pop().close();scopes--;}
    private static void updateSize()
    {
        int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
        TEXTURE_SIZES.peek().bind(width,height);
    }

    /** Texture renderers may bind their albedo after entering a native draw scope. */
    public static void onBind(int albedo)
    {
        if(scopes==0||resolving||!OptiFineShaders.isWorldPass()||GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE0)return;
        int[] maps=maps(albedo,neutralNormal,neutralSpecular);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE2);GlStateManager.bindTexture(maps[0]);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE3);GlStateManager.bindTexture(maps[1]);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        updateSize();
    }

    public static Texture track(Texture texture,Link source)
    {
        if(texture==null||!texture.isValid()||source==null)return texture;
        Entry entry=TRACKED.get(texture.id);
        if(entry==null||!source.equals(entry.source))
        {
            forget(texture.id);entry=new Entry(texture,source);TRACKED.put(texture.id,entry);
        }
        if(texture.getParent()!=null)entry.frame=texture.getParent().textures.indexOf(texture);
        return texture;
    }

    public static void trackVariant(Texture texture,Link source,float smooth,float metal,float sss,float emission,float relief)
    {
        track(texture,source);Entry entry=TRACKED.get(texture.id);
        float[] next={smooth,metal,sss,emission,relief};
        if(!Arrays.equals(entry.sliders,next)){entry.sliders=next;entry.dirty=true;}
    }

    public static void trackFrame(Texture variant,Link source,Texture frame)
    {
        track(variant,source);
        TRACKED.get(variant.id).frame=frame.getParent()==null?-1:frame.getParent().textures.indexOf(frame);
    }

    public static void forget(int id)
    {
        Entry entry=TRACKED.remove(id);if(entry!=null)entry.deleteGenerated();
    }

    /** May load maps lazily, but preserves the caller's albedo binding and texture unit. */
    public static int[] maps(int albedo,int defaultNormal,int defaultSpecular)
    {
        Entry entry=TRACKED.get(albedo);
        if(entry==null)return new int[]{defaultNormal,defaultSpecular};
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean wasResolving=resolving;resolving=true;
        try
        {
            if(entry.generation!=BBSResources.getAssetsVersion())
            {entry.generation=BBSResources.getAssetsVersion();entry.dirty=true;entry.reliefKey=-1;}
            if(entry.sliders==null)
                return new int[]{fileMap(entry,"_n.png",defaultNormal),fileMap(entry,"_s.png",defaultSpecular)};
            if(entry.dirty)
            {
                Pixels spec=specular(entry.sliders);
                try
                {
                    if(entry.specular==null)entry.specular=Texture.textureFromPixels(spec,GL11.GL_NEAREST);
                    else {entry.specular.bind();entry.specular.uploadTexture(spec);}
                }
                finally{spec.delete();}
                int relief=Math.round(clamp(entry.sliders[4])*255F);
                if(relief!=entry.reliefKey)
                {
                    if(entry.normal!=null){entry.normal.delete();entry.normal=null;}
                    entry.reliefKey=relief;
                    if(relief>0)
                    {
                        Pixels albedoPixels=Texture.pixelsFromTexture(entry.texture);
                        if(albedoPixels!=null)
                        {
                            Pixels normal=normal(albedoPixels,entry.sliders[4]);
                            try{entry.normal=Texture.textureFromPixels(normal,GL11.GL_NEAREST);}
                            finally{normal.delete();albedoPixels.delete();}
                        }
                    }
                }
                entry.dirty=false;
            }
            return new int[]{entry.normal==null?fileMap(entry,"_n.png",defaultNormal):entry.normal.id,entry.specular.id};
        }
        finally{GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GlStateManager.bindTexture(bound);GlStateManager.setActiveTexture(active);resolving=wasResolving;}
    }

    private static int fileMap(Entry entry,String suffix,int fallback)
    {
        /* These sources describe a generated color or an account, not a sibling file.
         * In particular, player:name_n would fetch another player's skin as a normal map. */
        if(!hasSidecarFiles(entry.source))return fallback;
        Texture texture=BBSModClient.getTextures().getTexture(suffixed(entry.source,suffix),GL11.GL_NEAREST,true);
        if(texture==null||texture==BBSModClient.getTextures().getError()||!texture.isValid())return fallback;
        if(entry.frame>=0&&texture.getParent()!=null&&!texture.getParent().textures.isEmpty())
            texture=texture.getParent().textures.get(entry.frame%texture.getParent().textures.size());
        return texture.id;
    }

    static boolean hasSidecarFiles(Link source)
    {
        if(source==null||Link.COLOR.equals(source.source)||"player".equals(source.source))return false;
        if(source instanceof MultiLink)
            for(FilteredLink child:((MultiLink)source).children)if(!hasSidecarFiles(child.path))return false;
        return true;
    }

    public static Link suffixed(Link link,String suffix)
    {
        if(link instanceof MultiLink)
        {
            MultiLink copy=(MultiLink)((MultiLink)link).copy();
            for(FilteredLink child:copy.children)if(child.path!=null)child.path=suffixed(child.path,suffix);
            return copy;
        }
        return new Link(link.source,StringUtils.removeExtension(link.path)+suffix);
    }

    /** Exact original LabPBR packing; file maps above remain untouched for OldPBR packs. */
    public static Pixels specular(float[] sliders)
    {
        int r=Math.round(clamp(sliders[0])*255F);
        int g=sliders[1]<=0?10:sliders[1]>=1?255:Math.round(10F+sliders[1]*219F);
        int b=sliders[2]<=0?0:Math.round(65F+clamp(sliders[2])*190F);
        int a=sliders[3]<=0?255:Math.round(clamp(sliders[3])*254F);
        Pixels pixels=Pixels.fromSize(1,1);pixels.setColor(0,0,new Color(r/255F,g/255F,b/255F,a/255F));pixels.rewindBuffer();return pixels;
    }

    /** Original luminance gradient: RG normal XY, B unoccluded AO, A height for parallax. */
    public static Pixels normal(Pixels albedo,float relief)
    {
        int w=albedo.width,h=albedo.height;float strength=clamp(relief)*4F;float[] height=new float[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){Color c=albedo.getColor(x,y);height[x+y*w]=(c.r+c.g+c.b)/3F;}
        Pixels normal=Pixels.fromSize(w,h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
        {
            float nx=(height[Math.max(x-1,0)+y*w]-height[Math.min(x+1,w-1)+y*w])*strength;
            float ny=(height[x+Math.min(y+1,h-1)*w]-height[x+Math.max(y-1,0)*w])*strength;
            float length=(float)Math.sqrt(nx*nx+ny*ny+1F);
            normal.setColor(x,y,new Color(nx/length*.5F+.5F,ny/length*.5F+.5F,1F,height[x+y*w]));
        }
        normal.rewindBuffer();return normal;
    }
    private static float clamp(float v){return Math.max(0F,Math.min(1F,v));}

    private static final class Entry
    {
        final Texture texture;final Link source;
        int frame=-1,generation=-1,reliefKey=-1;
        float[] sliders;boolean dirty;
        Texture normal,specular;
        Entry(Texture texture,Link source){this.texture=texture;this.source=source;}
        void deleteGenerated(){if(normal!=null)normal.delete();if(specular!=null)specular.delete();}
    }
}
