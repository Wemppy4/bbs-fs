package mchorse.bbs_mod.ui.utils;

import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.OS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;

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
        if (folder == null || !folder.isDirectory())
        {
            return false;
        }

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

    public static boolean openModelFolder(String id)
    {
        if (id == null || id.isEmpty())
        {
            return false;
        }

        File folder = BBSModClient.getModels().getModelFolder(id);

        /* Built-in models may not have an external folder yet. Never launch Explorer with a missing path. */
        return folder != null && (folder.isDirectory() || folder.mkdirs()) && openFolder(folder);
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
        Minecraft mc = Minecraft.getMinecraft();
        float scale = BBSModClient.getGUIScale();

        int vx = Math.round(area.x * scale);
        int vy = Math.round(mc.displayHeight - (area.y + area.h) * scale);
        int vw = Math.round(area.w * scale);
        int vh = Math.round(area.h * scale);

        GlStateManager.viewport(vx, vy, vw, vh);

        return new int[] {vx, vy, vw, vh};
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
            Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(BBSMod.CLICK, pitch));
        }
        else
        {
            Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }
}
