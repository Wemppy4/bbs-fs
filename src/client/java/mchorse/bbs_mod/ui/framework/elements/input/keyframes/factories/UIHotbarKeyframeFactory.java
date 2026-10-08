package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.film.replays.Hotbar;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemHotbar;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.item.ItemStack;

/**
 * A hotbar key laid out as the hotbar: nine cells, the one in hand at the key's tick framed.
 * A cell edited here changes in every selected key, while the rest of each key's row stays its own.
 */
public class UIHotbarKeyframeFactory extends UIKeyframeFactory<Hotbar>
{
    private final UIItemHotbar slots;
    private Hotbar hotbar = new Hotbar();
    private int selected = -1;

    public UIHotbarKeyframeFactory(UITrackValue<Hotbar> track, UIKeyframes editor)
    {
        super(track, editor);

        this.slots = new UIItemHotbar((slot) -> this.hotbar.get(slot), () -> this.selected, this::pickItem);
        this.slots.fillWidth();
        this.slots.context((menu) ->
        {
            int slot = this.slots.getSlot(this.getContext());

            if (slot < 0)
            {
                return;
            }

            ItemStack stack = this.hotbar.get(slot);

            menu.action(Icons.CLOSE, UIKeys.ITEM_STACK_CONTEXT_RESET, () -> this.setItem(slot, ItemStack.EMPTY));

            if (!stack.isEmpty())
            {
                menu.action(Icons.PLAYER, UIKeys.ITEM_STACK_CONTEXT_GIVE, () -> UIItemStack.giveToPlayer(stack));
            }
        });

        this.scroll.add(this.slots);
        this.update();
    }

    private void pickItem(int slot)
    {
        UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forItem((stack) -> this.setItem(slot, stack), this.hotbar.get(slot));

        UIOverlay.addOverlay(this.getContext(), panel, 0.5F, 0.75F);
        UIUtils.playClick();
    }

    private void setItem(int slot, ItemStack stack)
    {
        this.track.edit((hotbar) -> hotbar.set(slot, stack.copy()));
        this.hotbar.set(slot, stack.copy());
    }

    @Override
    public void update()
    {
        this.hotbar = this.getDisplayValue();
        this.selected = this.track.sheet.channel.getParent() instanceof ReplayKeyframes keyframes
            ? keyframes.getSelectedSlot(this.track.getValueTick())
            : -1;
    }
}
