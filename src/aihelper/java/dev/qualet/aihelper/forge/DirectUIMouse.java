package dev.qualet.aihelper.forge;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Test-only per-frame mouse cache. Never moves or grabs the operating-system cursor. */
public final class DirectUIMouse
{
    private static final Field X = field("x"), Y = field("y"), BUTTONS = field("buttons");
    private static GuiScreen screen;
    private static int x, y;
    private static final boolean[] down = new boolean[16];
    private int previousX, previousY;
    private byte[] previousButtons;

    private static Field field(String name)
    {
        try { Field field = Mouse.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }

    public static void set(GuiScreen target, int mouseX, int mouseY, int button, String mode)
    {
        if (screen != target) clear();
        screen = target; x = mouseX; y = mouseY;
        if (button >= 0 && button < down.length)
        {
            if ("down".equals(mode)) down[button] = true;
            if ("up".equals(mode)) down[button] = false;
        }
    }

    public static void clear() { screen = null; java.util.Arrays.fill(down, false); }

    @SubscribeEvent public void before(GuiScreenEvent.DrawScreenEvent.Pre event)
    {
        if (screen == null) return;
        if (event.getGui() != screen || !(screen instanceof UIScreen)) { clear(); return; }
        try
        {
            previousX = X.getInt(null); previousY = Y.getInt(null);
            ByteBuffer buttons = (ByteBuffer) BUTTONS.get(null);
            previousButtons = new byte[buttons.capacity()];
            for (int i = 0; i < previousButtons.length; i++)
            {
                previousButtons[i] = buttons.get(i);
                buttons.put(i, (byte) (i < down.length && down[i] ? 1 : 0));
            }
            float scale = BBSModClient.getGUIScale();
            X.setInt(null, Math.round(x * scale));
            Y.setInt(null, Minecraft.getMinecraft().displayHeight - 1 - Math.round(y * scale));
        }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    @SubscribeEvent public void after(GuiScreenEvent.DrawScreenEvent.Post event)
    {
        if (previousButtons == null) return;
        try
        {
            X.setInt(null, previousX); Y.setInt(null, previousY);
            ByteBuffer buttons = (ByteBuffer) BUTTONS.get(null);
            for (int i = 0; i < previousButtons.length; i++) buttons.put(i, previousButtons[i]);
            previousButtons = null;
        }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
