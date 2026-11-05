package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;

import java.util.function.Consumer;

/** Asks for a name, or any other line of text, at the cursor. */
public class UIPromptPopup extends UIInputPopup
{
    /** Wide enough for a file name next to a button that says "Rename". */
    public static final int WIDTH = 200;

    public final UITextbox text;

    public Consumer<String> callback;

    public UIPromptPopup(IKey title, IKey message, Consumer<String> callback)
    {
        super(title, message, WIDTH);

        this.callback = callback;
        this.text = new UITextbox(null);

        this.field(this.text);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        /* A selection set up before opening (e.g. the name without its extension) stays as it is */
        if (!this.text.textbox.isSelected())
        {
            this.text.textbox.moveCursorToEnd();
        }

        parent.getContext().focus(this.text);
    }

    @Override
    protected void apply()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.text.getText());
        }
    }
}
