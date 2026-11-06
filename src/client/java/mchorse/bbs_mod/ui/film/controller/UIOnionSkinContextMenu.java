package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIPopup;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.LinkedHashSet;
import java.util.Set;

public class UIOnionSkinContextMenu extends UIPopup
{
    public UIIcon enable;
    public UIIcon all;
    public UIIcon group;
    public UITrackpad preFrames;
    public UIColor preColor;
    public UITrackpad postFrames;
    public UIColor postColor;

    private UIFilmPanel panel;
    private ValueOnionSkin onionSkin;

    public UIOnionSkinContextMenu(UIFilmPanel panel, ValueOnionSkin onionSkin)
    {
        super(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);

        this.panel = panel;
        this.onionSkin = onionSkin;

        this.enable = new UIIcon(Icons.VISIBLE, (b) -> this.onionSkin.enabled.set(!this.onionSkin.enabled.get()));
        this.enable.highlight(this.onionSkin.enabled::get, Direction.BOTTOM);
        this.enable.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);
        this.preFrames = new UITrackpad((v) -> this.onionSkin.preFrames.set(v.intValue()));
        this.preFrames.limit(0, 10, true).setValue(this.onionSkin.preFrames.get());
        this.preColor = new UIColor((c) -> this.onionSkin.preColor.set(c));
        this.preColor.withAlpha().setColor(this.onionSkin.preColor.get());
        this.postFrames = new UITrackpad((v) -> this.onionSkin.postFrames.set(v.intValue()));
        this.postFrames.limit(0, 10, true).setValue(this.onionSkin.postFrames.get());
        this.postColor = new UIColor((c) -> this.onionSkin.postColor.set(c));
        this.postColor.withAlpha().setColor(this.onionSkin.postColor.get());
        this.all = new UIIcon(Icons.POSE, (b) -> this.onionSkin.all.set(!this.onionSkin.all.get()));
        this.all.highlight(this.onionSkin.all::get, Direction.BOTTOM);
        this.all.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_ALL_DESCRIPTION);
        this.group = new UIIcon(Icons.MORE, (b) ->
        {
            this.getContext().replaceContextMenu((menu) ->
            {
                Replay replay = this.panel.replayEditor.getReplay();

                if (replay == null)
                {
                    return;
                }

                /* A bone stands for its form's whole pose, so all of a form's bones are one row. */
                Set<String> groups = new LinkedHashSet<>();

                for (TrackId track : replay.properties.tracks.keySet())
                {
                    groups.add(track.is(TrackKind.BONE) ? TrackId.property(track.formPath(), FormProperties.POSE_PROPERTY).toKey() : track.toKey());
                }

                for (String property : groups)
                {
                    menu.action(Icons.FOLDER, IKey.constant(property), this.onionSkin.group.get().equals(property), () ->
                    {
                        this.onionSkin.group.set(property);
                        this.group.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_GROUP.format(property));
                    });
                }
            });
        });
        this.group.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_GROUP.format(this.onionSkin.group.get()));

        this.column.add(
            UI.row(this.enable, this.all, this.group).h(UIConstants.ICON_SIZE),
            UI.row(this.preFrames, this.preColor).tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_PREV),
            UI.row(this.postFrames, this.postColor).tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_NEXT)
        );
    }
}