package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.AddonLifecycle;
import mchorse.bbs_mod.api.client.events.*;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.resources.packs.ClasspathSourcePack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.ExternalAssetsSourcePack;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.item.Item;
import java.io.File;
import mchorse.bbs_mod.forge.studio.StudioSession;
import mchorse.bbs_mod.forge.studio.StudioScreen;

public class ClientProxy extends CommonProxy {
    public static ModelManager models;
    private net.minecraft.world.World lastWorld;
    private boolean heldLoadingScreen;
    private final java.util.Queue<net.minecraft.network.NetworkManager> disconnectedConnections = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public void preInit() {
        super.preInit();
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="bbs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static class Models {
        @SubscribeEvent public static void bake(net.minecraftforge.client.event.ModelBakeEvent event) {
            ModelResourceLocation id = new ModelResourceLocation("bbs:model", "inventory");
            net.minecraft.client.renderer.block.model.IBakedModel baked = event.getModelRegistry().getObject(id);
            if (baked != null) event.getModelRegistry().putObject(id, new ModelItemRenderer.Baked(baked));
            ModelResourceLocation gunId = new ModelResourceLocation("bbs:gun", "inventory");
            net.minecraft.client.renderer.block.model.IBakedModel gunBaked = event.getModelRegistry().getObject(gunId);
            if (gunBaked != null) event.getModelRegistry().putObject(gunId, new mchorse.bbs_mod.client.renderer.item.GunItemRenderer.Baked(gunBaked));
        }
        @SubscribeEvent public static void register(net.minecraftforge.client.event.ModelRegistryEvent event) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MODEL_BLOCK),0,new ModelResourceLocation("bbs:model","inventory"));
            ModelLoader.setCustomModelResourceLocation(STRUCTURE_WAND,0,new ModelResourceLocation("bbs:structure_wand","inventory"));
            for(net.minecraft.block.Block block:CHROMA_BLOCKS)ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block),0,new ModelResourceLocation(block.getRegistryName(),"inventory"));
            ModelLoader.setCustomModelResourceLocation(GUN_ITEM,0,new ModelResourceLocation("bbs:gun","inventory"));
        }
    }
    public void init() {
        AddonLifecycle.registerClient();
        mchorse.bbs_mod.BBSModClient.getL10n();
        BBSMod.events.post(new RegisterModelLoadersEvent());
        BBSMod.events.post(new RegisterFormSectionsEvent());
        models = new ModelManager(BBSMod.getProvider());
        mchorse.bbs_mod.utils.resources.PlayerSkins.init(new File(BBSMod.getAssetsFolder().getParentFile(), "skin_cache"));
        BBSMod.getProvider().register(new mchorse.bbs_mod.utils.resources.PlayerSkinSourcePack());
        ClientRegistry.bindTileEntitySpecialRenderer(ModelTileEntity.class,new ModelTileRenderer());
        GlobalKeybinds.register();
        Item.getItemFromBlock(MODEL_BLOCK).setTileEntityItemStackRenderer(ModelItemRenderer.INSTANCE);
        GUN_ITEM.setTileEntityItemStackRenderer(mchorse.bbs_mod.client.renderer.item.GunItemRenderer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new GunClientHandler());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new mchorse.bbs_mod.client.renderer.MorphRenderer());
        mchorse.bbs_mod.selectors.SelectorOwnerCapability.register();
        MinecraftForge.EVENT_BUS.register(new mchorse.bbs_mod.selectors.SelectorClientHandler());
        MinecraftForge.EVENT_BUS.register(new mchorse.bbs_mod.forge.camera.ForgeCameraHandler(mchorse.bbs_mod.BBSModClient.getCameraController()));
        BBSMod.events.post(new RegisterTrackCategoriesEvent());
        mchorse.bbs_mod.api.client.editor.TrackCategories.finishRegistration();
        mchorse.bbs_mod.ui.utils.keys.KeybindSettings.registerClasses();
        BBSMod.events.post(new RegisterKeybindsEvent());
        mchorse.bbs_mod.ui.utils.keys.KeybindSettings.migrateTrackSearchShortcuts(BBSMod.setupConfig(
            mchorse.bbs_mod.ui.utils.icons.Icons.KEY_CAP, "keybinds", BBSMod.getSettingsPath("keybinds.json"),
            mchorse.bbs_mod.ui.utils.keys.KeybindSettings::register));
        BBSMod.events.post(new RegisterClientSettingsEvent());
        mchorse.bbs_mod.BBSModClient.configureSettingsUI();
        mchorse.bbs_mod.ui.UIKeys.C_KEYBIND_CATGORIES.load(mchorse.bbs_mod.ui.utils.keys.KeyCombo.getCategoryKeys());
        mchorse.bbs_mod.ui.UIKeys.C_KEYBIND_CATGORIES_TOOLTIP.load(mchorse.bbs_mod.ui.utils.keys.KeyCombo.getCategoryKeys());
        BBSMod.getFactoryCameraClips()
            .register(Link.bbs("audio"), mchorse.bbs_mod.camera.clips.misc.AudioClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(mchorse.bbs_mod.ui.utils.icons.Icons.SOUND, 0xffc825))
            .register(Link.bbs("video"), mchorse.bbs_mod.camera.clips.misc.VideoClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(mchorse.bbs_mod.ui.utils.icons.Icons.VIDEO_CAMERA, 0xd21f3c))
            .register(Link.bbs("tracker"), mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(mchorse.bbs_mod.ui.utils.icons.Icons.USER, 0x4cedfc))
            .register(Link.bbs("spline"), mchorse.bbs_mod.camera.clips.misc.SplineClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(mchorse.bbs_mod.ui.utils.icons.Icons.GRAPH, 0x5599ff))
            .register(Link.bbs("curve"), mchorse.bbs_mod.camera.clips.misc.CurveClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(mchorse.bbs_mod.ui.utils.icons.Icons.ARC, 0xff1493));
        mchorse.bbs_mod.ui.film.clips.renderer.UIClipRenderers.setup();
        BBSMod.events.post(new RegisterClipRenderersEvent());
        mchorse.bbs_mod.forms.FormUtilsClient.setup();
        BBSMod.events.post(new RegisterFormRenderersEvent());
        mchorse.bbs_mod.ui.forms.editors.UIFormEditor.setup();
        BBSMod.events.post(new RegisterFormEditorsEvent());
        BBSMod.events.post(new RegisterFormPanelsEvent());
        BBSMod.events.post(new RegisterReplayActionsEvent());
        mchorse.bbs_mod.ui.film.clips.UIClip.setup();
        BBSMod.events.post(new RegisterClipPanelsEvent());
        mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory.setup();
        BBSMod.events.post(new RegisterKeyframeEditorsEvent());
        mchorse.bbs_mod.settings.ui.UIValueMap.setup();
        BBSMod.events.post(new RegisterValueWidgetsEvent());
        mchorse.bbs_mod.film.replays.tracks.TrackStyle.setup();
        BBSMod.events.post(new RegisterTrackStylesEvent());
        mchorse.bbs_mod.importers.Importers.setup();
        BBSMod.events.post(new RegisterImportersEvent());
        mchorse.bbs_mod.ui.film.FrameOverlays.setup();
        BBSMod.events.post(new RegisterFrameOverlaysEvent());
        BBSMod.events.post(new RegisterPreviewOverlaysEvent());
        BBSMod.events.post(new RegisterFilmToolsEvent());
        mchorse.bbs_mod.fonts.nativefonts.NativeDefaultFont.prepare();
        mchorse.bbs_mod.cubic.animation.ItemUsePose.setSource(mchorse.bbs_mod.client.renderer.ThirdPersonItemUse::get);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(mchorse.bbs_mod.entity.ActorEntity.class,
            mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(mchorse.bbs_mod.entity.GunProjectileEntity.class,
            mchorse.bbs_mod.client.renderer.entity.GunProjectileEntityRenderer::new);
        mchorse.bbs_mod.utils.resources.CemResourceLifecycle.install();
        BBSMod.getProvider().register(new mchorse.bbs_mod.utils.resources.MinecraftSourcePack());
        models.reload();
        mchorse.bbs_mod.BBSResources.init();
        registerDashboardPanels();
        mchorse.bbs_mod.forms.structure.StructureWand.register();
        ModelBlock.editingCheck = () -> mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() instanceof mchorse.bbs_mod.ui.dashboard.UIDashboard
            || (Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().player.getHeldItemMainhand().getItem() == Item.getItemFromBlock(MODEL_BLOCK));
        BBSMod.events.post(new BBSClientReadyEvent());
        BBSMod.LOGGER.info("BBS FS Forge client ready; 0 opens the dashboard, F4 records the world, F6 records the selected film");
    }
    private static void registerDashboardPanels() {
        mchorse.bbs_mod.ui.dashboard.DashboardPanelRegistry.registerPinned("selectors",
            mchorse.bbs_mod.ui.utils.icons.Icons.PROPERTIES, mchorse.bbs_mod.ui.UIKeys.SELECTORS_TITLE,
            context -> mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(context,
                new mchorse.bbs_mod.ui.selectors.UISelectorsOverlayPanel(), 430, 300));
        mchorse.bbs_mod.ui.dashboard.DashboardPanelRegistry.register("particles", 3,
            mchorse.bbs_mod.ui.UIKeys.SNOWSTORM_TITLE, mchorse.bbs_mod.ui.utils.icons.Icons.PARTICLE,
            mchorse.bbs_mod.ui.particles.UIParticleSchemePanel::new);
        mchorse.bbs_mod.ui.dashboard.DashboardPanelRegistry.register("model_blocks", 2,
            mchorse.bbs_mod.ui.UIKeys.MODEL_BLOCKS_TITLE, mchorse.bbs_mod.ui.utils.icons.Icons.BLOCK,
            mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel::new);
        mchorse.bbs_mod.ui.dashboard.DashboardPanelRegistry.register("morphing", 0,
            mchorse.bbs_mod.ui.UIKeys.MORPHING_TITLE, mchorse.bbs_mod.ui.utils.icons.Icons.MORPH,
            mchorse.bbs_mod.ui.morphing.UIMorphingPanel::new);
        mchorse.bbs_mod.ui.dashboard.DashboardPanelRegistry.register("film", 1,
            mchorse.bbs_mod.ui.UIKeys.FILM_TITLE, mchorse.bbs_mod.ui.utils.icons.Icons.FILM, dashboard -> {
                mchorse.bbs_mod.ui.film.UIFilmPanel panel = new mchorse.bbs_mod.ui.film.UIFilmPanel(dashboard);
                mchorse.bbs_mod.network.FilmClientBridge.bind(panel::getData, panel::receiveActions);
                return panel;
            }).layout((panel, data) -> ((mchorse.bbs_mod.ui.film.UIFilmPanel) panel).applyLayoutPreset(data))
            .exportSize(panel -> mchorse.bbs_mod.ui.film.UIFilmPanel.applyExportSizeToBBS());
        mchorse.bbs_mod.ui.dashboard.DashboardPanelRegistry.register("model_editor", 4,
            mchorse.bbs_mod.ui.UIKeys.MODEL_EDITOR_TITLE, mchorse.bbs_mod.ui.utils.icons.Icons.POSE,
            mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel::new);
        mchorse.bbs_mod.ui.onboarding.Onboarding.registerShown(mchorse.bbs_mod.ui.morphing.UIMorphingPanel.class, mchorse.bbs_mod.ui.onboarding.Tours.MORPHING);
        mchorse.bbs_mod.ui.onboarding.Onboarding.registerOpened(mchorse.bbs_mod.ui.film.UIFilmPanel.class, mchorse.bbs_mod.ui.onboarding.Tours.FILM);
        mchorse.bbs_mod.ui.onboarding.Onboarding.registerOpened(mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel.class, mchorse.bbs_mod.ui.onboarding.Tours.MODEL_EDITOR);
    }
    public void openModel(ModelTileEntity tile) {
        if (tile == null) return;
        mchorse.bbs_mod.ui.dashboard.UIDashboard menu = mchorse.bbs_mod.BBSModClient.getDashboard();
        menu.finishBuilding();
        mchorse.bbs_mod.ui.framework.UIScreen.open(menu);
        mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel panel = menu.getPanel(mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel.class);
        menu.setPanel(panel);
        panel.fill(tile, true);
    }
    @SubscribeEvent public void loadingScreen(net.minecraftforge.client.event.GuiOpenEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getGui() == null && mc.currentScreen instanceof net.minecraft.client.gui.GuiDownloadTerrain
            && mchorse.bbs_mod.ui.dashboard.DashboardWarmup.shouldKeepLoading(mc)) {
            heldLoadingScreen = true;
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public void overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
        mchorse.bbs_mod.ui.framework.UIBaseMenu menu = mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu();
        if (event.getType() == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL
            && (mchorse.bbs_mod.BBSModClient.getCameraController().getCurrent() instanceof mchorse.bbs_mod.camera.controller.PlayCameraController
                || menu != null && menu.canHideHUD())) event.setCanceled(true);
    }
    private void world() {
        Minecraft mc=Minecraft.getMinecraft();
        if(lastWorld==mc.world) return;
        mchorse.bbs_mod.BBSModClient.getWorldExportSession().stop();
        mchorse.bbs_mod.client.renderer.LivePlayerItemUse.endFrame();
        mchorse.bbs_mod.BBSModClient.getFilms().reset();
        StudioSession.unload();
        lastWorld=mc.world;
        ModelItemRenderer.INSTANCE.clear();
        if (mc.world == null) mchorse.bbs_mod.BBSResources.leaveWorld();
        else mchorse.bbs_mod.BBSResources.enterWorld();
    }
    @SubscribeEvent public void studioTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            net.minecraft.network.NetworkManager disconnected;
            while ((disconnected = disconnectedConnections.poll()) != null) resetConnection(disconnected);
            mchorse.bbs_mod.client.renderer.LivePlayerItemUse.endFrame();
            world();
        }
        if (event.phase == TickEvent.Phase.END && Minecraft.getMinecraft().world != null && !Minecraft.getMinecraft().isGamePaused()) {
            mchorse.bbs_mod.BBSModClient.getFilms().updateEndWorld();
            mchorse.bbs_mod.BBSModClient.getFilms().update();
        }
        if (event.phase == TickEvent.Phase.END) {
            ModelItemRenderer.INSTANCE.tick();
            mchorse.bbs_mod.BBSModClient.update();
            mchorse.bbs_mod.BBSResources.tick();
            mchorse.bbs_mod.BBSModClient.getFormCategories().getUserForms().flush();
            mchorse.bbs_mod.ui.dashboard.DashboardWarmup.tick(Minecraft.getMinecraft());
            if (heldLoadingScreen && !mchorse.bbs_mod.ui.dashboard.DashboardWarmup.shouldKeepLoading(Minecraft.getMinecraft())) {
                heldLoadingScreen = false;
                if (Minecraft.getMinecraft().currentScreen instanceof net.minecraft.client.gui.GuiDownloadTerrain)
                    Minecraft.getMinecraft().displayGuiScreen(null);
            }
        }
        if(event.phase==TickEvent.Phase.END && Minecraft.getMinecraft().world!=null && StudioSession.current!=null) StudioSession.current.update();
    }
    @SubscribeEvent public void cameraTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START)
        {
            if (Minecraft.getMinecraft().currentScreen instanceof mchorse.bbs_mod.ui.framework.UIScreen)
                ((mchorse.bbs_mod.ui.framework.UIScreen) Minecraft.getMinecraft().currentScreen).pollFrameInput();
            mchorse.bbs_mod.client.renderer.LivePlayerItemUse.endFrame();
            mchorse.bbs_mod.BBSModClient.beginFrame();
            mchorse.bbs_mod.BBSModClient.getFilms().startRenderFrame(event.renderTickTime);
            mchorse.bbs_mod.client.renderer.LivePlayerItemUse.beginFrame();
            mchorse.bbs_mod.ui.framework.UIBaseMenu menu = mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu();
            if (menu != null) menu.startRenderFrame(event.renderTickTime);
        }
        if(event.phase==TickEvent.Phase.START && StudioSession.current!=null) StudioSession.current.runtime.updateCamera(event.renderTickTime);
        if(event.phase==TickEvent.Phase.END) mchorse.bbs_mod.client.renderer.LivePlayerItemUse.endFrame();
    }
    @SubscribeEvent public void scene(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        mchorse.bbs_mod.graphics.WorldRenderContext context=mchorse.bbs_mod.graphics.WorldRenderContext.capture(event.getPartialTicks());
        int matrixMode=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE);
        boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean wasRenderingWorld=mchorse.bbs_mod.client.BBSRendering.renderingWorld;
        net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.loadIdentity();
        mchorse.bbs_mod.client.BBSRendering.renderingWorld=true;
        try {
            mchorse.bbs_mod.BBSModClient.getFilms().render(context);
            if (Minecraft.getMinecraft().currentScreen instanceof mchorse.bbs_mod.ui.framework.UIScreen)
                ((mchorse.bbs_mod.ui.framework.UIScreen) Minecraft.getMinecraft().currentScreen).renderInWorld(context);
        }
        finally {
            try {
                mchorse.bbs_mod.client.BBSRendering.finishWorldForms();
            }
            finally {
                mchorse.bbs_mod.client.BBSRendering.renderingWorld=wasRenderingWorld;
                net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
                net.minecraft.client.renderer.GlStateManager.matrixMode(matrixMode);
                if(depth)net.minecraft.client.renderer.GlStateManager.enableDepth();else net.minecraft.client.renderer.GlStateManager.disableDepth();
            }
        }
        if(StudioSession.current!=null) StudioSession.current.runtime.render(event.getPartialTicks());
    }
    @SubscribeEvent public void disconnected(net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        /* closeChannel waits for Netty while Minecraft may hold its scheduled-task monitor.
         * Do not take that monitor from the disconnect event; drain on the next client tick. */
        disconnectedConnections.add(event.getManager());
    }
    private void resetConnection(net.minecraft.network.NetworkManager connection) {
            if (Minecraft.getMinecraft().getConnection() != null
                && Minecraft.getMinecraft().getConnection().getNetworkManager() != connection) return;
            mchorse.bbs_mod.client.renderer.LivePlayerItemUse.endFrame();
            mchorse.bbs_mod.BBSModClient.getWorldExportSession().stop();
            mchorse.bbs_mod.BBSModClient.getFilms().reset();
            mchorse.bbs_mod.BBSModClient.getMinecraftSoundCapture().end();
            mchorse.bbs_mod.BBSModClient.resetDashboard();
            mchorse.bbs_mod.network.ClientNetwork.reset();
            mchorse.bbs_mod.forms.renderers.utils.RenderFrame.invalidate();
    }
    @SubscribeEvent public void hud(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if(event.getType()!=net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL)return;
        mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D batcher=new mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D(
            new mchorse.bbs_mod.ui.framework.UIDrawContext(event.getResolution().getScaleFactor()));
        batcher.beginBatch();
        try { mchorse.bbs_mod.BBSModClient.getFilms().renderHud(batcher,event.getPartialTicks()); }
        finally { batcher.endBatch(); }
    }
    @SubscribeEvent public void camera(net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event) {
        if(StudioSession.current!=null && StudioSession.current.runtime.cameraEnabled) event.setRoll(StudioSession.current.runtime.cameraPosition.angle.roll);
    }
    @SubscribeEvent public void fov(net.minecraftforge.client.event.EntityViewRenderEvent.FOVModifier event) {
        if(StudioSession.current!=null && StudioSession.current.runtime.cameraEnabled) event.setFOV(StudioSession.current.runtime.cameraPosition.angle.fov);
    }
}
