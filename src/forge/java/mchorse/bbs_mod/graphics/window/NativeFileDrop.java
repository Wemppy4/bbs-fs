package mchorse.bbs_mod.graphics.window;

import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.utils.IFileDropListener;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import java.awt.Canvas;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** File-drop boundary for LWJGL2. Windows' existing HWND receives WM_DROPFILES; embedded canvases use AWT. */
public final class NativeFileDrop {
    private static final Queue<String[]> PENDING = new ConcurrentLinkedQueue<>();
    private static WindowsDrop windows;
    /* Keep callbacks alive if another native subclass has chained to ours. */
    private static final List<WindowsDrop> WINDOWS_HOOKS = new ArrayList<>();
    private static DropTarget awt;
    private static boolean warned;
    private static boolean enabled;
    private NativeFileDrop() {}

    public static void attach() {
        enabled = true;
        try {
            Canvas parent = Display.getParent();
            if (parent != null) {
                if (awt == null) awt = new DropTarget(parent, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
                    @Override public void drop(DropTargetDropEvent event) {
                        if (!enabled || !event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { event.rejectDrop(); return; }
                        try {
                            event.acceptDrop(DnDConstants.ACTION_COPY);
                            List<?> files = (List<?>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                            List<String> paths = new ArrayList<>();
                            for (Object file : files) if (file instanceof java.io.File) paths.add(((java.io.File) file).getAbsolutePath());
                            PENDING.add(paths.toArray(new String[0])); event.dropComplete(true);
                        } catch (Exception error) { event.dropComplete(false); BBSMod.LOGGER.error("Could not read dropped files", error); }
                    }
                }, true);
                awt.setActive(true);
            } else if (System.getProperty("os.name", "").startsWith("Windows")) {
                long handle = windowHandle();
                if (windows == null || !windows.alive || Pointer.nativeValue(windows.window) != handle) {
                    if (windows != null) windows.close();
                    windows = new WindowsDrop(new Pointer(handle));
                    WINDOWS_HOOKS.add(windows);
                }
                windows.accept(true);
            }
        } catch (Exception | LinkageError error) {
            if (!warned) { warned=true; BBSMod.LOGGER.warn("Native file drop could not be attached",error); }
        }
    }
    public static void detach() {
        enabled=false; PENDING.clear();
        if (windows!=null) windows.accept(false);
        if (awt!=null) awt.setActive(false);
    }
    public static void poll() {
        String[] paths;
        while((paths=PENDING.poll())!=null) {
            net.minecraft.client.gui.GuiScreen screen=Minecraft.getMinecraft().currentScreen;
            if(enabled && screen instanceof IFileDropListener) ((IFileDropListener)screen).acceptFilePaths(paths);
        }
    }
    /** Same queue used by the native callbacks; useful for non-native window integrations. */
    public static void enqueue(String[] paths) { if(paths!=null&&paths.length>0) PENDING.add(paths.clone()); }
    public static boolean isAttached() { return enabled && ((windows!=null&&windows.alive)||awt!=null); }
    private static long windowHandle() throws ReflectiveOperationException {
        Field implementation=Display.class.getDeclaredField("display_impl");implementation.setAccessible(true);
        Object display=implementation.get(null);
        Field hwnd=display.getClass().getDeclaredField("hwnd");hwnd.setAccessible(true);
        long handle=hwnd.getLong(display);
        if(handle==0)throw new IllegalStateException("LWJGL window has no handle");
        return handle;
    }
    public interface WindowProc extends StdCallLibrary.StdCallCallback {
        Pointer invoke(Pointer window,int message,Pointer wParam,Pointer lParam);
    }
    private static final class WindowsDrop {
        private static final int GWL_WNDPROC=-4,WM_DROPFILES=0x233,WM_NCDESTROY=0x82;
        private final NativeLibrary user=NativeLibrary.getInstance("user32"),shell=NativeLibrary.getInstance("shell32");
        private final Function set=user.getFunction(Native.POINTER_SIZE==8?"SetWindowLongPtrW":"SetWindowLongW",Function.ALT_CONVENTION);
        private final Function get=user.getFunction(Native.POINTER_SIZE==8?"GetWindowLongPtrW":"GetWindowLongW",Function.ALT_CONVENTION);
        private final Function call=user.getFunction("CallWindowProcW",Function.ALT_CONVENTION);
        private final Function accept=shell.getFunction("DragAcceptFiles",Function.ALT_CONVENTION);
        private final Function query=shell.getFunction("DragQueryFileW",Function.ALT_CONVENTION);
        private final Function finish=shell.getFunction("DragFinish",Function.ALT_CONVENTION);
        private final Pointer window;
        private final WindowProc callback=this::message;
        private final Pointer function=CallbackReference.getFunctionPointer(callback);
        private final Pointer previous;
        private boolean alive=true;
        WindowsDrop(Pointer window) {
            this.window=window;
            previous=set.invokePointer(new Object[]{window,GWL_WNDPROC,function});
            if(previous==null)throw new IllegalStateException("SetWindowLongPtrW failed: "+Native.getLastError());
        }
        void accept(boolean value) {if(alive)accept.invokeVoid(new Object[]{window,value?1:0});}
        void close() {
            if(!alive)return;
            accept(false);
            // Do not break a later subclass installed by another mod.
            Pointer active=get.invokePointer(new Object[]{window,GWL_WNDPROC});
            if(function.equals(active))set.invokePointer(new Object[]{window,GWL_WNDPROC,previous});
            alive=false;
        }
        private Pointer message(Pointer hwnd,int message,Pointer wParam,Pointer lParam) {
            if(message==WM_DROPFILES) {
                try {
                    int count=query.invokeInt(new Object[]{wParam,-1,null,0});
                    String[] paths=new String[count];
                    for(int i=0;i<count;i++) {
                        int length=query.invokeInt(new Object[]{wParam,i,null,0});
                        Memory buffer=new Memory((length+1L)*Native.WCHAR_SIZE);
                        query.invokeInt(new Object[]{wParam,i,buffer,length+1});
                        paths[i]=buffer.getWideString(0);
                    }
                    if(enabled)PENDING.add(paths);
                } catch(RuntimeException error) {BBSMod.LOGGER.error("Could not read Windows file drop",error);}
                finally {finish.invokeVoid(new Object[]{wParam});}
                return null;
            }
            Pointer result=call.invokePointer(new Object[]{previous,hwnd,message,wParam,lParam});
            if(message==WM_NCDESTROY)alive=false;
            return result;
        }
    }
}
