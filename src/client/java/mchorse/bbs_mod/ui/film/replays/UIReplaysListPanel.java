package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

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

    public final UIReplayList replays;

    private final Area rightClickAnchorArea = new Area();

    public UIReplaysListPanel(UIFilmPanel panel, Consumer<List<Replay>> callback, Consumer<Form> formConsumer)
    {
        this.filmPanel = panel;
        this.replays = new UIReplayList(callback, formConsumer, panel);

        this.addReplay = new UIIcon(Icons.ADD, (b) -> this.replays.addReplay());
        this.dupeReplay = new UIIcon(Icons.DUPE, (b) -> this.replays.dupeReplay());
        this.removeReplay = new UIIcon(Icons.REMOVE, (b) -> this.replays.removeReplay());

        int leftW = BAR_ICON_SIZE * 3 + BAR_ICON_MARGIN * 2;

        this.bar.relative(this.content).x(0).y(0).w(1F).h(BAR_HEIGHT);
        this.leftBar.relative(this.bar).x(0).y(0).w(leftW).h(BAR_HEIGHT).row(BAR_ICON_MARGIN).height(BAR_HEIGHT);

        this.addReplay.w(BAR_ICON_SIZE);
        this.dupeReplay.w(BAR_ICON_SIZE);
        this.removeReplay.w(BAR_ICON_SIZE);

        this.leftBar.add(this.addReplay, this.dupeReplay, this.removeReplay);
        this.bar.add(this.leftBar);

        this.replays.relative(this.content).x(0).y(0, BAR_HEIGHT).w(1F).h(1F, -BAR_HEIGHT);
        this.content.add(this.bar, this.replays);

        this.refreshEditPanelOffset();
        this.add(this.content);
    }

    public void refreshEditPanelOffset()
    {
        int top = this.filmPanel.getEditPanelTopOffsetPx();
        this.content.relative(this).x(0).y(0, top).w(1F).h(1F, -top);
        this.resize();
    }

    private void updateButtonsState()
    {
        boolean hasFilm = this.filmPanel.getData() != null;
        boolean hasSelection = this.replays.hasReplaySelection();

        this.addReplay.setEnabled(hasFilm);
        this.dupeReplay.setEnabled(hasSelection);
        this.removeReplay.setEnabled(hasSelection);
    }

    @Override
    public void render(UIContext context)
    {
        int panelBg = Colors.mulRGB(BBSSettings.primaryColor(Colors.A100), 0.1F);
        int barBg = Colors.mulRGB(BBSSettings.primaryColor(Colors.A100), 0.3F);

        this.area.render(context.batcher, panelBg);
        this.updateButtonsState();
        context.batcher.box(this.bar.area.x, this.bar.area.y, this.bar.area.ex(), this.bar.area.ey(), barBg);
        super.render(context);
    }
}
