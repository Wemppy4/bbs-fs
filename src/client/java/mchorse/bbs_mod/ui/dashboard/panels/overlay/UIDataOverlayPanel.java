package mchorse.bbs_mod.ui.dashboard.panels.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.DataPath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;

public class UIDataOverlayPanel <T extends ValueGroup> extends UICRUDOverlayPanel
{
    protected UIDataDashboardPanel<T> panel;

    /** Clipboard-only (no presets): the panel's saved files are already its "presets". */
    protected UICopyPasteController copyPasteController;

    public UIDataOverlayPanel(IKey title, UIDataDashboardPanel<T> panel, Consumer<String> callback)
    {
        super(title, callback);

        this.panel = panel;

        this.copyPasteController = new UICopyPasteController(null, "_ContentType_" + panel.getType().getId())
            .labels(UIKeys.PANELS_CONTEXT_COPY, UIKeys.PANELS_CONTEXT_PASTE)
            .supplier(() -> this.panel.getData() == null ? null : this.panel.getData().toData().asMap())
            .canCopy(() -> this.panel.getData() != null)
            .consumer((data, mouseX, mouseY) -> this.addNewData(this.getContext(), data));

        this.namesList.done = (path) -> !path.folder && BBSSettings.doneData.get().contains(this.donePrefix() + path);
        this.namesList.context((menu) ->
        {
            DataPath selected = this.namesList.getCurrentFirst();

            if (selected != null && !selected.folder)
            {
                boolean done = this.namesList.isDone(selected);

                menu.action(Icons.CHECKMARK, done ? UIKeys.PANELS_CONTEXT_UNMARK_DONE : UIKeys.PANELS_CONTEXT_MARK_DONE, () -> this.setDone(selected.toString(), !done));
            }

            /* The entries that create data follow the header buttons; copying and opening the folder are
             * read-only, so they stay available even when the panel is a pure picker. */
            boolean editable = this.showActionButtons();

            if (editable)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addNewFolder);
            }

            /* Copying stays available even in a pure picker; pasting creates data, so it is
             * offered only where the panel can create. The controller supplies the standard
             * labels and the greying-out other editors' menus already have. */
            if (editable)
            {
                this.copyPasteController.installClipboard(menu, 0, 0);
            }
            else
            {
                menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this.copyPasteController::copy);
            }

            File folder = this.panel.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, this::openFolder);
            }
        });
    }

    /* Done marks */

    /**
     * Where this panel's marks start in {@link BBSSettings#doneData}. Documents kept on the server
     * (films) belong to a world, so their marks are told apart by world or server: film "2" of one
     * world isn't done in another.
     */
    private String donePrefix()
    {
        String prefix = this.panel.getType().getId();

        if (this.panel.getType().getRepository().getFolder() == null)
        {
            prefix += "@" + getWorldScope();
        }

        return prefix + ":";
    }

    private static String getWorldScope()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getServer() != null)
        {
            return String.valueOf(mc.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName());
        }

        ServerInfo server = mc.getCurrentServerEntry();

        return server == null ? "" : server.address;
    }

    private void setDone(String id, boolean done)
    {
        Set<String> keys = BBSSettings.doneData.get();

        if (done) keys.add(this.donePrefix() + id);
        else keys.remove(this.donePrefix() + id);

        BBSSettings.doneData.set(keys);
    }

    /**
     * Carry the marks along when a document or a folder is renamed, and drop them when it's
     * removed ({@code to} is null), so a new document with an old name doesn't come in done.
     */
    private void moveDone(String from, String to, boolean folder)
    {
        Set<String> keys = BBSSettings.doneData.get();
        String prefix = this.donePrefix();
        String fromKey = prefix + from;
        boolean changed = false;

        for (String key : new ArrayList<>(keys))
        {
            String rest;

            if (!folder && key.equals(fromKey)) rest = "";
            else if (folder && key.startsWith(fromKey + "/")) rest = key.substring(fromKey.length());
            else continue;

            keys.remove(key);

            if (to != null) keys.add(prefix + to + rest);

            changed = true;
        }

        if (changed)
        {
            BBSSettings.doneData.set(keys);
        }
    }

    protected void openFolder()
    {
        File folder = this.panel.getType().getRepository().getFolder();

        if (folder != null)
        {
            UIUtils.openFolder(new File(folder, this.namesList.getPath().toString()));
        }
    }

    /* CRUD */

    @Override
    protected void addNewData(UIContext context, String name, MapType mapType)
    {
        if (name.trim().isEmpty())
        {
            context.notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        if (!this.namesList.hasInHierarchy(name))
        {
            this.panel.save();

            T data = null;
            this.namesList.addFile(name);

            if (mapType == null)
            {
                data = (T) this.panel.getType().getRepository().create(name);

                this.fillDefaultData(data);
            }
            else
            {
                data = (T) this.panel.getType().getRepository().create(name, mapType);
            }

            this.panel.fill(data);
        }
    }

    @Override
    protected void addNewFolder(String path)
    {
        if (path.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        this.panel.getType().getRepository().addFolder(path, (bool) ->
        {
            if (bool)
            {
                this.panel.requestNames();
            }
        });
    }

    protected void fillDefaultData(T data)
    {
        this.panel.fillDefaultData(data);
    }

    @Override
    protected void dupeData(String name)
    {
        if (name.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        if (this.panel.getData() != null && !this.namesList.hasInHierarchy(name))
        {
            this.panel.save();
            this.panel.getType().getRepository().save(name, this.panel.getData().toData().asMap());
            this.namesList.addFile(name);

            T data = (T) this.panel.getType().getRepository().create(name, this.panel.getData().toData().asMap());

            this.panel.fill(data);
        }
    }

    @Override
    protected void renameData(String name)
    {
        if (name.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        if (this.panel.getData() != null && !this.namesList.hasInHierarchy(name))
        {
            String from = this.panel.getData().getId();

            this.panel.getType().getRepository().rename(from, name);

            this.namesList.removeFile(this.panel.getData().getId());
            this.namesList.addFile(name);

            this.panel.getData().setId(name);
            this.panel.onDataRenamed(from, name);
            this.moveDone(from, name, false);
        }
    }

    @Override
    protected void renameFolder(String name)
    {
        if (name.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        String path = this.namesList.getCurrentFirst().toString();

        this.panel.getType().getRepository().renameFolder(path, name, (bool) ->
        {
            if (bool)
            {
                if (this.panel.getData() != null)
                {
                    String id = this.panel.getData().getId();

                    if (id.startsWith(path + "/"))
                    {
                        this.panel.getData().setId(name + id.substring(path.length()));
                    }
                }

                this.panel.onDataFolderRenamed(path, name);
                this.moveDone(path, name, true);

                this.panel.requestNames();
            }
        });
    }

    @Override
    protected void removeData()
    {
        if (this.panel.getData() != null)
        {
            String id = this.panel.getData().getId();

            this.panel.getType().getRepository().delete(id);

            this.namesList.removeFile(this.panel.getData().getId());
            this.panel.fill(null);
            this.panel.onDataRemoved(id);
            this.moveDone(id, null, false);
        }
    }

    @Override
    protected void removeFolder()
    {
        String path = this.namesList.getCurrentFirst().toString();

        this.panel.getType().getRepository().deleteFolder(path, (bool) ->
        {
            if (bool)
            {
                this.panel.onDataFolderRemoved(path);
                this.moveDone(path, null, true);
                this.panel.requestNames();
            }
        });
    }
}
