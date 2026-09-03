package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.OS;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class UIUtils
{
    /**
     * Open web link (in default web browser)
     */
    public static boolean openWebLink(String address)
    {
        if (OS.CURRENT == OS.WINDOWS)
        {
            return runSysCommand("rundll32", "url.dll,FileProtocolHandler", address);
        }
        else if (OS.CURRENT == OS.MACOS)
        {
            return runSysCommand("open", address);
        }

        return runSysCommand("kde-open", address)
            || runSysCommand("gnome-open", address)
            || runSysCommand("xdg-open", address);
    }

    /**
     * Open a folder (in default file browser)
     */
    public static boolean openFolder(File folder)
    {
        try
        {
            String path = folder.getAbsolutePath();

            if (OS.CURRENT == OS.WINDOWS)
            {
                return runSysCommand("explorer", path);
            }
            else if (OS.CURRENT == OS.MACOS)
            {
                return runSysCommand("open", path);
            }

            return runSysCommand("kde-open", path)
                || runSysCommand("gnome-open", path)
                || runSysCommand("xdg-open", path);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    private static boolean runSysCommand(String... command)
    {
        try
        {
            Process p = Runtime.getRuntime().exec(command);

            if (p == null)
            {
                return false;
            }

            try
            {
                return p.exitValue() == 0;
            }
            catch (IllegalThreadStateException e)
            {
                return true;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return false;
        }
    }

    /**
     * Map a GUI area to a framebuffer-pixel viewport and apply it. GUI units map
     * to pixels by the window's scale factor, which since ui_scale became a float
     * can be fractional — so no rounding of the scale itself, only of the final
     * pixel edges. The window getters are the overridden ones during video
     * export, so the same mapping holds there.
     *
     * @return {x, y, w, h} of the applied viewport, for building a matching projection
     */
    public static int[] viewportArea(Area area)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        float scale = (float) mc.getWindow().getScaleFactor();

        int vx = Math.round(area.x * scale);
        int vy = Math.round(mc.getWindow().getFramebufferHeight() - (area.y + area.h) * scale);
        int vw = Math.round(area.w * scale);
        int vh = Math.round(area.h * scale);

        RenderSystem.viewport(vx, vy, vw, vh);

        return new int[] {vx, vy, vw, vh};
    }

    /**
     * Read the viewport that is set right now.
     *
     * <p>Restoring a viewport by <em>deriving</em> it is a bug: neither the window
     * size nor {@link net.minecraft.client.gl.Framebuffer#beginWrite(boolean)
     * beginWrite(true)} tells the truth while a film renders. Both then report the
     * video framebuffer's size (video size x framebuffer scale) instead of the
     * screen's, and everything the UI draws afterwards lands in a viewport bigger
     * than the window. Save with this around any pass that binds another
     * framebuffer, and put it back with {@link #restoreViewport(int[])}.
     */
    public static int[] currentViewport()
    {
        int[] viewport = new int[4];

        GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);

        return viewport;
    }

    /**
     * Put back a viewport captured by {@link #currentViewport()}.
     */
    public static void restoreViewport(int[] viewport)
    {
        if (viewport == null)
        {
            return;
        }

        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    /** Enable or disable every control in a container — the container stands, only its fields go quiet. */
    public static void setEnabledDeep(UIElement container, boolean enabled)
    {
        for (UIElement element : container.getChildren(UIElement.class, new ArrayList<>(), false))
        {
            element.setEnabled(enabled);
        }
    }

    public static void playClick()
    {
        playClick(1F);
    }

    public static void playClick(float pitch)
    {
        if (BBSSettings.clickSound.get())
        {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(BBSMod.CLICK, pitch));
        }
        else
        {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }
}