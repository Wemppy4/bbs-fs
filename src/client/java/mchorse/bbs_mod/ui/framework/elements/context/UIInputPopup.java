package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.UIConstants;
import org.lwjgl.glfw.GLFW;

/**
 * A popup that asks for one value to do something with: a field and, next to it, a button that
 * says what will happen. Unlike the fields of a plain popup, the value is not applied as it is
 * typed &mdash; making a folder or duplicating five replays happens once, on the button or Enter,
 * and a click outside walks away from it.
 *
 * <p>A subclass puts its field in front of the button with {@link #field(UIElement)} and hands the
 * value on in {@link #apply()}.</p>
 */
public abstract class UIInputPopup extends UIPopup
{
    public final UIButton button;

    private final UIElement row;

    /**
     * @param message what the value is for, shown on hover: the title already says what is asked,
     *                so the longer explanation stays out of the way until it is wanted
     */
    protected UIInputPopup(IKey title, IKey message, int width)
    {
        super(title, width);

        this.button = new UIButton(UIKeys.GENERAL_OK, (b) -> this.confirm());
        this.row = new UIElement();
        this.row.row(UIConstants.MARGIN).preferred(0).height(UIConstants.CONTROL_HEIGHT);
        this.row.add(this.button);
        this.verb(UIKeys.GENERAL_OK);

        this.column.add(this.row);

        if (message != null)
        {
            this.column.tooltip(message);
        }
    }

    protected void field(UIElement field)
    {
        this.row.prepend(field);
    }

    /** What the button says: the action itself ("Rename", "Duplicate") rather than "OK". */
    public void verb(IKey label)
    {
        this.button.label = label;
        this.button.fit();
    }

    protected abstract void apply();

    public void confirm()
    {
        /* Gone first: what the value is used for may open the next popup, which this one must not
         * then take down with it */
        this.removeFromParent();
        this.apply();
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(Keys.CONFIRM) || context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
        {
            this.confirm();

            return true;
        }

        return super.subKeyPressed(context);
    }
}
