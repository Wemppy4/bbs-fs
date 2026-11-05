package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIConfirmOverlayPanel extends UIMessageOverlayPanel
{
    public UIButton confirm;

    public Consumer<Boolean> callback;

    private boolean result;

    public UIConfirmOverlayPanel(IKey title, IKey message, Consumer<Boolean> callback)
    {
        super(title, message);

        this.callback = callback;

        this.confirm = new UIButton(UIKeys.GENERAL_OK, (b) -> this.confirm());

        this.confirm.relative(this.content).x(1F, -6).y(1F, -6).anchor(1F, 1F);
        this.confirm.fit();
        this.content.add(this.confirm);

        this.bottom = this.confirm;
    }

    /** What the button says: the action the question is about ("Remove") rather than "OK". */
    public UIConfirmOverlayPanel verb(IKey label)
    {
        this.confirm.label = label;
        this.confirm.fit();

        return this;
    }

    /** Red, for the questions whose answer can't be taken back. */
    public UIConfirmOverlayPanel danger()
    {
        this.confirm.color(Colors.RED);

        return this;
    }

    @Override
    public void confirm()
    {
        this.result = true;

        this.close();
    }

    @Override
    public void onClose()
    {
        super.onClose();

        if (this.callback != null)
        {
            this.callback.accept(this.result);
        }
    }
}