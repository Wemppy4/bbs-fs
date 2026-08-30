package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.input.KeyboardInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin
{
    private static float getMovementMultiplier(boolean positive, boolean negative)
    {
        return positive == negative ? 0F : (positive ? 1F : -1F);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(boolean slowDown, float slowDownFactor, CallbackInfo info)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (
            menu instanceof UIDashboard dashboard &&
            dashboard.getPanels().panel instanceof UIFilmPanel filmPanel &&
            filmPanel.getController().isControlling()
        ) {
            KeyboardInput input = (KeyboardInput) (Object) this;

            input.pressingForward = Window.isKeyPressed(GLFW.GLFW_KEY_W);
            input.pressingBack = Window.isKeyPressed(GLFW.GLFW_KEY_S);
            input.pressingLeft = Window.isKeyPressed(GLFW.GLFW_KEY_A);
            input.pressingRight = Window.isKeyPressed(GLFW.GLFW_KEY_D);
            input.movementForward = getMovementMultiplier(input.pressingForward, input.pressingBack);
            input.movementSideways = getMovementMultiplier(input.pressingLeft, input.pressingRight);
            input.jumping = Window.isKeyPressed(GLFW.GLFW_KEY_SPACE);
            input.sneaking = Window.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT);

            if (slowDown)
            {
                input.movementSideways *= slowDownFactor;
                input.movementForward *= slowDownFactor;
            }
        }
    }
}