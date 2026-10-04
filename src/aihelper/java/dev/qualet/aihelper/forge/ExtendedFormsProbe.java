package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.renderers.utils.FormPreviewFit;
import mchorse.bbs_mod.forms.structure.*;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.forge.studio.NativeFormRenderer;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.ui.framework.*;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.util.*;

/** Actual renderer/picker/serialization fixtures. No player input, world edits or fake renderers. */
public final class ExtendedFormsProbe extends UIBaseMenu
{
    private final Form[] forms=new Form[12];
    private final String[] names={"Block / grass","Block / water","Item / diamond sword","Label / Cyrillic","Structure / fluids + TESR","Spline / repeated forms","Framebuffer / nested label","Framebuffer / nested buffers","Mob / pig","Player / wide","Player / slim","Player / posed bones + attachment"};
    private final JsonArray errors=new JsonArray();
    private final int[] pixels=new int[12];
    private int frames;
    private boolean stateRestored=true;
    private final boolean freeze=BBSSettings.freezeModels.get();
    private final StubEntity entity=new StubEntity();
    private final StructureForm structure=new StructureForm();
    private final TrailForm trail=new TrailForm();
    private final VideoForm video=new VideoForm();
    private boolean videoPreview;
    private java.io.File videoFixture;
    private static JsonObject videoCleanup;
    private final Map<Object,Object> closingVideoPlayers=new IdentityHashMap<>();

    public ExtendedFormsProbe()
    {
        BBSSettings.freezeModels.set(true);entity.setWorld(Minecraft.getMinecraft().world);
        BlockForm block=new BlockForm();block.blockState.set(Blocks.GRASS.getDefaultState());forms[0]=block;
        BlockForm fluid=new BlockForm();fluid.blockState.set(Blocks.WATER.getDefaultState());forms[1]=fluid;
        ItemForm item=new ItemForm();item.stack.set(new ItemStack(Items.DIAMOND_SWORD));forms[2]=item;
        LabelForm label=new LabelForm();label.text.set("Вемпи: Ёж §cBBS§r FS");label.max.set(70);label.shadowColor.set(new Color(0,0,0,.8F));label.background.set(new Color(.08F,.2F,.35F,.8F));forms[3]=label;
        Map<BlockPos,IBlockState> blocks=new LinkedHashMap<>();Map<BlockPos,NBTTagCompound> tiles=new LinkedHashMap<>();
        for(int x=0;x<4;x++)for(int z=0;z<3;z++)blocks.put(new BlockPos(x,0,z),Blocks.GRASS.getDefaultState());
        blocks.put(new BlockPos(0,1,0),Blocks.GLASS.getDefaultState());blocks.put(new BlockPos(1,1,0),Blocks.WATER.getDefaultState());
        blocks.put(new BlockPos(2,1,0),Blocks.CHEST.getDefaultState());blocks.put(new BlockPos(3,1,0),Blocks.STANDING_SIGN.getDefaultState());
        NBTTagCompound sign=new NBTTagCompound();sign.setString("id","minecraft:sign");for(int n=1;n<=4;n++)sign.setString("Text"+n,"{\"text\":\""+(n==1?"BBS FS":"")+"\"}");tiles.put(new BlockPos(3,1,0),sign);
        String id=StructureManager.nextPreviewId();StructureManager.setPreview(StructureRenderData.create(id,new Vec3i(4,3,3),blocks,tiles));structure.structure.set(id);forms[4]=structure;
        SplineForm spline=new SplineForm();spline.repeatEnabled.set(true);spline.repeatCount.set(4);BlockForm repeat=new BlockForm();repeat.blockState.set(Blocks.GOLD_BLOCK.getDefaultState());repeat.transform.get().scale.set(.3F);BodyPart part=new BodyPart("repeated");part.setForm(repeat);spline.parts.addBodyPart(part);forms[5]=spline;
        FramebufferForm framebuffer=new FramebufferForm();framebuffer.width.set(512);framebuffer.height.set(256);LabelForm child=new LabelForm();child.text.set("BBS Ёж");child.background.set(new Color(.1F,.5F,.25F,1));child.transform.get().scale.set(.45F);BodyPart text=new BodyPart("label");text.setForm(child);framebuffer.parts.addBodyPart(text);forms[6]=framebuffer;
        FramebufferForm outer=new FramebufferForm();outer.scale.set(1F);BodyPart nested=new BodyPart("nested");nested.setForm(FormUtils.copy(framebuffer));outer.parts.addBodyPart(nested);forms[7]=outer;
        MobForm pig=new MobForm();pig.mobID.set("minecraft:pig");forms[8]=pig;
        MobForm wide=new MobForm();wide.mobID.set("minecraft:player");forms[9]=wide;
        MobForm slim=new MobForm();slim.mobID.set("minecraft:player");slim.slim.set(true);forms[10]=slim;
        MobForm posed=new MobForm();posed.mobID.set("minecraft:player");posed.pose.get().getOrCreate("head").rotate.y=.5F;posed.pose.get().getOrCreate("right_arm").rotate.z=-.8F;
        posed.pose.get().getOrCreate("left_arm").scale.set(1.3F,.6F,1.2F);
        BlockForm hat=new BlockForm();hat.blockState.set(Blocks.GOLD_BLOCK.getDefaultState());hat.transform.get().scale.set(.35F);
        BodyPart attached=new BodyPart("hat");attached.bone.set("head");attached.transform.get().translate.y=.3F;attached.setForm(hat);posed.parts.addBodyPart(attached);forms[11]=posed;
    }
    @Override public boolean canPause(){return false;}
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(0,0,width,height,0xff171b22);render.batcher.text("BBS FS 1.12.2 / native form parity fixtures",8,6,-1);
        int cw=width/4,ch=(height-24)/3;
        for(int i=0;i<forms.length;i++)
        {
            int x=i%4*cw,y=24+i/4*ch;render.batcher.box(x+2,y,x+cw-2,y+ch-2,0xff07090c);render.batcher.text(names[i],x+5,y+3,-1);
        }
        render.batcher.flush();GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        for(int i=0;i<forms.length;i++)
        {
            int x=i%4*cw+6,y=24+i/4*ch+18,w=cw-12,h=ch-25;
            int before=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            IntBuffer viewport=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
            boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);IntBuffer scissorBox=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_SCISSOR_BOX,scissorBox);
            try(NativeFormDraw.State state=new NativeFormDraw.State())
            {
                float gui=BBSModClient.getGUIScale();GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(Math.round(x*gui),Minecraft.getMinecraft().displayHeight-Math.round((y+h)*gui),Math.round(w*gui),Math.round(h*gui));
                GlStateManager.depthFunc(GL11.GL_LEQUAL);GlStateManager.enableDepth();
                FormRenderer<?> renderer=FormUtilsClient.getRenderer(forms[i]);if(renderer==null)throw new IllegalStateException("Unregistered "+forms[i].getClass());
                Matrix4f stock=NativeFormRenderer.getUIMatrix(context,x,y,x+w,y+h),fit=FormPreviewFit.frame(renderer,stock,x,y,x+w,y+h,context.getTransition());
                if(forms[i] instanceof LabelForm||forms[i] instanceof FramebufferForm)
                {
                    stock.rotateY((float)Math.PI);
                    fit=FormPreviewFit.frame(renderer,stock,x,y,x+w,y+h,context.getTransition());
                }
                MatrixStack stack=new MatrixStack();stack.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix()).mul(context.batcher.getContext().getMatrices().peek().getPositionMatrix()).mul(fit==null?stock:fit);
                FormRenderingContext rendering=new FormRenderingContext().set(FormRenderType.ENTITY,entity,stack,0x00f000f0,10<<16,context.getTransition()).inUI();
                if(videoPreview&&i==1)renderer.renderPreview(context,x,y,x+w,y+h);else renderer.render(rendering);
                pixels[i]=countPixels(x,y,w,h);
            }
            catch(Exception e){errors.add(forms[i].getClass().getSimpleName()+": "+e);}
            finally{GL11.glScissor(scissorBox.get(0),scissorBox.get(1),scissorBox.get(2),scissorBox.get(3));if(!scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);}
            IntBuffer after=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,after);
            stateRestored&=before==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&program==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            for(int j=0;j<4;j++)stateRestored&=viewport.get(j)==after.get(j);
        }
        int gl=GL11.glGetError();if(gl!=0)errors.add("GL "+gl);frames++;
    }
    private static int countPixels(int x,int y,int w,int h)
    {
        float scale=BBSModClient.getGUIScale();int pw=Math.max(1,(int)(w*scale)),ph=Math.max(1,(int)(h*scale));
        ByteBuffer bytes=BufferUtils.createByteBuffer(pw*ph*4);int pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);
            GL11.glReadPixels(Math.round(x*scale),Minecraft.getMinecraft().displayHeight-Math.round((y+h)*scale),pw,ph,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
            int count=0;for(int n=0;n<bytes.limit();n+=4)if((bytes.get(n)&255)>20||(bytes.get(n+1)&255)>20||(bytes.get(n+2)&255)>20)count++;return count;
        }
        finally{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);}
    }
    private JsonObject describe()
    {
        JsonObject out=new JsonObject();out.addProperty("ok",true);out.addProperty("frames",frames);out.addProperty("stateRestored",stateRestored);out.add("errors",errors);
        JsonArray values=new JsonArray(),roundtrip=new JsonArray();for(int i=0;i<forms.length;i++){values.add(pixels[i]);Form copy=FormUtils.fromData(FormUtils.toData(forms[i]));roundtrip.add(copy!=null&&copy.getClass()==forms[i].getClass());}
        out.add("visiblePixels",values);out.add("roundtrip",roundtrip);out.addProperty("structureVertices",((StructureFormRenderer)FormUtilsClient.getRenderer(structure)).getBakedVertexCount());
        if(videoCleanup!=null)out.add("videoCleanup",videoCleanup);
        out.addProperty("trailRenderer",FormUtilsClient.getRenderer(trail)!=null);out.addProperty("videoRenderer",FormUtilsClient.getRenderer(video)!=null);return out;
    }
    private JsonArray checkPicking()
    {
        JsonArray result=new JsonArray();
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        FloatBuffer clear=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE,clear);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        mchorse.bbs_mod.graphics.FramebufferPool pool=BBSModClient.getFramebuffers().getFormFramebuffers();
        mchorse.bbs_mod.graphics.Framebuffer target=null;
        try(NativeFormDraw.State state=new NativeFormDraw.State())
        {
            target=pool.get(256,256);target.apply();GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,256,256,0,-500,500);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
            try
            {
                for(Form form:forms)
                {
                    GlStateManager.depthMask(true);GlStateManager.clearColor(0,0,0,0);target.clear();GlStateManager.depthFunc(GL11.GL_LEQUAL);
                    FormRenderer<?> renderer=FormUtilsClient.getRenderer(form);
                    mchorse.bbs_mod.utils.AABB bounds=renderer.getPreviewBounds();
                    MatrixStack matrix=new MatrixStack();matrix.peek().getPositionMatrix().translation(128,150,0).scale(45,-45,45).rotateX(.25F).rotateY(.4F);
                    StencilMap map=new StencilMap();map.setup();
                    renderer.render(new FormRenderingContext().set(FormRenderType.ENTITY,entity,matrix,0x00f000f0,10<<16,0).inUI().stencilMap(map));
                    ByteBuffer bytes=BufferUtils.createByteBuffer(256*256*4);GL11.glReadPixels(0,0,256,256,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
                    int count=0,unknown=0;Set<String> bones=new TreeSet<>();
                    for(int p=0;p<bytes.limit();p+=4)
                    {
                        int id=(bytes.get(p)&255)|(bytes.get(p+1)&255)<<8|(bytes.get(p+2)&255)<<16;
                        if((bytes.get(p+3)&255)>127&&id>0)
                        {if(map.indexMap.containsKey(id)&&map.indexMap.get(id).a!=null){count++;if(!map.indexMap.get(id).b.isEmpty())bones.add(map.indexMap.get(id).b);}else unknown++;}
                    }
                    JsonObject entry=new JsonObject();entry.addProperty("form",form.getClass().getSimpleName());entry.addProperty("pixels",count);entry.addProperty("unknownPixels",unknown);JsonArray boneNames=new JsonArray();for(String bone:bones)boneNames.add(bone);entry.add("bones",boneNames);result.add(entry);
                }
            }
            finally{GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();}
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
            GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
            if(target!=null)pool.release(target);
        }
        return result;
    }
    public static JsonObject handle(JsonObject request)
    {
        if(request.has("wand"))
        {
            try{return StructureWandProbe.handle(request.get("wand").getAsString());}
            catch(Exception e){throw new IllegalStateException("Structure wand probe failed",e);}
        }
        if(request.has("open")&&request.get("open").getAsBoolean())UIScreen.open(new ExtendedFormsProbe());
        if(!(UIScreen.getCurrentMenu() instanceof ExtendedFormsProbe))throw new IllegalStateException("Open forms probe first");
        ExtendedFormsProbe probe=(ExtendedFormsProbe)UIScreen.getCurrentMenu();
        if(request.has("mutate")){probe.structure.origin.get().x+=1;((LabelForm)probe.forms[3]).text.set("Изменено §a✓");}
        if(request.has("invalidate"))StructureManager.invalidate();
        if(request.has("video"))
        {
            probe.video.video.set(Link.create(request.get("video").getAsString()));probe.forms[0]=probe.video;probe.forms[1]=probe.video;probe.videoPreview=true;
            probe.names[0]="Video / entity clock";probe.names[1]="Video / frozen palette preview";
        }
        if(request.has("videoAge"))probe.entity.setAge(request.get("videoAge").getAsInt());
        if(request.has("videoOffset"))probe.video.videoOffset.set(request.get("videoOffset").getAsFloat());
        if(request.has("videoLoop"))probe.video.loop.set(request.get("videoLoop").getAsBoolean());
        JsonObject result=probe.describe();
        if(request.has("picking"))result.add("picking",probe.checkPicking());
        if(request.has("framebuffers"))result.add("framebuffers",probe.framebuffers());
        if(request.has("mobMatrices"))result.add("mobMatrices",probe.mobMatrices());
        if(request.has("videoFixture"))
        {
            probe.videoFixture=new java.io.File(mchorse.bbs_mod.BBSMod.getAssetsPath("videos"),"aihelper_"+UUID.randomUUID().toString().replace("-","")+".mp4");
            result.addProperty("videoFile",probe.videoFixture.getAbsolutePath());result.addProperty("videoLink",Link.assets("videos/"+probe.videoFixture.getName()).toString());
        }
        if(request.has("videoState"))result.add("videoState",probe.videoState());
        return result;
    }
    private JsonObject videoState()
    {
        JsonObject result=new JsonObject();
        try(NativeFormDraw.State restore=new NativeFormDraw.State())
        {
            VideoFormRenderer renderer=(VideoFormRenderer)FormUtilsClient.getRenderer(video);
            java.lang.reflect.Field preview=VideoFormRenderer.class.getDeclaredField("uiPlayerKey");preview.setAccessible(true);
            Object[] owners={renderer,preview.get(renderer)};
            for(int i=0;i<owners.length;i++)
            {
                mchorse.bbs_mod.video.VideoPlayer player=BBSModClient.getVideos().getPlayer(owners[i],video.video.get());
                JsonObject value=new JsonObject();value.addProperty("valid",player!=null&&player.isValid());
                if(player!=null)
                {
                    java.lang.reflect.Field frame=player.getClass().getDeclaredField("currentFrame"),texture=player.getClass().getDeclaredField("texture");frame.setAccessible(true);texture.setAccessible(true);
                    value.addProperty("frame",frame.getInt(player));value.addProperty("duration",player.getDuration());
                    mchorse.bbs_mod.graphics.texture.Texture pixels=(mchorse.bbs_mod.graphics.texture.Texture)texture.get(player);
                    if(pixels!=null)
                    {
                        value.addProperty("width",pixels.width);value.addProperty("height",pixels.height);pixels.bind(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                        ByteBuffer data=BufferUtils.createByteBuffer(pixels.width*pixels.height*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
                        long red=0,green=0,blue=0;for(int p=0;p<data.limit();p+=4){red+=data.get(p)&255;green+=data.get(p+1)&255;blue+=data.get(p+2)&255;}
                        int count=pixels.width*pixels.height;JsonArray rgb=new JsonArray();rgb.add(red/(float)count);rgb.add(green/(float)count);rgb.add(blue/(float)count);value.add("rgb",rgb);
                    }
                }
                result.add(i==0?"world":"preview",value);
            }
        }
        catch(Exception e){throw new IllegalStateException("Cannot inspect video form fixture",e);}
        return result;
    }
    private JsonArray mobMatrices()
    {
        JsonArray out=new JsonArray();
        for(int n=8;n<12;n++)
        {
            FormRenderer<?> renderer=FormUtilsClient.getRenderer(forms[n]);
            mchorse.bbs_mod.forms.renderers.utils.MatrixCache evaluated=renderer.collectMatrices(entity,0);
            JsonObject mob=new JsonObject();mob.addProperty("id",((MobForm)forms[n]).mobID.get());mob.addProperty("slim",((MobForm)forms[n]).slim.get());
            mob.addProperty("head",evaluated.has("head"));mob.addProperty("matrixCount",evaluated.keySet().size());
            JsonArray bones=new JsonArray();for(String bone:renderer.getBones())bones.add(bone);mob.add("bones",bones);
            if(n==11)
            {
                String child=forms[n].parts.getAllTyped().get(0).getId();mob.addProperty("attachment",evaluated.has(child));
                mob.addProperty("posedHead",!evaluated.get("head").matrix().equals(FormUtilsClient.getRenderer(forms[9]).collectMatrices(entity,0).get("head").matrix(),.0001F));
            }
            out.add(mob);
        }
        return out;
    }
    private JsonArray framebuffers()
    {
        JsonArray out=new JsonArray();
        try(NativeFormDraw.State state=new NativeFormDraw.State())
        {
            java.lang.reflect.Field idle=mchorse.bbs_mod.graphics.FramebufferPool.class.getDeclaredField("idle");idle.setAccessible(true);
            for(Object value:(java.util.Collection<?>)idle.get(BBSModClient.getFramebuffers().getFormFramebuffers()))
            {
                mchorse.bbs_mod.graphics.Framebuffer f=(mchorse.bbs_mod.graphics.Framebuffer)value;
                mchorse.bbs_mod.graphics.texture.Texture texture=f.getMainTexture();texture.bind();
                ByteBuffer data=BufferUtils.createByteBuffer(texture.width*texture.height*4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
                int alpha=0,rgb=0;for(int p=0;p<data.limit();p+=4){if((data.get(p+3)&255)>127)alpha++;if((data.get(p)&255)>20||(data.get(p+1)&255)>20||(data.get(p+2)&255)>20)rgb++;}
                JsonObject t=new JsonObject();t.addProperty("id",f.id);t.addProperty("width",texture.width);t.addProperty("height",texture.height);t.addProperty("alphaPixels",alpha);t.addProperty("rgbPixels",rgb);out.add(t);
            }
        }
        catch(Exception e){throw new IllegalStateException(e);}
        return out;
    }
    @Override public void onClose(UIBaseMenu next)
    {
        if(videoFixture!=null)
        {
            videoCleanup=new JsonObject();videoCleanup.addProperty("file",videoFixture.getAbsolutePath());
            videoCleanup.add("before",videoOwners());
            ((VideoFormRenderer)FormUtilsClient.getRenderer(video)).releaseVideoPlayers();
            videoCleanup.add("after",videoOwners());
            java.io.IOException failure=null;int retries=0;
            for(;retries<50;retries++)
            {
                try{java.nio.file.Files.deleteIfExists(videoFixture.toPath());failure=null;break;}
                catch(java.io.IOException e)
                {
                    failure=e;
                    /* Both decoder workers/processes have exited (snapshot above). Windows
                     * can still briefly retain a sharing lock after process teardown. */
                    try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
                }
            }
            videoCleanup.addProperty("sharingRetries",retries);
            if(failure!=null){videoCleanup.addProperty("error",failure.toString());mchorse.bbs_mod.BBSMod.LOGGER.warn("Cannot remove video fixture "+videoFixture,failure);}
            videoCleanup.addProperty("deleted",!videoFixture.exists());
        }
        BBSSettings.freezeModels.set(freeze);StructureManager.setPreview(null);super.onClose(next);
    }
    private JsonArray videoOwners()
    {
        JsonArray values=new JsonArray();
        try
        {
            java.lang.reflect.Field owners=BBSModClient.getVideos().getClass().getDeclaredField("ownedPlayers");owners.setAccessible(true);
            Map<?,?> registered=(Map<?,?>)owners.get(BBSModClient.getVideos());
            for(Map.Entry<?,?> item:registered.entrySet())
            {
                Object entry=item.getValue();java.lang.reflect.Field p=entry.getClass().getDeclaredField("player"),l=entry.getClass().getDeclaredField("link");p.setAccessible(true);l.setAccessible(true);
                if(!Objects.equals(l.get(entry),video.video.get()))continue;
                closingVideoPlayers.put(item.getKey(),p.get(entry));
            }
            for(Map.Entry<Object,Object> item:closingVideoPlayers.entrySet())
            {
                Object player=item.getValue();JsonObject row=new JsonObject();row.addProperty("owner",item.getKey().getClass().getSimpleName()+"@"+System.identityHashCode(item.getKey()));row.addProperty("registered",registered.containsKey(item.getKey()));
                if(player!=null)
                {
                    for(String name:new String[]{"disposed","seeking","process","seekThread"})
                    {
                        java.lang.reflect.Field field=player.getClass().getDeclaredField(name);field.setAccessible(true);Object value=field.get(player);
                        row.addProperty(name,value instanceof Process?((Process)value).isAlive():value instanceof Thread?((Thread)value).isAlive():value instanceof Boolean?(Boolean)value:false);
                    }
                }
                values.add(row);
            }
        }
        catch(Exception failure){throw new IllegalStateException(failure);}
        return values;
    }
}
