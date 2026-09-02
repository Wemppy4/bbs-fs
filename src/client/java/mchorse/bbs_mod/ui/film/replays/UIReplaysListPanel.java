package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;

import java.util.List;
import java.util.function.Consumer;

public class UIReplaysListPanel extends UIElement
{
    private static final int BAR_HEIGHT = 20;
    private static final int BAR_ICON_SIZE = 20;
    private static final int BAR_ICON_MARGIN = 2;

    private final UIFilmPanel filmPanel;

    public final UIElement content = new UIElement();
    public final UIElement bar = new UIElement();
    public final UIElement leftBar = new UIElement();
    public final UIIcon addReplay;
    public final UIIcon dupeReplay;
    public final UIIcon removeReplay;
    public final UIIcon presets;

    public final UIReplayList replays;

    private final Area rightClickAnchorArea = new Area();

    public UIReplaysListPanel(UIFilmPanel panel, Consumer<List<Replay>> callback, Consumer<Form> formConsumer)
    {
        this.filmPanel = panel;
        this.replays = new UIReplayList(callback, formConsumer, panel);

        this.addReplay = new UIIcon(Icons.ADD, (b) -> this.replays.addReplay());
        this.dupeReplay = new UIIcon(Icons.DUPE, (b) -> this.replays.dupeReplay());
        this.removeReplay = new UIIcon(Icons.REMOVE, (b) -> this.replays.removeReplay());
        this.presets = new UIIcon(Icons.MORE, (b) -> this.replays.openReplayPresets());
        this.presets.tooltip(UIKeys.GENERAL_PRESETS, Direction.LEFT);

        int leftW = BAR_ICON_SIZE * 3 + BAR_ICON_MARGIN * 2;

        this.bar.relative(this.content).x(0).y(0).w(1F).h(BAR_HEIGHT);
        this.leftBar.relative(this.bar).x(0).y(0).w(leftW).h(BAR_HEIGHT).row(BAR_ICON_MARGIN).height(BAR_HEIGHT);

        this.addReplay.w(BAR_ICON_SIZE);
        this.dupeReplay.w(BAR_ICON_SIZE);
        this.removeReplay.w(BAR_ICON_SIZE);

        this.presets.relative(this.bar).x(1F, -BAR_ICON_SIZE - BAR_ICON_MARGIN).y(0).w(BAR_ICON_SIZE).h(BAR_HEIGHT);

        this.leftBar.add(this.addReplay, this.dupeReplay, this.removeReplay);
        this.bar.add(this.leftBar, this.presets);

        this.replays.relative(this.content).x(0).y(0, BAR_HEIGHT).w(1F).h(1F, -BAR_HEIGHT);
        this.content.add(this.bar, this.replays);

        this.content.relative(this).x(0).y(0).w(1F).h(1F);
        this.add(this.content);
    }

    private void updateButtonsState()
    {
        boolean hasFilm = this.filmPanel.getData() != null;
        boolean hasSelection = this.replays.hasReplaySelection();

        this.addReplay.setEnabled(hasFilm);
        this.dupeReplay.setEnabled(hasSelection);
        this.removeReplay.setEnabled(hasSelection);
        this.presets.setEnabled(hasFilm);
    }

    @Override
    public void render(UIContext context)
    {
        int barBg = BBSSettings.baseSurface();

        this.updateButtonsState();
        context.batcher.box(this.bar.area.x, this.bar.area.y, this.bar.area.ex(), this.bar.area.ey(), barBg);
        super.render(context);
    }
}
