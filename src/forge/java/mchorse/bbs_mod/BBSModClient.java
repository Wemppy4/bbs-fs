package mchorse.bbs_mod;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.forge.ClientProxy;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/** Forge client services used by the original BBS UI. */
public final class BBSModClient
{
    private static TextureManager textures;
    private static L10n l10n;
    private static mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard;
    private static mchorse.bbs_mod.forms.FormCategories formCategories;
    public static mchorse.bbs_mod.forms.FormCategories getFormCategories()
    {
        if (formCategories == null) formCategories = new mchorse.bbs_mod.forms.FormCategories();
        return formCategories;
    }
    public static mchorse.bbs_mod.ui.dashboard.UIDashboard getDashboardIfCreated()
    {
        if (dashboard != null) dashboard.finishBuilding();
        return dashboard;
    }
    public static mchorse.bbs_mod.ui.dashboard.UIDashboard getDashboard()
    {
        mchorse.bbs_mod.ui.dashboard.UIDashboard result = getUnfinishedDashboard();
        result.finishBuilding();
        return result;
    }
    public static mchorse.bbs_mod.ui.dashboard.UIDashboard getUnfinishedDashboard()
    {
        if (dashboard == null) dashboard = new mchorse.bbs_mod.ui.dashboard.UIDashboard();
        return dashboard;
    }
    public static void resetDashboard()
    {
        if (formCategories != null) formCategories.getUserForms().flush();
        dashboard = null;
        mchorse.bbs_mod.ui.dashboard.DashboardWarmup.reset();
        cameraController.reset();
    }
    private static mchorse.bbs_mod.film.FilmManager localFilms;
    private static mchorse.bbs_mod.particles.ParticleManager particles;
    private static mchorse.bbs_mod.selectors.EntitySelectors selectors;
    public static mchorse.bbs_mod.selectors.EntitySelectors getSelectors()
    {
        if (selectors == null)
        {
            selectors = new mchorse.bbs_mod.selectors.EntitySelectors();
            selectors.read();
        }
        return selectors;
    }
    public static mchorse.bbs_mod.particles.ParticleManager getParticles()
    {
        if (particles == null) particles = new mchorse.bbs_mod.particles.ParticleManager(() -> BBSMod.getAssetsPath("particles"), BBSMod.getDynamicSourcePack());
        return particles;
    }
    private static final mchorse.bbs_mod.film.Films films = new mchorse.bbs_mod.film.Films();
    private static final mchorse.bbs_mod.audio.MinecraftSoundCapture minecraftSoundCapture = new mchorse.bbs_mod.audio.MinecraftSoundCapture();
    public static mchorse.bbs_mod.audio.MinecraftSoundCapture getMinecraftSoundCapture() { return minecraftSoundCapture; }
    public static mchorse.bbs_mod.film.Films getFilms() { return films; }
    private static mchorse.bbs_mod.audio.SoundManager sounds;
    private static mchorse.bbs_mod.utils.ScreenshotRecorder screenshotRecorder;
    public static mchorse.bbs_mod.utils.ScreenshotRecorder getScreenshotRecorder()
    {
        if (screenshotRecorder == null)
            screenshotRecorder = new mchorse.bbs_mod.utils.ScreenshotRecorder(new java.io.File(BBSMod.getSettingsFolder().getParentFile(), "screenshots"));
        return screenshotRecorder;
    }
    private static mchorse.bbs_mod.video.VideoManager videos;
    private static final mchorse.bbs_mod.fonts.FontManager fonts = new mchorse.bbs_mod.fonts.FontManager();
    private static final mchorse.bbs_mod.graphics.FramebufferManager framebuffers = new mchorse.bbs_mod.graphics.FramebufferManager();
    private static final mchorse.bbs_mod.utils.VideoRecorder videoRecorder = new mchorse.bbs_mod.utils.VideoRecorder();
    private static final mchorse.bbs_mod.film.WorldVideoExportSession worldExportSession = new mchorse.bbs_mod.film.WorldVideoExportSession();
    public static mchorse.bbs_mod.film.WorldVideoExportSession getWorldExportSession() { return worldExportSession; }
    public static boolean isVideoExportDelayPending() { return worldExportSession.isWarmingUp(); }
    public static long getVideoExportDelayRemainingMs() { return worldExportSession.getWarmupRemainingMs(); }
    public static net.minecraft.client.settings.KeyBinding getKeyRecordVideo() { return mchorse.bbs_mod.forge.GlobalKeybinds.RECORD_VIDEO; }
    private static final mchorse.bbs_mod.camera.controller.CameraController cameraController = new mchorse.bbs_mod.camera.controller.CameraController();
    public static mchorse.bbs_mod.camera.controller.CameraController getCameraController() { return cameraController; }
    private static long lastFrame;
    private static float frameDuration;
    public static float getFrameDuration() { return frameDuration; }
    public static void beginFrame()
    {
        long now = System.nanoTime();
        frameDuration = lastFrame == 0 ? 1F : (now - lastFrame) / 50_000_000F;
        if (mchorse.bbs_mod.client.BBSRendering.isHoldingExportFrame()) frameDuration = 0F;
        else if (videoRecorder.isRecording()) frameDuration = mchorse.bbs_mod.client.BBSRendering.getLastFrameDuration();
        lastFrame = now;
        mchorse.bbs_mod.cubic.model.ModelSetupQueue.drain();
        mchorse.bbs_mod.forms.renderers.utils.RenderFrame.nextFrame();
        if (videos != null) videos.startFrame();
    }

    public static TextureManager getTextures()
    {
        if (textures == null) textures = new TextureManager(BBSMod.getProvider());
        return textures;
    }
    public static ModelManager getModels() { return ClientProxy.models; }
    public static mchorse.bbs_mod.blocks.entities.ModelProperties getItemStackProperties(net.minecraft.item.ItemStack stack)
    {
        mchorse.bbs_mod.forge.ModelItemRenderer.Entry entry = mchorse.bbs_mod.forge.ModelItemRenderer.INSTANCE.get(stack);
        if (entry != null) return entry.tile.getProperties();
        mchorse.bbs_mod.client.renderer.item.GunItemRenderer.Entry gun = mchorse.bbs_mod.client.renderer.item.GunItemRenderer.INSTANCE.get(stack);
        return gun == null ? null : gun.properties;
    }
    /** Original world hotkeys use the same GLFW key IDs saved by the form editor. */
    public static void onEndKey(int key, int action)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (action != mchorse.bbs_mod.graphics.window.InputCodes.PRESS || key == mchorse.bbs_mod.graphics.window.InputCodes.KEY_UNKNOWN
            || mc.player == null || mc.currentScreen != null) return;

        mchorse.bbs_mod.morphing.Morph morph = mchorse.bbs_mod.morphing.Morph.getMorph(mc.player);
        if (morph != null && morph.getForm() != null && morph.getForm().findState(key, (form, state) ->
        {
            mchorse.bbs_mod.network.ClientNetwork.sendFormTrigger(state.id.get(), mchorse.bbs_mod.network.ServerNetwork.STATE_TRIGGER_MORPH);
            form.playState(state);
        })) return;

        mchorse.bbs_mod.blocks.entities.ModelProperties main = getItemStackProperties(mc.player.getHeldItemMainhand());
        mchorse.bbs_mod.blocks.entities.ModelProperties offhand = getItemStackProperties(mc.player.getHeldItemOffhand());
        if (main != null && main.getForm() != null && main.getForm().findState(key, (form, state) ->
        {
            mchorse.bbs_mod.network.ClientNetwork.sendFormTrigger(state.id.get(), mchorse.bbs_mod.network.ServerNetwork.STATE_TRIGGER_MAIN_HAND_ITEM);
            form.playState(state);
        })) return;
        if (offhand != null && offhand.getForm() != null && offhand.getForm().findState(key, (form, state) ->
        {
            mchorse.bbs_mod.network.ClientNetwork.sendFormTrigger(state.id.get(), mchorse.bbs_mod.network.ServerNetwork.STATE_TRIGGER_OFF_HAND_ITEM);
            form.playState(state);
        })) return;

        for (mchorse.bbs_mod.forms.forms.Form form : getFormCategories().getRecentForms().getCategories().get(0).getForms())
        {
            if (form.hotkey.get() == key) { mchorse.bbs_mod.network.ClientNetwork.sendPlayerForm(form); return; }
        }
        for (mchorse.bbs_mod.forms.categories.UserFormCategory category : getFormCategories().getUserForms().categories)
        {
            for (mchorse.bbs_mod.forms.forms.Form form : category.getForms())
                if (form.hotkey.get() == key) { mchorse.bbs_mod.network.ClientNetwork.sendPlayerForm(form); return; }
        }
    }

    public static mchorse.bbs_mod.film.FilmManager getLocalFilms()
    {
        if (localFilms == null) localFilms = new mchorse.bbs_mod.film.FilmManager(() -> new java.io.File(BBSMod.getAssetsFolder().getParentFile(), "data/films"));
        return localFilms;
    }
    public static mchorse.bbs_mod.audio.SoundManager getSounds()
    {
        if (sounds == null) sounds = new mchorse.bbs_mod.audio.SoundManager(BBSMod.getProvider());
        return sounds;
    }
    public static mchorse.bbs_mod.video.VideoManager getVideos()
    {
        if (videos == null) videos = new mchorse.bbs_mod.video.VideoManager();
        return videos;
    }
    public static mchorse.bbs_mod.utils.VideoRecorder getVideoRecorder() { return videoRecorder; }
    public static mchorse.bbs_mod.fonts.FontManager getFonts() { return fonts; }
    public static mchorse.bbs_mod.graphics.FramebufferManager getFramebuffers() { return framebuffers; }
    public static L10n getL10n()
    {
        if (l10n == null)
        {
            l10n = new L10n();
            l10n.registerOne(lang -> Link.assets("strings/" + lang + ".json"));
            BBSMod.events.post(new mchorse.bbs_mod.api.client.events.RegisterL10nEvent(l10n));
            l10n.reload(getLanguageKey(), BBSMod.getProvider());
        }
        return l10n;
    }
    public static String getLanguageKey() { return getLanguageKey(BBSSettings.language.get()); }
    public static String getLanguageKey(String key) { return key.isEmpty() ? Minecraft.getMinecraft().gameSettings.language : key; }
    public static void reloadLanguage(String language) { getL10n().reload(language, BBSMod.getProvider()); }
    public static float getGUIScale()
    {
        float scale = BBSSettings.userIntefaceScale.get();
        return scale > 0 ? scale : new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
    }
    public static void update()
    {
        if (textures != null) textures.update();
        if (sounds != null) sounds.update();
        if (videos != null) videos.update();
        fonts.update();
        worldExportSession.update();
    }
    /** Original client labels and axis tints for the shared ordering settings. */
    public static void configureSettingsUI()
    {
        BBSSettings.rotate3dSphereMode.modes(
            UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_TRACKBALL,
            UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_ARCBALL);
        BBSSettings.translateHotkeyOrder
            .labels(UIKeys.TRANSFORMS_TARGET_SCREEN, IKey.constant("X"), IKey.constant("Y"), IKey.constant("Z"))
            .colors(0, Colors.A100 | Colors.RED, Colors.A100 | Colors.GREEN, Colors.A100 | Colors.BLUE);
        BBSSettings.scaleHotkeyOrder
            .labels(UIKeys.TRANSFORMS_TARGET_ALL, IKey.constant("X"), IKey.constant("Y"), IKey.constant("Z"))
            .colors(0, Colors.A100 | Colors.RED, Colors.A100 | Colors.GREEN, Colors.A100 | Colors.BLUE);
        BBSSettings.rotateHotkeyOrder
            .labels(UIKeys.TRANSFORMS_TARGET_VIEW, UIKeys.TRANSFORMS_TARGET_SPHERE, IKey.constant("X"), IKey.constant("Y"), IKey.constant("Z"))
            .colors(0, 0, Colors.A100 | Colors.RED, Colors.A100 | Colors.GREEN, Colors.A100 | Colors.BLUE);
    }

    public static void assetsChanged()
    {
        particles = null;
        mchorse.bbs_mod.forms.renderers.ParticleFormRenderer.lastUpdate++;
        if (sounds != null) sounds.deleteSounds();
        if (videos != null) videos.delete();
        sounds = null;
        videos = null;
        fonts.delete();
        mchorse.bbs_mod.forms.renderers.utils.RenderFrame.invalidate();
        mchorse.bbs_mod.forms.structure.StructureManager.invalidate();
        if (textures != null)
        {
            textures.delete();
            textures.provider = BBSMod.getProvider();
        }
        if (l10n != null) l10n.reload(getLanguageKey(), BBSMod.getProvider());
    }
}
