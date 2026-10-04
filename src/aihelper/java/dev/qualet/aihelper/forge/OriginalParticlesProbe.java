package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.particles.*;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.particles.vanilla.VanillaParticleEffect;
import mchorse.bbs_mod.particles.vanilla.VanillaParticleScene;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;

/** Genuine production simulation and GPU rendering into an offscreen target; never opens or focuses a window. */
public final class OriginalParticlesProbe {
    public static JsonObject run(JsonObject request) throws Exception {
        JsonObject out=new JsonObject();out.addProperty("ok",true);
        if(UIScreen.getCurrentMenu() instanceof UIDashboard){
            UIDashboard dashboard=(UIDashboard)UIScreen.getCurrentMenu();
            if(dashboard.getPanels().panel instanceof UIParticleSchemePanel){
                UIParticleSchemePanel panel=(UIParticleSchemePanel)dashboard.getPanels().panel;
                out.addProperty("panel",panel.getClass().getSimpleName());
                out.addProperty("sections",panel.sections.size());
                out.addProperty("dataId",panel.getData()==null?"":panel.getData().getId());
                out.addProperty("emitterAge",panel.renderer.emitter.age);
                out.addProperty("particles",panel.renderer.emitter.particles.size());
            }
        }
        if(!request.has("test")||!request.get("test").getAsBoolean())return out;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null)throw new IllegalStateException("A loaded client world is required");
        ParticleScheme source;
        try(java.io.InputStream in=BBSMod.getProvider().getAsset(Link.assets("particles/default_placeholder.json"))){source=ParticleScheme.parse(IOUtils.readText(in));}
        if(source==null)throw new IllegalStateException("Bundled particle scheme did not parse");
        ParticleScheme copy=ParticleScheme.dupe(source);
        out.addProperty("roundtrip",mchorse.bbs_mod.data.types.BaseType.equals(ParticleScheme.toData(source),ParticleScheme.toData(copy)));
        out.addProperty("componentCount",source.components.size());
        out.addProperty("curveCount",source.curves.size());
        String id="__aihelper_particles_"+System.nanoTime();
        java.io.File file=BBSModClient.getParticles().getFile(id);
        try {
            boolean saved=BBSModClient.getParticles().save(id,ParticleScheme.toData(source));
            ParticleScheme loaded=BBSModClient.getParticles().load(id);
            out.addProperty("repositoryRoundtrip",saved&&loaded!=null&&mchorse.bbs_mod.data.types.BaseType.equals(ParticleScheme.toData(loaded),ParticleScheme.toData(source)));
        } finally {if(file!=null)Files.deleteIfExists(file.toPath());}
        ParticleEmitter emitter=new ParticleEmitter();emitter.setScheme(copy);
        for(int i=0;i<40;i++)emitter.update();
        out.addProperty("spawned",emitter.particles.size());
        boolean finite=true,moving=false,limited=true;
        for(Particle particle:emitter.particles){finite&=Double.isFinite(particle.position.x)&&Double.isFinite(particle.position.y)&&Double.isFinite(particle.position.z);moving|=particle.position.distanceSquared(particle.prevPosition)>0;limited&=particle.age<=particle.lifetime+1;}
        out.addProperty("finite",finite);out.addProperty("motion",moving);out.addProperty("lifetime",limited);
        /* BBS pauses emission, while already emitted particles continue to age. Check inside
         * a cycle: at its boundary the original lifetime component legitimately restarts it. */
        ParticleEmitter pauseEmitter=new ParticleEmitter();pauseEmitter.setScheme(ParticleScheme.dupe(source));
        for(int i=0;i<5;i++)pauseEmitter.update();
        int age=pauseEmitter.age,count=pauseEmitter.particles.size();
        Particle first=pauseEmitter.particles.get(0);int particleAge=first.age;
        pauseEmitter.paused=true;pauseEmitter.update();
        out.addProperty("pause",pauseEmitter.age==age&&pauseEmitter.particles.size()==count&&first.age==particleAge+1);
        pauseEmitter.paused=false;pauseEmitter.update();
        out.addProperty("resume",pauseEmitter.age==age+1&&pauseEmitter.particles.size()>count);
        ParticleSettings settings=new ParticleSettings();settings.particle=new ResourceLocation("minecraft:smoke");
        out.addProperty("modernNameAlias",VanillaParticleEffect.from(settings).type==EnumParticleTypes.SMOKE_NORMAL);
        settings.particle=new ResourceLocation("minecraft:blockcrack");settings.arguments="minecraft:stone 0";
        VanillaParticleEffect block=VanillaParticleEffect.from(settings);out.addProperty("blockArguments",block.arguments.length==1&&block.arguments[0]>0);
        settings.particle=new ResourceLocation("minecraft:flame");settings.arguments="";
        VanillaParticleScene scene=new VanillaParticleScene();
        for(int i=0;i<20;i++)scene.spawn(VanillaParticleEffect.from(settings),(i%5-2)*0.15,(i/5-2)*0.15,0,0,0.02,0);
        scene.tick();out.addProperty("vanillaCount",scene.size());
        out.add("render",render(emitter,scene));
        out.addProperty("glError",GL11.glGetError());
        return out;
    }
    private static JsonObject render(ParticleEmitter emitter,VanillaParticleScene scene)throws Exception {
        JsonObject out=new JsonObject();final int size=192;
        int previousFbo=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int packAlignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),packRow=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),packSkipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),packSkipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS),packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        FloatBuffer clear=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE,clear);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),fog=GL11.glIsEnabled(GL11.GL_FOG),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        Framebuffer framebuffer=new Framebuffer(size,size,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-3,3,-3,3,-20,20);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try {
            framebuffer.bindFramebuffer(true);GlStateManager.disableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            Camera camera=new Camera();camera.position.set(0,0,4);camera.updateView();
            MatrixStack stack=new MatrixStack();emitter.setupCameraProperties(camera);
            GlStateManager.clearColor(0,0,0,0);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            try(ParticleRenderPass pass=new ParticleRenderPass(false)){emitter.render(stack,0,0.5F,true);}
            out.addProperty("bedrockPixels",pixels(size,"original-particles-bedrock.png"));
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);scene.render(camera,0.5F);
            out.addProperty("vanillaPixels",pixels(size,"original-particles-vanilla.png"));
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            framebuffer.deleteFramebuffer();OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,previousFbo);GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
            GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();if(fog)GlStateManager.enableFog();else GlStateManager.disableFog();if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,packAlignment);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,packRow);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,packSkipRows);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,packSkipPixels);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);GL20.glUseProgram(previousProgram);
        }
        return out;
    }
    private static int pixels(int size,String name)throws Exception {
        ByteBuffer data=BufferUtils.createByteBuffer(size*size*4);GL11.glReadPixels(0,0,size,size,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        int nonzero=0;java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){int i=(y*size+x)*4,r=data.get(i)&255,g=data.get(i+1)&255,b=data.get(i+2)&255,a=data.get(i+3)&255;if(r+g+b>0)nonzero++;image.setRGB(x,size-1-y,a<<24|r<<16|g<<8|b);}
        java.io.File file=new java.io.File(Minecraft.getMinecraft().gameDir,"screenshots/ai/"+name);file.getParentFile().mkdirs();javax.imageio.ImageIO.write(image,"png",file);return nonzero;
    }
}
