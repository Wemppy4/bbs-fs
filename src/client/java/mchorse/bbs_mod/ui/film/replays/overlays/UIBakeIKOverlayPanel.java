package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The options of baking IK into keyframes (see {@link mchorse.bbs_mod.film.IKBake}): which
 * chains, over which ticks and how densely, and whether the chains go off on the baked range.
 */
public class UIBakeIKOverlayPanel extends UIOverlayPanel
{
    public UIStringList chains;
    public UITrackpad start;
    public UITrackpad end;
    public UITrackpad step;
    public UIToggle disable;
    public UIButton bake;

    private final IUIBakeIKCallback callback;

    /**
     * @param tips     the chains on offer, by their tip bone; all of them start selected
     * @param lastTick the film's last tick, where the range ends by default
     */
    public UIBakeIKOverlayPanel(Collection<String> tips, int lastTick, IUIBakeIKCallback callback)
    {
        super(UIKeys.FILM_REPLAY_BAKE_IK_TITLE);

        this.callback = callback;

        this.chains = new UIStringList((l) -> {});
        this.chains.multi();
        /* Six rows is the minimum; the list takes whatever height the fields below it leave */
        this.chains.h(UIStringList.DEFAULT_HEIGHT * 6).expand();
        this.chains.background();
        this.chains.tooltip(UIKeys.FILM_REPLAY_BAKE_IK_CHAINS);
        this.chains.add(tips);
        this.chains.sort();

        for (int i = 0; i < tips.size(); i++)
        {
            this.chains.addIndex(i);
        }

        this.start = new UITrackpad();
        this.start.integer().limit(0).setValue(0D);
        this.start.tooltip(UIKeys.FILM_REPLAY_BAKE_IK_START);
        this.end = new UITrackpad();
        this.end.integer().limit(0).setValue(lastTick);
        this.end.tooltip(UIKeys.FILM_REPLAY_BAKE_IK_END);
        this.step = new UITrackpad();
        this.step.integer().limit(1).setValue(1D);
        this.disable = new UIToggle(UIKeys.FILM_REPLAY_BAKE_IK_DISABLE, true, (b) -> {});
        this.disable.tooltip(UIKeys.FILM_REPLAY_BAKE_IK_DISABLE_TOOLTIP);
        this.bake = new UIButton(UIKeys.FILM_REPLAY_BAKE_IK_BAKE, (b) ->
        {
            this.callback.bake(
                new ArrayList<>(this.chains.getCurrent()),
                (int) this.start.getValue(),
                (int) this.end.getValue(),
                (int) this.step.getValue(),
                this.disable.getValue()
            );

            this.close();
        });

        UIScrollView scroll = UI.scrollView(5, 6,
            this.chains,
            UI.label(UIKeys.FILM_REPLAY_BAKE_IK_RANGE), UI.row(this.start, this.end),
            UI.label(UIKeys.FILM_REPLAY_BAKE_IK_STEP), this.step,
            this.disable,
            this.bake
        );

        scroll.full(this.content);
        this.content.add(scroll);
    }

    public static interface IUIBakeIKCallback
    {
        public void bake(List<String> tips, int start, int end, int step, boolean disable);
    }
}
