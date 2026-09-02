package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input
{
    /* NOTE(1.21.11 port): movementVector is declared in the superclass Input (this mixin's declared
     * parent), so it is a normal inherited protected field accessed directly — NOT @Shadow. Mixin only
     * attaches @Shadow members found in the target class, and this field lives in the mixin's superclass. */

    private static float getMovementMultiplier(boolean positive, boolean negative)
    {
        return positive == negative ? 0F : (positive ? 1F : -1F);
    }

    /**
     * Whether the key this binding is bound to is down right now.
     *
     * <p>Asked of the window rather than of the binding, because a screen is open the whole time
     * an actor is puppeteered (the film editor is one) and vanilla stops updating its bindings
     * while a screen holds the keyboard. That is why this used to read W/A/S/D straight from GLFW
     * &mdash; which also meant a rebound layout steered nothing at all.
     */
    private static boolean isBoundKeyDown(KeyBinding binding)
    {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);

        if (key.getCode() == GLFW.GLFW_KEY_UNKNOWN)
        {
            return false;
        }

        if (key.getCategory() == InputUtil.Type.MOUSE)
        {
            return GLFW.glfwGetMouseButton(Window.getWindow(), key.getCode()) == GLFW.GLFW_PRESS;
        }

        return key.getCategory() == InputUtil.Type.KEYSYM && Window.isKeyPressed(key.getCode());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo info)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (!(menu instanceof UIDashboard dashboard) || !(dashboard.getPanels().panel instanceof UIFilmPanel filmPanel))
        {
            return;
        }

        /* canControl, not isControlling: an overlay over the preview takes the input for itself,
         * and the actor used to keep walking on the last keys it saw while its author was busy in
         * a popup. The mouse already stood down for an overlay; the keyboard did not. */
        if (!filmPanel.getController().canControl())
        {
            return;
        }

        GameOptions options = MinecraftClient.getInstance().options;

        boolean forward = isBoundKeyDown(options.forwardKey);
        boolean back = isBoundKeyDown(options.backKey);
        boolean left = isBoundKeyDown(options.leftKey);
        boolean right = isBoundKeyDown(options.rightKey);
        boolean jump = isBoundKeyDown(options.jumpKey);
        boolean sneak = isBoundKeyDown(options.sneakKey);

        /* Sprinting is not part of the input vanilla reads here — the player's own tick asks the
         * binding itself — so the binding is what has to be told, or a take could never record a
         * sprint at all. */
        options.sprintKey.setPressed(isBoundKeyDown(options.sprintKey));

        this.playerInput = new PlayerInput(forward, back, left, right, jump, sneak, this.playerInput.sprint());
        this.movementVector = new Vec2f(getMovementMultiplier(left, right), getMovementMultiplier(forward, back)).normalize();
    }
}
