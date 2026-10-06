package mchorse.bbs_mod.particles.vanilla;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.particles.ParticleRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.List;
/** Native particle factories and simulation, owned by one viewport instead of the world queue. */
public class VanillaParticleScene {
    private static final ResourceLocation TEXTURE=new ResourceLocation("textures/particle/particles.png");
    private static boolean rendering;
    private final List<Particle> particles=new ArrayList<>();
    private final List<Particle> pending=new ArrayList<>();
    private final Vector3d origin=new Vector3d();
    private FactoryManager factory;
    private World world;
    public static boolean isRendering(){return rendering;}
    public int size(){return particles.size()+pending.size();}
    public void clear(){particles.clear();pending.clear();}
    private final class FactoryManager extends ParticleManager {
        FactoryManager(World world){super(world,Minecraft.getMinecraft().getTextureManager());}
        @Override public void addEffect(Particle particle){if(particle!=null&&size()<4096)pending.add(particle);}
    }
    public void spawn(VanillaParticleEffect effect,double x,double y,double z,double vx,double vy,double vz){
        Minecraft mc=Minecraft.getMinecraft(); if(mc.world==null||size()>=4096)return;
        if(world!=mc.world){world=mc.world;clear();factory=new FactoryManager(world);}
        if(size()==0){Entity anchor=mc.getRenderViewEntity();origin.set(anchor==null?0:anchor.posX,world.getHeight()+32,anchor==null?0:anchor.posZ);}
        effect.spawn(factory,world,origin.x+x,origin.y+y,origin.z+z,vx,vy,vz);
    }
    public void tick(){
        if(factory==null)return;
        particles.addAll(pending);pending.clear();
        Minecraft mc=Minecraft.getMinecraft();ParticleManager previous=mc.effectRenderer;mc.effectRenderer=factory;
        try{for(java.util.Iterator<Particle> it=particles.iterator();it.hasNext();){Particle p=it.next();p.onUpdate();if(!p.isAlive())it.remove();}}
        finally{mc.effectRenderer=previous;}
    }
    public void render(Camera camera,float transition){render(camera,transition,null);}
    public void render(Camera camera,float transition,Matrix4f displacement){
        if(particles.isEmpty())return;
        Minecraft mc=Minecraft.getMinecraft();Entity anchor=mc.getRenderViewEntity();if(anchor==null)return;
        double oldX=Particle.interpPosX,oldY=Particle.interpPosY,oldZ=Particle.interpPosZ;Vec3d oldDirection=Particle.cameraViewDir;
        Particle.interpPosX=origin.x+camera.position.x;Particle.interpPosY=origin.y+camera.position.y;Particle.interpPosZ=origin.z+camera.position.z;
        Vector3f direction=camera.getLookDirection();Particle.cameraViewDir=new Vec3d(direction.x,direction.y,direction.z);
        Matrix4f view=new Matrix4f(camera.view);if(displacement!=null)view.mul(displacement);
        java.nio.FloatBuffer matrix=BufferUtils.createFloatBuffer(16);view.get(matrix);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GL11.glLoadMatrix(matrix);
        boolean oldDepth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), oldLighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        try(ParticleRenderPass pass=new ParticleRenderPass(false)){
            rendering=true;GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,1F/255F);
            float yaw=camera.rotation.y, pitch=-camera.rotation.x;
            float x=(float)Math.cos(yaw), yz=(float)Math.sin(yaw), z=(float)Math.cos(pitch), xy=-yz*(float)Math.sin(pitch), xz=x*(float)Math.sin(pitch);
            for(int layer=0;layer<4;layer++)for(int depth=0;depth<2;depth++){
                boolean any=false;for(Particle particle:particles)if(particle.getFXLayer()==layer&&(particle.shouldDisableDepth()?0:1)==depth){any=true;break;}
                if(!any)continue;
                GlStateManager.depthMask(depth!=0);mc.getTextureManager().bindTexture(layer==1?TextureMap.LOCATION_BLOCKS_TEXTURE:TEXTURE);
                Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();
                if(layer!=3)buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
                for(Particle p:particles)if(p.getFXLayer()==layer&&(p.shouldDisableDepth()?0:1)==depth)p.renderParticle(buffer,anchor,transition,x,z,yz,xy,xz);
                if(layer!=3)tess.draw();
            }
        }finally{
            rendering=false;Particle.interpPosX=oldX;Particle.interpPosY=oldY;Particle.interpPosZ=oldZ;Particle.cameraViewDir=oldDirection;
            if(oldDepth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(oldLighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            GlStateManager.popMatrix();
        }
    }
}
