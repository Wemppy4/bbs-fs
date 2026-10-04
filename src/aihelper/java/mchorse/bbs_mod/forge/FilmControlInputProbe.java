package mchorse.bbs_mod.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.dashboard.*;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.overlay.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.*;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.util.*;

/** Bound-key mapping and the actual Forge event's overlay gate, without OS input. */
public final class FilmControlInputProbe
{
    public static JsonObject run() throws Exception
    {
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null)throw new IllegalStateException("World required");
        GameSettings settings=mc.gameSettings;GuiScreen previousScreen=mc.currentScreen;UIDashboard dashboard=BBSModClient.getDashboard();dashboard.finishBuilding();UIDashboardPanel previousPanel=dashboard.getPanels().panel;
        UIFilmPanel film=dashboard.getPanel(UIFilmPanel.class);Field controlled=film.getController().getClass().getDeclaredField("controlled");controlled.setAccessible(true);Object previousControlled=controlled.get(film.getController());
        Field pressed=ReflectionHelper.findField(KeyBinding.class,"pressed","field_74513_e");boolean sprint=pressed.getBoolean(settings.keyBindSprint);
        int original=settings.keyBindForward.getKeyCode();KeyModifier modifier=settings.keyBindForward.getKeyModifier();UIOverlay overlay=null;JsonObject result=new JsonObject();
        try
        {
            UIScreen.open(dashboard);dashboard.setPanel(film);controlled.set(film.getController(),new StubEntity(mc.world));
            result.addProperty("canControl",film.getController().canControl());
            settings.keyBindForward.setKeyModifierAndCode(KeyModifier.NONE,Keyboard.KEY_UP);KeyBinding.resetKeyBindingArrayAndHash();
            Set<Integer> keys=new HashSet<>(Arrays.asList(Keyboard.KEY_UP,settings.keyBindLeft.getKeyCode(),settings.keyBindJump.getKeyCode(),settings.keyBindSprint.getKeyCode()));MovementInput input=new MovementInput();
            FilmControlInput.apply(input,settings,keys::contains);
            result.addProperty("reboundForward",input.moveForward==1&&input.moveStrafe==1&&input.jump&&pressed.getBoolean(settings.keyBindSprint));
            keys.add(settings.keyBindBack.getKeyCode());keys.add(settings.keyBindRight.getKeyCode());FilmControlInput.apply(input,settings,keys::contains);result.addProperty("opposedCancel",input.moveForward==0&&input.moveStrafe==0);
            keys.remove(settings.keyBindBack.getKeyCode());keys.remove(settings.keyBindRight.getKeyCode());keys.add(settings.keyBindSneak.getKeyCode());FilmControlInput.apply(input,settings,keys::contains);result.addProperty("sneakFactor",Math.abs(input.moveForward-.3F)<.0001F&&input.sneak);
            settings.keyBindForward.setKeyModifierAndCode(KeyModifier.NONE,-100);keys.clear();keys.add(-100);FilmControlInput.apply(input,settings,keys::contains);result.addProperty("mouseBinding",input.moveForward==1);
            overlay=UIOverlay.addOverlay(dashboard.context,new UIOverlayPanel(L10n.lang("AI control fixture")));
            result.addProperty("overlayBlocks",!film.getController().canControl());
            input.moveForward=1;input.jump=true;MinecraftForge.EVENT_BUS.post(new InputUpdateEvent(mc.player,input));
            result.addProperty("eventClearedMovement",input.moveForward==0&&!input.jump);result.addProperty("ok",true);
        }
        finally
        {
            if(overlay!=null)overlay.removeFromParent();controlled.set(film.getController(),previousControlled);
            settings.keyBindForward.setKeyModifierAndCode(modifier,original);KeyBinding.resetKeyBindingArrayAndHash();pressed.setBoolean(settings.keyBindSprint,sprint);
            if(previousPanel!=null)dashboard.setPanel(previousPanel);mc.displayGuiScreen(previousScreen);
        }
        return result;
    }
    private FilmControlInputProbe(){}
}
