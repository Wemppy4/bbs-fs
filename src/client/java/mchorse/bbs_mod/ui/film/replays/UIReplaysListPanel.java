package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.icons.Icons;

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
    public final UIIcon addReplay;
    /**
     * The one thing the bar is worth its height for. Duplicating, removing and presets live in the
     * list's own context menu, where every other thing done to a replay lives; searching has
     * nowhere else to go, and a list of a hundred replays needs it far more than it needs buttons.
     */
    public final UITextbox search;

    public final UIReplayList replays;

    public UIReplaysListPanel(UIFilmPanel panel, Consumer<List<Replay>> callback, Consumer<Form> formConsumer)
    {
        this.filmPanel = panel;
        this.replays = new UIReplayList(callback, formConsumer, panel);

        this.addReplay = new UIIcon(Icons.ADD, (b) -> this.replays.addReplay());
        this.addReplay.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_ADD);

        this.search = new UITextbox(1000, (text) -> this.replays.filter(text));
        this.search.placeholder(UIKeys.GENERAL_SEARCH);

        this.bar.relative(this.content).x(0).y(0).w(1F).h(BAR_HEIGHT);

        this.addReplay.relative(this.bar).x(0).y(0).w(BAR_ICON_SIZE).h(BAR_HEIGHT);
        this.search.relative(this.bar).x(BAR_ICON_SIZE + BAR_ICON_MARGIN).y(0).w(1F, -BAR_ICON_SIZE - BAR_ICON_MARGIN).h(BAR_HEIGHT);

        this.bar.add(this.addReplay, this.search);

        this.replays.relative(this.content).x(0).y(0, BAR_HEIGHT).w(1F).h(1F, -BAR_HEIGHT);
        this.content.add(this.bar, this.replays);

        this.content.relative(this).x(0).y(0).w(1F).h(1F);
        this.add(this.content);
    }

    @Override
    public void render(UIContext context)
    {
        int barBg = BBSSettings.baseSurface();

        this.addReplay.setEnabled(this.filmPanel.getData() != null);
        context.batcher.box(this.bar.area.x, this.bar.area.y, this.bar.area.ex(), this.bar.area.ey(), barBg);
        super.render(context);
    }
}
