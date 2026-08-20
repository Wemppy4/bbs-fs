package mchorse.bbs_mod.ui.utils;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * The folder picker Explorer itself puts up - the Common Item Dialog, the one
 * with the address bar, the sidebar and the search box, on every Windows since
 * Vista.
 *
 * tinyfd only knows SHBrowseForFolder, the cramped little tree box from Windows
 * 2000, and offers no switch for anything newer, so Windows gets this instead.
 * Every other OS stays on tinyfd, where its dialogs are the native ones already.
 *
 * There is no COM binding in the game, so the interfaces are driven by vtable
 * slot through JNA, which Minecraft ships anyway for OSHI. The slot numbers are
 * the order the methods are declared in, counting the three IUnknown ones every
 * interface starts with - COM's ABI freezes that order forever, which is what
 * makes calling by number safe.
 */
class WindowsFolderDialog
{
    private static final Guid.GUID CLSID_FILE_OPEN_DIALOG = new Guid.GUID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}");
    private static final Guid.GUID IID_FILE_OPEN_DIALOG = new Guid.GUID("{D57C7288-D4AD-4768-BE02-9D969532D960}");
    private static final Guid.GUID IID_SHELL_ITEM = new Guid.GUID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}");

    private static final int CLSCTX_INPROC_SERVER = 0x1;
    /** Turns the file dialog into a folder dialog */
    private static final int FOS_PICKFOLDERS = 0x20;
    /** Refuses the virtual folders, so a pick always has a real path */
    private static final int FOS_FORCEFILESYSTEM = 0x40;
    private static final int SIGDN_FILESYSPATH = 0x80058000;

    /**
     * @param folder where to open at, or null to let Windows decide
     * @return the picked folder, or null when it was cancelled
     * @throws RuntimeException when the dialog couldn't be put up at all, which
     *         is the caller's cue to fall back to tinyfd
     */
    public static String pick(String title, String folder)
    {
        /* The dialog is apartment threaded, and this is a thread of our own, so
         * nothing else can have claimed it for a different model */
        WinNT.HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
        boolean owned = COMUtils.SUCCEEDED(initialized);

        try
        {
            PointerByReference created = new PointerByReference();
            WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(CLSID_FILE_OPEN_DIALOG, null, CLSCTX_INPROC_SERVER, IID_FILE_OPEN_DIALOG, created);

            if (COMUtils.FAILED(hr))
            {
                throw new RuntimeException("Couldn't create the file open dialog: " + hr.intValue());
            }

            FileDialog dialog = new FileDialog(created.getValue());

            try
            {
                return pick(dialog, title, folder);
            }
            finally
            {
                dialog.Release();
            }
        }
        finally
        {
            if (owned)
            {
                Ole32.INSTANCE.CoUninitialize();
            }
        }
    }

    private static String pick(FileDialog dialog, String title, String folder)
    {
        IntByReference options = new IntByReference();

        dialog.getOptions(options);
        dialog.setOptions(options.getValue() | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);

        if (title != null)
        {
            dialog.setTitle(new WString(title));
        }

        setFolder(dialog, folder);

        /* No owner window: this runs on a thread of its own, and handing the
         * dialog the game's window would have it disable a window belonging to
         * another thread for as long as it is up */
        if (COMUtils.FAILED(dialog.show(null)))
        {
            /* Cancelling comes back as a failed result too, and there is no
             * telling it from a real error here - either way there is no pick */
            return null;
        }

        PointerByReference picked = new PointerByReference();

        if (COMUtils.FAILED(dialog.getResult(picked)))
        {
            return null;
        }

        ShellItem item = new ShellItem(picked.getValue());

        try
        {
            PointerByReference name = new PointerByReference();

            if (COMUtils.FAILED(item.getDisplayName(SIGDN_FILESYSPATH, name)))
            {
                return null;
            }

            Pointer pointer = name.getValue();

            try
            {
                return pointer.getWideString(0);
            }
            finally
            {
                /* The shell allocated this string and wants it back */
                Ole32.INSTANCE.CoTaskMemFree(pointer);
            }
        }
        finally
        {
            item.Release();
        }
    }

    /**
     * Opening at the folder the setting points at. Worth having, not worth
     * failing over - a dialog that opened elsewhere still picks folders.
     */
    private static void setFolder(FileDialog dialog, String folder)
    {
        if (folder == null || folder.isEmpty())
        {
            return;
        }

        try
        {
            PointerByReference item = new PointerByReference();
            WinNT.HRESULT hr = Shell.INSTANCE.SHCreateItemFromParsingName(new WString(folder), null, IID_SHELL_ITEM, item);

            if (COMUtils.SUCCEEDED(hr))
            {
                ShellItem shellItem = new ShellItem(item.getValue());

                dialog.setFolder(shellItem.getPointer());
                shellItem.Release();
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
    }

    /**
     * Not part of JNA's own Shell32 binding, and it takes no A/W suffix, so it
     * is mapped by its plain name.
     */
    private interface Shell extends StdCallLibrary
    {
        Shell INSTANCE = Native.load("shell32", Shell.class);

        WinNT.HRESULT SHCreateItemFromParsingName(WString path, Pointer bindContext, Guid.GUID riid, PointerByReference item);
    }

    /** IFileDialog - IFileOpenDialog's own two slots sit past these and go unused */
    private static class FileDialog extends Unknown
    {
        public FileDialog(Pointer pointer)
        {
            super(pointer);
        }

        public WinNT.HRESULT show(Pointer owner)
        {
            return hresult(this._invokeNativeInt(3, new Object[] {this.getPointer(), owner}));
        }

        public WinNT.HRESULT setOptions(int options)
        {
            return hresult(this._invokeNativeInt(9, new Object[] {this.getPointer(), options}));
        }

        public WinNT.HRESULT getOptions(IntByReference options)
        {
            return hresult(this._invokeNativeInt(10, new Object[] {this.getPointer(), options}));
        }

        public WinNT.HRESULT setFolder(Pointer item)
        {
            return hresult(this._invokeNativeInt(12, new Object[] {this.getPointer(), item}));
        }

        public WinNT.HRESULT setTitle(WString title)
        {
            return hresult(this._invokeNativeInt(17, new Object[] {this.getPointer(), title}));
        }

        public WinNT.HRESULT getResult(PointerByReference item)
        {
            return hresult(this._invokeNativeInt(20, new Object[] {this.getPointer(), item}));
        }
    }

    private static class ShellItem extends Unknown
    {
        public ShellItem(Pointer pointer)
        {
            super(pointer);
        }

        public WinNT.HRESULT getDisplayName(int form, PointerByReference name)
        {
            return hresult(this._invokeNativeInt(5, new Object[] {this.getPointer(), form, name}));
        }
    }

    private static WinNT.HRESULT hresult(int value)
    {
        return new WinNT.HRESULT(value);
    }
}
