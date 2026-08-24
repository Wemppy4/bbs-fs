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
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.sodium.SodiumUtils;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.mixin.client.FogRendererAccessor;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import java.io.File;
import java.nio.ByteBuffer;
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

    /**
     * The projection the world was last rendered with, captured per frame by
     * {@code GameRendererMixin#onSetWorldProjection} — the matrix of the world's UBO upload, so it
     * already carries the orthographic substitution when the orbit camera asks for one.
     * {@code GameRendererMixin#onRenderProjectionArg} hands the same matrix to Sodium's chunk capture.
     *
     * <p>Needed because BBS picking runs in the GUI phase, where the engine's bound Projection UBO is the
     * interface's ortho — drawing world geometry against it puts every pixel somewhere other than where the
     * user sees it. 1.21.1 solved this with RenderSystem.setProjectionMatrix; on 1.21.11 the projection is
     * GPU-owned, so the picker passes bind this explicitly instead (BBSPickerRenderer#setProjectionOverride).
     */
    private static final Matrix4f worldProjection = new Matrix4f();

    public static void setWorldProjection(Matrix4f projection)
    {
        worldProjection.set(projection);
    }

    public static Matrix4f getWorldProjection()
    {
        return worldProjection;
    }

    private static boolean customSize;

    /**
     * Resolved at class initialisation rather than in {@link #setup()} on purpose: BBSShaders assigns
     * each pipeline to an Iris program as it registers it, and its own pipelines are static finals, so
     * whether the flag is set yet would otherwise depend on which class the client happened to touch
     * first — and a false read here fails silently, leaving forms invisible under a shaderpack again.
     * FabricLoader is up long before any of this.
     */
    private static boolean iris = FabricLoader.getInstance().isModLoaded("iris");

    /** Same class-init timing (and reason) as {@link #iris}: a late false read fails silently. */
    private static boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
    private static boolean optifine;

    private static int width;
    private static int height;

    /* Orbit distance for the orthographic projection; negative = perspective.
     * Re-armed every frame by the film editor's orbit camera (which is set up
     * from Camera#update, between renderWorld's HEAD and its projection use),
     * so it can never go stale when another controller takes over. */
    private static float orthoDistance = -1F;

    private static boolean toggleFramebuffer;
    private static Framebuffer framebuffer;
    private static Framebuffer clientFramebuffer;
    private static Texture texture;

    /** Private read FBO used to snapshot our framebuffer's colour attachment into {@link #texture}. */
    private static int captureReadFramebuffer = -1;

    /** Private draw FBO the snapshot {@link #texture} is attached to for that blit. */
    private static int captureDrawFramebuffer = -1;

    /** Set while a world recording is holding its snapshot back until the interface has been drawn. */
    private static boolean deferredCapture;

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
        /* The world always renders at the export size. The interface (HUD) is drawn after the
         * world but still into our export framebuffer — toggleFramebuffer stays on until the blit —
         * so it must use the export size too. Otherwise it renders at the real window size and, when
         * the window can't physically reach the requested resolution, comes out stretched in the
         * file. Excluded while a BBS editor is open so the film panel's own UI keeps rendering at the
         * real window size. */
        return customSize && (renderingWorld || (toggleFramebuffer && UIScreen.getCurrentMenu() == null));
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
        int newWidth = !customSize ? 0 : w;
        int newHeight = !customSize ? 0 : h;

        /* No-op when nothing actually changes. A redundant setCustomSize(false)
         * — e.g. a film panel disappearing while custom size is already off, which
         * happens when the dashboard is first lazily created by the teleport/record
         * keybinds — must NOT resize the vanilla framebuffers: that stalls the GPU
         * and freezes the screen for a frame even though the state didn't change. */
        if (BBSRendering.customSize == customSize && width == newWidth && height == newHeight)
        {
            return;
        }

        LOGGER.info("[BBS film] setCustomSize customSize={} w={} h={} (stored width/height will be {})",
            customSize, w, h, customSize ? w + "/" + h : "0/0");
        BBSRendering.customSize = customSize;

        width = newWidth;
        height = newHeight;

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
            /* RGB8 (no alpha) on purpose: the world framebuffer's sky/cleared regions carry a non-opaque alpha
             * that, if preserved, would make the sky show through as the panel background in the preview blit
             * (GUI_TEXTURED multiplies texel alpha). Capturing into RGB8 drops it so the preview stays opaque. */
            texture.setFormat(TextureFormat.RGB_U8);
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
        /* Iris is coupled again — see the field, which resolves itself, plus the pipeline assignment in
         * BBSShaders. Enough for a shaderpack to draw BBS forms and for the shadow pass to be told
         * apart; the rest of the 1.21.1 integration (PBR textures, shader-curve uniforms, the pack's
         * option menus inside BBS's UI) stays decoupled.
         *
         * Sodium still is: nothing in BBS asks it anything except the ortho frame's point-camera
         * culling relaxation, which is a nicety. */

        LOGGER.info("[BBS shaders] Iris integration {}", iris ? "on" : "off (mod not present)");
        optifine = FabricLoader.getInstance().isModLoaded("optifabric");

        /* Under the orthographic projection the whole frame sits at roughly the same depth, but
         * blocks near the screen edges are laterally further from the camera point than the view
         * distance — the fog paints them sky coloured, which reads as geometry vanishing at the
         * edges. Push every fog bound out of reach for the ortho frame (the 1.21.1 port did the
         * same via BackgroundRenderer + RenderSystem.setShaderFogStart/End; both are gone, fog is
         * a UBO now, and FogModifier is the remaining override point).
         *
         * Registered at index 0: FogRenderer#applyFog takes the FIRST modifier whose shouldApply
         * passes and stops (verified against the bytecode) — appending would put us behind
         * AtmosphericFogModifier, which matches every normal above-water frame, and we would
         * never run. */
        FogRendererAccessor.bbs$getFogModifiers().add(0, new FogModifier()
        {
            @Override
            public boolean shouldApply(CameraSubmersionType submersionType, Entity entity)
            {
                return BBSRendering.isOrthoActive();
            }

            @Override
            public void applyStartEndModifier(FogData fogData, Camera camera, ClientWorld clientWorld, float f, RenderTickCounter renderTickCounter)
            {
                fogData.environmentalStart = 1_000_000F;
                fogData.renderDistanceStart = 1_000_000F;
                fogData.environmentalEnd = 1_001_000F;
                fogData.renderDistanceEnd = 1_001_000F;
                fogData.skyEnd = 1_001_000F;
                fogData.cloudEnd = 1_001_000F;
            }
        });

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
                    composeIntoClientFramebuffer();
                }
            }
        }
    }

    private static void reassignFramebuffer(Framebuffer framebuffer)
    {
        MinecraftClient.getInstance().framebuffer = framebuffer;
    }

    /**
     * Hand the frame we rendered off-screen back to the client framebuffer, so the game presents it
     * the way it presents any other frame. Only the no-UI recording path needs this: with the film
     * panel open the preview draws the snapshot texture itself.
     *
     * <p>1.21.1 did this with {@code framebuffer.draw(w, h)} — a fullscreen quad into whatever was
     * bound, which the line above had just made the client framebuffer. The 1.21.11 method that
     * inherited the name, {@code blitToScreen()}, is NOT that: it is
     * {@code CommandEncoder.presentTexture}, which goes straight to the window. And vanilla presents
     * again at the end of the same frame — {@code MinecraftClient.render} captures its framebuffer
     * BEFORE the world render (so it captures the client one, not ours) and blits that. Our frame was
     * therefore shown and immediately overwritten by a client framebuffer holding nothing but the
     * frame's opening clear: two presents per frame, the second one black. That is the black flicker
     * that covered the whole screen for the length of every world recording.
     *
     * <p>Copying the colour attachment across is the faithful replacement. {@code drawBlit} is not:
     * it runs through {@code ENTITY_OUTLINE_BLIT}, which alpha-blends (SRC_ALPHA/ONE_MINUS_SRC_ALPHA),
     * and the world framebuffer's sky and cleared regions carry a non-opaque alpha (the same alpha
     * {@link #getTexture()} drops by capturing into RGB8), so blending would darken them into the
     * black destination.
     */
    private static void composeIntoClientFramebuffer()
    {
        Framebuffer client = clientFramebuffer;

        if (client == null || client.getColorAttachment() == null || framebuffer.getColorAttachment() == null)
        {
            return;
        }

        /* The two normally match (the world export sizes the window to the export resolution, and
         * without that it exports at the window size), but the export size is rounded to even pixels,
         * so a copy of the shared region is what is always defined. */
        int w = Math.min(framebuffer.textureWidth, client.textureWidth);
        int h = Math.min(framebuffer.textureHeight, client.textureHeight);

        if (w <= 0 || h <= 0)
        {
            return;
        }

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            framebuffer.getColorAttachment(), client.getColorAttachment(), 0, 0, 0, 0, 0, w, h);
    }

    /* Rendering */

    public static void onWorldRenderBegin()
    {
        /* NOTE(ortho lifetime): the ortho flag must NOT be reset here. On 1.21.1 the orbit camera armed
         * it from Camera#update, which ran INSIDE renderWorld — after this HEAD hook — so a HEAD reset
         * was safe. On 1.21.11 Camera#update moved to GameRenderer.render's updateCamera, BEFORE
         * renderWorld: a HEAD reset would wipe the freshly armed flag before anything reads it (that
         * exact inversion made the whole ortho toggle a no-op). The reset lives in onWorldRenderEnd. */
        MinecraftClient mc = MinecraftClient.getInstance();
        BBSModClient.getFilms().startRenderFrame(mc.getRenderTickCounter().getTickProgress(false));

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            menu.startRenderFrame(mc.getRenderTickCounter().getTickProgress(false));
        }

        renderingWorld = true;

        /* A capture deferred to after the interface never got its turn (the interface pass threw, or the
         * frame ended some other way). Unwind it here rather than starting a second frame on top of a
         * still-swapped mc.framebuffer — that is how the screen goes black and stays black. */
        if (deferredCapture)
        {
            deferredCapture = false;

            toggleFramebuffer(false);
        }

        if (!customSize)
        {
            return;
        }

        toggleFramebuffer(true);
    }

    public static void onWorldRenderEnd()
    {
        if (orthoDistance > 0F)
        {
            /* Give back the culling disabled for this ortho frame (see setOrthoDistance);
             * the orbit re-arms the flag next frame from Camera#update if ortho is still on. */
            MinecraftClient.getInstance().chunkCullingEnabled = true;

            if (sodium)
            {
                SodiumUtils.restorePointCameraCulling();
            }
        }

        orthoDistance = -1F;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController controller)
        {
            /* Recorded into ImmediateGui's private state and flushed right here — a throwaway
             * GuiRenderState nobody renders is how these subtitles used to vanish (two-phase GUI).
             * Flushing now, mid-world-phase, also puts them into mc.framebuffer, which during an
             * export is the export framebuffer: subtitles belong in the film. */
            Batcher2D batcher = new Batcher2D(ImmediateGui.begin());

            /* 1.21.11: UISubtitleRenderer.renderSubtitles takes a 3D MatrixStack (context.getMatrices() is now a
             * 2D Matrix3x2fStack). The subtitle renderer manages its own transform stack, so feed a fresh one. */
            UISubtitleRenderer.renderSubtitles(new MatrixStack(), batcher, SubtitleClip.getSubtitles(controller.getContext()));
            ImmediateGui.end();
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
                /* Same ImmediateGui flush as the playback branch above: the menu's context here
                 * still points at the PREVIOUS frame's already-composited DrawContext, so recording
                 * into it dropped the subtitles on the floor. Flushing immediately also lands them
                 * in mc.framebuffer — the film preview/export framebuffer during this phase — so
                 * they show in the panel preview and in the exported file, like on 1.21.1. */
                Batcher2D batcher = new Batcher2D(ImmediateGui.begin());

                UISubtitleRenderer.renderSubtitles(new MatrixStack(), batcher, SubtitleClip.getSubtitles(panel.getRunner().getContext()));
                ImmediateGui.end();
            }
        }

        renderingWorld = false;
    }

    public static void onRenderBeforeScreen()
    {
        /* On 1.21.1 InGameHud.render DREW the interface, into whatever was bound — our export framebuffer,
         * because the restore below sat at that method's TAIL, after the drawing. That is why a world
         * recording carried the hotbar, the health bar and everything else. On 1.21.11 InGameHud.render only
         * RECORDS into a GuiRenderState; the drawing happens later, in GuiRenderer.render. Capturing here
         * would capture the bare world, which is exactly what went missing from first-person recordings.
         *
         * So when no BBS menu is up — the world recording — hold both the snapshot and the restore until
         * {@link #onRenderAfterInterface()}, which runs once the interface really has been drawn, still into
         * our framebuffer because mc.framebuffer is still pointed at it. The size the interface lays itself
         * out at already follows (see canReplaceFramebuffer).
         *
         * With a BBS menu open the interface must NOT land in the export framebuffer: the film panel draws
         * its own UI at window size and blits the preview texture into it, so that path captures and restores
         * right here, before the interface is composited. */
        if (customSize && UIScreen.getCurrentMenu() == null)
        {
            deferredCapture = true;

            return;
        }

        captureAndRestore();
    }

    /**
     * Runs right after the interface has been composited (see {@code GameRendererMixin}), for the world
     * recording that deferred its capture in {@link #onRenderBeforeScreen()}.
     */
    public static void onRenderAfterInterface()
    {
        if (!deferredCapture)
        {
            return;
        }

        deferredCapture = false;

        captureAndRestore();
    }

    /**
     * Copy the world that just rendered into {@link #framebuffer} into the BBS snapshot {@link #texture} — the
     * one the film preview draws and {@link mchorse.bbs_mod.utils.VideoRecorder} reads back — rescaling it from
     * the framebuffer's physical size down to the export size.
     *
     * <p>A blit, not a {@code glCopyTexSubImage2D}: copying cannot rescale, and rescaling is the whole point
     * (see the caller). It also buys back the supersampling the HiDPI export had on 1.21.1 — the world is
     * rendered at native resolution and filtered down into the file.</p>
     *
     * <p>1.21.11: {@code Framebuffer.beginWrite()} was removed, so neither end of the blit is bound for us. Both
     * get a private FBO here — the framebuffer's colour attachment as the read source, the snapshot texture as
     * the draw target. The bindings are saved and restored; the modern pipeline rebinds its render-pass targets
     * afterwards, so this stays isolated. The snapshot being RGB8 (see {@link #getTexture()}) drops the
     * framebuffer's non-opaque sky alpha along the way, which is what keeps the preview opaque.</p>
     *
     * <p>Unlike a copy, a blit is clipped by the scissor box and filtered through the colour write mask, and at
     * this point in the frame both belong to whoever drew last. They are neutralised around the blit and put back
     * through {@link GlStateManager} so its cache stays truthful (touching that state behind it desyncs the cache
     * — the same trap {@link mchorse.bbs_mod.graphics.texture.Texture#bind()} documents).</p>
     */
    private static void blitIntoSnapshot(Texture texture, int w, int h)
    {
        int sourceWidth = framebuffer.textureWidth;
        int sourceHeight = framebuffer.textureHeight;

        if (captureReadFramebuffer == -1)
        {
            captureReadFramebuffer = GL30.glGenFramebuffers();
            captureDrawFramebuffer = GL30.glGenFramebuffers();
        }

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int sourceId = ((GlTexture) framebuffer.getColorAttachment()).getGlId();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, captureReadFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sourceId, 0);
        GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureDrawFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture.id, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);

        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean[] mask = readColorMask();

        if (scissor)
        {
            GlStateManager._disableScissorTest();
        }

        GlStateManager._colorMask(true, true, true, true);

        /* GL_LINEAR only where it actually resamples: at 1:1 — every display that is not HiDPI — a nearest
         * blit is the same copy the snapshot has always been. */
        GlStateManager._glBlitFrameBuffer(
            0, 0, sourceWidth, sourceHeight,
            0, 0, w, h,
            GL11.GL_COLOR_BUFFER_BIT,
            sourceWidth == w && sourceHeight == h ? GL11.GL_NEAREST : GL11.GL_LINEAR
        );

        GlStateManager._colorMask(mask[0], mask[1], mask[2], mask[3]);

        if (scissor)
        {
            GlStateManager._enableScissorTest();
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
    }

    private static boolean[] readColorMask()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer mask = stack.malloc(4);

            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);

            return new boolean[] {mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0};
        }
    }

    private static void captureAndRestore()
    {
        /* Snapshot only when we actually redirected the world into our framebuffer this frame (film panel
         * open / recording). Outside that, mc.framebuffer was never swapped, so our framebuffer holds nothing
         * worth copying and the snapshot would just waste a per-frame GPU copy. */
        if (customSize)
        {
            /* The snapshot IS the recording, so it is sized in video pixels — not in the physical pixels the
             * world was just rendered at. On a HiDPI display those are not the same number: WindowMixin reports
             * the framebuffer size as getVideoWidth() * getOriginalFramebufferScale(), so on a Retina Mac
             * (scale 2) the framebuffer is twice the export size in each axis. Sizing the snapshot from
             * framebuffer.textureWidth therefore handed VideoRecorder a texture four times the buffer it had
             * allocated (getVideoWidth() * getVideoHeight() * 3), and glGetTexImage — which downloads the whole
             * level, there is no size to pass it — wrote straight past the end of it. Every export on a Mac
             * died there, inside the driver's pixel-store loop (SIGBUS). */
            Texture texture = getTexture();
            int w = getVideoWidth();
            int h = getVideoHeight();

            if (texture.width != w || texture.height != h)
            {
                texture.bind();
                texture.setSize(w, h);
                texture.unbind();
            }

            blitIntoSnapshot(texture, w, h);
        }

        toggleFramebuffer(false);

        /* AFTER the restore: mc.framebuffer points back at the screen, so the operator overlay
         * shows up there and never lands in the exported file. */
        renderRecordingOverlay();

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
         * and RenderSystem.getProjectionMatrix()/MinecraftClient.getProfiler() were removed.
         *
         * Still a stub now that Iris is coupled again — and nothing waits on it: forms draw from
         * AFTER_ENTITIES in every case (see BBSModClient), and the reason 1.21.1 needed this hook at all
         * is covered by handing Iris the pipeline-to-program mapping instead. Rebuild it only if a pack
         * turns out to need BBS geometry inside the chunk-layer pass specifically. */
        if (isIrisShadersEnabled())
        {
            /* renderCoolStuff(context) — needs a reconstructed WorldRenderContext (see TODO above). */
        }
    }

    public static void renderHud(DrawContext drawContext, float tickDelta)
    {
        Batcher2D batcher2D = new Batcher2D(drawContext);

        BBSModClient.getFilms().renderHud(batcher2D, tickDelta);
    }

    /**
     * Draw the recording countdown / frame-counter overlay. This is operator UI: it is drawn from
     * {@link #onRenderBeforeScreen()} after the export blit but before the buffer is copied to the
     * screen, so it shows up on screen but is never captured into the file.
     */
    private static void renderRecordingOverlay()
    {
        if (!BBSSettings.recordingOverlays.get() || UIScreen.getCurrentMenu() != null)
        {
            return;
        }

        String label;

        if (BBSModClient.isVideoExportDelayPending())
        {
            int countdown = Math.max(0, (int) Math.ceil(BBSModClient.getVideoExportDelayRemainingMs() / 50D));

            label = String.valueOf(countdown / 20F);
        }
        else if (BBSModClient.getVideoRecorder().isRecording())
        {
            int count = BBSModClient.getVideoRecorder().getCounter();

            label = UIKeys.FILM_VIDEO_RECORDING.format(
                count,
                BBSModClient.getKeyRecordVideo().getBoundKeyLocalizedText().getString()
            ).get();
        }
        else
        {
            return;
        }

        /* Recorded and flushed on the spot through ImmediateGui — a bare GuiRenderState that
         * nobody renders (the old code here) never reaches the screen on the two-phase GUI. */
        renderRecordingTimerOverlay(new Batcher2D(ImmediateGui.begin()), label);
        ImmediateGui.end();
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

        /* Draw morph forms collected during the (build-phase) entity render. AFTER_ENTITIES is the only
         * world context where the BBS immediate form pipeline lands correctly (entity queue flushed +
         * camera model-view still active). See MorphRenderer / LivingEntityRendererMorphMixin. */
        MorphRenderer.renderQueued(worldRenderContext);

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

    /**
     * Arm the orthographic projection for the current frame. Pass the orbit
     * camera's distance to the pivot; negative disables. The value is reset
     * at the beginning of every world render, so the caller must re-arm it
     * each frame for as long as ortho should stay on.
     */
    public static void setOrthoDistance(float distance)
    {
        orthoDistance = distance;

        if (distance > 0F)
        {
            /* The chunk occlusion culling walks sections outward from the
             * camera POINT, which is only sound for a perspective projection —
             * under ortho's parallel sightlines it over-culls sections near
             * the screen edges. Disable it for the frame (Sodium honours the
             * same flag); the frustum and render distance still cull. Sodium's
             * own point-camera heuristics get the same treatment. */
            MinecraftClient.getInstance().chunkCullingEnabled = false;

            if (sodium)
            {
                SodiumUtils.disablePointCameraCulling();
            }
        }
    }

    public static boolean isOrthoActive()
    {
        return orthoDistance > 0F;
    }

    /**
     * Build the orthographic projection replacing the given perspective one
     * (returns the input untouched when ortho is not armed). FOV and aspect are
     * derived from the perspective matrix itself, so the ortho frame height
     * matches the perspective frame height at the orbit pivot's distance: the
     * subject keeps its size when toggling projections, and the scroll zoom
     * keeps working through the orbit distance.
     *
     * @param minHalfHeight a lower bound on the frame's half height, and the
     *        slack behind the camera plane the near plane is given; the frustum
     *        culling matrix is built with a loose bound on both, so culling
     *        stays conservative when zoomed all the way in.
     */
    public static Matrix4f getOrthoProjection(GameRenderer renderer, Matrix4f perspective, float minHalfHeight)
    {
        if (orthoDistance <= 0F)
        {
            return perspective;
        }

        float tanHalfFov = 1F / perspective.m11();
        float aspect = perspective.m11() / perspective.m00();
        float halfHeight = Math.max(minHalfHeight, orthoDistance * tanHalfFov);
        float halfWidth = halfHeight * aspect;

        /* The near plane sits exactly at the camera, the way a perspective one
         * effectively does: under ortho's parallel sightlines everything BEHIND
         * the camera projects into the frame as well, so a hillside the camera
         * stands in paints itself over the subject, and no amount of orbiting
         * gets past it. Clipping at the camera plane drops precisely what the
         * eye has already passed and nothing the eye still faces — pushing the
         * plane any further in would slice the ground in front of the camera
         * and leave a hole where it was. Zooming in walks the camera towards
         * the pivot, so the zoom doubles as the control over how much of an
         * obstacle in front gets cut.
         *
         * The far plane is the one vanilla builds its perspective with, which
         * already bounds everything the game draws; together with the near
         * plane it keeps the box tight enough for the frustum to cull with,
         * which matters here because chunk occlusion culling is off (see
         * setOrthoDistance). */
        float near = -minHalfHeight;
        float far = renderer.getFarPlaneDistance();

        return new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
    }

    /**
     * Whether a shaderpack is drawing the world. Around twenty places in BBS already ask this and the
     * matching {@link #isIrisShadowPass()} — the port kept every one of those branches and cut only the
     * sensor, pinning both to false, which is why turning shaders on made replays vanish and left the
     * shadow pass drawing forms and honouring the film camera's FOV.
     */
    public static boolean isIrisShadersEnabled()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShaderPackEnabled();
    }

    public static boolean isSodiumLoaded()
    {
        return sodium;
    }

    /**
     * Whether Iris is currently filling its shadow map rather than the frame the player sees. That pass
     * runs the world render a second time from the sun's point of view, so anything BBS draws without
     * checking lands in the shadow map at the shadow camera's placement — a form smeared away from the
     * thing it belongs to.
     */
    public static boolean isIrisShadowPass()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShadowPass();
    }

    /**
     * Hold the vertex layout Iris hands out steady while a render layer's buffer is uploaded
     * outside of the immediate provider's own draw — the deferred translucent pass ends and
     * uploads those buffers itself (see CustomVertexConsumerProvider#draw). Without it a form
     * drawn where the level isn't rendering, like the form editor's viewport, gets its plain
     * entity vertices read at Iris' extended stride and shreds into stretched triangles. Returns
     * the previous state, to be handed back to {@link #endIrisBufferUpload(boolean)}.
     */
    public static boolean beginIrisBufferUpload(BufferBuilder builder)
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.beginBufferUpload(builder);
    }

    public static void endIrisBufferUpload(boolean extended)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.endBufferUpload(extended);
    }

    /**
     * The family of shaderpack program a BBS pipeline belongs to. Kept BBS-side so that every
     * registration site can name one without dragging Iris's classes into itself;
     * {@link IrisUtils#assignPipeline} does the translation.
     */
    public enum IrisProgramKind
    {
        /** Lit, textured geometry with light and normals: forms. */
        ENTITY,
        /** The same, for the translucent pass. */
        ENTITY_TRANSLUCENT,
        /** BBS's own particle emitter. */
        PARTICLE,
        /** Textured geometry with no colour or light of its own — trail strips. */
        TEXTURED,
        /** Flat coloured triangles: label shadows, gizmo bodies, IK debug, the world overlays. */
        BASIC,
        /** The same in line mode. */
        LINES
    }

    /**
     * Hand a BBS pipeline to Iris so a loaded shaderpack draws it with one of its own programs.
     * Silently does nothing without Iris.
     *
     * <p>Called by {@link BBSShaders} for the WORLD variants of the form pipelines only, and the two
     * conditions that makes it satisfy were both proven by a run that assigned the shared pipelines:
     *
     * <ul>
     *   <li>The program's vertex format must match. Assigning a plain position/colour pipeline (the
     *       gizmo, the world overlays) to the pack's BASIC program drew it BLACK — the pack's program
     *       reads attributes that geometry does not carry. Only the full entity format (model,
     *       billboard-with-shading) and the particle format are handed over.</li>
     *   <li>The pipeline must be world-only. The shared model pipeline also draws the form editor's
     *       preview and the film panel's, into framebuffers of BBS's own; assignment is per pipeline,
     *       so the pack's entity program followed it there and clipped the form against a depth
     *       buffer that has nothing to do with it — "part of the form hidden as if behind blocks",
     *       in a viewport with no blocks in it. Hence the split: the world variants exist for the
     *       world's own frame and nothing else.</li>
     * </ul>
     *
     * <p>Why assignment is the only way a form survives a shaderpack on 1.21.11 (read out of Iris
     * 1.10.7 + vanilla bytecode): a pack draws the world into its OWN G-buffers — every render pass
     * whose program is the pack's binds them via {@code ExtendedShader.iris$setupState} — and at the
     * end of the frame composites the result into the client framebuffer, overwriting it. A draw
     * that keeps a BBS program lands in the client framebuffer (the pass's declared target) and is
     * wiped by that composite even when it is not skipped outright. Only draws carrying the pack's
     * programs land in the G-buffers and survive — and come out lit, fogged and shadowed by the pack.
     */
    public static void assignIrisPipeline(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, IrisProgramKind kind)
    {
        if (!iris || pipeline == null)
        {
            return;
        }

        try
        {
            IrisUtils.assignPipeline(pipeline, kind);

            /* One line per pipeline, and BBS registers a good dozen — worth keeping, because a pipeline
             * missing from this list is geometry a shaderpack will not draw, and in game that reads as
             * "some replays show and some don't" rather than as anything shader-shaped. */
            LOGGER.info("[BBS shaders] {} -> Iris {}", pipeline.getLocation(), kind);
        }
        catch (Throwable e)
        {
            /* A pack-less Iris, a version whose API moved, a double assignment we failed to prevent:
             * none of that is worth taking the editor down for — the geometry just draws unshaded. */
            LOGGER.error("[BBS shaders] failed to hand {} to Iris", pipeline.getLocation(), e);
        }
    }

    /**
     * Make a shaderpack treat a BBS pipeline exactly as it treats {@code prototype}, a vanilla pipeline
     * the BBS one is a re-shadered clone of. Silently does nothing without Iris.
     *
     * <p>Preferred over {@link #assignIrisPipeline} wherever a vanilla counterpart exists, and the
     * reasons are worth keeping here because each one was a bug we shipped:
     *
     * <ul>
     *   <li>Naming a program kind covers the main pass only — forms cast no shadow under a pack, because
     *       Iris's shadow map is a separate assignment table that {@code assignPipeline} never writes.</li>
     *   <li>Naming a kind freezes one program for the whole frame, so the same pipeline drawn by the
     *       hand renderer asks for an entity program instead of a hand one.</li>
     *   <li>Iris resolves a kind to the FIRST matching program in its enum, and for entities that is the
     *       one whose alpha test treats vertex alpha as a discard THRESHOLD — which deleted every fully
     *       opaque form under a pack while a 1% fade slipped through. See {@link IrisUtils#copyPipeline}.</li>
     * </ul>
     */
    public static void mirrorIrisPipeline(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, com.mojang.blaze3d.pipeline.RenderPipeline prototype)
    {
        if (!iris || pipeline == null || prototype == null)
        {
            return;
        }

        try
        {
            IrisUtils.copyPipeline(prototype, pipeline);

            LOGGER.info("[BBS shaders] {} mirrors {}", pipeline.getLocation(), prototype.getLocation());
        }
        catch (Throwable e)
        {
            /* Same reasoning as assignIrisPipeline: an Iris whose internals moved must not take the
             * editor down — the geometry just draws with BBS's own shader. */
            LOGGER.error("[BBS shaders] failed to mirror {} onto {}", prototype.getLocation(), pipeline.getLocation(), e);
        }
    }

    /**
     * True while form draws are aimed at the world's own frame — the film's AFTER_ENTITIES pass and
     * the model block's vanilla pass. {@link BBSShaders} reads it (through {@link #isIrisWorldForms()})
     * to hand those draws the world variants of its pipelines, the ones assigned to a shaderpack's
     * programs; everywhere else (editor previews, GUI, offscreen framebuffers) forms keep the shared
     * pipelines and BBS's own shaders.
     */
    private static boolean worldForms;

    /**
     * Open the span in which form draws belong to the world's frame; close with
     * {@link #endWorldForms(boolean)}, passing back what this returned.
     *
     * <p>Spans nest — a morph form drawn inside the world span can hold an item whose own form opens
     * one of its own — so this returns the previous state instead of assuming there was none. Closing
     * a nested span with a plain "off" would end the enclosing one early, and the rest of the world's
     * forms would silently fall back to the shared pipelines.
     *
     * <p>This used to set Iris's {@code isMainBound} false for the span, the 1.21.1 lever against a
     * pack dropping BBS's draws — and a run proved that on 1.21.11 it is worse than useless. The write
     * gate it opens no longer matters (the draws land in the client framebuffer either way, and the
     * pack's end-of-frame composite wipes them — see {@link #assignIrisPipeline}), while the SAME flag
     * gates Iris's program substitution ({@code shouldOverrideShaders()} in
     * {@code MixinShaderManager_Overrides}): with it forced false, the vanilla-layer draws inside the
     * span — item forms, mob forms — lost the pack's programs too and died with everything else.
     * So the span now marks context only; the Iris flag is left alone.
     */
    public static boolean beginWorldForms()
    {
        boolean prev = worldForms;

        worldForms = true;

        return prev;
    }

    /** Closes {@link #beginWorldForms()}, restoring whatever span enclosed it. */
    public static void endWorldForms(boolean prev)
    {
        worldForms = prev;
    }

    /**
     * Pause the world-forms span for a nested offscreen render: those draws must keep BBS's shared
     * pipelines — a pack program would bind the pack's G-buffers underneath them and the pixels would
     * leave the target's framebuffer entirely. Restore with {@link #restoreWorldForms(boolean)}.
     *
     * <p>Two callers, both drawing into a target of their own during the world phase: a framebuffer
     * form rendering its children ({@code FramebufferFormRenderer}), and an in-panel 3D viewport
     * rendering into its preview texture ({@code UIModelRenderer#renderModelToTexture}). The viewport
     * is the sharper case, because it draws through a VANILLA entity layer rather than a BBS pipeline:
     * without this the pack claims that layer, binds its own G-buffer over the preview's, and the
     * viewport's geometry ends up smeared across the world in the panel's projection.
     *
     * <p>This also tells Iris the main target is unbound for the span ({@link IrisUtils#setMainBound}):
     * inside the world render Iris otherwise disables colour/depth writes for any program that is not
     * its own ({@code MixinCompiledShaderProgram} → {@code DepthColorStorage.disableDepthColor}), and
     * these draws are BBS's own programs into BBS's own framebuffer. That is the one place the 1.21.1
     * lever is still right: the target really is not the main one.
     */
    public static boolean suspendWorldForms()
    {
        boolean prev = worldForms;

        worldForms = false;

        setIrisMainBound(false);

        return prev;
    }

    /** Closes {@link #suspendWorldForms()}. */
    public static void restoreWorldForms(boolean prev)
    {
        setIrisMainBound(true);

        worldForms = prev;
    }

    /**
     * Whether form draws right now should carry a shaderpack's programs: inside the world-forms span
     * with a pack enabled. This is the single switch {@link BBSShaders} keys its world pipeline
     * variants on, and {@link mchorse.bbs_mod.forms.FormTranslucentQueue} its two-pass split — the
     * pack's program ignores the PASS_MODE define, so splitting under it would draw both passes in
     * full and double every translucent texel.
     */
    public static boolean isIrisWorldForms()
    {
        return worldForms && isIrisShadersEnabled();
    }

    private static void setIrisMainBound(boolean bound)
    {
        if (!iris)
        {
            return;
        }

        try
        {
            IrisUtils.setMainBound(bound);
        }
        catch (Throwable e)
        {
            LOGGER.error("[BBS shaders] failed to tell Iris the main target is {}", bound ? "bound" : "unbound", e);
        }
    }

    public static void trackTexture(Texture texture)
    {}

    /**
     * Options the loaded shaderpack declares as sliders. {@link mchorse.bbs_mod.utils.iris.ShaderCurves}
     * exposes only these as curves: a slider is an option the pack itself says is continuous, so turning
     * it into an animatable uniform cannot break a {@code #if} branch the way a toggle would.
     */
    public static List<String> getShadersSliderOptions()
    {
        if (!iris)
        {
            return Collections.emptyList();
        }

        return IrisUtils.getSliderProperties();
    }

    /** The pack's own names for its options, for the curve picker. Empty without a pack. */
    public static Map<String, String> getShadersLanguageMap(String language)
    {
        if (!iris)
        {
            return Collections.emptyMap();
        }

        return IrisUtils.getShadersLanguageMap(language);
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
        if (sodium)
        {
            /* Sodium's intrinsic writers bypass the vanilla VertexConsumer chain; the Sodium-aware
             * wrapper forwards them (see RecolorVertexSodiumConsumer). Class touch is gated. */
            return (b) -> SodiumUtils.createVertexBuffer(b, color);
        }

        return (b) -> new RecolorVertexConsumer(b, color);
    }
}