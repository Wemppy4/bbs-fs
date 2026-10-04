package mchorse.bbs_mod.utils;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import java.lang.reflect.Field;

/** LWJGL2 equivalent of the original scoped export window resize. */
public final class WorldExportWindowSession
{
    private boolean active, changed, fullscreen, maximized;
    private int width, height, x, y;
    private Pointer window;

    public void begin(int width, int height)
    {
        if (!this.active)
        {
            this.active = true; this.changed = false;
            this.width = Display.getWidth(); this.height = Display.getHeight();
            this.x = Display.getX(); this.y = Display.getY(); this.fullscreen = Display.isFullscreen();
            this.window = nativeWindow(); this.maximized = this.window != null && user("IsZoomed").invokeInt(new Object[]{this.window}) != 0;
        }
        if (this.fullscreen) return;
        if (this.maximized) user("ShowWindow").invokeInt(new Object[]{this.window,9});
        this.changed = this.maximized || this.width != width || this.height != height;
        resize(width,height);
        Display.setLocation(this.x,this.y);
    }

    public void restore()
    {
        try
        {
            if (this.active && !this.fullscreen && this.changed)
            {
                resize(this.width,this.height);
                Display.setLocation(this.x,this.y);
                if (this.maximized && this.window != null) user("ShowWindow").invokeInt(new Object[]{this.window,3});
            }
        }
        finally { this.clear(); }
    }

    public void clear() { this.active = this.changed = this.maximized = false; this.window = null; }

    private static void resize(int width, int height)
    {
        width = Math.max(2,width); height = Math.max(2,height);
        try
        {
            if (Display.getWidth() != width || Display.getHeight() != height) Display.setDisplayMode(new DisplayMode(width,height));
            Minecraft client = Minecraft.getMinecraft();
            if (client.displayWidth != width || client.displayHeight != height) client.resize(width,height);
        }
        catch (org.lwjgl.LWJGLException error) { throw new IllegalStateException("Cannot resize Minecraft export window",error); }
    }

    private static Function user(String function) { return NativeLibrary.getInstance("user32").getFunction(function,Function.ALT_CONVENTION); }
    private static Pointer nativeWindow()
    {
        if (!System.getProperty("os.name", "").startsWith("Windows") || Display.getParent() != null) return null;
        try
        {
            Field field = Display.class.getDeclaredField("display_impl"); field.setAccessible(true);
            Object implementation = field.get(null);
            Field handle = implementation.getClass().getDeclaredField("hwnd"); handle.setAccessible(true);
            return new Pointer(handle.getLong(implementation));
        }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot inspect native export window",error); }
    }
}
