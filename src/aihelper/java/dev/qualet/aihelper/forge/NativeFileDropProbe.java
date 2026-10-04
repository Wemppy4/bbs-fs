package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import mchorse.bbs_mod.graphics.window.NativeFileDrop;
import mchorse.bbs_mod.ui.utils.IFileDropListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Delivers an actual HDROP to the existing HWND, without input, focus, or window changes. */
final class NativeFileDropProbe
{
    private static final class Listener extends GuiScreen implements IFileDropListener
    {
        String[] received;
        @Override public void acceptFilePaths(String[] paths) { this.received = paths; }
    }

    static JsonObject run(String[] paths) throws Exception
    {
        JsonObject out = new JsonObject();
        if (!System.getProperty("os.name", "").startsWith("Windows"))
            throw new IllegalStateException("The native file-drop fixture requires the Windows LWJGL2 backend");
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen previous = mc.currentScreen;
        boolean attached = NativeFileDrop.isAttached();
        Listener listener = new Listener();
        NativeLibrary kernel = NativeLibrary.getInstance("kernel32");
        Function alloc = kernel.getFunction("GlobalAlloc",Function.ALT_CONVENTION);
        Function lock = kernel.getFunction("GlobalLock",Function.ALT_CONVENTION);
        Function unlock = kernel.getFunction("GlobalUnlock",Function.ALT_CONVENTION);
        Function free = kernel.getFunction("GlobalFree",Function.ALT_CONVENTION);
        Pointer drop = null;
        try
        {
            NativeFileDrop.attach();
            if (!NativeFileDrop.isAttached()) throw new IllegalStateException("Native WM_DROPFILES listener did not attach");
            Method handle = NativeFileDrop.class.getDeclaredMethod("windowHandle"); handle.setAccessible(true);
            Pointer window = new Pointer((Long)handle.invoke(null));
            byte[] names = (String.join("\0",paths) + "\0\0").getBytes(StandardCharsets.UTF_16LE);
            drop = alloc.invokePointer(new Object[]{0x42,20L+names.length});
            if (drop == null) throw new IllegalStateException("GlobalAlloc failed");
            Pointer memory = lock.invokePointer(new Object[]{drop});
            if (memory == null) throw new IllegalStateException("GlobalLock failed");
            memory.setInt(0,20); memory.setInt(16,1); memory.write(20,names,0,names.length);
            unlock.invokeInt(new Object[]{drop});
            /* All dispatch and polling run synchronously on the existing client thread. */
            mc.currentScreen = listener;
            NativeLibrary.getInstance("user32").getFunction("SendMessageW",Function.ALT_CONVENTION)
                .invokePointer(new Object[]{window,0x233,drop,Pointer.NULL});
            drop = null; // WM_DROPFILES transfers ownership to DragFinish in the production callback.
            NativeFileDrop.poll();
            out.addProperty("nativeCallback",Arrays.equals(paths,listener.received));
            out.addProperty("fileCount",listener.received == null ? 0 : listener.received.length);
            out.addProperty("unicodePath",listener.received != null && listener.received.length > 1 && listener.received[1].contains("Вемпи"));
        }
        finally
        {
            mc.currentScreen = previous;
            if (!attached) NativeFileDrop.detach();
            if (drop != null) free.invokePointer(new Object[]{drop});
        }
        out.addProperty("screenRestored",mc.currentScreen == previous);
        out.addProperty("attachmentRestored",NativeFileDrop.isAttached() == attached);
        return out;
    }
}
