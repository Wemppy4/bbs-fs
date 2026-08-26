package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ContextMenuManager
{
    public List<ContextAction> actions = new ArrayList<>();
    public List<MenuIcon> icons = new ArrayList<>();
    public Consumer<UIRemovedEvent> onClose;
    public boolean autoKeys;
    public UISimpleContextMenu menu;

    private IKey category = UIKeys.CONTEXT_MENU_KEY_CATEGORY;

    public ContextMenuManager custom(UISimpleContextMenu menu)
    {
        this.menu = menu;

        return this;
    }

    public ContextMenuManager onClose(Consumer<UIRemovedEvent> onClose)
    {
        this.onClose = onClose;

        return this;
    }

    public ContextMenuManager autoKeys(IKey category)
    {
        this.category = category;

        return this.autoKeys();
    }

    public ContextMenuManager autoKeys()
    {
        this.autoKeys = true;

        return this;
    }

    public ContextAction action(IKey label, Runnable runnable)
    {
        return this.action(Icons.NONE, label, runnable);
    }

    public ContextAction action(Icon icon, IKey label, Runnable runnable)
    {
        if (icon == null || label == null)
        {
            throw new IllegalStateException("Icon (" + icon + ") and/or label (" + label + ") is null!");
        }

        return this.action(new ContextAction(icon, label, runnable));
    }

    public ContextAction action(Icon icon, IKey label, boolean hightlight, Runnable runnable)
    {
        return this.action(icon, label, hightlight ? BBSSettings.primaryColor(0) : 0, runnable);
    }

    public ContextAction action(Icon icon, IKey label, int color, Runnable runnable)
    {
        if (color == 0)
        {
            return action(icon, label, runnable);
        }

        if (icon == null || label == null)
        {
            throw new IllegalStateException("Icon (" + icon + ") and/or label (" + label + ") is null!");
        }

        return this.action(new ColorfulContextAction(icon, label, runnable, color));
    }

    public ContextAction action(ContextAction action)
    {
        this.actions.add(action);

        return action;
    }

    /**
     * Put a verb into the menu's icon bar instead of its list. Where it lands comes from the
     * verb's slot, not from when this was called — see {@link MenuVerb}.
     */
    public MenuIcon icon(MenuVerb verb, Runnable runnable)
    {
        MenuIcon icon = new MenuIcon(verb, runnable);

        this.icons.add(icon);

        return icon;
    }

    public UISimpleContextMenu create()
    {
        UISimpleContextMenu contextMenu = this.menu == null ? new UISimpleContextMenu() : this.menu;

        this.actions.sort(Comparator.comparingInt((a) -> a.order));

        contextMenu.actions.add(this.actions);
        contextMenu.getEvents().register(UIRemovedEvent.class, this.onClose);

        for (MenuIcon icon : this.icons)
        {
            contextMenu.bar.register(icon);
        }

        boolean keyed = this.autoKeys;

        for (int i = 0; i < this.actions.size(); i++)
        {
            ContextAction action = this.actions.get(i);

            if (action.keys != null)
            {
                keyed = true;

                Keybind register = contextMenu.keys().register(new KeyCombo(action.label, action.keys), () ->
                {
                    if (action.runnable != null)
                    {
                        action.runnable.run();
                    }

                    contextMenu.dismiss();
                });

                if (action.keyCategory != null)
                {
                    register.category(action.keyCategory);
                }
            }
            else if (this.autoKeys && i < 30)
            {
                IKey label = UIKeys.CONTEXT_MENU_KEY.format(action.label);
                int mod = i % 10;
                int key = i == 9 ? GLFW.GLFW_KEY_0 : GLFW.GLFW_KEY_1 + mod;

                KeyCombo combo;

                if (i >= 20)
                {
                    combo = new KeyCombo(label, key, GLFW.GLFW_KEY_LEFT_CONTROL);
                }
                else if (i >= 10)
                {
                    combo = new KeyCombo(label, key, GLFW.GLFW_KEY_LEFT_SHIFT);
                }
                else
                {
                    combo = new KeyCombo(label, key);
                }

                contextMenu.keys().register(combo, () ->
                {
                    if (action.runnable != null)
                    {
                        action.runnable.run();
                    }

                    contextMenu.dismiss();
                }).category(this.category);
            }
        }

        contextMenu.canFocusFilter(!keyed);

        return contextMenu.isEmpty() ? null : contextMenu;
    }
}