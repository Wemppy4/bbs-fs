package mchorse.bbs_mod;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.CurveClientClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.client.renderer.entity.GunProjectileEntityRenderer;
import mchorse.bbs_mod.client.renderer.item.GunItemRenderer;
import mchorse.bbs_mod.client.renderer.item.ModelBlockItemRenderer;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.events.register.RegisterClientSettingsEvent;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.FramebufferManager;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.items.GunZoom;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.particles.ParticleManager;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLError;
import mchorse.bbs_mod.resources.packs.URLRepository;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.resources.packs.URLTextureErrorCallback;
import mchorse.bbs_mod.selectors.EntitySelectors;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.KeybindSettings;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.WorldExportWindowSession;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.MinecraftSourcePack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.impl.client.rendering.BlockEntityRendererRegistryImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class BBSModClient implements ClientModInitializer
{
    private static TextureManager textures;
    private static FramebufferManager framebuffers;
    private static SoundManager sounds;
    private static L10n l10n;

    private static ModelManager models;
    private static FormCategories formCategories;
    private static ScreenshotRecorder screenshotRecorder;
    private static VideoRecorder videoRecorder;
    private static EntitySelectors selectors;

    private static ParticleManager particles;

    private static KeyBinding keyDashboard;
    private static KeyBinding keyItemEditor;
    private static KeyBinding keyPlayFilm;
    private static KeyBinding keyPauseFilm;
    private static KeyBinding keyRecordReplay;
    private static KeyBinding keyRecordVideo;
    private static KeyBinding keyPlayFilmAndRecord;
    private static KeyBinding keyOpenReplays;
    private static KeyBinding keyOpenMorphing;
    private static KeyBinding keyDemorph;
    private static KeyBinding keyTeleport;
    private static KeyBinding keyZoom;

    private static UIDashboard dashboard;

    private static CameraController cameraController = new CameraController();
    private static ModelBlockItemRenderer modelBlockItemRenderer = new ModelBlockItemRenderer();
    private static GunItemRenderer gunItemRenderer = new GunItemRenderer();
    private static Films films;
    private static GunZoom gunZoom;
    private static String playFilmAndRecordFilmId;

    private static PendingVideoExportState pendingVideoExportState = PendingVideoExportState.NONE;
    private static long pendingVideoExportStartAtMs;
    private static int pendingVideoExportWidth;
    private static int pendingVideoExportHeight;
    private static final WorldExportWindowSession worldExportWindowSession = new WorldExportWindowSession();

    private static float originalFramebufferScale;

    private enum PendingVideoExportState
    {
        NONE,
        VIDEO_DELAY,
        FILM_WAIT_FIRST_TICK,
        FILM_DELAY_PAUSED
    }

    private static class VideoSize
    {
        private final int width;
        private final int height;

        private VideoSize(int width, int height)
        {
            this.width = width;
            this.height = height;
        }
    }

    public static TextureManager getTextures()
    {
        return textures;
    }

    public static FramebufferManager getFramebuffers()
    {
        return framebuffers;
    }

    public static SoundManager getSounds()
    {
        return sounds;
    }

    public static L10n getL10n()
    {
        return l10n;
    }

    public static ModelManager getModels()
    {
        return models;
    }

    public static FormCategories getFormCategories()
    {
        return formCategories;
    }

    public static ScreenshotRecorder getScreenshotRecorder()
    {
        return screenshotRecorder;
    }

    public static VideoRecorder getVideoRecorder()
    {
        return videoRecorder;
    }

    public static EntitySelectors getSelectors()
    {
        return selectors;
    }

    public static ParticleManager getParticles()
    {
        return particles;
    }

    public static CameraController getCameraController()
    {
        return cameraController;
    }

    public static Films getFilms()
    {
        return films;
    }

    public static GunZoom getGunZoom()
    {
        return gunZoom;
    }

    public static KeyBinding getKeyZoom()
    {
        return keyZoom;
    }

    public static KeyBinding getKeyRecordVideo()
    {
        return keyRecordVideo;
    }

    public static boolean isVideoExportDelayPending()
    {
        return pendingVideoExportState != PendingVideoExportState.NONE;
    }

    public static long getVideoExportDelayRemainingMs()
    {
        if (!isVideoExportDelayPending())
        {
            return 0L;
        }

        return Math.max(0L, pendingVideoExportStartAtMs - System.currentTimeMillis());
    }

    /** Returns the dashboard without creating it. Used to avoid creating UI when handling keys (e.g. F6) before user has opened BBS. */
    public static UIDashboard getDashboardIfCreated()
    {
        return dashboard;
    }

    public static UIDashboard getDashboard()
    {
        if (dashboard == null)
        {
            dashboard = new UIDashboard();
        }

        return dashboard;
    }

    public static int getGUIScale()
    {
        int scale = BBSSettings.userIntefaceScale.get();

        if (scale == 0)
        {
            return MinecraftClient.getInstance().options.getGuiScale().getValue();
        }

        return scale;
    }

    public static float getOriginalFramebufferScale()
    {
        return Math.max(originalFramebufferScale, 1);
    }

    public static ModelProperties getItemStackProperties(ItemStack stack)
    {
        ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);

        if (item != null)
        {
            return item.entity.getProperties();
        }

        GunItemRenderer.Item gunItem = gunItemRenderer.get(stack);

        if (gunItem != null)
        {
            return gunItem.properties;
        }

        return null;
    }

    public static void onEndKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        if (action != GLFW.GLFW_PRESS)
        {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null || MinecraftClient.getInstance().currentScreen != null)
        {
            return;
        }

        Morph morph = Morph.getMorph(player);

        /* Animation state trigger */
        if (morph != null && morph.getForm() != null && morph.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_MORPH);
            form.playState(state);
        }))
            return;

        /* Animation state trigger for items*/
        ModelProperties main = getItemStackProperties(player.getStackInHand(Hand.MAIN_HAND));
        ModelProperties offhand = getItemStackProperties(player.getStackInHand(Hand.OFF_HAND));

        if (main != null && main.getForm() != null && main.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_MAIN_HAND_ITEM);
            form.playState(state);
        }))
            return;

        if (offhand != null && offhand.getForm() != null && offhand.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_OFF_HAND_ITEM);
            form.playState(state);
        }))
            return;

        /* Change form based on the hotkey */
        for (Form form : BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).getForms())
        {
            if (form.hotkey.get() == key)
            {
                ClientNetwork.sendPlayerForm(form);

                return;
            }
        }

        for (UserFormCategory category : BBSModClient.getFormCategories().getUserForms().categories)
        {
            for (Form form : category.getForms())
            {
                if (form.hotkey.get() == key)
                {
                    ClientNetwork.sendPlayerForm(form);

                    return;
                }
            }
        }
    }

    @Override
    public void onInitializeClient()
    {
        AssetProvider provider = BBSMod.getProvider();

        textures = new TextureManager(provider);
        framebuffers = new FramebufferManager();
        sounds = new SoundManager(provider);
        l10n = new L10n();
        l10n.register((lang) -> Collections.singletonList(Link.assets("strings/" + lang + ".json")));
        l10n.reload();

        BBSMod.events.post(new RegisterL10nEvent(l10n));

        File parentFile = BBSMod.getSettingsFolder().getParentFile();

        particles = new ParticleManager(() -> new File(BBSMod.getAssetsFolder(), "particles"));

        models = new ModelManager(provider);
        formCategories = new FormCategories();
        screenshotRecorder = new ScreenshotRecorder(new File(parentFile, "screenshots"));
        videoRecorder = new VideoRecorder();
        selectors = new EntitySelectors();
        selectors.read();
        films = new Films();

        BBSResources.init();

        URLRepository repository = new URLRepository(new File(parentFile, "url_cache"));

        provider.register(new URLSourcePack("http", repository));
        provider.register(new URLSourcePack("https", repository));

        KeybindSettings.registerClasses();

        BBSMod.setupConfig(Icons.KEY_CAP, "keybinds", new File(BBSMod.getSettingsFolder(), "keybinds.json"), KeybindSettings::register);

        BBSMod.events.post(new RegisterClientSettingsEvent());

        BBSSettings.language.postCallback((v, f) -> reloadLanguage(getLanguageKey()));
        BBSSettings.editorSeconds.postCallback((v, f) ->
        {
            if (dashboard != null && dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                panel.fillData();
            }
        });

        BBSSettings.theme.modes(
            UIKeys.ENGINE_THEME_LIGHT,
            UIKeys.ENGINE_THEME_DARK
        );

        BBSSettings.keystrokeMode.modes(
            UIKeys.ENGINE_KEYSTROKES_POSITION_AUTO,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_LEFT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_LEFT
        );

        BBSSettings.rotate3dSphereMode.modes(
            UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_TRACKBALL,
            UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_ARCBALL
        );

        UIKeys.C_KEYBIND_CATGORIES.load(KeyCombo.getCategoryKeys());
        UIKeys.C_KEYBIND_CATGORIES_TOOLTIP.load(KeyCombo.getCategoryKeys());

        /* Replace audio clip with client version that plays audio */
        BBSMod.getFactoryCameraClips()
            .register(Link.bbs("audio"), AudioClientClip.class, new ClipFactoryData(Icons.SOUND, 0xffc825))
            .register(Link.bbs("tracker"), TrackerClientClip.class, new ClipFactoryData(Icons.USER, 0x4cedfc))
            .register(Link.bbs("curve"), CurveClientClip.class, new ClipFactoryData(Icons.ARC, 0xff1493));

        /* Keybinds */
        keyDashboard = this.createKey("dashboard", GLFW.GLFW_KEY_0);
        keyItemEditor = this.createKey("item_editor", GLFW.GLFW_KEY_HOME);
        keyPlayFilm = this.createKey("play_film", GLFW.GLFW_KEY_RIGHT_CONTROL);
        keyPauseFilm = this.createKey("pause_film", GLFW.GLFW_KEY_BACKSLASH);
        keyRecordReplay = this.createKey("record_replay", GLFW.GLFW_KEY_RIGHT_ALT);
        keyRecordVideo = this.createKey("record_video", GLFW.GLFW_KEY_F4);
        keyPlayFilmAndRecord = this.createKey("play_film_and_record", GLFW.GLFW_KEY_F6);
        keyOpenReplays = this.createKey("open_replays", GLFW.GLFW_KEY_RIGHT_SHIFT);
        keyOpenMorphing = this.createKey("open_morphing", GLFW.GLFW_KEY_B);
        keyDemorph = this.createKey("demorph", GLFW.GLFW_KEY_PERIOD);
        keyTeleport = this.createKey("teleport", GLFW.GLFW_KEY_Y);
        keyZoom = this.createKeyMouse("zoom", 2);

        WorldRenderEvents.AFTER_ENTITIES.register((context) ->
        {
            if (!BBSRendering.isIrisShadersEnabled())
            {
                BBSRendering.renderCoolStuff(context);
            }

            if (BBSSettings.chromaSkyEnabled.get())
            {
                float d = BBSSettings.chromaSkyBillboard.get();

                if (d > 0)
                {
                    MatrixStack stack = context.matrixStack();
                    Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
                    Color color = Colors.COLOR.set(fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get());

                    stack.push();

                    MatrixStack.Entry peek = stack.peek();

                    peek.getPositionMatrix().identity();
                    peek.getNormalMatrix().identity();
                    stack.translate(0F, 0F, -d);

                    RenderSystem.enableDepthTest();
                    BufferBuilder builder = Tessellator.getInstance().getBuffer();

                    builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

                    float fov = MinecraftClient.getInstance().options.getFov().getValue();
                    float dd = d * (float) Math.pow(fov / 40F, 2F);

                    Draw.fillQuad(builder, stack,
                        -dd, -dd, 0,
                        dd, -dd, 0,
                        dd, dd, 0,
                        -dd, dd, 0,
                        color.r, color.g, color.b, 1F
                    );

                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);

                    BufferRenderer.drawWithGlobalProgram(builder.end());
                    RenderSystem.disableDepthTest();

                    stack.pop();
                }
            }
        });

        WorldRenderEvents.LAST.register((context) ->
        {
            if (videoRecorder.isRecording() && BBSRendering.canRender)
            {
                videoRecorder.recordFrame();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            dashboard = null;
            films = new Films();
            this.stopVideoRecording();
            playFilmAndRecordFilmId = null;

            ClientNetwork.resetHandshake();
            films.reset();
            cameraController.reset();
        });

        ClientTickEvents.START_CLIENT_TICK.register((client) ->
        {
            BBSRendering.startTick();
        });

        ClientTickEvents.END_WORLD_TICK.register((client) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (!mc.isPaused())
            {
                films.updateEndWorld();
            }

            BBSResources.tick();
        });

        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.currentScreen instanceof UIScreen screen)
            {
                screen.update();
            }

            cameraController.update();

            if (!mc.isPaused())
            {
                films.update();
                modelBlockItemRenderer.update();
                gunItemRenderer.update();
                textures.update();
            }

            if (playFilmAndRecordFilmId != null && this.hasPendingPlayFilmAndRecord() && !films.has(playFilmAndRecordFilmId))
            {
                this.stopVideoRecording();
                this.clearPlayFilmAndRecordSessionState();
            }

            this.updatePendingVideoRecording();

            if (playFilmAndRecordFilmId != null && !videoRecorder.isRecording() && !this.hasPendingPlayFilmAndRecord())
            {
                this.restoreWorldExportWindowSize();
                this.clearPlayFilmAndRecordSessionState();
            }

            while (keyDashboard.wasPressed()) UIScreen.open(getDashboard());
            while (keyItemEditor.wasPressed()) this.keyOpenModelBlockEditor(mc);
            while (keyPlayFilm.wasPressed()) this.keyPlayFilm();
            while (keyPauseFilm.wasPressed()) this.keyPauseFilm();
            while (keyRecordReplay.wasPressed()) this.keyRecordReplay();
            while (keyRecordVideo.wasPressed()) this.keyRecordVideo(mc);
            while (keyPlayFilmAndRecord.wasPressed()) this.keyPlayFilmAndRecord();
            while (keyOpenReplays.wasPressed()) this.keyOpenReplays();
            while (keyOpenMorphing.wasPressed())
            {
                UIDashboard dashboard = getDashboard();

                UIScreen.open(dashboard);
                dashboard.setPanel(dashboard.getPanel(UIMorphingPanel.class));
            }
            while (keyDemorph.wasPressed()) ClientNetwork.sendPlayerForm(null);
            while (keyTeleport.wasPressed()) this.keyTeleport();

            if (mc.player != null)
            {
                boolean zoom = keyZoom.isPressed();
                ItemStack stack = mc.player.getMainHandStack();

                if (gunZoom == null && zoom && stack.getItem() == BBSMod.GUN_ITEM)
                {
                    GunProperties properties = GunProperties.get(stack);

                    ClientNetwork.sendZoom(true);
                    gunZoom = new GunZoom(properties.fovTarget, properties.fovInterp, properties.fovDuration);
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
        {
            BBSRendering.renderHud(drawContext, tickDelta);

            if (gunZoom != null)
            {
                gunZoom.update(keyZoom.isPressed(), MinecraftClient.getInstance().getLastFrameDuration());

                if (gunZoom.canBeRemoved())
                {
                    ClientNetwork.sendZoom(false);
                    gunZoom = null;
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((e) -> BBSResources.stopWatchdog());
        ClientLifecycleEvents.CLIENT_STARTED.register((e) ->
        {
            BBSRendering.setupFramebuffer();
            provider.register(new MinecraftSourcePack());

            Window window = MinecraftClient.getInstance().getWindow();

            originalFramebufferScale = window.getFramebufferWidth() / window.getWidth();
        });

        URLTextureErrorCallback.EVENT.register((url, error) ->
        {
            UIBaseMenu menu = UIScreen.getCurrentMenu();

            if (menu != null)
            {
                url = url.substring(0, MathUtils.clamp(url.length(), 0, 40));

                if (error == URLError.FFMPEG)
                {
                    menu.context.notifyError(UIKeys.TEXTURE_URL_ERROR_FFMPEG.format(url));
                }
                else if (error == URLError.HTTP_ERROR)
                {
                    menu.context.notifyError(UIKeys.TEXTURE_URL_ERROR_HTTP.format(url));
                }
            }
        });

        BBSRendering.setup();

        /* Network */
        ClientNetwork.setup();

        /* Entity renderers */
        EntityRendererRegistry.register(BBSMod.ACTOR_ENTITY, ActorEntityRenderer::new);
        EntityRendererRegistry.register(BBSMod.GUN_PROJECTILE_ENTITY, GunProjectileEntityRenderer::new);

        BlockEntityRendererRegistryImpl.register(BBSMod.MODEL_BLOCK_ENTITY, ModelBlockEntityRenderer::new);

        BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.MODEL_BLOCK_ITEM, modelBlockItemRenderer);
        BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.GUN_ITEM, gunItemRenderer);

        /* Create folders */
        BBSMod.getAudioFolder().mkdirs();
        BBSMod.getAssetsPath("textures").mkdirs();

        for (String path : List.of("alex", "alex_simple", "steve", "steve_simple"))
        {
            BBSMod.getAssetsPath("models/emoticons/" + path + "/").mkdirs();
        }

        for (String path : List.of("alex", "alex_bends", "eyes", "eyes_1px", "steve", "steve_bends"))
        {
            BBSMod.getAssetsPath("models/player/" + path + "/").mkdirs();
        }
    }

    private void keyRecordVideo(MinecraftClient mc)
    {
        if (this.hasPendingVideoRecording())
        {
            if (this.hasPendingPlayFilmAndRecord() || playFilmAndRecordFilmId != null)
            {
                this.stopPlayFilmAndRecordSession(true);
            }
            else
            {
                this.stopVideoRecording();
            }

            return;
        }

        if (videoRecorder.isRecording())
        {
            this.stopVideoRecording();

            return;
        }

        Window window = mc.getWindow();
        VideoSize videoSize = this.getWorldExportVideoSize(window);

        this.applyWorldExportWindowSize(videoSize);
        this.startVideoRecording(videoSize.width, videoSize.height, false);
    }

    private KeyBinding createKey(String id, int key)
    {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + "." + id,
            InputUtil.Type.KEYSYM,
            key,
            "category." + BBSMod.MOD_ID + ".main"
        ));
    }

    private KeyBinding createKeyMouse(String id, int button)
    {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + "." + id,
            InputUtil.Type.MOUSE,
            button,
            "category." + BBSMod.MOD_ID + ".main"
        ));
    }

    private void keyOpenModelBlockEditor(MinecraftClient mc)
    {
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.MAINHAND);
        ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);
        GunItemRenderer.Item gunItem = gunItemRenderer.get(stack);

        if (item != null)
        {
            UIScreen.open(new UIModelBlockEditorMenu(item.entity.getProperties()));
        }
        else if (gunItem != null)
        {
            UIScreen.open(new UIModelBlockEditorMenu(gunItem.properties));
        }
    }

    private void keyPlayFilm()
    {
        if (getDashboardIfCreated() == null)
        {
            return;
        }

        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);
        if (panel.getData() != null)
        {
            Films.playFilm(panel.getData().getId(), false);
        }
    }

    /**
     * Start video recording and film playback together (Ctrl+F4).
     * Recording stops automatically when the film finishes.
     */
    private void keyPlayFilmAndRecord()
    {
        if (getDashboardIfCreated() == null)
        {
            return;
        }

        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);
        if (panel.getData() == null)
        {
            return;
        }

        Film film = panel.getData();
        boolean sameComboSession = film.getId().equals(playFilmAndRecordFilmId);

        if (sameComboSession && (videoRecorder.isRecording() || this.hasPendingPlayFilmAndRecord()))
        {
            this.stopPlayFilmAndRecordSession(true);

            return;
        }

        if (videoRecorder.isRecording() || this.hasPendingVideoRecording() || playFilmAndRecordFilmId != null)
        {
            return;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        VideoSize videoSize = this.getWorldExportVideoSize(window);

        playFilmAndRecordFilmId = film.getId();

        this.applyWorldExportWindowSize(videoSize);
        this.startVideoRecording(videoSize.width, videoSize.height, true);

        Films.playFilm(film.getId(), false);
        getFilms().setStopVideoRecordingWhenFilmFinished(film.getId());
    }

    private boolean startVideoRecording(int width, int height, boolean playFilmAndRecord)
    {
        float delaySeconds = Math.max(0F, BBSSettings.videoDelay.get());
        long delayMs = (long) (delaySeconds * 1000F);

        if (delayMs <= 0L)
        {
            videoRecorder.startRecording(null, BBSRendering.getTexture().id, width, height);
            BBSRendering.setCustomSize(videoRecorder.isRecording(), width, height);

            return false;
        }

        this.clearPendingVideoRecording();

        pendingVideoExportState = playFilmAndRecord ? PendingVideoExportState.FILM_WAIT_FIRST_TICK : PendingVideoExportState.VIDEO_DELAY;
        pendingVideoExportStartAtMs = System.currentTimeMillis() + delayMs;
        pendingVideoExportWidth = width;
        pendingVideoExportHeight = height;

        /* Keep export resolution during warmup so first captured frame is already settled. */
        BBSRendering.setCustomSize(true, width, height);

        return true;
    }

    private void updatePendingVideoRecording()
    {
        if (!this.hasPendingVideoRecording())
        {
            return;
        }

        if (pendingVideoExportState == PendingVideoExportState.FILM_WAIT_FIRST_TICK)
        {
            if (!this.pausePlayFilmAndRecordAfterFirstTick())
            {
                return;
            }

            pendingVideoExportState = PendingVideoExportState.FILM_DELAY_PAUSED;
        }

        if (System.currentTimeMillis() < pendingVideoExportStartAtMs)
        {
            return;
        }

        int width = pendingVideoExportWidth;
        int height = pendingVideoExportHeight;
        PendingVideoExportState previousState = pendingVideoExportState;

        this.clearPendingVideoRecording();

        if (previousState == PendingVideoExportState.FILM_DELAY_PAUSED)
        {
            this.resumePlayFilmAndRecordAfterDelay();
        }

        videoRecorder.startRecording(null, BBSRendering.getTexture().id, width, height);
        BBSRendering.setCustomSize(videoRecorder.isRecording(), width, height);
    }

    private boolean pausePlayFilmAndRecordAfterFirstTick()
    {
        if (playFilmAndRecordFilmId == null)
        {
            return true;
        }

        BaseFilmController controller = films.getController(playFilmAndRecordFilmId);

        if (controller == null || controller.getTick() < 1)
        {
            return false;
        }

        if (!controller.paused)
        {
            controller.togglePause();
        }

        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendActionState(playFilmAndRecordFilmId, ActionState.PAUSE, controller.getTick());
        }

        return true;
    }

    private void resumePlayFilmAndRecordAfterDelay()
    {
        if (playFilmAndRecordFilmId == null)
        {
            return;
        }

        BaseFilmController controller = this.getFilms().getController(playFilmAndRecordFilmId);
        int tick = 0;

        if (controller != null)
        {
            tick = Math.max(controller.getTick(), 0);

            if (controller.paused)
            {
                controller.togglePause();
            }
        }

        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendActionState(playFilmAndRecordFilmId, ActionState.PLAY, tick);
        }
    }

    private int getEvenVideoDimension(int value)
    {
        value = Math.max(value, 2);

        return value % 2 == 0 ? value : value - 1;
    }

    private VideoSize getWorldExportVideoSize(Window window)
    {
        if (BBSSettings.worldExportResizeWindow.get())
        {
            int width = this.getEvenVideoDimension(BBSSettings.videoWidth.get());
            int height = this.getEvenVideoDimension(BBSSettings.videoHeight.get());

            return new VideoSize(width, height);
        }

        int width = this.getEvenVideoDimension(window.getWidth());
        int height = this.getEvenVideoDimension(window.getHeight());

        return new VideoSize(width, height);
    }

    private void applyWorldExportWindowSize(VideoSize videoSize)
    {
        if (BBSSettings.worldExportResizeWindow.get())
        {
            worldExportWindowSession.begin(videoSize.width, videoSize.height);

            return;
        }

        worldExportWindowSession.clear();
    }

    private void restoreWorldExportWindowSize()
    {
        worldExportWindowSession.restore();
    }

    private boolean hasPendingVideoRecording()
    {
        return pendingVideoExportState != PendingVideoExportState.NONE;
    }

    private boolean hasPendingPlayFilmAndRecord()
    {
        return pendingVideoExportState == PendingVideoExportState.FILM_WAIT_FIRST_TICK || pendingVideoExportState == PendingVideoExportState.FILM_DELAY_PAUSED;
    }

    private void clearPendingVideoRecording()
    {
        pendingVideoExportState = PendingVideoExportState.NONE;
        pendingVideoExportStartAtMs = 0L;
        pendingVideoExportWidth = 0;
        pendingVideoExportHeight = 0;
    }

    private void stopPlayFilmAndRecordSession(boolean stopFilm)
    {
        this.stopVideoRecording();

        if (stopFilm && playFilmAndRecordFilmId != null && films.has(playFilmAndRecordFilmId))
        {
            Films.playFilm(playFilmAndRecordFilmId, false);
        }

        this.clearPlayFilmAndRecordSessionState();
    }

    private void clearPlayFilmAndRecordSessionState()
    {
        this.getFilms().clearStopVideoRecordingWhenFilmFinished();
        playFilmAndRecordFilmId = null;
    }

    private void stopVideoRecording()
    {
        this.clearPendingVideoRecording();

        if (videoRecorder.isRecording())
        {
            videoRecorder.stopRecording();
        }

        BBSRendering.setCustomSize(false, 0, 0);
        this.restoreWorldExportWindowSize();
    }

    private void keyPauseFilm()
    {
        if (getDashboardIfCreated() == null)
        {
            return;
        }

        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);
        if (panel.getData() != null)
        {
            Films.pauseFilm(panel.getData().getId());
        }
    }

    private void keyRecordReplay()
    {
        UIDashboard dashboard = getDashboard();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.getData() != null)
        {
            Recorder recorder = getFilms().getRecorder();

            if (recorder != null)
            {
                recorder = BBSModClient.getFilms().stopRecording();

                if (recorder == null || recorder.hasNotStarted() || panel.getData() == null)
                {
                    return;
                }

                panel.applyRecordedKeyframes(recorder, panel.getData());
            }
            else
            {
                Replay replay = panel.replayEditor.getReplay();
                int index = panel.getData().replays.getList().indexOf(replay);

                if (index >= 0)
                {
                    getFilms().startRecording(panel.getData(), index, 0);
                }
            }
        }
    }

    private void keyOpenReplays()
    {
        UIDashboard dashboard = getDashboard();

        UIScreen.open(dashboard);

        if (dashboard.getPanels().panel instanceof UIFilmPanel panel && panel.getData() != null)
        {
            panel.showPanel(panel.replayEditor);
        }
        else
        {
            dashboard.setPanel(dashboard.getPanel(UIFilmPanel.class));
        }
    }

    private void keyTeleport()
    {
        UIDashboard dashboard = getDashboard();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel != null)
        {
            panel.replayEditor.teleport();
        }
    }

    public static String getLanguageKey()
    {
        return getLanguageKey(BBSSettings.language.get());
    }

    public static String getLanguageKey(String key)
    {
        if (key.isEmpty())
        {
            key = MinecraftClient.getInstance().options.language;
        }

        return key;
    }

    public static void reloadLanguage(String language)
    {
        l10n.reload(language, BBSMod.getProvider());
    }
}
