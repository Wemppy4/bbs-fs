package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.items.GunZoom;
import mchorse.bbs_mod.client.renderer.item.GunItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Middle-mouse zoom uses the same interpolated FOV and commands as the original BBS client. */
public final class GunClientHandler {
    public static final KeyBinding ZOOM=new KeyBinding("key.bbs.zoom",-98,"category.bbs.main");
    private static GunZoom zoom;
    public GunClientHandler() { ClientRegistry.registerKeyBinding(ZOOM); }
    public static GunZoom getZoom() { return zoom; }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null||mc.player==null) { zoom=null;GunItemRenderer.INSTANCE.clear();return; }
        if(!mc.isGamePaused())GunItemRenderer.INSTANCE.tick();
        if(zoom==null&&mc.currentScreen==null&&ZOOM.isKeyDown()&&mc.player.getHeldItemMainhand().getItem()==CommonProxy.GUN_ITEM) {
            GunProperties properties=GunProperties.get(mc.player.getHeldItemMainhand());
            zoom=new GunZoom(properties.fovTarget,properties.fovInterp,properties.fovDuration);
            GunNetwork.sendZoom(true);
        }
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START||zoom==null)return;
        Minecraft mc=Minecraft.getMinecraft();
        boolean pressed=mc.player!=null&&mc.currentScreen==null&&mc.player.getHeldItemMainhand().getItem()==CommonProxy.GUN_ITEM&&ZOOM.isKeyDown();
        zoom.update(pressed,mc.isGamePaused()?0F:BBSModClient.getFrameDuration());
        if(zoom.canBeRemoved()) { GunNetwork.sendZoom(false);zoom=null; }
    }
    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier event) {
        if(zoom!=null)event.setFOV(zoom.getFOV(event.getFOV()));
    }
}