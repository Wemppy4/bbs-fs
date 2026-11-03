package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.MotionPathValue;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIEntryListEditor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * The preview's motion path button: the overlay on or off, the film's paths in the shared list
 * (add takes what's selected, like M), and the window around the current frame.
 */
public class UIMotionPathContextMenu extends UIContextMenu
{
    public UIToggle enable;
    public UIMotionPathList paths;
    public UIEntryListEditor<UIMotionPathList.Entry> pathsEditor;
    public UIToggle around;
    public UITrackpad before;
    public UITrackpad after;

    private UIElement column;

    private UIFilmPanel panel;
    private ValueMotionPath motionPath;

    public UIMotionPathContextMenu(UIFilmPanel panel, ValueMotionPath motionPath)
    {
        this.panel = panel;
        this.motionPath = motionPath;

        this.enable = new UIToggle(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE, this.motionPath.enabled.get(), (t) -> this.motionPath.enabled.set(t.getValue()));

        this.paths = new UIMotionPathList((picked) -> {});
        this.paths.h(UIConstants.LIST_ITEM_HEIGHT * 5);
        this.paths.emptyState(UIKeys.FILM_CONTROLLER_MOTION_PATH_EMPTY);
        this.pathsEditor = new UIEntryListEditor<>(this.paths, this::addSelected, null, this::removePath)
            .clear(this::clearPaths);

        this.around = new UIToggle(UIKeys.FILM_CONTROLLER_MOTION_PATH_AROUND, this.motionPath.aroundCurrent.get(), (t) -> this.motionPath.aroundCurrent.set(t.getValue()));
        this.before = new UITrackpad((v) -> this.motionPath.before.set(v.intValue()));
        this.before.limit(1, 1000, true).setValue(this.motionPath.before.get());
        this.after = new UITrackpad((v) -> this.motionPath.after.set(v.intValue()));
        this.after.limit(1, 1000, true).setValue(this.motionPath.after.get());

        this.column = UI.column(4, 8,
            this.enable,
            this.pathsEditor.strip, this.paths,
            this.around, UI.row(this.before, this.after)
        );
        this.column.relative(this).w(190);

        this.add(this.column);
        this.fillPaths();
        this.column.resize();
    }

    private Film film()
    {
        return this.panel.getData();
    }

    private void fillPaths()
    {
        List<UIMotionPathList.Entry> entries = new ArrayList<>();
        Film film = this.film();

        if (film != null)
        {
            for (Replay replay : film.replays.getList())
            {
                for (MotionPathValue path : replay.motionPaths.getAllTyped())
                {
                    entries.add(new UIMotionPathList.Entry(replay, path));
                }
            }
        }

        this.paths.setList(entries);
    }

    /** What M adds: the selected replay's point the gizmo is on. */
    private void addSelected()
    {
        UIFilmController controller = this.panel.getController();
        Replay replay = controller.getReplay();
        String key = MotionPath.key(controller.getEditTarget());

        if (this.film() == null || replay == null || key == null)
        {
            return;
        }

        MotionPath.add(this.film(), replay, key);
        this.motionPath.enabled.set(true);
        this.enable.setValue(true);
        this.fillPaths();
        this.paths.setCurrent(this.find(replay, replay.motionPaths.find(key)));
    }

    private UIMotionPathList.Entry find(Replay replay, MotionPathValue path)
    {
        for (UIMotionPathList.Entry entry : this.paths.getList())
        {
            if (entry.replay() == replay && entry.path() == path)
            {
                return entry;
            }
        }

        return null;
    }

    private void removePath(UIMotionPathList.Entry entry)
    {
        MotionPath.remove(entry.replay(), entry.path());
        this.paths.deselect();
        this.fillPaths();
    }

    private void clearPaths()
    {
        if (this.film() != null)
        {
            MotionPath.clear(this.film());
        }

        this.paths.deselect();
        this.fillPaths();
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }
}
