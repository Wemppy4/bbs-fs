package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

/**
 * The mouse while an actor is being driven by hand: either it looks around, the way it does in
 * the game, or it becomes a gamepad stick that writes into the actor's extra variables — the
 * left and right sticks, the triggers, and the two spare pairs.
 *
 * <p>The mode is a walk, not a set: the same key steps through look → left stick → right stick
 * → triggers → extra 1 → extra 2 and around again, which is why it is kept as a counter and
 * read modulo six rather than clamped.
 *
 * <p>Stick values are NOT this object's truth — the actor's variables are. Switching to a stick
 * reads the actor's current values back into the stick so it picks up where the channel left
 * off instead of snapping to centre.
 */
public class ActorMouseControl
{
    /** How many screen pixels a stick needs to travel its full range. */
    private static final float STICK_SENSITIVITY = 100F;

    private static final int MODES = 6;

    private int mode;

    private final Vector2f stick = new Vector2f();
    private final Vector2i lastMouse = new Vector2i();

    /** The active step of the walk: 0 looks around, 1..5 drive a stick pair. */
    public int getMode()
    {
        return this.mode % MODES;
    }

    /** Whether the mouse is looking around rather than driving a stick. */
    public boolean isLookMode()
    {
        return this.getMode() == 0;
    }

    public Vector2f getStick()
    {
        return this.stick;
    }

    /**
     * Switches the walk to that step, picking the stick up where the actor's variables already
     * stand. Pass the driven actor, or null when nothing is being driven.
     */
    public void setMode(int mode, IEntity controlled)
    {
        this.mode = mode;

        if (controlled != null)
        {
            int index = this.getMode() - 1;

            if (index >= 0)
            {
                float[] variables = controlled.getExtraVariables();

                this.stick.set(variables[index * 2 + 1], variables[index * 2]);
            }
        }
    }

    /** Writes the stick into the actor's variables for this tick. */
    public void applyTo(IEntity controlled)
    {
        if (this.isLookMode() || controlled == null)
        {
            return;
        }

        int index = this.getMode() - 1;
        float[] extraVariables = controlled.getExtraVariables();

        extraVariables[index * 2] = this.stick.y;
        extraVariables[index * 2 + 1] = this.stick.x;
    }

    /**
     * Follows the cursor for one frame: turns the actor's head in look mode (only on a server
     * that runs the mod, where the turn is authoritative), otherwise pushes the stick.
     *
     * <p>The last cursor position is remembered even when nothing is being driven, so taking
     * control does not read the whole idle travel as one jerk of the stick.
     */
    public void trackCursor(boolean driving, boolean onModdedServer)
    {
        Mouse mouse = MinecraftClient.getInstance().mouse;
        int x = (int) mouse.getX();
        int y = (int) mouse.getY();

        if (driving)
        {
            if (this.isLookMode() && onModdedServer)
            {
                float cursorDeltaX = (x - this.lastMouse.x) / 2F;
                float cursorDeltaY = (y - this.lastMouse.y) / 2F;

                MinecraftClient.getInstance().player.changeLookDirection(cursorDeltaX, cursorDeltaY);
            }
            else
            {
                float xx = (y - this.lastMouse.y) / STICK_SENSITIVITY;
                float yy = (x - this.lastMouse.x) / STICK_SENSITIVITY;

                this.stick.add(xx, yy);
                this.stick.x = MathUtils.clamp(this.stick.x, -1F, 1F);
                this.stick.y = MathUtils.clamp(this.stick.y, -1F, 1F);
            }
        }

        this.lastMouse.set(x, y);
    }

    /** Hides the cursor while driving, so the mouse can travel without hitting the screen edge. */
    public static void togglePointer(boolean disable)
    {
        net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();

        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, disable ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
    }
}
