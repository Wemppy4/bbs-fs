package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.PixelArt;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forge.CommonProxy;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.util.*;

/** Real registry/resource-pack lookup and GPU fractional-scale sampling. */
public final class OriginalNativeResourcesProbe
{
    public static JsonObject run() throws Exception
    {
        Minecraft mc=Minecraft.getMinecraft();JsonObject out=new JsonObject();out.addProperty("ok",true);
        JsonArray blocks=new JsonArray();
        for(Block block:CommonProxy.CHROMA_BLOCKS)
        {
            JsonObject result=new JsonObject();result.addProperty("id",block.getRegistryName().toString());
            result.addProperty("registered",Block.REGISTRY.getObject(block.getRegistryName())==block);
            result.addProperty("item",Item.getItemFromBlock(block).getRegistryName().equals(block.getRegistryName()));
            result.addProperty("tab",block.getCreativeTab()==CommonProxy.BBS_TAB);
            result.addProperty("hardness",block.getBlockHardness(block.getDefaultState(),mc.world,BlockPos.ORIGIN));
            result.addProperty("drops",block.quantityDropped(new Random(1)));
            IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(block.getDefaultState());
            result.addProperty("modelClass",model.getClass().getName());
            result.addProperty("texture",model.getParticleTexture().getIconName());
            int quads=model.getQuads(block.getDefaultState(),null,0).size();boolean diffuse=false;
            for(EnumFacing side:EnumFacing.values())for(net.minecraft.client.renderer.block.model.BakedQuad q:model.getQuads(block.getDefaultState(),side,0)) { quads++;diffuse|=q.shouldApplyDiffuseLighting(); }
            result.addProperty("quads",quads);result.addProperty("diffuse",diffuse);
            result.addProperty("ambientOcclusion",model.isAmbientOcclusion());
            result.addProperty("itemModel",mc.getRenderItem().getItemModelWithOverrides(new ItemStack(block),mc.world,mc.player)!=mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel());
            blocks.add(result);
        }
        out.add("chroma",blocks);
        java.util.Collection<Link> root=BBSMod.getProvider().getLinksFromPath(new Link("minecraft","textures/"),false);
        java.util.Collection<Link> all=BBSMod.getProvider().getLinksFromPath(new Link("minecraft","textures/"),true);
        out.addProperty("minecraftRoot",root.size());out.addProperty("minecraftTextures",all.size());
        Link steve=new Link("minecraft","textures/entity/steve.png");out.addProperty("steveListed",all.contains(steve));
        try(java.io.InputStream stream=BBSMod.getProvider().getAsset(steve)) { out.addProperty("stevePng",stream.read()==137); }
        out.addProperty("nativePixelShader",BBSShaders.getNativePixelArtProgram()==null?0:BBSShaders.getNativePixelArtProgram().getId());
        out.add("pixelart",sampling());return out;
    }

    private static JsonObject sampling()
    {
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),vbo=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        FloatBuffer projection=BufferUtils.createFloatBuffer(16),model=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,model);
        boolean old=BBSSettings.pixelArtSmoothing.get(),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        Framebuffer target=new Framebuffer(96,64,true);target.setFramebufferColor(0,0,0,1);
        int texture=GL11.glGenTextures();JsonObject out=new JsonObject();
        try
        {
            GL30.glBindVertexArray(0);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,0);GL20.glUseProgram(0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GlStateManager.bindTexture(texture);
            ByteBuffer pixels=BufferUtils.createByteBuffer(8*8*4);
            for(int y=0;y<8;y++)for(int x=0;x<8;x++) { byte value=(byte)(((x+y)%2==0)?255:0);pixels.put(value).put(value).put(value).put((byte)255); }pixels.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,8,8,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            target.bindFramebuffer(true);GlStateManager.disableDepth();GlStateManager.disableCull();GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GlStateManager.ortho(0,96,64,0,-1,1);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
            for(float scale:new float[]{1.5F,2F})
            {
                byte[][] frames=new byte[2][];
                for(int smooth=0;smooth<2;smooth++)
                {
                    BBSSettings.pixelArtSmoothing.set(smooth==1);GlStateManager.clearColor(0,0,0,1);target.framebufferClear();target.bindFramebuffer(true);
                    GlStateManager.bindTexture(texture);UIVertexBuffer b=UIVertexBuffer.immediate();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
                    b.pos(0,0,0).tex(0,0).color(255,255,255,255).endVertex();b.pos(0,8*scale,0).tex(0,1).color(255,255,255,255).endVertex();
                    b.pos(8*scale,8*scale,0).tex(1,1).color(255,255,255,255).endVertex();b.pos(8*scale,0,0).tex(1,0).color(255,255,255,255).endVertex();b.draw();
                    PixelArt.setDrawingUI(true);GlStateManager.pushMatrix();GlStateManager.translate(0,22,0);GlStateManager.scale(scale,scale,1);
                    mchorse.bbs_mod.fonts.nativefonts.NativeDefaultFont.renderer().drawString("BBS Ёж",0,0,0xffffffff,false);GlStateManager.popMatrix();PixelArt.setDrawingUI(false);
                    ByteBuffer data=BufferUtils.createByteBuffer(96*64*4);GL11.glReadPixels(0,0,96,64,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);frames[smooth]=new byte[data.capacity()];data.get(frames[smooth]);
                    int seam=0;for(int y=64-(int)(8*scale);y<64;y++)for(int x=0;x<8*scale;x++){int value=frames[smooth][(y*96+x)*4]&255;if(value>5&&value<250)seam++;}
                    out.addProperty("seams_"+scale+"_"+smooth,seam);
                    int glyphPixels=0;for(int y=0;y<45;y++)for(int x=0;x<96;x++)if((frames[smooth][(y*96+x)*4]&255)>10)glyphPixels++;
                    out.addProperty("glyphPixels_"+scale+"_"+smooth,glyphPixels);
                }
                int difference=0;for(int i=0;i<frames[0].length;i+=4)if(Math.abs((frames[0][i]&255)-(frames[1][i]&255))>5)difference++;out.addProperty("changed_"+scale,difference);
            }
            out.addProperty("glError",GL11.glGetError());
        }
        finally
        {
            PixelArt.setDrawingUI(false);BBSSettings.pixelArtSmoothing.set(old);GlStateManager.deleteTexture(texture);target.deleteFramebuffer();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GL11.glLoadMatrix(projection);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glLoadMatrix(model);GlStateManager.matrixMode(mode);
            GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL20.glUseProgram(program);if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
        }
        return out;
    }
}
