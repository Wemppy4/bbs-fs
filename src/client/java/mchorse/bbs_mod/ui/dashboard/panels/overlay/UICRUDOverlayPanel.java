package mchorse.bbs_mod.ui.dashboard.panels.overlay;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.list.UIDataFolderTree;
import mchorse.bbs_mod.ui.framework.UIContext;
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
    public UIDataFolderTree folders;

    protected Consumer<String> callback;

    public UICRUDOverlayPanel(IKey title, Consumer<String> callback)
    {
        super(title);
        this.w(1F, -20).h(0.8F);
        this.flex.w.max = 480;

        this.callback = callback;

        this.add = new UIIcon(Icons.ADD, (b) -> this.addNewData(this.getContext()));

        if (this.showActionButtons())
        {
            this.add.context((menu) -> menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addNewFolder));
        }

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
        this.names.full(this.content).x(0.3F, 3).w(0.7F, -6);
        this.namesList = (UIDataPathList) this.names.list;
        this.names.label(UIKeys.GENERAL_SEARCH);
        this.names.list.y(22).h(1F, -22);
        this.folders = new UIDataFolderTree(this.namesList, title);
        this.folders.callback = values ->
        {
            if (!values.isEmpty())
            {
                this.names.filter("", true);
                this.namesList.goTo(values.get(0));
            }
        };
        this.folders.relative(this.content).xy(4, 0).w(0.3F, -4).h(1F);
        this.folders.background(mchorse.bbs_mod.BBSSettings.chromeSurface());
        this.content.add(this.folders, this.names);

        if (this.canCreate())
        {
            this.icons.add(this.add);
        }

        if (this.showActionButtons())
        {
            this.icons.add(this.dupe, this.rename, this.remove);
        }
    }

    @Override
    public void render(UIContext context)
    {
        DataPath selected = this.namesList.getCurrentFirst();
        this.dupe.setEnabled(selected != null && !selected.folder);
        this.rename.setEnabled(selected != null);
        this.remove.setEnabled(selected != null);
        super.render(context);
    }

    /**
     * Whether duplicate/rename/remove and folders are offered. Asset-backed panels (e.g. the model
     * editor) turn this off; the buttons still exist as fields, they're just never mounted. Default true.
     */
    public boolean showActionButtons()
    {
        return true;
    }

    /**
     * Whether a new document can be made: the add button, and the landing screen's "new" entry.
     * Apart from {@link #showActionButtons()} because a panel may make new documents of a kind it
     * can't otherwise manage — the model editor makes models, but doesn't rename or delete them.
     */
    public boolean canCreate()
    {
        return this.showActionButtons();
    }

    /**
     * Ask for a name and create a document with it; with Shift held the name is picked automatically.
     *
     * <p>The context is passed in rather than taken from this panel: the landing screen asks for a
     * new document while the data manager itself is not on screen, and an unmounted panel has none.</p>
     */
    public void addNewData(UIContext context)
    {
        if (Window.isShiftPressed())
        {
            this.addNewData(context, this.getNextAutoId(), null);
        }
        else
        {
            this.addNewData(context, null);
        }
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

    protected void addNewData(UIContext context, MapType data)
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_ADD,
            UIKeys.PANELS_MODALS_ADD,
            (str) -> this.addNewData(context, this.namesList.getPath(str).toString(), data)
        );

        panel.text.filename();

        UIOverlay.addOverlay(context, panel);
    }

    protected abstract void addNewData(UIContext context, String name, MapType data);

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
                (str) -> this.renameFolder(current.getParent().getChild(str).toString())
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
