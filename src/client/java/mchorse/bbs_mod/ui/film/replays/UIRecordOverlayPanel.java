package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class UIRecordOverlayPanel extends UIMessageOverlayPanel
{
    public UIIcon all;
    public UIIcon left;
    public UIIcon right;
    public UIIcon triggers;
    public UIIcon extra1;
    public UIIcon extra2;
    public UIIcon position;
    public UIIcon rotation;
    public UIIcon posRot;

    public UIElement bar;

    private Consumer<List<String>> callback;

    public UIRecordOverlayPanel(IKey title, IKey message, Consumer<List<String>> callback)
    {
        super(title, message);

        this.callback = callback;

        this.all = new UIIcon(Icons.SPHERE, (b) -> this.submit(null));
        this.left = new UIIcon(Icons.LEFT_STICK, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_LEFT_STICK)));
        this.right = new UIIcon(Icons.RIGHT_STICK, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_RIGHT_STICK)));
        this.triggers = new UIIcon(Icons.TRIGGER, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_TRIGGERS)));
        this.extra1 = new UIIcon(Icons.GRAPH, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_EXTRA1)));
        this.extra2 = new UIIcon(Icons.CURVES, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_EXTRA2)));
        this.position = new UIIcon(Icons.ALL_DIRECTIONS, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_POSITION)));
        this.rotation = new UIIcon(Icons.REFRESH, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_ROTATION)));
        this.posRot = new UIIcon(Icons.FULLSCREEN, (b) -> this.submit(Arrays.asList(ReplayKeyframes.GROUP_POSITION, ReplayKeyframes.GROUP_ROTATION)));

        this.all.tooltip(UIKeys.FILM_GROUPS_ALL);
        this.left.tooltip(UIKeys.FILM_GROUPS_LEFT_STICK);
        this.right.tooltip(UIKeys.FILM_GROUPS_RIGHT_STICK);
        this.triggers.tooltip(UIKeys.FILM_GROUPS_TRIGGERS);
        this.extra1.tooltip(UIKeys.FILM_GROUPS_EXTRA_1);
        this.extra2.tooltip(UIKeys.FILM_GROUPS_EXTRA_2);
        this.position.tooltip(UIKeys.FILM_GROUPS_ONLY_POSITION);
        this.rotation.tooltip(UIKeys.FILM_GROUPS_ONLY_ROTATION);
        this.posRot.tooltip(UIKeys.FILM_GROUPS_ONLY_POS_ROT);

        List<UIElement> buttons = new ArrayList<>();

        buttons.add(this.all);
        buttons.add(this.left);
        buttons.add(this.right);
        buttons.add(this.triggers);
        buttons.add(this.extra1);
        buttons.add(this.extra2);
        buttons.add(this.position);
        buttons.add(this.rotation);
        buttons.add(this.posRot);

        this.bar = UI.row(buttons.toArray(new UIElement[0]));

        this.bar.relative(this.content).x(0.5F).y(1F, -6).w(1F, -12).anchor(0.5F, 1F).row().resize();
        this.content.add(this.bar);

        this.bottom = this.bar;

        this.keys().register(Keys.RECORDING_GROUP_ALL, this.all::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_LEFT_STICK, this.left::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_RIGHT_STICK, this.right::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_TRIGGERS, this.triggers::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_EXTRA_1, this.extra1::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_EXTRA_2, this.extra2::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_ONLY_POSITION, this.position::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_ONLY_ROTATION, this.rotation::clickItself);
        this.keys().register(Keys.RECORDING_GROUP_POS_ROT, this.posRot::clickItself);
    }

    public void submit(List<String> groups)
    {
        this.close();

        if (this.callback != null)
        {
            this.callback.accept(groups);
        }
    }
}
