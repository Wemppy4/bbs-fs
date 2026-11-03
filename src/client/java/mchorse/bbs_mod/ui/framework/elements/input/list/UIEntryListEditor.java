package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The verbs every list of records shares, in one place: a strip of add / duplicate / remove icons
 * over the list, and the same verbs (plus "reset", which removes everything) in the list's context
 * menu. The host owns the data: it says how a record is added, copied and removed, and refills the
 * list; this keeps the rest the same in every list — which icons there are and where, what a right
 * click offers, the remove icons going dark with nothing picked, and a removal handing the pick to
 * the neighbour instead of leaving the settings under the list empty.
 *
 * <p>Not a container: the host lays {@link #strip} and {@link #list} out itself, the list usually
 * expanding into the room its page has left.</p>
 */
public class UIEntryListEditor<T>
{
    public final UIList<T> list;
    public final UIIcon add;
    public final UIIcon remove;
    /** Null when the host's records can't be copied. */
    public final UIIcon duplicate;
    public final UIElement strip;

    private final Runnable adder;
    private final Consumer<T> duplicator;
    private final Consumer<T> remover;
    private Runnable clearer;
    private BiConsumer<ContextMenuManager, T> menu;

    private IKey addLabel = UIKeys.GENERAL_ADD;
    private IKey duplicateLabel = UIKeys.GENERAL_DUPE;
    private IKey removeLabel = UIKeys.GENERAL_REMOVE;
    private IKey clearLabel = UIKeys.GENERAL_RESET;

    /** {@code duplicator} may be null: the strip and the menu then have no duplicate verb. */
    public UIEntryListEditor(UIList<T> list, Runnable adder, Consumer<T> duplicator, Consumer<T> remover)
    {
        this.list = list;
        this.adder = adder;
        this.duplicator = duplicator;
        this.remover = remover;

        this.add = new UIIcon(Icons.ADD, (b) -> this.adder.run());
        this.duplicate = duplicator == null ? null : new UIIcon(Icons.DUPE, (b) -> this.duplicate(this.list.getCurrentFirst()));
        this.remove = new UIIcon(Icons.REMOVE, (b) -> this.remove(this.list.getCurrentFirst()));

        /* The strip asks before every draw whether there's a pick for its icons to act on, so no
         * host has to remember to switch them on and off after each change of the list. */
        this.strip = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                UIEntryListEditor.this.updateIcons();
                super.render(context);
            }
        };
        this.strip.row(0).height(UIConstants.CONTROL_HEIGHT);

        for (UIIcon icon : new UIIcon[] {this.add, this.duplicate, this.remove})
        {
            if (icon != null)
            {
                icon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
                this.strip.add(icon);
            }
        }

        this.labels(this.addLabel, this.duplicateLabel, this.removeLabel);
        this.list.context(this::fillMenu);
    }

    /** What the verbs are called for this list's records ("Add weld"), on the icons and in the menu. */
    public UIEntryListEditor<T> labels(IKey add, IKey duplicate, IKey remove)
    {
        this.addLabel = add;
        this.duplicateLabel = duplicate;
        this.removeLabel = remove;

        this.add.tooltip(add);
        this.remove.tooltip(remove);

        if (this.duplicate != null)
        {
            this.duplicate.tooltip(duplicate);
        }

        return this;
    }

    /** Offer "reset" in the context menu: remove every record at once. */
    public UIEntryListEditor<T> clear(Runnable clearer)
    {
        this.clearer = clearer;

        return this;
    }

    public UIEntryListEditor<T> clear(IKey label, Runnable clearer)
    {
        this.clearLabel = label;

        return this.clear(clearer);
    }

    /** Whatever else the host's menu offers, the record under the cursor (null over empty space) given. */
    public UIEntryListEditor<T> menu(BiConsumer<ContextMenuManager, T> menu)
    {
        this.menu = menu;

        return this;
    }

    public void duplicate(T entry)
    {
        if (entry != null && this.duplicator != null)
        {
            this.duplicator.accept(entry);
        }
    }

    /** Remove a record and pick its neighbour — the one that took its place, else the one before. */
    public void remove(T entry)
    {
        if (entry == null)
        {
            return;
        }

        int index = this.list.getList().indexOf(entry);

        this.remover.accept(entry);

        int size = this.list.getList().size();

        if (index >= 0 && size > 0 && this.list.getCurrentFirst() == null)
        {
            this.list.pick(Math.min(index, size - 1));
        }
    }

    private void updateIcons()
    {
        boolean picked = this.list.getCurrentFirst() != null;

        this.remove.setEnabled(picked);

        if (this.duplicate != null)
        {
            this.duplicate.setEnabled(picked);
        }
    }

    /** A right click picks the record under it first (unless it is already among the picked), so the menu's verbs and the settings agree. */
    private void fillMenu(ContextMenuManager menu)
    {
        int index = this.list.getIndexAtCursor(this.list.getContext());
        T entry = index < 0 ? null : this.list.getList().get(index);

        if (entry != null && !this.list.getCurrent().contains(entry))
        {
            this.list.pick(index);
        }

        menu.icon(MenuVerb.ADD, this.adder).label(this.addLabel);

        if (entry != null)
        {
            menu.icon(MenuVerb.REMOVE, () -> this.remove(entry)).label(this.removeLabel);

            if (this.duplicator != null)
            {
                menu.action(Icons.DUPE, this.duplicateLabel, () -> this.duplicate(entry));
            }
        }

        if (this.clearer != null && !this.list.getList().isEmpty())
        {
            menu.icon(MenuVerb.RESET, this.clearer).label(this.clearLabel);
        }

        if (this.menu != null)
        {
            this.menu.accept(menu, entry);
        }
    }
}
