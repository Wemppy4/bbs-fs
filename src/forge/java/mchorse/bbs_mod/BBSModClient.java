package mchorse.bbs_mod;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.forge.ClientProxy;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
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
    }
    public static void assetsChanged()
    {
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
