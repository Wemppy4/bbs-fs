package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

import java.util.function.Consumer;

/** Asks for a number at the cursor: how many copies, how many ticks. */
public class UINumberPopup extends UIInputPopup
{
    public final UITrackpad value;

    public Consumer<Double> callback;

    public UINumberPopup(IKey title, IKey message, Consumer<Double> callback)
    {
        super(title, message, UIPopup.WIDTH);

        this.callback = callback;
        this.value = new UITrackpad();

        this.field(this.value);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        parent.getContext().focus(this.value);
    }

    @Override
    protected void apply()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.value.getValue());
        }
    }
}
