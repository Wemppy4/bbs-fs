package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.util.function.IntPredicate;

/** Original KeyboardInputMixin boundary, after 1.12 has filled MovementInput.
 * Screens keep vanilla input disabled; only the film's explicit actor control reads keys. */
@Mod.EventBusSubscriber(modid="bbs",value=Side.CLIENT)
public final class FilmControlInput
{
    private static boolean ownedSprint;
    @SubscribeEvent public static void input(InputUpdateEvent event)
    {
        Minecraft mc=Minecraft.getMinecraft();if(event.getEntityPlayer()!=mc.player)return;
        UIBaseMenu menu=UIScreen.getCurrentMenu();
        UIFilmPanel film=menu instanceof UIDashboard&&((UIDashboard)menu).getPanels().panel instanceof UIFilmPanel?(UIFilmPanel)((UIDashboard)menu).getPanels().panel:null;
        if(film==null||!film.getController().canControl())
        {
            if(ownedSprint){KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(),false);ownedSprint=false;}
            if(film!=null&&film.getController().isControlling())clear(event.getMovementInput());
            return;
        }
        apply(event.getMovementInput(),mc.gameSettings,FilmControlInput::physicalKey);
        ownedSprint=true;
    }
    static boolean physicalKey(int code)
    {
        if(code<0)return code+100>=0&&code+100<Mouse.getButtonCount()&&Mouse.isButtonDown(code+100);
        return code>0&&code<Keyboard.KEYBOARD_SIZE&&Keyboard.isKeyDown(code);
    }
    private static boolean down(KeyBinding binding,IntPredicate keys)
    {return binding.getKeyModifier().isActive()&&keys.test(binding.getKeyCode());}
    static void apply(MovementInput input,GameSettings settings,IntPredicate keys)
    {
        input.forwardKeyDown=down(settings.keyBindForward,keys);input.backKeyDown=down(settings.keyBindBack,keys);
        input.leftKeyDown=down(settings.keyBindLeft,keys);input.rightKeyDown=down(settings.keyBindRight,keys);
        input.moveForward=(input.forwardKeyDown?1:0)-(input.backKeyDown?1:0);input.moveStrafe=(input.leftKeyDown?1:0)-(input.rightKeyDown?1:0);
        input.jump=down(settings.keyBindJump,keys);input.sneak=down(settings.keyBindSneak,keys);
        KeyBinding.setKeyBindState(settings.keyBindSprint.getKeyCode(),down(settings.keyBindSprint,keys));
        if(input.sneak){input.moveForward*=.3F;input.moveStrafe*=.3F;}
    }
    private static void clear(MovementInput input)
    {input.moveForward=input.moveStrafe=0;input.forwardKeyDown=input.backKeyDown=input.leftKeyDown=input.rightKeyDown=input.jump=input.sneak=false;}
    private FilmControlInput(){}
}
