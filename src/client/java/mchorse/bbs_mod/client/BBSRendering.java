package mchorse.bbs_mod.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import mchorse.bbs_mod.graphics.InverseView;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class BBSRendering
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    public static boolean canRender;

    public static boolean renderingWorld;
    public static int lastAction;

    public static final Matrix4f camera = new Matrix4f();

    private static boolean customSize;
    private static boolean iris;
    private static boolean sodium;
    private static boolean optifine;

    private static int width;
    private static int height;

    private static boolean toggleFramebuffer;
    private static Framebuffer framebuffer;
    private static Framebuffer clientFramebuffer;
    private static Texture texture;

    private static Runnable pendingExportResolutionAction;

    public static int getMotionBlur()
    {
        return getMotionBlur(BBSSettings.videoFrameRate.get(), getMotionBlurFactor());
    }

    public static int getMotionBlur(double fps, int target)
    {
        int i = 0;

        while (fps < target)
        {
            fps *= 2;

            i++;
        }

        return i;
    }

    public static int getMotionBlurFactor()
    {
        return getMotionBlurFactor(BBSSettings.videoMotionBlur.get());
    }

    public static int getMotionBlurFactor(int integer)
    {
        return integer == 0 ? 0 : (int) Math.pow(2, 6 + integer);
    }

    public static int getVideoWidth()
    {
        return width == 0 ? BBSSettings.videoWidth.get() : width;
    }

    public static int getVideoHeight()
    {
        return height == 0 ? BBSSettings.videoHeight.get() : height;
    }

    public static int getVideoFrameRate()
    {
        int frameRate = BBSSettings.videoFrameRate.get();

        return frameRate * (1 << getMotionBlur(frameRate, getMotionBlurFactor()));
    }

    public static File getVideoFolder()
    {
        File movies = new File(BBSMod.getSettingsFolder().getParentFile(), "movies");
        File exportPath = new File(BBSSettings.videoExportPath.get());

        if (exportPath.isDirectory())
        {
            movies = exportPath;
        }

        movies.mkdirs();

        return movies;
    }

    public static boolean canReplaceFramebuffer()
    {
        return customSize && renderingWorld;
    }

    public static boolean isCustomSize()
    {
        return customSize;
    }

    public static void setCustomSize(boolean customSize)
    {
        setCustomSize(customSize, 0, 0);
    }

    public static void setCustomSize(boolean customSize, int w, int h)
    {
        LOGGER.info("[BBS film] setCustomSize customSize={} w={} h={} (stored width/height will be {})",
            customSize, w, h, customSize ? w + "/" + h : "0/0");
        BBSRendering.customSize = customSize;

        width = !customSize ? 0 : w;
        height = !customSize ? 0 : h;

        if (!customSize)
        {
            resizeExtraFramebuffers();
        }
    }

    public static Texture getTexture()
    {
        if (texture == null)
        {
            texture = new Texture();
            /* RGBA8 (not RGB8) so glCopyImageSubData from the framebuffer's RGBA8 colour attachment is
             * format-class compatible; the export read-back uses GL_BGR so the dropped alpha is harmless. */
            texture.setFormat(TextureFormat.RGBA_U8);
            texture.setFilter(GL11.GL_NEAREST);
        }

        return texture;
    }

    public static void startTick()
    {
        capturedModelBlocks.clear();
    }

    public static void setup()
    {
        /* Iris/Sodium support has been decoupled; these stay permanently disabled. */
        iris = false;
        sodium = false;
        optifine = FabricLoader.getInstance().isModLoaded("optifabric");

        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.getWorld().isClient())
            {
                capturedModelBlocks.add(entity);
            }
        });
    }

    /* Framebuffers */

    public static Framebuffer getFramebuffer()
    {
        return framebuffer;
    }

    public static void setupFramebuffer()
    {
        Window window = MinecraftClient.getInstance().getWindow();

        framebuffer = new WindowFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight());
    }

    public static void resizeExtraFramebuffers()
    {
        Set<Framebuffer> buffers = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        buffers.add(mc.worldRenderer.getEntityOutlinesFramebuffer());
        buffers.add(mc.worldRenderer.getTranslucentFramebuffer());
        buffers.add(mc.worldRenderer.getEntityFramebuffer());
        buffers.add(mc.worldRenderer.getParticlesFramebuffer());
        buffers.add(mc.worldRenderer.getWeatherFramebuffer());
        buffers.add(mc.worldRenderer.getCloudsFramebuffer());

        for (Framebuffer buffer : buffers)
        {
            resizeFramebuffer(buffer);
        }
    }

    public static void resizeFramebuffer(Framebuffer framebuffer)
    {
        if (framebuffer == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();

        if (framebuffer.textureWidth == w && framebuffer.textureHeight == h)
        {
            return;
        }

        /* 1.21.11: Framebuffer.resize lost the legacy macOS flag arg. */
        framebuffer.resize(w, h);
    }

    public static void toggleFramebuffer(boolean toggleFramebuffer)
    {
        if (toggleFramebuffer == BBSRendering.toggleFramebuffer)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        BBSRendering.toggleFramebuffer = toggleFramebuffer;

        if (toggleFramebuffer)
        {
            int w = mc.getWindow().getFramebufferWidth();
            int h = mc.getWindow().getFramebufferHeight();

            resizeExtraFramebuffers();

            if (framebuffer.textureWidth != w || framebuffer.textureHeight != h)
            {
                framebuffer.resize(w, h);
            }

            clientFramebuffer = mc.getFramebuffer();

            reassignFramebuffer(framebuffer);

            /* 1.21.11: Framebuffer.beginWrite(boolean) was removed — render targets are bound implicitly from
             * mc.getFramebuffer() when WorldRenderer/GameRenderer build their render passes. Reassigning
             * mc.framebuffer above is therefore sufficient to redirect the world render into our framebuffer. */
        }
        else
        {
            reassignFramebuffer(clientFramebuffer);

            if (width != 0)
            {
                /* When the film panel is open, the UI draws the preview texture in its block; do not
                 * blit our framebuffer to the full window or the preview would stretch to full screen. */
                UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
                boolean filmPanelShowing = currentMenu instanceof UIDashboard dashboard
                    && dashboard.getPanels().panel instanceof UIFilmPanel;
                if (!filmPanelShowing)
                {
                    /* 1.21.11: Framebuffer.draw(w, h) → blitToScreen() (presents our framebuffer to the window).
                     * Only runs for the no-UI live recording overlay; the film-panel preview path skips it. */
                    framebuffer.blitToScreen();
                }
            }
        }
    }

    private static void reassignFramebuffer(Framebuffer framebuffer)
    {
        MinecraftClient.getInstance().framebuffer = framebuffer;
    }

    /* Rendering */

    public static void onWorldRenderBegin()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        BBSModClient.getFilms().startRenderFrame(mc.getRenderTickCounter().getTickProgress(false));

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            menu.startRenderFrame(mc.getRenderTickCounter().getTickProgress(false));
        }

        renderingWorld = true;

        if (!customSize)
        {
            return;
        }

        toggleFramebuffer(true);
    }

    public static void onWorldRenderEnd()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController controller)
        {
            /* 1.21.11: DrawContext now takes (client, GuiRenderState, width, height); the Immediate-based
             * constructor was removed. */
            DrawContext drawContext = new DrawContext(mc, new GuiRenderState(), mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            Batcher2D batcher = new Batcher2D(drawContext);

            /* 1.21.11: UISubtitleRenderer.renderSubtitles takes a 3D MatrixStack (context.getMatrices() is now a
             * 2D Matrix3x2fStack). The subtitle renderer manages its own transform stack, so feed a fresh one. */
            UISubtitleRenderer.renderSubtitles(new MatrixStack(), batcher, SubtitleClip.getSubtitles(controller.getContext()));
        }

        if (!customSize)
        {
            renderingWorld = false;

            return;
        }

        UIBaseMenu currentMenu = UIScreen.getCurrentMenu();

        if (currentMenu instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                UISubtitleRenderer.renderSubtitles(new MatrixStack(), currentMenu.context.batcher, SubtitleClip.getSubtitles(panel.getRunner().getContext()));
            }
        }

        renderingWorld = false;
    }

    public static void onRenderBeforeScreen()
    {
        /* Snapshot only when we actually redirected the world into our framebuffer this frame (film panel
         * open / recording). Outside that, mc.framebuffer was never swapped, so our framebuffer holds nothing
         * worth copying and the snapshot would just waste a per-frame GPU copy. */
        if (customSize)
        {
            Texture texture = getTexture();
            int w = framebuffer.textureWidth;
            int h = framebuffer.textureHeight;

            /* Snapshot the world that just rendered into our reassigned WindowFramebuffer into the BBS texture
             * that the film preview blits and the VideoRecorder reads back.
             *
             * 1.21.11: Framebuffer.beginWrite() was removed, so glCopyTexSubImage2D (which reads from the
             * currently GL-bound read framebuffer) no longer has our framebuffer bound and would copy garbage.
             * Instead copy the colour attachment (a GlTexture, RGBA8) straight into the snapshot texture with
             * glCopyImageSubData — a direct texture-to-texture copy that touches no FBO or texture-unit binding
             * state (avoiding the GlStateManager corruption that raw unit binds cause in this port). */
            if (texture.width != w || texture.height != h)
            {
                texture.bind();
                texture.setSize(w, h);
                texture.unbind();
            }

            int sourceId = ((GlTexture) framebuffer.getColorAttachment()).getGlId();

            GL43.glCopyImageSubData(
                sourceId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                texture.id, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                w, h, 1
            );
        }

        toggleFramebuffer(false);

        if (pendingExportResolutionAction != null)
        {
            Runnable action = pendingExportResolutionAction;
            pendingExportResolutionAction = null;
            MinecraftClient.getInstance().execute(action);
        }
    }

    public static void scheduleAfterNextExportFrame(Runnable action)
    {
        pendingExportResolutionAction = action;
    }

    public static void onRenderChunkLayer(Matrix4f positionMatrix)
    {
        /* TODO(1.21.11 render): this Iris-only chunk-layer hook used to hand-build a Fabric WorldRenderContextImpl
         * via the old prepare(worldRenderer, tickCounter, blockOutlines, camera, gameRenderer, lightmap,
         * projectionMatrix, positionMatrix, consumers, profiler, advancedTranslucency, world) signature. That API
         * is gone: the context now lives in net.fabricmc.fabric.impl.client.rendering.world and prepare(...) takes
         * the new world-render-state objects (WorldRenderState/SectionRenderState/GpuBufferSlice command queue),
         * and RenderSystem.getProjectionMatrix()/MinecraftClient.getProfiler() were removed. Iris support is
         * permanently disabled (isIrisShadersEnabled() == false), so this path is currently dead; rebuild the
         * context through the new pipeline foundation if/when Iris is reintroduced. */
        if (isIrisShadersEnabled())
        {
            /* renderCoolStuff(context) — needs a reconstructed WorldRenderContext (see TODO above). */
        }
    }

    public static void renderHud(DrawContext drawContext, float tickDelta)
    {
        Batcher2D batcher2D = new Batcher2D(drawContext);
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        BBSModClient.getFilms().renderHud(batcher2D, tickDelta);

        if (BBSSettings.recordingOverlays.get() && UIScreen.getCurrentMenu() == null)
        {
            if (BBSModClient.isVideoExportDelayPending())
            {
                int countdown = Math.max(0, (int) Math.ceil(BBSModClient.getVideoExportDelayRemainingMs() / 50D));

                renderRecordingTimerOverlay(batcher2D, String.valueOf(countdown / 20F));
            }
            else if (videoRecorder.isRecording())
            {
                int count = videoRecorder.getCounter();
                String label = UIKeys.FILM_VIDEO_RECORDING.format(
                    count,
                    BBSModClient.getKeyRecordVideo().getBoundKeyLocalizedText().getString()
                ).get();

                renderRecordingTimerOverlay(batcher2D, label);
            }
        }
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label)
    {
        renderRecordingTimerOverlay(batcher2D, label, 5, 5);
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label, int x, int y)
    {
        int iconX = x + 16;

        batcher2D.icon(Icons.SPHERE, Colors.RED | Colors.A100, iconX, y, 1F, 0F);
        batcher2D.textCard(label, iconX + 3, y + 4, Colors.WHITE, Colors.A50);
    }

    public static void renderCoolStuff(WorldRenderContext worldRenderContext)
    {
        /* 1.21.11: the relocated Fabric WorldRenderContext (api.client.rendering.v1.world) again threads a real
         * MatrixStack through context.matrices(), so the previous position-matrix rebuild is no longer needed. */

        /* Feed the world camera orientation into the holder that replaced RenderSystem's inverse view rotation
         * matrix, so billboards and particles keep facing the camera in world space. The context no longer
         * exposes camera()/positionMatrix(); pull the camera from the game renderer directly. */
        InverseView.set(new Matrix3f().rotation(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation()));

        if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
        {
            screen.renderInWorld(worldRenderContext);
        }

        BBSModClient.getFilms().render(worldRenderContext);
    }

    public static boolean isOptifinePresent()
    {
        return optifine;
    }

    public static boolean isRenderingWorld()
    {
        return renderingWorld;
    }

    public static boolean isIrisShadersEnabled()
    {
        return false;
    }

    public static boolean isIrisShadowPass()
    {
        return false;
    }

    public static void trackTexture(Texture texture)
    {}

    public static float[] calculateTangents(float[] t, float[] v, float[] n, float[] u)
    {
        return t;
    }

    public static float[] calculateTangents(float[] v, float[] n, float[] u)
    {
        return v;
    }

    public static List<String> getShadersSliderOptions()
    {
        return Collections.emptyList();
    }

    public static Map<String, String> getShadersLanguageMap(String language)
    {
        return Collections.emptyMap();
    }

    /* Curves */

    public static Long getTimeOfDay()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get("sun_rotation") : null;

            if (v != null)
            {
                return (long) (v * 1000L);
            }
        }

        return null;
    }

    public static Double getBrightness()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get("brightness") : null;

            if (v != null)
            {
                return v;
            }
        }

        return null;
    }

    public static Double getWeather()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get("weather") : null;

            if (v != null)
            {
                return v;
            }
        }

        return null;
    }

    public static Integer getChromaSkyColorArgb()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Integer> values = CurveClip.getColorValues(controller.getContext());

            if (values != null)
            {
                return values.get(CurveClip.CHROMA_SKY_COLOR);
            }
        }

        return null;
    }

    public static Function<VertexConsumer, VertexConsumer> getColorConsumer(Color color)
    {
        return (b) -> new RecolorVertexConsumer(b, color);
    }
}