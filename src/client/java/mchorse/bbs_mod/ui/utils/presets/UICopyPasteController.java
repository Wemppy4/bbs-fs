package mchorse.bbs_mod.ui.utils.presets;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.utils.presets.PresetManager;

import java.util.function.Supplier;

public class UICopyPasteController
{
    public final PresetManager manager;
    public final String copyPrefix;
    private Supplier<MapType> supplier;
    private IPaste consumer;

    private Supplier<Boolean> canCopy;
    private Supplier<Boolean> canPaste;

    private IKey copyLabel = UIKeys.GENERAL_COPY;
    private IKey pasteLabel = UIKeys.GENERAL_PASTE;

    public UICopyPasteController(PresetManager manager, String copyPrefix)
    {
        this.manager = manager;
        this.copyPrefix = copyPrefix;
    }

    /**
     * Name what is being copied ("Copy clips" rather than "Copy"). It is a property of the
     * thing, not of the moment the menu opens, so it is said once here.
     */
    public UICopyPasteController labels(IKey copy, IKey paste)
    {
        this.copyLabel = copy;
        this.pasteLabel = paste;

        return this;
    }

    public UICopyPasteController supplier(Supplier<MapType> supplier)
    {
        this.supplier = supplier;

        return this;
    }

    public UICopyPasteController consumer(IPaste consumer)
    {
        this.consumer = consumer;

        return this;
    }

    public UICopyPasteController canCopy(Supplier<Boolean> canCopy)
    {
        this.canCopy = canCopy;

        return this;
    }

    public UICopyPasteController canPaste(Supplier<Boolean> canPaste)
    {
        this.canPaste = canPaste;

        return this;
    }

    public IPaste getConsumer()
    {
        return this.consumer;
    }

    public Supplier<MapType> getSupplier()
    {
        return this.supplier;
    }

    public boolean copy()
    {
        MapType type = this.supplier.get();

        if (type != null)
        {
            Window.setClipboard(type, this.copyPrefix);
        }

        return type != null;
    }

    public boolean paste(int mouseX, int mouseY)
    {
        MapType type = Window.getClipboardMap(this.copyPrefix);

        if (type != null)
        {
            this.consumer.paste(type, mouseX, mouseY);
        }

        return type != null;
    }

    public void openPresets(UIContext context, int mouseX, int mouseY)
    {
        UIOverlay.addOverlay(context, new UIPresetsOverlayPanel(this, mouseX, mouseY), 240, 0.5F);
    }

    public boolean canCopy()
    {
        if (this.canCopy != null && !this.canCopy.get())
        {
            return false;
        }

        return true;
    }

    public boolean canPaste()
    {
        if (!this.canPreviewPresets())
        {
            return false;
        }

        return Window.getClipboardMap(this.copyPrefix) != null;
    }

    public boolean canPreviewPresets()
    {
        return !(this.canPaste != null && !this.canPaste.get());
    }

    /**
     * Put copy, paste and presets into a context menu's icon bar. For menus where pasting has
     * no place of its own to land in.
     */
    public void install(ContextMenuManager menu, UIContext context)
    {
        this.install(menu, context, 0, 0);
    }

    /**
     * The same, for menus where what is pasted lands where the menu was opened — a clip on a
     * timeline, a replay at a point in the world.
     */
    public void install(ContextMenuManager menu, UIContext context, int mouseX, int mouseY)
    {
        menu.icon(MenuVerb.COPY, this::copy).label(this.copyLabel).enabled(this.canCopy());
        menu.icon(MenuVerb.PASTE, () -> this.paste(mouseX, mouseY)).label(this.pasteLabel).enabled(this.canPaste());
        menu.icon(MenuVerb.PRESETS, () -> this.openPresets(context, mouseX, mouseY)).enabled(this.canPreviewPresets());
    }

    public static interface IPaste
    {
        public void paste(MapType mapType, int mouseX, int mouseY);
    }
}