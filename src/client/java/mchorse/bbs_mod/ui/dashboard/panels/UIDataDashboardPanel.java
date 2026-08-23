package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.ui.utils.UIUtils;

import java.util.Collection;

public abstract class UIDataDashboardPanel <T extends ValueGroup> extends UICRUDDashboardPanel
{
    public UIIcon saveIcon;

    protected T data;

    private boolean openedBefore;

    private Timer savingTimer = new Timer(0);

    public UIDataDashboardPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.saveIcon = new UIIcon(Icons.SAVED, (b) -> this.save());

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
    }

    /* Tabs — the panel says what an id means, UITabList does the bookkeeping */

    @Override
    public void openTab(String id)
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

    /* Renames and removals done in the data manager, mirrored onto the open tabs */

    public void onDataRenamed(String from, String to)
    {
        if (from == null || to == null || from.equals(to))
        {
            return;
        }

        this.tabs.renameId(from, to);
    }

    public void onDataFolderRenamed(String fromPath, String name)
    {
        if (fromPath == null || name == null || name.trim().isEmpty())
        {
            return;
        }

        this.tabs.renameFolder(fromPath, name);
    }

    public void onDataRemoved(String id)
    {
        if (id == null)
        {
            return;
        }

        this.tabs.forgetId(id);

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

    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        return new UIDataOverlayPanel<>(this.getTitle(), this, this::pickData);
    }

    @Override
    protected void openDataManager()
    {
        super.openDataManager();
    }

    @Override
    public void pickData(String id)
    {
        this.tabs.pick(id);
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

        this.saveIcon.setEnabled(data != null);
        this.editor.setVisible(data != null);
        this.overlay.dupe.setEnabled(data != null);
        this.overlay.rename.setEnabled(data != null);
        this.overlay.remove.setEnabled(data != null);

        this.fillData(data);

        if (data != null && data.getId() != null)
        {
            this.overlay.namesList.setCurrentFile(data.getId());
        }

        this.savingTimer.mark(BBSSettings.editorPeriodicSave.get() * 1000L);
    }

    protected abstract void fillData(T data);

    public void fillDefaultData(T data)
    {}

    public void fillNames(Collection<String> names)
    {
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
    public void resize()
    {
        super.resize();

        if (!this.openedBefore && this.getContext() != null && this.shouldAutoOpenListOnFirstResize())
        {
            this.openDataManager();

            this.openedBefore = true;
        }
    }

    /** If false, the list overlay is not auto-opened when the panel is first shown. Default true. */
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return true;
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
    public void open()
    {
        super.open();

        int seconds = BBSSettings.editorPeriodicSave.get();

        if (seconds > 0)
        {
            this.savingTimer.mark(seconds * 1000L);
        }
    }

    @Override
    public void close()
    {
        super.close();

        this.save();
    }

    @Override
    public void render(UIContext context)
    {
        if (this.data == null)
        {
            this.renderDataManagerHint(context);
        }

        super.render(context);

        if (!this.editor.isEnabled() && this.data != null)
        {
            this.renderLockedArea(context);
        }

        this.checkPeriodicSave(context);
    }

    /**
     * Nudge towards the panel's menu while nothing is open: an arrow bobbing under the button
     * that leads to the data manager.
     */
    private void renderDataManagerHint(UIContext context)
    {
        UIIcon button = this.actions().getMenuButton();

        if (button == null)
        {
            return;
        }

        double ticks = context.getTickTransition() % 15D;
        double factor = Math.abs(ticks / 15D * 2 - 1F);

        int x = button.area.mx();
        int y = button.area.ey() + 10 + (int) Interpolations.SINE_INOUT.interpolate(10, 0, factor);

        context.batcher.icon(Icons.ARROW_UP, x, y, 0.5F, 0.5F);
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
