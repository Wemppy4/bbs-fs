package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.*;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIChoiceButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.*;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.*;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.lang.reflect.Field;

/** Real transform widgets, gizmo geometry/picking and gesture dispatch in an isolated menu. */
public final class OriginalGizmoProbe extends UIBaseMenu
{
    private static OriginalGizmoProbe last;
    private final StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private final StencilMap map = new StencilMap();
    private final Viewport viewport = new Viewport();
    private final ModelForm form = new ModelForm();
    private final Transform transform = form.transform.get();
    private final Matrix4f parent = new Matrix4f();
    private final UIPropTransform editor;
    private final UIElement sphereSetting = new UIElement();
    private final int oldSphereMode = BBSSettings.rotate3dSphereMode.get();
    private final boolean oldSimple = BBSSettings.simpleKeyboardTransform.get();
    private int pendingNativeKey;
    private ByteBuffer savedKeyboard, injectedKeyboard;
    private Object keyboardEvent;
    private final Map<Field,Object> savedKeyEvent = new LinkedHashMap<>();
    private JsonObject nativeKey = new JsonObject();
    private Gizmo.HandleMask mask = Gizmo.HandleMask.ALL;
    private String mode = "move";
    private int frames, glError, framebufferStatus, begins, changes, ends, picks, releases;
    private boolean stateRestored = true;
    private final float oldSmoothness = BBSSettings.editorCameraSmoothness.get();
    private final boolean oldFreeze = BBSSettings.freezeModels.get();
    private final int oldSpace = BBSSettings.transformSpace.get();
    private final boolean oldGizmos = BBSSettings.gizmos.get();
    private final boolean[] oldElements = {BBSSettings.gizmoShowTranslate.get(), BBSSettings.gizmoShowScale.get(),
        BBSSettings.gizmoShowRotate.get(), BBSSettings.gizmoShowViewRotate.get(), BBSSettings.gizmoShowSphere.get()};

    private OriginalGizmoProbe()
    {
        BBSSettings.editorCameraSmoothness.set(0F);
        BBSSettings.freezeModels.set(true);
        BBSSettings.gizmos.set(true);
        BBSSettings.gizmoShowTranslate.set(true); BBSSettings.gizmoShowScale.set(true);
        BBSSettings.gizmoShowRotate.set(true); BBSSettings.gizmoShowViewRotate.set(true); BBSSettings.gizmoShowSphere.set(true);
        TransformSpace.PARENT.remember();
        this.editor = new UIPropTransform().callbacks(() -> begins++, () -> { changes++; form.bumpPoseVersion(); }, () -> ends++);
        this.editor.setTransform(transform);
        this.editor.enableHotkeys(() -> viewport.area.isInside(this.context));
        this.editor.hotkeyDrag(this::drag);
        form.model.set("helper_drone");
        viewport.relative(this.main).xy(16, 48).w(1F, -272).h(1F, -64);
        viewport.setPosition(0F, .5F, 0F); viewport.setDistance(3F); viewport.setRotation(25F, 18F);
        editor.relative(this.main).x(1F,-240).y(56).w(224);
        this.main.add(viewport, editor);
        BBSSettings.rotate3dSphereMode.set(0);
        BBSSettings.simpleKeyboardTransform.set(false);
        sphereSetting.relative(this.main).x(1F,-240).y(172).w(224).column(4).vertical().stretch();
        sphereSetting.add(UIValueMap.create(BBSSettings.rotate3dSphereMode, sphereSetting).toArray(new UIElement[0]));
        this.main.add(sphereSetting);
        MinecraftForge.EVENT_BUS.register(this);
        this.setMode("move");
    }

    private void setMode(String mode)
    {
        this.mode = mode;
        EnumSet<Gizmo.Op> ops;
        switch (mode)
        {
            case "move": ops=EnumSet.of(Gizmo.Op.MOVE,Gizmo.Op.SCREEN); break;
            case "scale": ops=EnumSet.of(Gizmo.Op.SCALE,Gizmo.Op.SCALE_ALL); break;
            case "rotate": ops=EnumSet.of(Gizmo.Op.ROTATE,Gizmo.Op.VIEW,Gizmo.Op.TRACKBALL); break;
            case "all": ops=EnumSet.allOf(Gizmo.Op.class); break;
            default: throw new IllegalArgumentException("Unknown gizmo fixture mode: " + mode);
        }
        mask=Gizmo.HandleMask.of(ops,EnumSet.allOf(Axis.class));
    }

    private GizmoDrag drag()
    {
        GizmoDrag drag=GizmoDrag.fromRenderedGizmo(viewport.camera,viewport.area);
        if (drag!=null)
        {
            Matrix4f own=new Matrix4f(parent).mul(transform.createMatrix());
            drag.setGlobalAxes(viewport.getSceneAxes()).setFrameAxes(own,parent);
            drag.setJacobian(parent.get3x3(new Matrix3f()));
        }
        return drag;
    }

    @Override public boolean canPause() { return false; }
    @Override protected void releaseTransform() { releases++; super.releaseTransform(); }
    @Override public void onClose(UIBaseMenu next)
    {
        MinecraftForge.EVENT_BUS.unregister(this);
        try { restoreKeyboard(); } catch (Exception e) { throw new IllegalStateException(e); }
        editor.getGesture().reject(); viewport.interaction.stop(); Gizmo.INSTANCE.stop(); Gizmo.INSTANCE.forgetPlacement();
        BBSSettings.editorCameraSmoothness.set(oldSmoothness); BBSSettings.freezeModels.set(oldFreeze);
        BBSSettings.transformSpace.set(oldSpace); BBSSettings.gizmos.set(oldGizmos);
        BBSSettings.gizmoShowTranslate.set(oldElements[0]); BBSSettings.gizmoShowScale.set(oldElements[1]);
        BBSSettings.gizmoShowRotate.set(oldElements[2]); BBSSettings.gizmoShowViewRotate.set(oldElements[3]); BBSSettings.gizmoShowSphere.set(oldElements[4]);
        BBSSettings.rotate3dSphereMode.set(oldSphereMode); BBSSettings.simpleKeyboardTransform.set(oldSimple);
    }

    /** Queue a native key between the 20 Hz input tick and world-render placement reset. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void injectFrameKey(TickEvent.RenderTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.START||pendingNativeKey==0||UIScreen.getCurrentMenu()!=this)return;
        try
        {
            Field read=field(Keyboard.class,"readBuffer"); ByteBuffer buffer=(ByteBuffer)read.get(null);
            if(buffer.hasRemaining())return;
            savedKeyboard=buffer; keyboardEvent=field(Keyboard.class,"current_event").get(null);
            for(Field value:keyboardEvent.getClass().getDeclaredFields())
                if(!java.lang.reflect.Modifier.isStatic(value.getModifiers())){value.setAccessible(true);savedKeyEvent.put(value,value.get(keyboardEvent));}
            nativeKey=new JsonObject(); nativeKey.addProperty("key",pendingNativeKey); nativeKey.addProperty("beforeHasRay",drag()!=null);
            injectedKeyboard=ByteBuffer.allocate(Keyboard.EVENT_SIZE*2).order(buffer.order());
            for(byte pressed:new byte[]{1,0})injectedKeyboard.putInt(pendingNativeKey).put(pressed).putInt(0).putLong(System.nanoTime()).put((byte)0);
            injectedKeyboard.flip(); read.set(null,injectedKeyboard); pendingNativeKey=0;
        }
        catch(Exception failure){nativeKey.addProperty("error",failure.toString());pendingNativeKey=0;}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void inspectFrameKey(TickEvent.RenderTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||injectedKeyboard==null)return;
        nativeKey.addProperty("consumed",!injectedKeyboard.hasRemaining());
        nativeKey.addProperty("strategy",editor.getGesture().getStrategy()==null?"none":editor.getGesture().getStrategy().getClass().getSimpleName());
        nativeKey.addProperty("simple",editor.getGesture().isSimpleKeyboardTransform());
        try {restoreKeyboard();} catch(Exception failure){nativeKey.addProperty("error",failure.toString());}
    }
    private static Field field(Class<?> type,String name)throws Exception {Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private void restoreKeyboard()throws Exception
    {
        if(savedKeyboard!=null)field(Keyboard.class,"readBuffer").set(null,savedKeyboard);
        for(Map.Entry<Field,Object> entry:savedKeyEvent.entrySet())entry.getKey().set(keyboardEvent,entry.getValue());
        savedKeyEvent.clear();savedKeyboard=injectedKeyboard=null;keyboardEvent=null;
    }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(0,0,width,height,0xff14181e);
        render.batcher.text("BBS FS: original Gizmo / " + mode,16,12,-1);
        render.batcher.text("G / R / S, axis keys, numeric input, Enter / Escape",16,28,-1);
        render.batcher.box(viewport.area.x,viewport.area.y,viewport.area.ex(),viewport.area.ey(),0xff222832);
    }

    private final class Viewport extends UIModelRenderer implements GizmoViewport
    {
        final GizmoInteraction interaction = new GizmoInteraction(this);
        @Override public void resize()
        {
            super.resize(); stencil.setup(Link.bbs("gizmo_probe"));
            stencil.resizeGUI(Math.max(1,area.w),Math.max(1,area.h));
        }
        @Override public StencilFormFramebuffer getGizmoStencil() { return stencil; }
        @Override public Matrix4f getGizmoProjection() { return camera.projection; }
        @Override public Area getGizmoArea() { return area; }
        @Override public boolean startGizmo(UIContext context,int id)
        {
            return Gizmo.INSTANCE.start(id,context.mouseX,context.mouseY,editor,drag());
        }
        @Override public void pickGizmoForm(UIContext context,Form picked,String bone) { picks++; }
        @Override public boolean subMouseClicked(UIContext context)
        {
            return area.isInside(context) && interaction.mouseClicked(context) || super.subMouseClicked(context);
        }
        @Override public boolean subMouseReleased(UIContext context)
        {
            return interaction.mouseReleased(context) || super.subMouseReleased(context);
        }
        private void placeGizmo(MatrixStack stack,boolean picking)
        {
            stack.push();
            try
            {
                Matrix4f placement=new Matrix4f(OriginalGizmoProbe.this.parent).translate(transform.translate);
                if (editor.getSpace().placesOnOwnFrame()) placement.rotate(transform.createRotation());
                MatrixStackUtils.multiply(stack,placement);
                Gizmo.INSTANCE.reorientForSpace(stack,editor.getSpace(),camera.view,getSceneAxes());
                if (picking) Gizmo.INSTANCE.renderStencil(stack,mask);
                else Gizmo.INSTANCE.captureVisual(stack,mask);
            }
            finally { stack.pop(); }
        }
        private void renderForm(UIContext context,FormRenderingContext rendering)
        {
            MatrixStack stack=context.batcher.getContext().getMatrices();
            stack.push();
            try { MatrixStackUtils.multiply(stack,OriginalGizmoProbe.this.parent); FormUtilsClient.render(form,rendering); }
            finally { stack.pop(); }
        }
        @Override protected void renderUserModel(UIContext context)
        {
            MatrixStack stack=context.batcher.getContext().getMatrices();
            FormRenderingContext rendering=new FormRenderingContext()
                .set(FormRenderType.PREVIEW,getEntity(),stack,0x00f000f0,10<<16,context.getTransition())
                .camera(camera).modelRenderer(context.getTick());
            renderForm(context,rendering);
            Gizmo.INSTANCE.setViewportHeight(area.h); placeGizmo(stack,false);
            int target=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            IntBuffer savedViewport=BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_VIEWPORT,savedViewport);
            FloatBuffer clear=readMatrix(GL11.GL_COLOR_CLEAR_VALUE);
            boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try
            {
                GL11.glDisable(GL11.GL_SCISSOR_TEST); map.setup(); GlStateManager.clearColor(0,0,0,0); stencil.apply();
                framebufferStatus=GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                renderForm(context,rendering.stencilMap(map));
                placeGizmo(stack,true);
                stencil.pickGUI(context,area,BBSSettings.gizmoHoverTolerance.get(),Gizmo.STENCIL_MAX);
                stencil.unbind(map);
            }
            finally
            {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);
                GlStateManager.viewport(savedViewport.get(0),savedViewport.get(1),savedViewport.get(2),savedViewport.get(3));
                GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
        @Override public void render(UIContext context)
        {
            FloatBuffer projection=readMatrix(GL11.GL_PROJECTION_MATRIX),view=readMatrix(GL11.GL_MODELVIEW_MATRIX);
            int target=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            Gizmo.INSTANCE.forgetPlacement();
            super.render(context);
            interaction.renderGizmo(context); interaction.renderSphereHighlight(context); interaction.renderReadout(context); interaction.update(context);
            stateRestored &= same(projection,readMatrix(GL11.GL_PROJECTION_MATRIX)) && same(view,readMatrix(GL11.GL_MODELVIEW_MATRIX))
                && target==GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) && program==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int error=GL11.glGetError(); if(error!=0)glError=error; frames++;
        }
    }

    private static FloatBuffer readMatrix(int kind) { FloatBuffer b=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(kind,b);return b; }
    private static boolean same(FloatBuffer a,FloatBuffer b) { for(int i=0;i<16;i++)if(a.get(i)!=b.get(i))return false;return true; }
    private static JsonArray vector(Vector3f v) { JsonArray a=new JsonArray();a.add(v.x);a.add(v.y);a.add(v.z);return a; }
    private static JsonObject area(Area a) { JsonObject o=new JsonObject();o.addProperty("x",a.x);o.addProperty("y",a.y);o.addProperty("w",a.w);o.addProperty("h",a.h);return o; }

    /** Samples come from real ID pixels, choosing an interior pixel where possible. */
    private JsonObject scan()
    {
        Texture texture=stencil.getFramebuffer().getMainTexture();
        ByteBuffer data=BufferUtils.createByteBuffer(texture.width*texture.height*4);
        int target=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] parameters={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS};
        int[] values=new int[parameters.length];
        try
        {
            for(int i=0;i<parameters.length;i++){values[i]=GL11.glGetInteger(parameters[i]);GL11.glPixelStorei(parameters[i],i==0?1:0);}
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);stencil.getFramebuffer().bind();
            GL11.glReadPixels(0,0,texture.width,texture.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);
            for(int i=0;i<parameters.length;i++)GL11.glPixelStorei(parameters[i],values[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);
        }
        Map<Integer,int[]> hits=new TreeMap<>();
        for(int y=0;y<texture.height;y++)for(int x=0;x<texture.width;x++)
        {
            int id=idAt(data,x,y,texture.width,texture.height);if(id==0)continue;
            int[] hit=hits.get(id);if(hit==null){hit=new int[]{0,x,y,-1};hits.put(id,hit);}hit[0]++;
            int density=0;
            for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)if(idAt(data,x+dx,y+dy,texture.width,texture.height)==id)density++;
            if(density>hit[3]){hit[1]=x;hit[2]=y;hit[3]=density;}
        }
        JsonObject out=new JsonObject();
        for(Map.Entry<Integer,int[]> entry:hits.entrySet())
        {
            int[] hit=entry.getValue();JsonObject sample=new JsonObject();sample.addProperty("pixels",hit[0]);
            sample.addProperty("x",viewport.area.x+Math.round(hit[1]/BBSModClient.getGUIScale()));
            sample.addProperty("y",viewport.area.y+viewport.area.h-1-Math.round(hit[2]/BBSModClient.getGUIScale()));
            out.add(Integer.toString(entry.getKey()),sample);
        }
        return out;
    }
    private static int idAt(ByteBuffer data,int x,int y,int w,int h)
    {
        if(x<0||y<0||x>=w||y>=h)return 0;int at=(y*w+x)*4;if(data.get(at+3)==0)return 0;
        return (data.get(at)&255)|(data.get(at+1)&255)<<8|(data.get(at+2)&255)<<16;
    }
    private JsonObject snapshot()
    {
        JsonObject out=new JsonObject();TransformGesture gesture=editor.getGesture();
        out.addProperty("ok",true);out.addProperty("frames",frames);out.addProperty("mode",mode);
        out.addProperty("stateRestored",stateRestored);out.addProperty("glError",glError);out.addProperty("framebufferStatus",framebufferStatus);
        out.addProperty("editing",gesture.isEditing());out.addProperty("hotkey",gesture.isHotkeyMode());
        out.addProperty("strategy",gesture.getStrategy()==null?"none":gesture.getStrategy().getClass().getSimpleName());
        out.addProperty("simple",gesture.isSimpleKeyboardTransform());out.add("nativeKey",new com.google.gson.JsonParser().parse(nativeKey.toString()));
        out.addProperty("sphereMode",BBSSettings.rotate3dSphereMode.get());
        out.addProperty("sphereSubtype",BBSSettings.rotate3dSphereMode.getSubtype().name());
        List<UICirculate> buttons=sphereSetting.getChildren(UICirculate.class);
        if(!buttons.isEmpty()){out.add("sphereButton",area(buttons.get(0).area));out.addProperty("sphereLabel",buttons.get(0).getLabel());out.addProperty("sphereLabels",buttons.get(0).getLabels().size());}
        out.addProperty("screenTranslate",gesture.isScreenTranslate());out.addProperty("scaleAll",gesture.isScaleAll());out.addProperty("viewRotate",gesture.isViewRotate());out.addProperty("sphereRotate",gesture.isSphereRotate());
        out.addProperty("op",String.valueOf(gesture.getOp()));out.addProperty("axis",String.valueOf(gesture.getAxis()));out.addProperty("axis2",String.valueOf(gesture.getAxis2()));
        out.addProperty("space",editor.getSpace().name());out.addProperty("rotationMode",transform.rotationMode.name());
        out.addProperty("numeric",gesture.numericDisplay());out.addProperty("numericActive",gesture.isNumericActive());
        out.addProperty("pickedIndex",stencil.getIndex());out.addProperty("sphereHovered",viewport.interaction.isSphereHovered());
        out.addProperty("begins",begins);out.addProperty("changes",changes);out.addProperty("ends",ends);out.addProperty("picks",picks);out.addProperty("releases",releases);
        out.add("translate",vector(transform.translate));out.add("scale",vector(transform.scale));out.add("rotate",vector(transform.getEulerRotation(new Vector3f())));
        Quaternionf q=transform.createRotation();JsonArray quat=new JsonArray();quat.add(q.x);quat.add(q.y);quat.add(q.z);quat.add(q.w);out.add("quat",quat);
        out.add("area",area(viewport.area));
        for(UIChoiceButton<?> button:editor.getChildren(UIChoiceButton.class)) if(button.getValue() instanceof TransformSpace)out.add("spaceButton",area(button.area));
        Vector2f center=new Vector2f();if(Gizmo.INSTANCE.computeScreenCenter(viewport.camera.projection,viewport.area.x,viewport.area.y,viewport.area.w,viewport.area.h,center))
        {JsonArray c=new JsonArray();c.add(center.x);c.add(center.y);out.add("center",c);out.addProperty("radius",Gizmo.INSTANCE.computeScreenRadius(viewport.camera.projection,viewport.area.x,viewport.area.y,viewport.area.w,viewport.area.h));}
        return out;
    }
    public static JsonObject handle(JsonObject request)
    {
        if(request.has("open")&&request.get("open").getAsBoolean())
        {
            if(UIScreen.getCurrentMenu() instanceof OriginalGizmoProbe)Minecraft.getMinecraft().displayGuiScreen(null);
            last=new OriginalGizmoProbe();UIScreen.open(last);
        }
        if(last==null)throw new IllegalStateException("Open the gizmo probe first");
        if(request.has("close")&&request.get("close").getAsBoolean()){Minecraft.getMinecraft().displayGuiScreen(null);JsonObject out=new JsonObject();out.addProperty("ok",true);return out;}
        if(request.has("reset")&&request.get("reset").getAsBoolean())
        {
            last.editor.getGesture().reject();Gizmo.INSTANCE.stop();last.transform.identity();last.parent.identity();last.editor.setTransform(last.transform);last.form.bumpPoseVersion();
        }
        if(request.has("mode"))last.setMode(request.get("mode").getAsString());
        if(request.has("parentYaw")){last.parent.identity().rotateY((float)Math.toRadians(request.get("parentYaw").getAsFloat()));last.form.bumpPoseVersion();}
        if(request.has("simple"))BBSSettings.simpleKeyboardTransform.set(request.get("simple").getAsBoolean());
        if(request.has("nativeKey"))
        {
            if(last.pendingNativeKey!=0||last.injectedKeyboard!=null)throw new IllegalStateException("A native key is pending");
            int code=Keyboard.getKeyIndex(request.get("nativeKey").getAsString().toUpperCase(Locale.ROOT));
            if(code==0)throw new IllegalArgumentException("Unknown native key");
            last.nativeKey=new JsonObject();last.pendingNativeKey=code;
        }
        JsonObject out=last.snapshot();if(request.has("scan")&&request.get("scan").getAsBoolean())out.add("scan",last.scan());return out;
    }
}

