package mchorse.bbs_mod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.converters.*;
import mchorse.bbs_mod.camera.clips.overwrite.*;
import mchorse.bbs_mod.camera.clips.modifiers.*;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.actions.types.chat.*;
import mchorse.bbs_mod.actions.types.*;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.clips.ClipFactory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import mchorse.bbs_mod.forge.CommonProxy;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import java.io.File;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.ExternalAssetsSourcePack;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/** Forge lifecycle follows the sided entry-point pattern used by bb-sources. */
@Mod(modid = BBSMod.MOD_ID, name = "BBS FS", version = "2.8-1.12.2-alpha.1", acceptedMinecraftVersions = "[1.12.2]")
public class BBSMod
{
    public static final String MOD_ID = "bbs";
    public static final mchorse.bbs_mod.api.EventBus events = new mchorse.bbs_mod.api.EventBus();
    public static final net.minecraft.util.SoundEvent CLICK = new net.minecraft.util.SoundEvent(new net.minecraft.util.ResourceLocation("bbs", "click")).setRegistryName("bbs", "click");
    public static Logger LOGGER;
    @SidedProxy(clientSide = "mchorse.bbs_mod.forge.ClientProxy", serverSide = "mchorse.bbs_mod.forge.CommonProxy")
    public static CommonProxy proxy;
    private static File assetsFolder;
    private static File settingsFolder;
    private static AssetProvider provider;
    private static mchorse.bbs_mod.resources.packs.DynamicSourcePack dynamicSourcePack;
    private static mchorse.bbs_mod.film.FilmManager films;
    private static final mchorse.bbs_mod.actions.ActionManager actions = new mchorse.bbs_mod.actions.ActionManager();
    public static mchorse.bbs_mod.actions.ActionManager getActions() { return actions; }
    private static final mchorse.bbs_mod.settings.SettingsManager settings = new mchorse.bbs_mod.settings.SettingsManager();
    private static File gameFolder = new File(".");
    public static mchorse.bbs_mod.settings.SettingsManager getSettings() { return settings; }
    public static File getGameFolder() { return gameFolder; }
    public static File getGamePath(String path) { return new File(gameFolder, path); }
    public static File getExportFolder() { return getGamePath("export"); }
    public static mchorse.bbs_mod.settings.Settings setupConfig(mchorse.bbs_mod.ui.utils.icons.Icon icon, String id, File destination, java.util.function.Consumer<mchorse.bbs_mod.settings.SettingsBuilder> registerer)
    {
        mchorse.bbs_mod.settings.SettingsBuilder builder = new mchorse.bbs_mod.settings.SettingsBuilder(icon, id, destination);
        registerer.accept(builder);
        mchorse.bbs_mod.settings.Settings config = builder.getConfig();
        settings.modules.put(id, config);
        if (destination != null) settings.load(config, destination);
        return config;
    }
    private static void setupSettings(File file) {
        setupConfig(Icons.GEAR, "bbs", file, BBSSettings::register);
    }
    private static final mchorse.bbs_mod.forms.FormArchitect forms = new mchorse.bbs_mod.forms.FormArchitect();
    private static final mchorse.bbs_mod.utils.factory.MapFactory<mchorse.bbs_mod.utils.clips.Clip, ClipFactoryData> factoryCameraClips;
    private static final mchorse.bbs_mod.utils.factory.MapFactory<mchorse.bbs_mod.utils.clips.Clip, ClipFactoryData> factoryActionClips;
    public static mchorse.bbs_mod.utils.factory.MapFactory<mchorse.bbs_mod.utils.clips.Clip, ClipFactoryData> getFactoryCameraClips() { return factoryCameraClips; }
    public static mchorse.bbs_mod.utils.factory.MapFactory<mchorse.bbs_mod.utils.clips.Clip, ClipFactoryData> getFactoryActionClips() { return factoryActionClips; }
    static {
        setupSettings(null);
        factoryCameraClips = new ClipFactory()
            .register(Link.bbs("idle"), IdleClip.class, new ClipFactoryData(Icons.FRUSTUM, 0x159e64)
                .withConverter(Link.bbs("dolly"), new IdleToDollyConverter())
                .withConverter(Link.bbs("path"), new IdleToPathConverter())
                .withConverter(Link.bbs("keyframe"), new IdleToKeyframeConverter()))
            .register(Link.bbs("dolly"), DollyClip.class, new ClipFactoryData(Icons.CAMERA, 0xffa500)
                .withConverter(Link.bbs("idle"), IdleConverter.CONVERTER)
                .withConverter(Link.bbs("path"), new DollyToPathConverter())
                .withConverter(Link.bbs("keyframe"), new DollyToKeyframeConverter()))
            .register(Link.bbs("path"), PathClip.class, new ClipFactoryData(Icons.GALLERY, 0x6820ad)
                .withConverter(Link.bbs("idle"), IdleConverter.CONVERTER)
                .withConverter(Link.bbs("dolly"), new PathToDollyConverter())
                .withConverter(Link.bbs("keyframe"), new PathToKeyframeConverter()))
            .register(Link.bbs("keyframe"), KeyframeClip.class, new ClipFactoryData(Icons.CURVES, 0xde2e9f)
                .withConverter(Link.bbs("idle"), IdleConverter.CONVERTER))
            .register(Link.bbs("translate"), TranslateClip.class, new ClipFactoryData(Icons.UPLOAD, 0x4ba03e))
            .register(Link.bbs("angle"), AngleClip.class, new ClipFactoryData(Icons.ARC, 0xd77a0a))
            .register(Link.bbs("drag"), DragClip.class, new ClipFactoryData(Icons.FADING, 0x4baff7))
            .register(Link.bbs("shake"), ShakeClip.class, new ClipFactoryData(Icons.EXCHANGE, 0x159e64))
            .register(Link.bbs("math"), MathClip.class, new ClipFactoryData(Icons.GRAPH, 0x6820ad))
            .register(Link.bbs("remapper"), RemapperClip.class, new ClipFactoryData(Icons.TIME, 0x222222))
            .register(Link.bbs("audio"), AudioClip.class, new ClipFactoryData(Icons.SOUND, 0xffc825))
            .register(Link.bbs("look"), LookClip.class, new ClipFactoryData(Icons.VISIBLE, 0x197fff))
            .register(Link.bbs("orbit"), OrbitClip.class, new ClipFactoryData(Icons.GLOBE, 0xd82253))
            .register(Link.bbs("tracker"), mchorse.bbs_mod.camera.clips.modifiers.TrackerClip.class, new ClipFactoryData(Icons.USER, 0x4cedfc))
            .register(Link.bbs("spline"), mchorse.bbs_mod.camera.clips.overwrite.SplineClip.class, new ClipFactoryData(Icons.GRAPH, 0x5599ff))
            .register(Link.bbs("curve"), mchorse.bbs_mod.camera.clips.misc.CurveClip.class, new ClipFactoryData(Icons.ARC, 0xff1493))
            .register(Link.bbs("subtitle"), mchorse.bbs_mod.camera.clips.misc.SubtitleClip.class, new ClipFactoryData(Icons.FONT, 0x888899))
            .register(Link.bbs("dolly_zoom"), DollyZoomClip.class, new ClipFactoryData(Icons.FILTER, 0x7d56c9));

        factoryActionClips = new ClipFactory()
            .register(Link.bbs("chat"), ChatActionClip.class, new ClipFactoryData(Icons.BUBBLE, Colors.YELLOW))
            .register(Link.bbs("command"), CommandActionClip.class, new ClipFactoryData(Icons.PROPERTIES, Colors.ACTIVE))
            .register(Link.bbs("damage"), DamageActionClip.class, new ClipFactoryData(Icons.SKULL, Colors.CURSOR))
            .register(Link.bbs("swipe"), SwipeActionClip.class, new ClipFactoryData(Icons.LIMB, Colors.ORANGE));

        forms.register(Link.bbs("model"), mchorse.bbs_mod.forms.forms.ModelForm.class);
        forms.register(Link.bbs("mob"), mchorse.bbs_mod.forms.forms.MobForm.class);
        forms.register(Link.bbs("billboard"), mchorse.bbs_mod.forms.forms.BillboardForm.class);
        forms.register(Link.bbs("extruded"), mchorse.bbs_mod.forms.forms.ExtrudedForm.class);
        forms.register(Link.bbs("anchor"), mchorse.bbs_mod.forms.forms.AnchorForm.class);
        factoryCameraClips.register(Link.bbs("image"), mchorse.bbs_mod.camera.clips.misc.ImageClip.class, new ClipFactoryData(Icons.GALLERY, 0x5278cd));
        factoryCameraClips.register(Link.bbs("video"), mchorse.bbs_mod.camera.clips.misc.VideoClip.class, new ClipFactoryData(Icons.VIDEO_CAMERA, 0xd21f3c));
    }
    public static mchorse.bbs_mod.forms.FormArchitect getForms() { return forms; }
    public static File getAssetsFolder() { return assetsFolder; }
    public static mchorse.bbs_mod.resources.packs.DynamicSourcePack getDynamicSourcePack() { return dynamicSourcePack; }
    public static File getWorldAssetsFolder()
    {
        mchorse.bbs_mod.resources.ISourcePack pack = dynamicSourcePack == null ? null : dynamicSourcePack.getSecondary();
        return pack instanceof ExternalAssetsSourcePack ? ((ExternalAssetsSourcePack) pack).getFolder() : null;
    }
    public static File getAssetsPath(String path) { return new File(assetsFolder, path); }
    public static File getAudioFolder() { return getAssetsPath("audio"); }
    public static File getSettingsFolder() { return settingsFolder; }
    public static File getSettingsPath(String path) { return new File(settingsFolder, path); }
    public static AssetProvider getProvider() { return provider; }
    public static mchorse.bbs_mod.film.FilmManager getFilms() { return films; }
    public static void setProvider(AssetProvider value) { provider = value; }
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        LOGGER = event.getModLog();
        gameFolder = event.getModConfigurationDirectory().getParentFile();
        assetsFolder = new File(event.getModConfigurationDirectory(), "bbs/assets");
        assetsFolder.mkdirs();
        settingsFolder = new File(assetsFolder.getParentFile(), "settings");
        setupSettings(getSettingsPath("bbs.json"));
        provider = new AssetProvider();
        dynamicSourcePack = new mchorse.bbs_mod.resources.packs.DynamicSourcePack(new ExternalAssetsSourcePack(Link.ASSETS, assetsFolder).providesFiles());
        provider.register(dynamicSourcePack);
        mchorse.bbs_mod.resources.packs.URLRepository urlCache = new mchorse.bbs_mod.resources.packs.URLRepository(new File(assetsFolder.getParentFile(), "url_cache"));
        provider.register(new mchorse.bbs_mod.resources.packs.URLSourcePack("http", urlCache));
        provider.register(new mchorse.bbs_mod.resources.packs.URLSourcePack("https", urlCache));
        KeyframeFactories.setup();
        proxy.preInit();
        LOGGER.info("BBS FS Forge 1.12.2 foundation initializing");
    }
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) { proxy.init(); }
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        File world = event.getServer().getWorld(0).getSaveHandler().getWorldDirectory();
        films = new mchorse.bbs_mod.film.FilmManager(() -> new File(world, "bbs/films"));
        mchorse.bbs_mod.forge.FilmServerEvents.serverStarting(event.getServer());
        event.registerServerCommand(new mchorse.bbs_mod.forge.BBSCommand());
    }
    @Mod.EventHandler
    public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        mchorse.bbs_mod.forge.FilmServerEvents.serverStopping();
    }
    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) { films = null; }
}
