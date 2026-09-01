package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.onboarding.Onboarding;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.ui.utils.UIUtils;

import java.io.File;
import java.util.Collection;

public abstract class UIDataDashboardPanel <T extends ValueGroup> extends UICRUDDashboardPanel
{
    public UIIcon saveIcon;

    protected T data;

    private Timer savingTimer = new Timer(0);

    public UIDataDashboardPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.saveIcon = new UIIcon(Icons.SAVED, (b) -> this.save());
        this.saveIcon.tooltip(UIKeys.GENERAL_SAVE);

        this.actions().common(this.saveIcon);

        /* A separate element is needed to make save keybind a more priority than other keybinds, because
         * the keybinds are processed afterwards. */
        UIElement savePlease = new UIElement().noCulling();

        savePlease.keys().register(Keys.SAVE, () ->
        {
            UIUtils.playClick();
            this.save();
        }).active(() -> this.data != null);
        this.add(savePlease);

        this.onOpen(this::startPeriodicSave);
        this.onClose(this::save);
    }

    private void startPeriodicSave()
    {
        int seconds = BBSSettings.editorPeriodicSave.get();

        if (seconds > 0)
        {
            this.savingTimer.mark(seconds * 1000L);
        }
    }

    /* Tabs — the panel says what an id means, UITabList does the bookkeeping */

    @Override
    protected void showTab(String id)
    {
        if (id == null)
        {
            this.fill(null);
        }
        else
        {
            this.requestData(id);
        }
    }

    @Override
    public String getOpenId()
    {
        return this.data == null ? null : this.data.getId();
    }

    @Override
    public void saveOpen()
    {
        this.save();
    }

    /* Renames and removals done in the data manager, mirrored onto the open tabs and the recent list */

    public void onDataRenamed(String from, String to)
    {
        if (from == null || to == null || from.equals(to))
        {
            return;
        }

        this.tabs.renameId(from, to);
        BBSSettings.recentData.rename(this.getType().getId(), from, to);
    }

    public void onDataFolderRenamed(String fromPath, String name)
    {
        if (fromPath == null || name == null || name.trim().isEmpty())
        {
            return;
        }

        this.tabs.renameFolder(fromPath, name);
        BBSSettings.recentData.renameFolder(this.getType().getId(), fromPath, name);
    }

    public void onDataRemoved(String id)
    {
        if (id == null)
        {
            return;
        }

        this.tabs.forgetId(id);
        BBSSettings.recentData.forget(this.getType().getId(), id);

        if (this.data != null && id.equals(this.data.getId()))
        {
            this.fill(null);
        }
    }

    public void onDataFolderRemoved(String path)
    {
        if (path == null || path.isEmpty())
        {
            return;
        }

        this.tabs.forgetFolder(path);
        BBSSettings.recentData.forgetFolder(this.getType().getId(), path);

        String id = this.data == null ? null : this.data.getId();
        String prefix = path.endsWith("/") ? path : path + "/";

        if (id != null && id.startsWith(prefix))
        {
            this.fill(null);
        }
    }

    public T getData()
    {
        return this.data;
    }

    /**
     * Get the content type of this panel
     */
    public abstract ContentType getType();

    /* ILandingHost — the landing screen of a panel that edits saved documents */

    @Override
    public String getRecentType()
    {
        return this.getType().getId();
    }

    @Override
    public File getDataFolder()
    {
        return this.getType().getRepository().getFolder();
    }

    /**
     * Label of the landing screen's entry that creates a new document, or null when there is
     * nothing to create: asset-backed panels (the model editor) keep the data manager as a pure
     * picker, and the landing screen offers exactly what the manager does.
     */
    @Override
    public IKey getCreateLabel()
    {
        return this.overlay.showActionButtons() ? UIKeys.GENERAL_ADD : null;
    }

    @Override
    public void addNewData(UIContext context)
    {
        this.overlay.addNewData(context);
    }

    @Override
    public void showInList(String id)
    {
        this.openDataManager();
        this.overlay.namesList.setCurrentFile(id);
    }

    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        return new UIDataOverlayPanel<>(this.getTitle(), this, this::pickData);
    }

    public void requestData(String id)
    {
        this.getType().getRepository().load(id, (data) -> this.fill((T) data));
    }

    /* Data population */

    public void fill(T data)
    {
        this.data = data;

        this.tabs.setOpenId(data == null ? null : data.getId());
        this.syncLanding();

        this.saveIcon.setEnabled(data != null);
        this.editor.setVisible(data != null);
        this.overlay.dupe.setEnabled(data != null);
        this.overlay.rename.setEnabled(data != null);
        this.overlay.remove.setEnabled(data != null);

        this.fillData(data);

        if (data != null)
        {
            Onboarding.dataOpened(this);
        }

        if (data != null && data.getId() != null)
        {
            this.overlay.namesList.setCurrentFile(data.getId());
            BBSSettings.recentData.touch(this.getType().getId(), data.getId());
        }

        this.savingTimer.mark(BBSSettings.editorPeriodicSave.get() * 1000L);
    }

    protected abstract void fillData(T data);

    public void fillDefaultData(T data)
    {}

    @Override
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        String value = this.tabs.getCurrentId();

        if (value == null && this.data != null)
        {
            value = this.data.getId();

            this.tabs.setOpenId(value);
        }

        this.overlay.namesList.fill(names);

        if (value != null)
        {
            this.overlay.namesList.setCurrentFile(value);
        }
    }

    @Override
    public void requestNames()
    {
        UIDataUtils.requestNames(this.getType(), this::fillNames);
    }

    public void save()
    {
        if (!this.update && this.data != null && this.editor.isEnabled())
        {
            this.forceSave();
        }
    }

    public void forceSave()
    {
        this.getType().getRepository().save(this.data.getId(), this.data.toData().asMap());
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (!this.editor.isEnabled() && this.data != null)
        {
            this.renderLockedArea(context);
        }

        this.checkPeriodicSave(context);
    }

    private void checkPeriodicSave(UIContext context)
    {
        if (this.data == null)
        {
            return;
        }

        int seconds = BBSSettings.editorPeriodicSave.get();

        if (seconds > 0)
        {
            if (this.savingTimer.check() && this.canSave(context))
            {
                this.savingTimer.mark(seconds * 1000L);

                this.save();
                context.notifySuccess(UIKeys.PANELS_SAVED_NOTIFICATION.format(this.data.getId()));
            }
        }
    }

    protected boolean canSave(UIContext context)
    {
        return true;
    }
}
