package mchorse.bbs_mod.ui.framework.elements.input.text;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.Textbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import org.lwjgl.glfw.GLFW;

public abstract class UIBaseTextbox extends UIElement implements IFocusedUIElement
{
    public final Textbox textbox;

    public UIBaseTextbox()
    {
        super();

        this.textbox = new Textbox(this::userInput);
        this.textbox.setFont(Batcher2D.getDefaultTextRenderer());
    }

    protected void userInput(String string)
    {}

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        this.textbox.setEnabled(enabled);
    }

    @Override
    public void setVisible(boolean visible)
    {
        super.setVisible(visible);
        this.textbox.setVisible(visible);
    }

    @Override
    public boolean isFocused()
    {
        return this.textbox.isFocused();
    }

    @Override
    public void focus(UIContext context)
    {
        this.textbox.setFocused(true);
    }

    @Override
    public void unfocus(UIContext context)
    {
        this.textbox.setFocused(false);
    }

    @Override
    public void selectAll(UIContext context)
    {
        this.textbox.moveCursorToStart();
        this.textbox.setSelection(this.textbox.getText().length());
    }

    @Override
    public void unselect(UIContext context)
    {
        this.textbox.deselect();
    }

    public String getText()
    {
        return this.textbox.getText();
    }

    protected void requestTextCursor(UIContext context)
    {
        if (this.isEnabled() && (this.isFocused() || this.area.isInside(context)))
        {
            context.requestCursor(GLFW.GLFW_IBEAM_CURSOR);
        }
    }
}
