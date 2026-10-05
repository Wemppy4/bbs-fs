package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.FrameOverlays;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIDrawContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Timer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.io.File;
import java.nio.IntBuffer;

/** Native world framebuffer, deterministic export clock and the original held-frame lifecycle. */
public final class BBSRendering
{
    public static final java.util.Set<mchorse.bbs_mod.forge.ModelTileEntity> capturedModelBlocks = new java.util.HashSet<>();
    public static boolean renderingWorld;
    public static volatile boolean canRender;
    private static boolean customSize, capturing;
    private static int width, height, windowWidth, windowHeight;
    private static Framebuffer framebuffer, clientFramebuffer;
    private static Texture texture;
    private static mchorse.bbs_mod.graphics.Framebuffer exportFramebuffer;
    private static Runnable pendingExportResolutionAction;
    private static int pendingExportFrames;
    private static volatile boolean holdingExportFrame;
    private static boolean ownsWorldQueue, worldRenderLast;
    private static int heldFrames;
    private static long lastFrameTime;
    private static float lastFrameDuration;
    private static final IntBuffer viewport=BufferUtils.createIntBuffer(16);
    private BBSRendering() {}

    public static boolean isRenderingWorld(){return renderingWorld;}
    public static boolean isCustomSize(){return customSize;}
    public static boolean canReplaceFramebuffer(){return capturing;}
    public static int getVideoWidth(){return width==0?BBSSettings.videoWidth.get():width;}
    public static int getVideoHeight(){return height==0?BBSSettings.videoHeight.get():height;}
    public static void setVideoResolution(int width,int height)
    {
        if(width<0||height<0||(width==0)!=(height==0))throw new IllegalArgumentException("Invalid export resolution");
        BBSRendering.width=width;BBSRendering.height=height;
    }
    public static int getMotionBlur(double fps,int target)
    {
        if(fps<=0)throw new IllegalArgumentException("Frame rate must be positive");
        int levels=0;while(fps<target){fps*=2;levels++;}return levels;
    }
    public static int getMotionBlur(){return getMotionBlur(BBSSettings.videoFrameRate.get(),getMotionBlurFactor());}
    public static int getMotionBlurFactor(){return getMotionBlurFactor(BBSSettings.videoMotionBlur.get());}
    public static int getMotionBlurFactor(int value){return value==0?0:(int)Math.pow(2,6+value);}
    public static int getVideoFrameRate(){int fps=BBSSettings.videoFrameRate.get();return fps*(1<<getMotionBlur(fps,getMotionBlurFactor()));}
    public static File getVideoFolder()
    {
        File requested=new File(BBSSettings.videoExportPath.get());
        File folder=requested.isDirectory()?requested:new File(BBSMod.getSettingsFolder().getParentFile(),"movies");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("Cannot create export folder "+folder);
        return folder;
    }
    public static void setCustomSize(boolean enabled){setCustomSize(enabled,0,0);}
    public static void setCustomSize(boolean enabled,int w,int h)
    {
        if(w<0||h<0||(w==0)!=(h==0))throw new IllegalArgumentException("Invalid preview resolution");
        int targetWidth=enabled?w:0,targetHeight=enabled?h:0;
        if(!enabled){pendingExportResolutionAction=null;pendingExportFrames=0;holdingExportFrame=false;}
        if(customSize==enabled&&width==targetWidth&&height==targetHeight)return;
        if(capturing)onRenderBeforeScreen();
        customSize=enabled;width=targetWidth;height=targetHeight;
        if(!enabled)resizeExtraFramebuffers();
    }
    public static Texture getTexture()
    {
        if(texture==null){texture=new Texture();texture.setFormat(TextureFormat.RGB_U8);texture.setFilter(GL11.GL_NEAREST);}
        return texture;
    }
    public static Framebuffer getFramebuffer(){return framebuffer;}
    /** Minecraft.getFramebuffer is redirected only inside the captured world/HUD scope. */
    public static Framebuffer renderFramebuffer(Framebuffer original){return capturing?framebuffer:original;}
    public static int capturedScaledWidth(int original){return capturing?new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth():original;}
    public static int capturedScaledHeight(int original){return capturing?new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight():original;}
    public static void resizeExtraFramebuffers()
    {
        mchorse.bbs_mod.graphics.OptiFineShaders.resize();
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.renderGlobal!=null)mc.renderGlobal.createBindEntityOutlineFbs(mc.displayWidth,mc.displayHeight);
        if(mc.entityRenderer!=null)mc.entityRenderer.updateShaderGroupSize(mc.displayWidth,mc.displayHeight);
    }
    private static final java.lang.reflect.Field PAUSED_PARTIAL = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
        Minecraft.class, "renderPartialTicksPaused", "field_193996_ah");

    public static float worldTransition(float transition)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isGamePaused()) return transition;
        try { return PAUSED_PARTIAL.getFloat(mc); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }

    public static void onWorldRenderBegin(float transition)
    {
        if(capturing)throw new IllegalStateException("Nested native world capture");
        renderingWorld=true;Gizmo.INSTANCE.forgetPlacement();capturedModelBlocks.clear();
        if(!customSize)return;
        Minecraft mc=Minecraft.getMinecraft();
        windowWidth=mc.displayWidth;windowHeight=mc.displayHeight;clientFramebuffer=mc.getFramebuffer();
        viewport.clear();GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        int w=getVideoWidth(),h=getVideoHeight(),max=GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if(w<=0||h<=0||w>max||h>max)throw new IllegalArgumentException("Unsupported export resolution "+w+"x"+h+" (GPU max "+max+")");
        boolean resize=framebuffer==null||framebuffer.framebufferWidth!=w||framebuffer.framebufferHeight!=h;
        if(framebuffer==null)framebuffer=new Framebuffer(w,h,true);
        else if(resize)framebuffer.createBindFramebuffer(w,h);
        mc.displayWidth=w;mc.displayHeight=h;capturing=true;
        if(resize)resizeExtraFramebuffers();
        framebuffer.bindFramebuffer(true);
    }
    /** Anaglyph renders two world passes; each must collect its own camera-space geometry. */
    public static void onWorldRenderPassBegin()
    {
        ownsWorldQueue=!FormTranslucentQueue.isActive();
        if(ownsWorldQueue)FormTranslucentQueue.begin();
        worldRenderLast=FormRenderLast.open();
    }
    /** OptiFine renders shadow entities directly, without Forge's RenderWorldLastEvent. */
    public static void onShadowEntities(float transition)
    {
        if (!mchorse.bbs_mod.graphics.OptiFineShaders.isShadowPass()
            || net.minecraftforge.client.MinecraftForgeClient.getRenderPass() != 0) return;
        mchorse.bbs_mod.graphics.WorldRenderContext context = mchorse.bbs_mod.graphics.WorldRenderContext.capture(transition);
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        boolean previous = renderingWorld;
        renderingWorld = true;
        try
        {
            BBSModClient.getFilms().render(context);
            UIBaseMenu menu = UIScreen.getCurrentMenu();
            if (menu instanceof UIDashboard && ((UIDashboard) menu).getPanels().panel instanceof UIFilmPanel)
            {
                UIFilmPanel film = (UIFilmPanel) ((UIDashboard) menu).getPanels().panel;
                if (film.getController().editorController != null) film.getController().editorController.render(context);
            }
        }
        finally
        {
            renderingWorld = previous;
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(mode);
        }
    }
    /** Called under ClientProxy's identity GL view, before the native hand pass. */
    public static void finishWorldForms()
    {
        boolean last=worldRenderLast;worldRenderLast=false;
        try {FormRenderLast.close(last);}
        finally
        {
            boolean owns=ownsWorldQueue;ownsWorldQueue=false;
            if(owns&&FormTranslucentQueue.isActive())FormTranslucentQueue.flush();
        }
    }
    public static void onWorldRenderEnd(float transition)
    {
        finishWorldForms();
        if (BBSModClient.getCameraController().getCurrent() instanceof mchorse.bbs_mod.camera.controller.PlayCameraController)
        {
            mchorse.bbs_mod.camera.controller.PlayCameraController controller = (mchorse.bbs_mod.camera.controller.PlayCameraController) BBSModClient.getCameraController().getCurrent();
            Batcher2D batcher = new Batcher2D(new UIDrawContext(1F));
            FrameOverlays.render(batcher.getContext().getMatrices(), batcher, controller.getContext());
        }
        UIBaseMenu menu=UIScreen.getCurrentMenu();
        if(menu instanceof UIDashboard && ((UIDashboard)menu).getPanels().panel instanceof UIFilmPanel)
        {
            UIFilmPanel film=(UIFilmPanel)((UIDashboard)menu).getPanels().panel;
            Batcher2D batcher=new Batcher2D(new UIDrawContext(1F));
            batcher.beginBatch();
            try {FrameOverlays.render(batcher.getContext().getMatrices(),batcher,film.getRunner().getContext());}
            finally {batcher.endBatch();}
        }
        renderingWorld=false;
    }
    private static boolean filmPanelShowing()
    {
        UIBaseMenu menu=UIScreen.getCurrentMenu();
        return menu instanceof UIDashboard&&((UIDashboard)menu).getPanels().panel instanceof UIFilmPanel;
    }
    /** After native HUD, before GuiScreen. Repeated calls are harmless (hide-GUI fallback). */
    public static void onRenderBeforeScreen()
    {
        if(!capturing)return;
        Minecraft mc=Minecraft.getMinecraft();boolean completed=false;
        try
        {
            Texture output=getTexture();int w=getVideoWidth(),h=getVideoHeight();
            if(output.width!=w||output.height!=h){output.bind();output.setSize(w,h);output.unbind();}
            if(exportFramebuffer==null){exportFramebuffer=new mchorse.bbs_mod.graphics.Framebuffer();exportFramebuffer.attach(output,GL30.GL_COLOR_ATTACHMENT0);}
            int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try
            {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,framebuffer.framebufferObject);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,exportFramebuffer.id);
                GL30.glBlitFramebuffer(0,0,framebuffer.framebufferWidth,framebuffer.framebufferHeight,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
            }
            finally
            {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
                if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            if(canRender&&BBSModClient.getVideoRecorder().isRecording())
            {
                BBSModClient.getMinecraftSoundCapture().captureFrame();BBSModClient.getVideoRecorder().recordFrame();
            }
            completed=true;
        }
        finally {restoreRenderTarget();}
        if(completed&&!filmPanelShowing())framebuffer.framebufferRender(mc.displayWidth,mc.displayHeight);
        mc.entityRenderer.setupOverlayRendering();
        if(completed&&pendingExportResolutionAction!=null&&--pendingExportFrames<=0)
        {
            Runnable action=pendingExportResolutionAction;pendingExportResolutionAction=null;holdingExportFrame=false;
            mc.addScheduledTask(action);
        }
    }
    private static void restoreRenderTarget()
    {
        if(!capturing)return;
        Minecraft mc=Minecraft.getMinecraft();capturing=false;
        mc.displayWidth=windowWidth;mc.displayHeight=windowHeight;
        clientFramebuffer.bindFramebuffer(false);
        GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
    }
    /** The transformer calls this from the updateCameraAndRender exception boundary. */
    public static void abortFrame()
    {
        renderingWorld=false;
        try {finishWorldForms();}
        finally {restoreRenderTarget();}
    }
    public static void scheduleAfterNextExportFrame(Runnable action){pendingExportResolutionAction=action;pendingExportFrames=1;holdingExportFrame=false;}
    public static void scheduleAfterHeldExportFrame(Runnable action){pendingExportResolutionAction=action;pendingExportFrames=Math.max(1,BBSSettings.videoHeldFrames.get());holdingExportFrame=true;}
    public static boolean isHoldingExportFrame(){return holdingExportFrame;}
    public static float getLastFrameDuration(){return lastFrameDuration;}
    /** Timer.updateTimer HEAD: true means native time advancement has been replaced. */
    public static boolean updateTimer(Timer timer)
    {
        if(holdingExportFrame)
        {
            timer.elapsedTicks=0;timer.elapsedPartialTicks=0;lastFrameDuration=0;heldFrames=0;lastFrameTime=0;canRender=false;return true;
        }
        VideoRecorder recorder=BBSModClient.getVideoRecorder();
        if(!recorder.isRecording()){heldFrames=0;lastFrameTime=0;canRender=false;return false;}
        timer.elapsedTicks=0;timer.elapsedPartialTicks=0;lastFrameDuration=0;
        if(recorder.getCounter()==0)timer.renderPartialTicks=0;
        if(heldFrames==0)
        {
            long time=Minecraft.getSystemTime();
            if(BBSSettings.videoLimitFrameRate.get())
            {
                long interval=(long)(1000F/getVideoFrameRate());
                if(time-lastFrameTime<interval){canRender=false;return true;}
                lastFrameTime=time;
            }
            lastFrameDuration=20F/getVideoFrameRate();timer.elapsedPartialTicks=lastFrameDuration;
            timer.renderPartialTicks+=lastFrameDuration;timer.elapsedTicks=(int)timer.renderPartialTicks;timer.renderPartialTicks-=timer.elapsedTicks;
            recorder.serverTicks+=timer.elapsedTicks;canRender=true;
        }
        else canRender=false;
        if(++heldFrames>=Math.max(1,BBSSettings.videoHeldFrames.get()))heldFrames=0;
        return true;
    }
    /** IntegratedServer advances exactly the ticks produced by the capture clock. */
    public static int serverTicksToRun()
    {
        if(holdingExportFrame)return 0;
        VideoRecorder recorder=BBSModClient.getVideoRecorder();
        return recorder.isRecording()?Math.max(0,recorder.serverTicks-recorder.lastServerTicks):1;
    }
    public static void serverTickComplete()
    {
        VideoRecorder recorder=BBSModClient.getVideoRecorder();if(recorder.isRecording())recorder.lastServerTicks++;
    }
    private static <T> T getCurveValue(String key, java.util.function.Function<mchorse.bbs_mod.camera.clips.CameraClipContext, java.util.Map<String, T>> values)
    {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) return null;
        mchorse.bbs_mod.camera.controller.ICameraController camera = BBSModClient.getCameraController().getCurrent();
        if (camera instanceof mchorse.bbs_mod.camera.controller.CameraWorkCameraController)
        {
            T value = values.apply(((mchorse.bbs_mod.camera.controller.CameraWorkCameraController) camera).getContext()).get(key);
            if (value != null) return value;
        }
        for (mchorse.bbs_mod.film.BaseFilmController film : BBSModClient.getFilms().getControllers())
        {
            if (film instanceof mchorse.bbs_mod.film.WorldFilmController)
            {
                T value = values.apply(((mchorse.bbs_mod.film.WorldFilmController) film).getContext()).get(key);
                if (value != null) return value;
            }
        }
        return null;
    }
    public static Long getTimeOfDay()
    {
        Double value = getCurveValue("sun_rotation", mchorse.bbs_mod.camera.clips.misc.CurveClip::getValues);
        return value == null ? null : (long) (value * 1000L);
    }
    public static Double getShaderCurveValue(String option) { return getCurveValue(mchorse.bbs_mod.camera.clips.misc.CurveClip.SHADER_CURVES_PREFIX + option, mchorse.bbs_mod.camera.clips.misc.CurveClip::getValues); }
    public static Double getBrightness() { return getCurveValue("brightness", mchorse.bbs_mod.camera.clips.misc.CurveClip::getValues); }
    public static Double getWeather() { return getCurveValue("weather", mchorse.bbs_mod.camera.clips.misc.CurveClip::getValues); }
    public static float getSunHorizontalRotation()
    {
        Double value = getCurveValue("sun_horizontal_rotation", mchorse.bbs_mod.camera.clips.misc.CurveClip::getValues);
        return value == null ? 0F : value.floatValue();
    }
    public static Integer getChromaSkyColorArgb() { return getCurveValue(mchorse.bbs_mod.camera.clips.misc.CurveClip.CHROMA_SKY_COLOR, mchorse.bbs_mod.camera.clips.misc.CurveClip::getColorValues); }
    public static long curveWorldTime(long original) { Long value = renderingWorld ? getTimeOfDay() : null; return value == null ? original : value; }
    public static float curveRain(float original) { Double value = renderingWorld ? getWeather() : null; return value == null ? original : value.floatValue(); }
    public static float curveBrightness(float original) { Double value = getBrightness(); return value == null ? original : value.floatValue(); }
    public static float curveSunYaw(float original) { return original + getSunHorizontalRotation(); }
    public static boolean renderChromaSky()
    {
        if (!BBSSettings.chromaSkyEnabled.get()) return false;
        Integer value = getChromaSkyColorArgb();
        int color = value == null ? BBSSettings.chromaSkyColor.get() : value;
        float r = (color >> 16 & 255) / 255F, g = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
        GlStateManager.clearColor(r, g, b, 1F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        java.nio.FloatBuffer fog = BufferUtils.createFloatBuffer(4); fog.put(r).put(g).put(b).put(1F).flip();
        GL11.glFog(GL11.GL_FOG_COLOR, fog);
        return true;
    }
    public static void renderRecordingTimerOverlay(Batcher2D batcher,String label){renderRecordingTimerOverlay(batcher,label,5,5);}
    public static void renderRecordingTimerOverlay(Batcher2D batcher,String label,int x,int y)
    {
        batcher.icon(Icons.SPHERE,Colors.RED|Colors.A100,x+16,y,1F,0F);
        batcher.textCard(label,x+19,y+4,Colors.WHITE,Colors.A50);
    }
}
