package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.FilmTarget;
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
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIEntryListEditor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * The preview's motion path button: the overlay on or off, the selected replay's paths in the shared
 * list (add picks a bone of the replay; M adds whatever is selected), and the window around the
 * current frame.
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
        this.pathsEditor = new UIEntryListEditor<>(this.paths, this::pickBone, null, this::removePath)
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

    private Replay replay()
    {
        return this.panel.getController().getReplay();
    }

    private void fillPaths()
    {
        List<UIMotionPathList.Entry> entries = new ArrayList<>();
        Replay replay = this.replay();

        if (replay != null)
        {
            for (MotionPathValue path : replay.motionPaths.getAllTyped())
            {
                entries.add(new UIMotionPathList.Entry(replay, path));
            }
        }

        this.paths.setList(entries);
        this.pathsEditor.add.setEnabled(replay != null);
    }

    /** The plus: one of the replay's bones from the bone picker, opened over this menu. */
    private void pickBone()
    {
        Replay replay = this.replay();

        if (replay == null)
        {
            return;
        }

        UIAnchorKeyframeFactory.displayAttachments(this.panel, replay.getId(), "", (bone) ->
        {
            if (bone.isEmpty())
            {
                return;
            }

            String key = MotionPath.key(FilmTarget.bone(bone, null));

            MotionPath.add(replay, key);
            this.motionPath.enabled.set(true);
            this.enable.setValue(true);
            this.fillPaths();
            this.paths.setCurrent(this.find(replay.motionPaths.find(key)));
        });
    }

    private UIMotionPathList.Entry find(MotionPathValue path)
    {
        for (UIMotionPathList.Entry entry : this.paths.getList())
        {
            if (entry.path() == path)
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
        if (this.replay() != null)
        {
            MotionPath.clear(this.replay());
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
