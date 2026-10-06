package dev.qualet.aihelper.forge;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Development-only Forge adapter for ../ai_helper/tools/mc.py's HTTP contract.
 * Ported from ai_helper's HttpApi/Bridge/AutoJoin/StateReader/Screenshots approach.
 * This source set is deliberately absent from the BBS distribution JAR.
 */
@Mod(modid="aihelper",name="AI Helper Forge",version="0.2.0-forge1122",clientSideOnly=true,
    dependencies="required-after:bbs",acceptedMinecraftVersions="[1.12.2]")
public class ForgeAiHelper
{
    private static final Gson GSON=new GsonBuilder().serializeNulls().create();
    private static final List<JsonObject> LOG=new ArrayList<>();
    private static final Map<Integer,Long> RELEASES=new HashMap<>();
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final mchorse.bbs_mod.utils.WorldExportWindowSession UI_WINDOW = new mchorse.bbs_mod.utils.WorldExportWindowSession();
    private static HttpServer http;
    private static long tick;
    private static boolean autoJoin=true;
    private static final String TOKEN=System.getProperty("aihelper.token","");

    @Mod.EventHandler public void init(FMLInitializationEvent event) throws Exception {
        MC.gameSettings.pauseOnLostFocus=false;
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new DirectUIMouse());
        AbstractAppender appender=new AbstractAppender("AIHelperForge",null,PatternLayout.createDefaultLayout(),false) {
            public void append(LogEvent event) {
                JsonObject item=new JsonObject();
                item.addProperty("level",event.getLevel().name()); item.addProperty("logger",event.getLoggerName());
                item.addProperty("message",event.getMessage().getFormattedMessage());
                synchronized(LOG) { LOG.add(item); if(LOG.size()>2000) LOG.remove(0); }
            }
        };
        appender.start(); ((org.apache.logging.log4j.core.Logger)LogManager.getRootLogger()).addAppender(appender);
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",Integer.getInteger("aihelper.port",25612)),0);
        http.setExecutor(Executors.newCachedThreadPool(r -> {Thread t=new Thread(r,"aihelper-forge-http");t.setDaemon(true);return t;}));
        http.createContext("/",this::request); http.start();
        LogManager.getLogger().info("AI Helper Forge listening on loopback port {}",http.getAddress().getPort());
    }
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        tick++;
        for(Iterator<Map.Entry<Integer,Long>> it=RELEASES.entrySet().iterator();it.hasNext();) {
            Map.Entry<Integer,Long> e=it.next();
            if(tick>=e.getValue()) { KeyBinding.setKeyBindState(e.getKey(),false); it.remove(); }
        }
        if(autoJoin && MC.currentScreen instanceof GuiMainMenu) {
            autoJoin=false;
            join("ai_test");
        }
    }
    private static void join(String name) {
        if(!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid world name");
        WorldSettings settings=new WorldSettings(42,GameType.CREATIVE,false,false,WorldType.FLAT);
        settings.enableCommands(); MC.launchIntegratedServer(name,name,settings);
    }
    private void request(HttpExchange exchange) throws IOException {
        JsonObject result;
        try {
            if(!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) throw new IllegalStateException("Loopback only");
            if(!TOKEN.isEmpty() && !("Bearer "+TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) throw new IllegalStateException("Unauthorized");
            JsonObject params=new JsonObject();
            String query=exchange.getRequestURI().getRawQuery();
            if(query!=null) for(String part:query.split("&")) {
                String[] pair=part.split("=",2); params.addProperty(URLDecoder.decode(pair[0],"UTF-8"),pair.length==2?URLDecoder.decode(pair[1],"UTF-8"):"");
            }
            if(exchange.getRequestMethod().equals("POST")) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
                while((n=exchange.getRequestBody().read(buffer))!=-1) { bytes.write(buffer,0,n); if(bytes.size()>1024*1024) throw new IOException("Request too large"); }
                if(bytes.size()>0) { JsonObject body=new JsonParser().parse(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getAsJsonObject(); for(Map.Entry<String,JsonElement> e:body.entrySet()) params.add(e.getKey(),e.getValue()); }
            }
            String endpoint=exchange.getRequestURI().getPath();
            if(endpoint.equals("/log")) result=logs(params);
            else if(endpoint.equals("/wait")) {
                long end=onClient(() -> tick)+integer(params,"ticks",20);
                long deadline=System.currentTimeMillis()+30000;
                while(onClient(() -> tick)<end) { if(System.currentTimeMillis()>deadline) throw new TimeoutException("Client ticks stalled"); Thread.sleep(20); }
                result=ok();
            } else if(endpoint.equals("/cmd")) result=command(params);
            else result=onClient(() -> route(endpoint,params));
        } catch(Exception e) { result=new JsonObject(); result.addProperty("ok",false); result.addProperty("error",e.toString()); }
        byte[] bytes=GSON.toJson(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.sendResponseHeaders(200,bytes.length);
        try(OutputStream stream=exchange.getResponseBody()) { stream.write(bytes); }
    }
    private static <T> T onClient(Callable<T> task) throws Exception { return MC.addScheduledTask(task).get(60,TimeUnit.SECONDS); }
    private static JsonObject ok() { JsonObject o=new JsonObject();o.addProperty("ok",true);return o; }
    private static String str(JsonObject p,String key,String fallback) { return p.has(key)?p.get(key).getAsString():fallback; }
    private static int integer(JsonObject p,String key,int fallback) { return p.has(key)?p.get(key).getAsInt():fallback; }
    private static boolean bool(JsonObject p,String key,boolean fallback) { return p.has(key)?p.get(key).getAsBoolean():fallback; }
    private static JsonObject health() {
        JsonObject o=ok(); o.addProperty("inWorld",MC.player!=null && MC.world!=null);
        o.addProperty("screen",MC.currentScreen==null?null:MC.currentScreen.getClass().getSimpleName());
        o.addProperty("minecraft","1.12.2"); o.addProperty("bbsLoaded",net.minecraftforge.fml.common.Loader.isModLoaded("bbs")); o.addProperty("java",System.getProperty("java.version"));
        o.addProperty("gameDir",MC.gameDir.getAbsolutePath()); o.addProperty("tick",tick);
        return o;
    }
    private static JsonObject route(String endpoint,JsonObject p) throws Exception {
        JsonObject out=ok();
        switch(endpoint) {
            case "/bbs-feedback26-probe": return Feedback26Probe.run(p);
            case "/health": case "/status": return health();
            case "/bbs-ui-timing-probe": return UiTimingProbe.run(p);
            case "/bbs-depth-state-probe": return DepthStateProbe.run(p);
            case "/gl-state":
                out.addProperty("program", GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM));
                out.addProperty("activeTexture", GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE));
                out.addProperty("depthFunc", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
                out.addProperty("depthMask", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
                out.addProperty("clearDepth", GL11.glGetFloat(GL11.GL_DEPTH_CLEAR_VALUE));
                out.addProperty("alphaFunc", GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));
                out.addProperty("alphaRef", GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
                out.addProperty("texture", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
                out.addProperty("unpackRow", GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH));
                for (int flag : new int[]{GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_ALPHA_TEST,GL11.GL_BLEND,GL11.GL_TEXTURE_2D,GL11.GL_SCISSOR_TEST}) out.addProperty("flag"+flag,GL11.glIsEnabled(flag));
                out.addProperty("hudHidden", MC.gameSettings.hideGUI);
                out.addProperty("glError", GL11.glGetError());
                return out;
            case "/bbs-camera-probe": return OriginalCameraProbe.handle(p);
            case "/bbs-viewport-probe": return OriginalViewportProbe.handle(p);
            case "/bbs-model-gpu-probe": return OriginalModelGPUProbe.handle(p);
            case "/bbs-optifine-probe": return OptiFineProbe.handle(p);
            case "/bbs-shader-curves-probe": return OriginalShaderCurvesProbe.handle(p);
            case "/bbs-shader-world-forms-probe": return ShaderWorldFormsProbe.handle(p);
            case "/bbs-native-ui-render-probe": return NativeUiRenderProbe.handle(p);
            case "/bbs-extended-forms-probe": return ExtendedFormsProbe.handle(p);
            case "/bbs-trail-mob-probe": return mchorse.bbs_mod.forms.renderers.TrailMobProbe.run(p);
            case "/bbs-forms-transparency-probe": return mchorse.bbs_mod.forms.renderers.FormsTransparencyProbe.run(p);
            case "/bbs-model-item-gpu-probe": return mchorse.bbs_mod.forms.renderers.ModelItemGPUProbe.run(p);
            case "/bbs-film-control-input-probe": return mchorse.bbs_mod.forge.FilmControlInputProbe.run();
            case "/bbs-film-player-visibility-probe": return FilmPlayerVisibilityProbe.run(p);
            case "/bbs-model-blocks-probe": return ModelBlocksProbe.handle(p);
            case "/bbs-export-fixture": return OriginalExportFixture.handle(p);
            case "/bbs-particles-probe": return OriginalParticlesProbe.run(p);
            case "/bbs-importers-probe": return OriginalImportersProbe.run(p);
            case "/bbs-guns-probe": return OriginalGunsProbe.run(p);
            case "/bbs-addon-probe": return OriginalAddonProbe.run();
            case "/bbs-native-resources-probe": return OriginalNativeResourcesProbe.run();
            case "/bbs-actions-probe": return OriginalActionsProbe.run(p);
            case "/bbs-selectors-probe": return OriginalSelectorsProbe.run(p);
            case "/bbs-world-export-probe": return OriginalWorldExportProbe.run(p);
            case "/bbs-commands-probe": return OriginalCommandsProbe.run(p);
            case "/bbs-hotkeys-probe": return OriginalHotkeysProbe.run(p);
            case "/bbs-input-regressions-probe": return OriginalInputRegressionsProbe.run(p);
            case "/bbs-film-backend-probe": return OriginalFilmBackendProbe.handle(p);
            case "/bbs-gizmo-probe": return OriginalGizmoProbe.handle(p);
            case "/bbs-dashboard-probe": return OriginalDashboardProbe.snapshot();
            case "/bbs-font-probe": return OriginalFontProbe.handle(p);
            case "/bbs-default-font-probe": return OriginalDefaultFontProbe.handle(p);
            case "/bbs-structures-probe": return OriginalStructuresProbe.run(p);
            case "/bbs-media-probe": return OriginalMediaProbe.run(p);
            case "/bbs-forms-probe":
                if (bool(p,"open",false)) mchorse.bbs_mod.ui.framework.UIScreen.open(new OriginalFormsProbe());
                if (!(mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() instanceof OriginalFormsProbe)) throw new IllegalStateException("Open the forms probe first");
                OriginalFormsProbe formsProbe = (OriginalFormsProbe)mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu();
                if (p.has("action")) formsProbe.action(p);
                return formsProbe.describe();
            case "/bbs-clips-probe":
                if (bool(p,"open",false)) mchorse.bbs_mod.ui.framework.UIScreen.open(new OriginalClipsProbe());
                if (!(mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() instanceof OriginalClipsProbe)) throw new IllegalStateException("Open the clips probe first");
                return ((OriginalClipsProbe)mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu()).snapshot();
            case "/bbs-timeline-probe":
                if (bool(p,"open",false)) mchorse.bbs_mod.ui.framework.UIScreen.open(new OriginalTimelineProbe());
                if (!(mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() instanceof OriginalTimelineProbe)) throw new IllegalStateException("Open the timeline probe first");
                return ((OriginalTimelineProbe)mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu()).snapshot();
            case "/ui-mouse":
                if (!(MC.currentScreen instanceof mchorse.bbs_mod.ui.framework.UIScreen)) throw new IllegalStateException("Open a BBS screen first");
                int ux = integer(p,"x",0), uy = integer(p,"y",0), ub = integer(p,"button",0);
                float us = mchorse.bbs_mod.BBSModClient.getGUIScale();
                if (bool(p,"warp",true)) { DirectUIMouse.clear(); org.lwjgl.input.Mouse.setCursorPosition(Math.round(ux * us), MC.displayHeight - 1 - Math.round(uy * us)); }
                else DirectUIMouse.set(MC.currentScreen,ux,uy,ub,str(p,"mode","move"));
                mchorse.bbs_mod.ui.framework.UIBaseMenu um = mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu();
                um.context.setMouse(ux,uy,ub);
                String umode = str(p,"mode","move");
                if (umode.equals("down")) um.mouseClicked(ux,uy,ub);
                if (umode.equals("up")) um.mouseReleased(ux,uy,ub);
                return out;
            case "/ui-window":
                DirectUIMouse.clear();
                if (bool(p,"restore",false)) UI_WINDOW.restore();
                else UI_WINDOW.begin(Math.max(640,Math.min(2560,integer(p,"width",1280))),Math.max(360,Math.min(1440,integer(p,"height",720))));
                out.addProperty("width",MC.displayWidth);out.addProperty("height",MC.displayHeight);return out;
            case "/bbs-ui-probe":
                if (bool(p,"open",false)) mchorse.bbs_mod.ui.framework.UIScreen.open(new OriginalUIProbe());
                if (p.has("scale")) mchorse.bbs_mod.BBSSettings.userIntefaceScale.set(p.get("scale").getAsFloat());
                if (p.has("blur")) mchorse.bbs_mod.BBSSettings.interfaceBlur.set(p.get("blur").getAsBoolean());
                if (!(mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() instanceof OriginalUIProbe)) throw new IllegalStateException("Open the original UI probe first");
                return ((OriginalUIProbe)mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu()).snapshot();
            case "/state":
                out=health();
                if(MC.player!=null) {
                    JsonObject player=new JsonObject(); player.addProperty("x",MC.player.posX);player.addProperty("y",MC.player.posY);player.addProperty("z",MC.player.posZ);
                    player.addProperty("yaw",MC.player.rotationYaw);player.addProperty("pitch",MC.player.rotationPitch);player.addProperty("health",MC.player.getHealth());out.add("player",player);
                    JsonObject world=new JsonObject();world.addProperty("time",MC.world.getTotalWorldTime());out.add("world",world);
                } return out;
            case "/screen":
                out=health();
                ScaledResolution res=new ScaledResolution(MC);out.addProperty("width",MC.currentScreen==null?res.getScaledWidth():MC.currentScreen.width);out.addProperty("height",MC.currentScreen==null?res.getScaledHeight():MC.currentScreen.height);
                JsonArray buttons=new JsonArray();
                if(MC.currentScreen!=null) {
                    List<GuiButton> list=ReflectionHelper.getPrivateValue(GuiScreen.class,MC.currentScreen,"buttonList","field_146292_n");
                    for(GuiButton b:list) { JsonObject v=new JsonObject();v.addProperty("id",b.id);v.addProperty("text",b.displayString);v.addProperty("x",b.x);v.addProperty("y",b.y);v.addProperty("width",b.width);v.addProperty("height",b.height);buttons.add(v); }
                } out.add("widgets",buttons);
                out.add("fields",new JsonArray());return out;
            case "/world": if(MC.world!=null) throw new IllegalStateException("Disconnect first");join(str(p,"world","ai_test"));return out;
            case "/disconnect":
                if(MC.world!=null) MC.world.sendQuittingDisconnectingPacket();
                MC.loadWorld(null);MC.displayGuiScreen(new GuiMainMenu());return out;
            case "/quit": MC.addScheduledTask(() -> MC.shutdown());return out;
            case "/release": KeyBinding.unPressAllKeys();RELEASES.clear();DirectUIMouse.clear();return out;
            case "/key":
                String key=str(p,"key","escape");
                int code=Keyboard.getKeyIndex(key.toUpperCase(Locale.ROOT));
                if(key.equals("escape")) code=Keyboard.KEY_ESCAPE;
                if(key.equals("enter")) code=Keyboard.KEY_RETURN;
                if(key.equals("forward")) code=MC.gameSettings.keyBindForward.getKeyCode();
                if(key.equals("use")) code=MC.gameSettings.keyBindUseItem.getKeyCode();
                if(code==0) throw new IllegalArgumentException("Unsupported key: "+key);
                String mode=str(p,"mode","tap");
                if(MC.currentScreen!=null && !mode.equals("up")) guiKeyWithModifiers('\0',code,p);
                else {
                    KeyBinding.setKeyBindState(code,!mode.equals("up"));
                    if(!mode.equals("up")) {KeyBinding.onTick(code);MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());}
                    if(mode.equals("tap")||mode.equals("hold")) RELEASES.put(code,tick+Math.max(1,integer(p,"ticks",1)));
                } return out;
            case "/type": for(char c:str(p,"text","").toCharArray()) guiKey(c,Keyboard.KEY_NONE);return out;
            case "/click":
                if(MC.currentScreen==null) throw new IllegalStateException("No GUI");
                GuiScreen clickedScreen=MC.currentScreen;
                Method click=ReflectionHelper.findMethod(GuiScreen.class,"mouseClicked","func_73864_a",int.class,int.class,int.class);
                click.invoke(clickedScreen,integer(p,"x",0),integer(p,"y",0),integer(p,"button",0));
                if(MC.currentScreen==clickedScreen) ReflectionHelper.findMethod(GuiScreen.class,"mouseReleased","func_146286_b",int.class,int.class,int.class).invoke(clickedScreen,integer(p,"x",0),integer(p,"y",0),integer(p,"button",0));
                return out;
            case "/scroll":
                if (MC.currentScreen instanceof mchorse.bbs_mod.ui.framework.UIScreen) {
                    withModifiers(p, () -> ((mchorse.bbs_mod.ui.framework.UIScreen)MC.currentScreen).mouseScrolled(integer(p,"x",0),integer(p,"y",0),0,integer(p,"wheel",-120)/120D));return out;
                }
                throw new IllegalStateException("Open a BBS screen first");
            case "/look":
                if(MC.player==null) throw new IllegalStateException("No player");
                MC.player.rotationYaw=(float)Double.parseDouble(str(p,"yaw","0"));MC.player.rotationPitch=(float)Double.parseDouble(str(p,"pitch","0"));return out;
            case "/block":
                if(MC.world==null) throw new IllegalStateException("No world");
                BlockPos pos=new BlockPos(integer(p,"x",0),integer(p,"y",0),integer(p,"z",0));
                out.addProperty("id",MC.world.getBlockState(pos).getBlock().getRegistryName().toString());
                if(MC.world.getTileEntity(pos)!=null) out.addProperty("nbt",MC.world.getTileEntity(pos).writeToNBT(new NBTTagCompound()).toString());return out;
            case "/screenshot": return screenshot(p);
            case "/bbs1122":
                out.add("models",GSON.toJsonTree(mchorse.bbs_mod.forge.ClientProxy.models.getAvailableKeys()));
                out.add("loadedModels",GSON.toJsonTree(mchorse.bbs_mod.forge.ClientProxy.models.models.keySet()));
                out.addProperty("renderEpoch",mchorse.bbs_mod.forms.renderers.utils.RenderFrame.getEpoch());
                out.addProperty("glError",GL11.glGetError());
                return out;
            default: throw new IllegalArgumentException("Unsupported Forge AI Helper route: "+endpoint);
        }
    }
    /** Set native modifier state only during one synchronous test event, then restore it. */
    private static void guiKeyWithModifiers(char c, int code, JsonObject p) throws Exception {
        withModifiers(p, () -> guiKey(c, code));
    }
    @FunctionalInterface private interface ModifiedInput { void run() throws Exception; }
    private static void withModifiers(JsonObject p, ModifiedInput input) throws Exception {
        if (!p.has("ctrl") && !p.has("shift") && !p.has("alt")) { input.run(); return; }
        java.lang.reflect.Field field = Keyboard.class.getDeclaredField("keyDownBuffer");
        field.setAccessible(true);
        java.nio.ByteBuffer keys = (java.nio.ByteBuffer) field.get(null);
        int[] codes = {Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT, Keyboard.KEY_LMENU, Keyboard.KEY_RMENU};
        byte[] previous = new byte[codes.length];
        String[] names = {"ctrl", "ctrl", "shift", "shift", "alt", "alt"};
        for (int i = 0; i < codes.length; i++) previous[i] = keys.get(codes[i]);
        try {
            for (int i = 0; i < codes.length; i++) keys.put(codes[i], (byte) (i % 2 == 0 && bool(p,names[i],false) ? 1 : 0));
            input.run();
        } finally {
            for (int i = 0; i < codes.length; i++) keys.put(codes[i], previous[i]);
        }
    }
    private static void guiKey(char c,int code) throws Exception {
        if(MC.currentScreen==null) throw new IllegalStateException("No GUI");
        ReflectionHelper.findMethod(GuiScreen.class,"keyTyped","func_73869_a",char.class,int.class).invoke(MC.currentScreen,c,code);
    }
    private static JsonObject command(JsonObject p) throws Exception {
        String text=str(p,"cmd","");if(text.startsWith("/"))text=text.substring(1);
        final String cmd=text;
        if(str(p,"as","server").equals("player")) return onClient(() -> {MC.player.sendChatMessage("/"+cmd);return ok();});
        MinecraftServer server=MC.getIntegratedServer();if(server==null)throw new IllegalStateException("No integrated server");
        return server.callFromMainThread(() -> {
            JsonArray output=new JsonArray();
            net.minecraft.entity.player.EntityPlayerMP player=server.getPlayerList().getPlayers().get(0);
            ICommandSender sender=new ICommandSender() {
                public String getName(){return player.getName();}
                public ITextComponent getDisplayName(){return player.getDisplayName();}
                public void sendMessage(ITextComponent message){output.add(new JsonPrimitive(message.getUnformattedText()));}
                public boolean canUseCommand(int level,String name){return true;}
                public BlockPos getPosition(){return player.getPosition();}
                public Vec3d getPositionVector(){return player.getPositionVector();}
                public World getEntityWorld(){return player.world;}
                public Entity getCommandSenderEntity(){return player;}
                public boolean sendCommandFeedback(){return true;}
                public void setCommandStat(net.minecraft.command.CommandResultStats.Type type,int amount){ }
                public MinecraftServer getServer(){return server;}
            };
            int result=server.getCommandManager().executeCommand(sender,cmd);
            JsonObject out=ok();out.addProperty("result",result);out.add("output",output);return out;
        }).get(30,TimeUnit.SECONDS);
    }
    private static JsonObject screenshot(JsonObject p) throws IOException {
        if(bool(p,"hideGui",false)) throw new IllegalArgumentException("Forge screenshot uses the current framebuffer; hideGui is not supported yet");
        String name=str(p,"name","shot").replaceAll("[^a-zA-Z0-9_-]","_")+".png";
        File dir=new File(MC.gameDir,"screenshots/ai");dir.mkdirs();
        BufferedImage image=ScreenShotHelper.createScreenshot(MC.displayWidth,MC.displayHeight,MC.getFramebuffer());
        int scale=Math.max(1,integer(p,"downscale",1));
        if(scale>1) { BufferedImage resized=new BufferedImage(Math.max(1,image.getWidth()/scale),Math.max(1,image.getHeight()/scale),BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D graphics=resized.createGraphics();graphics.drawImage(image,0,0,resized.getWidth(),resized.getHeight(),null);graphics.dispose();image=resized; }
        File file=new File(dir,name);ImageIO.write(image,"png",file);
        JsonObject out=ok();out.addProperty("path",file.getAbsolutePath());out.addProperty("width",image.getWidth());out.addProperty("height",image.getHeight());
        if(bool(p,"base64",false))out.addProperty("base64",Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath())));
        return out;
    }
    private static JsonObject logs(JsonObject p) {
        JsonObject out=ok();JsonArray entries=new JsonArray();String filter=str(p,"contains","");String level=str(p,"level","");
        synchronized(LOG) {
            int from=Math.max(0,LOG.size()-integer(p,"limit",60));
            for(int i=from;i<LOG.size();i++) { JsonObject v=LOG.get(i);if(!v.get("message").getAsString().contains(filter))continue;
                if(level.startsWith("ERROR") && !v.get("level").getAsString().equals("ERROR") && !v.get("level").getAsString().equals("FATAL"))continue;entries.add(v);
            }
        } out.add("entries",entries);return out;
    }
}
