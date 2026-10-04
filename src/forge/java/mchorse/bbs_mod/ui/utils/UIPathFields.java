package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import java.util.List;
import java.util.function.*;

/** Path binding fields and the rotation choice shared with the spline camera clip. */
public class UIPathFields extends UIElement
{
    public final UITrackpad progress;
    private final DoubleSupplier readProgress;

    public static IKey key(String name) { return L10n.lang("bbs.ui.spline." + name); }

    public UIPathFields(DoubleSupplier readProgress, DoubleConsumer writeProgress, IntSupplier readRotation, IntConsumer writeRotation)
    {
        this.readProgress = readProgress;
        this.progress = new UITrackpad(value -> writeProgress.accept(value));
        this.progress.tooltip(key("progress_tip"));
        this.column(UIConstants.MARGIN).vertical().stretch();
        this.add(UI.labelRow(key("progress"), this.progress), rotationButton(readRotation, writeRotation));
    }

    public static UIButton rotationButton(IntSupplier readRotation, IntConsumer writeRotation)
    {
        UIButton button = new UIButton(key("rotation.1"), b -> UIChoiceMenu.of(java.util.Arrays.asList(0, 1, 2))
            .current(readRotation.getAsInt())
            .icon(mode -> mode == 0 ? Icons.ORBIT : mode == 1 ? Icons.ARROW_RIGHT : Icons.HORIZONTAL)
            .label(mode -> key("rotation." + mode))
            .open(b.getContext(), mode -> writeRotation.accept(mode)));
        button.valueBinding(() -> button.label = key("rotation." + readRotation.getAsInt()));
        return button;
    }
    @Override public void render(UIContext context)
    {
        if (!this.progress.isUserEditing()) this.progress.setValue(this.readProgress.getAsDouble());
        super.render(context);
    }
}
