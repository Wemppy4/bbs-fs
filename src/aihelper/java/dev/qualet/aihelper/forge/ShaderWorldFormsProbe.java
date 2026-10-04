package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.structure.*;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.graphics.OptiFineShaders;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.CemResourceLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Native world shader draws and resource reloads in the isolated ai_helper game only. */
public final class ShaderWorldFormsProbe
{
    private static ShaderWorldFormsProbe active;
    private static FolderResourcePack fixture;
    private static Path folder;
    private static int beforeVersion;
    private static Object firstModel;
    private static boolean modelReplaced;
    private final Form[] forms=new Form[8];
    private final String[] names={"grass","water","item","label","structure","framebuffer","pig","cem/cow"};
    private final int[] samples=new int[8];
    private final Set<String> errors=new LinkedHashSet<>();
    private int frames,worldFrames,shaderFrames;
    private boolean restored=true;
    private JsonArray cemDraw;
    private int cemOverlay=10<<16;

    private ShaderWorldFormsProbe()
    {
        BlockForm grass=new BlockForm();grass.blockState.set(Blocks.GRASS.getDefaultState());grass.color.set(new Color(.8F,1,.8F,1));forms[0]=grass;
        BlockForm water=new BlockForm();water.blockState.set(Blocks.WATER.getDefaultState());forms[1]=water;
        ItemForm item=new ItemForm();item.stack.set(new ItemStack(Items.DIAMOND_SWORD));forms[2]=item;
        LabelForm label=new LabelForm();label.text.set("BBS Ёж");label.color.set(new Color(1,.7F,.2F,1));label.background.set(new Color(.1F,.25F,.3F,1));label.transform.get().scale.set(.4F);forms[3]=label;
        Map<BlockPos,IBlockState> blocks=new LinkedHashMap<>();
        blocks.put(BlockPos.ORIGIN,Blocks.GRASS.getDefaultState());blocks.put(new BlockPos(1,0,0),Blocks.WATER.getDefaultState());blocks.put(new BlockPos(0,1,0),Blocks.CHEST.getDefaultState());
        String id=StructureManager.nextPreviewId();StructureManager.setPreview(StructureRenderData.create(id,new Vec3i(2,2,1),blocks,Collections.<BlockPos,NBTTagCompound>emptyMap()));
        StructureForm structure=new StructureForm();structure.structure.set(id);structure.transform.get().scale.set(.65F);forms[4]=structure;
        FramebufferForm fb=new FramebufferForm();fb.width.set(256);fb.height.set(128);fb.scale.set(.65F);
        LabelForm child=new LabelForm();child.text.set("BBS FS");child.background.set(new Color(.7F,.15F,.1F,1));child.transform.get().scale.set(.4F);
        BodyPart part=new BodyPart("text");part.setForm(child);fb.parts.addBodyPart(part);forms[5]=fb;
        MobForm pig=new MobForm();pig.mobID.set("minecraft:pig");pig.transform.get().scale.set(.6F);forms[6]=pig;
        ModelForm cem=new ModelForm();cem.model.set("cem/cow");cem.transform.get().scale.set(.5F);forms[7]=cem;
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event)
    {
        if(active!=this)return;
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null)return;
        boolean shader=OptiFineShaders.isWorldPass();worldFrames++;if(shader)shaderFrames++;
        StubEntity entity=new StubEntity();entity.setWorld(mc.world);
        int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        java.nio.FloatBuffer modelView=org.lwjgl.BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,modelView);
        boolean renderingWorld=mchorse.bbs_mod.client.BBSRendering.renderingWorld;
        /* Production ClientProxy.scene supplies an identity GL view to BBS models: their
         * context stack already contains the complete camera-space transform. */
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
        mchorse.bbs_mod.client.BBSRendering.renderingWorld=true;
        try
        {
        for(int i=0;i<forms.length;i++)
        {
            if(i==7&&fixture==null)continue;
            int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int query=GL15.glGenQueries();GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED,query);
            try
            {
                MatrixStack stack=new MatrixStack();stack.peek().getPositionMatrix().translation((i%4-1.5F)*1.65F,1.3F-i/4*1.7F,-8F).rotateX(.18F).rotateY(.3F);
                FormRenderingContext context=new FormRenderingContext().set(FormRenderType.ENTITY,entity,stack,0x00f000f0,i==7?cemOverlay:10<<16,event.getPartialTicks()).camera(mc.player);
                Form current=forms[i];
                if(i==7&&frames>0&&cemDraw==null&&firstModel instanceof mchorse.bbs_mod.cubic.ModelInstance)
                    cemDraw=CemDrawDiagnostics.render((mchorse.bbs_mod.cubic.ModelInstance)firstModel,()->FormUtilsClient.getRenderer(current).render(context));
                else FormUtilsClient.getRenderer(current).render(context);
            }
            catch(Throwable e){errors.add(names[i]+": "+e);}
            finally{GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);samples[i]=GL15.glGetQueryObjecti(query,GL15.GL_QUERY_RESULT);GL15.glDeleteQueries(query);}
            restored&=fbo==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&program==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                &&vao==GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)&&buffer==GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        }
        int gl=GL11.glGetError();if(gl!=0)errors.add("GL "+gl);frames++;
        }
        finally
        {
            mchorse.bbs_mod.client.BBSRendering.renderingWorld=renderingWorld;
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glLoadMatrix(modelView);GlStateManager.matrixMode(matrixMode);
        }
    }

    @SuppressWarnings("unchecked") private static List<IResourcePack> defaults()throws Exception
    {
        Minecraft mc=Minecraft.getMinecraft();
        for(Field field:Minecraft.class.getDeclaredFields())if(List.class.isAssignableFrom(field.getType()))
        {
            field.setAccessible(true);Object value=field.get(mc);
            if(value instanceof List&&((List<?>)value).contains(mc.defaultResourcePack))return (List<IResourcePack>)value;
        }
        throw new IllegalStateException("Native default resource pack list not found");
    }

    private static void enableCem()throws Exception
    {
        if(fixture!=null)return;
        Minecraft mc=Minecraft.getMinecraft();beforeVersion=BBSResources.getAssetsVersion();
        folder=Files.createTempDirectory(mc.gameDir.toPath(),".aihelper-cem-");
        write("pack.mcmeta","{\"pack\":{\"pack_format\":3,\"description\":\"BBS CEM runtime QA fixture\"}}");
        write("assets/minecraft/optifine/cem/cow.jem","{\"textureSize\":[64,32],\"models\":[{\"part\":\"body\",\"id\":\"body\",\"invertAxis\":\"xy\",\"boxes\":[{\"textureOffset\":[0,0],\"coordinates\":[-6,-8,-12,12,16,24]}]}]}");
        Path png=folder.resolve("assets/minecraft/textures/entity/cow/cow.png");Files.createDirectories(png.getParent());
        BufferedImage image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<64;x++)image.setRGB(x,y,((x/4+y/4)&1)==0?0xffdd9933:0xff442255);
        ImageIO.write(image,"png",png.toFile());fixture=new FolderResourcePack(folder.toFile());defaults().add(fixture);
        mc.refreshResources();firstModel=BBSModClient.getModels().loadModel("cem/cow");
    }
    private static void write(String path,String value)throws Exception
    {Path target=folder.resolve(path);Files.createDirectories(target.getParent());Files.write(target,value.getBytes(StandardCharsets.UTF_8));}
    private static void disableCem()throws Exception
    {
        if(fixture==null)return;
        defaults().remove(fixture);fixture=null;Minecraft.getMinecraft().refreshResources();
        try(java.util.stream.Stream<Path> paths=Files.walk(folder))
        {for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}
        folder=null;firstModel=null;
    }

    public static JsonObject handle(JsonObject request)throws Exception
    {
        String action=request.has("action")?request.get("action").getAsString():"state";
        if(action.equals("pbr-start")||action.equals("pbr-state"))
        {JsonObject pbr=new JsonObject();pbr.addProperty("action",action.equals("pbr-start")?"start":"state");return OptiFinePbrProbe.handle(pbr);}
        if(action.equals("cem-enable"))enableCem();
        if(action.equals("cem-reload"))
        {Minecraft.getMinecraft().refreshResources();Object next=BBSModClient.getModels().loadModel("cem/cow");modelReplaced=next!=null&&firstModel!=next;firstModel=next;}
        if(action.equals("cem-disable"))disableCem();
        if(action.equals("start")&&active==null){active=new ShaderWorldFormsProbe();MinecraftForge.EVENT_BUS.register(active);}
        if(action.equals("cem-material")&&active!=null)
        {
            ModelForm model=(ModelForm)active.forms[7];mchorse.bbs_mod.forms.forms.utils.FormMaterial material=model.materials.getOrCreate("");
            if(request.has("smoothness"))material.smoothness.set(request.get("smoothness").getAsFloat());
            if(request.has("metallic"))material.metallic.set(request.get("metallic").getAsFloat());
            if(request.has("emission"))material.pixelEmission.set(request.get("emission").getAsFloat());
            if(request.has("relief"))material.relief.set(request.get("relief").getAsFloat());
            if(request.has("overlay"))
            {
                JsonArray color=request.getAsJsonArray("overlay");
                model.overlayColor.set(new Color(color.get(0).getAsFloat(),color.get(1).getAsFloat(),color.get(2).getAsFloat(),color.get(3).getAsFloat()));
            }
            if(request.has("hurt"))active.cemOverlay=(request.get("hurt").getAsBoolean()?3:10)<<16;
            active.cemDraw=null;
        }
        if(action.equals("stop")&&active!=null){MinecraftForge.EVENT_BUS.unregister(active);active=null;StructureManager.setPreview(null);}
        JsonObject out=new JsonObject();out.addProperty("ok",true);out.addProperty("active",active!=null);out.addProperty("cemEnabled",fixture!=null);
        out.addProperty("cemLifecycleInstalled",CemResourceLifecycle.getPack()!=null);out.addProperty("assetsVersion",BBSResources.getAssetsVersion());out.addProperty("versionBeforeEnable",beforeVersion);
        out.addProperty("cemCatalog",CemResourceLifecycle.getPack()!=null&&CemResourceLifecycle.getPack().hasAsset(Link.assets("models/cem/cow/cow.jem")));
        out.addProperty("cemModelLoaded",firstModel!=null);out.addProperty("cemModelReplacedOnReload",modelReplaced);
        if(active!=null)
        {
            out.addProperty("frames",active.frames);out.addProperty("worldFrames",active.worldFrames);out.addProperty("shaderFrames",active.shaderFrames);out.addProperty("stateRestored",active.restored);
            JsonArray counts=new JsonArray(),names=new JsonArray(),errors=new JsonArray();for(int value:active.samples)counts.add(value);for(String name:active.names)names.add(name);for(String error:active.errors)errors.add(error);
            out.add("samples",counts);out.add("names",names);out.add("errors",errors);
            if(active.cemDraw!=null)out.add("cemDraw",active.cemDraw);
        }
        return out;
    }
}
