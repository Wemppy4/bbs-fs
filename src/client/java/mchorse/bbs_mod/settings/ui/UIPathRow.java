package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * A path over the full width: its name on a line of its own, and under it the
 * field with a folder button beside it. A path doesn't fit the narrow field the
 * other settings use, and nobody should have to type one out.
 *
 * The button picks, that being what it is wanted for nearly every time, and
 * everything else the path can do hangs off its right click menu.
 */
public abstract class UIPathRow implements UISettingsLayout.IValueRow
{
    protected final ValueString path;

    public UIPathRow(ValueString path)
    {
        this.path = path;
    }

    @Override
    public List<BaseValue> getValues()
    {
        return Collections.singletonList(this.path);
    }

    @Override
    public List<UIElement> create(UIElement ui)
    {
        UITextbox textbox = UIValueFactory.stringUI(this.path, null);
        UIIcon folder = new UIIcon(Icons.FOLDER, (b) -> this.pick(textbox));

        textbox.tooltip(L10n.lang(UIValueFactory.getValueCommentKey(this.path)));
        folder.tooltip(this.getTooltip());
        /* As wide as the icon and as tall as the field beside it - a field is
         * CONTROL_HEIGHT tall, not the 20 its row slot gets */
        folder.wh(Icons.FOLDER.w, UIConstants.CONTROL_HEIGHT);

        UIElement row = new UIElement();

        row.row(4).height(UIConstants.CONTROL_HEIGHT);
        row.add(textbox, folder);

        UILabel label = UIValueFactory.label(this.path);

        label.h(10);

        /* The name belongs to the field under it, so they sit closer to each
         * other than to the settings around them */
        UIElement column = new UIElement();

        column.column(1).vertical().stretch();
        column.add(label, row);
        /* On the whole row rather than on the button: the button is a small
         * target, and everything the menu offers is about the path, which is
         * what the whole row is */
        column.context((menu) -> this.context(menu, column, textbox));

        return Collections.singletonList(UIValueFactory.commetTooltip(column, this.path));
    }

    /**
     * The pick arrives from the dialog's own thread, by which time this page may
     * well have been rebuilt or closed - so the value is what has to be written,
     * and the field is only kept in step for as long as it is still on screen.
     */
    protected void set(UITextbox textbox, File file)
    {
        String picked = file.getAbsolutePath();

        this.path.set(picked);
        textbox.setText(picked);
    }

    /** What the button says it does, right click included */
    protected abstract IKey getTooltip();

    protected abstract void pick(UITextbox textbox);

    protected abstract void context(ContextMenuManager menu, UIElement element, UITextbox textbox);
}
