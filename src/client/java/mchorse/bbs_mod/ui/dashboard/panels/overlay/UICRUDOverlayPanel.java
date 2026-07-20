package mchorse.bbs_mod.ui.dashboard.panels.overlay;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;

import java.util.function.Consumer;

public abstract class UICRUDOverlayPanel extends UIOverlayPanel
{
    public UIIcon add;
    public UIIcon dupe;
    public UIIcon rename;
    public UIIcon remove;
    public UISearchList<DataPath> names;
    public UIDataPathList namesList;

    protected Consumer<String> callback;

    public UICRUDOverlayPanel(IKey title, Consumer<String> callback)
    {
        super(title);

        this.callback = callback;

        this.add = new UIIcon(Icons.ADD, (b) ->
        {
            if (Window.isShiftPressed())
            {
                this.addNewData(this.getNextAutoId(), null);
            }
            else
            {
                this.addNewData(null);
            }
        });
        this.add.context((menu) -> menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addNewFolder));
        this.dupe = new UIIcon(Icons.DUPE, this::dupeData);
        this.rename = new UIIcon(Icons.EDIT, this::renameData);
        this.remove = new UIIcon(Icons.REMOVE, this::removeData);

        this.names = new UISearchList<>(new UIDataPathList((list) ->
        {
            if (this.callback != null)
            {
                this.callback.accept(list.get(0).toString());
            }
        }));
        this.names.full(this.content).x(6).w(1F, -12);
        this.namesList = (UIDataPathList) this.names.list;
        this.names.label(UIKeys.GENERAL_SEARCH);
        this.content.add(this.names);

        if (this.showActionButtons())
        {
            this.icons.add(this.add, this.dupe, this.rename, this.remove);
        }
    }

    /**
     * Whether create/duplicate/rename/remove are offered. Asset-backed panels (e.g. the model editor)
     * turn this off, leaving the overlay as a pure picker — mirroring the same hook on the selection
     * screen. The buttons still exist as fields, they're just never mounted. Default true.
     */
    protected boolean showActionButtons()
    {
        return true;
    }

    private String getNextAutoId()
    {
        int i = 1;

        while (true)
        {
            DataPath copy = this.namesList.getPath().copy();

            copy.combine(new DataPath(String.valueOf(i)));

            if (!this.namesList.getList().contains(copy))
            {
                return copy.toString();
            }

            i += 1;

            if (i >= 10000)
            {
                DataPath last = this.namesList.getPath().copy();

                last.combine(new DataPath("afk"));

                return last.toString();
            }
        }
    }

    /* CRUD */

    protected void addNewData(MapType data)
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_ADD,
            UIKeys.PANELS_MODALS_ADD,
            (str) -> this.addNewData(this.namesList.getPath(str).toString(), data)
        );

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    protected abstract void addNewData(String name, MapType data);

    protected void addNewFolder()
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_ADD_FOLDER,
            (str) -> this.addNewFolder(this.namesList.getPath(str).toString())
        );

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    protected abstract void addNewFolder(String path);

    private DataPath getCurrentSelectedPath()
    {
        return this.namesList == null ? null : this.namesList.getCurrentFirst();
    }

    private boolean ensureCurrentSelection()
    {
        if (this.getCurrentSelectedPath() != null)
        {
            return true;
        }

        if (this.getContext() != null)
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
        }

        return false;
    }

    protected void dupeData(UIIcon element)
    {
        if (!this.ensureCurrentSelection())
        {
            return;
        }

        DataPath current = this.getCurrentSelectedPath();

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_DUPE,
            UIKeys.PANELS_MODALS_DUPE,
            (str) -> this.dupeData(this.namesList.getPath(str).toString())
        );

        panel.text.setText(current.getLast());
        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    protected abstract void dupeData(String name);

    protected void renameData(UIIcon element)
    {
        if (!this.ensureCurrentSelection())
        {
            return;
        }

        DataPath current = this.getCurrentSelectedPath();

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_RENAME,
            UIKeys.PANELS_MODALS_RENAME,
            (str) -> this.renameData(this.namesList.getPath(str).toString())
        );

        if (current.folder)
        {
            if ("..".equals(current.getLast()))
            {
                return;
            }

            panel = new UIPromptOverlayPanel(
                UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE,
                UIKeys.PANELS_MODALS_RENAME_FOLDER,
                (str) -> this.renameFolder(this.namesList.getPath(str).toString())
            );
        }

        panel.text.setText(current.getLast());
        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    protected abstract void renameData(String name);

    protected abstract void renameFolder(String name);

    protected void removeData(UIIcon element)
    {
        if (!this.ensureCurrentSelection())
        {
            return;
        }

        DataPath current = this.getCurrentSelectedPath();

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            UIKeys.GENERAL_REMOVE,
            UIKeys.PANELS_MODALS_REMOVE,
            (confirm) ->
            {
                if (confirm) this.removeData();
            }
        );

        if (current.folder)
        {
            if ("..".equals(current.getLast()))
            {
                return;
            }

            panel = new UIConfirmOverlayPanel(
                UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE,
                UIKeys.PANELS_MODALS_REMOVE_FOLDER,
                (confirm) ->
                {
                    if (confirm) this.removeFolder();
                }
            );
        }

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    protected abstract void removeData();

    protected abstract void removeFolder();
}