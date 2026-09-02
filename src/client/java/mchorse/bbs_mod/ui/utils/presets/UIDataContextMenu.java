package mchorse.bbs_mod.ui.utils.presets;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenuBar;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.utils.presets.DataManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A menu that is a preset picker: the same {@link UIContextMenuBar bar of verbs} every context
 * menu has, and under it a searchable list of what has been saved.
 *
 * <p>Unlike an ordinary menu, this one is worked <em>inside</em> — you try a pose, then another,
 * then save the result — so none of its buttons dismiss it.</p>
 */
public class UIDataContextMenu extends UIContextMenu
{
    /** How many saved entries the list is tall enough to show. */
    public static final int ROWS = 12;

    public UIContextMenuBar bar;
    public UISearchList<String> entries;

    private final MenuIcon copy;
    private final MenuIcon paste;
    private final MenuIcon reset;
    private final MenuIcon save;

    private DataManager manager;
    private String group;
    private MapType data;
    private Supplier<MapType> supplier;
    private Consumer<MapType> callback;
    private String copyGroup = "_CopyPose";
    private boolean scrolledToCurrent;

    public UIDataContextMenu(DataManager manager, String group, Supplier<MapType> supplier, Consumer<MapType> callback)
    {
        this.manager = manager;
        this.group = group;
        this.supplier = supplier;
        this.callback = callback;
        this.data = this.manager.getData(group);

        this.bar = new UIContextMenuBar(this::dismiss);

        this.copy = new MenuIcon(MenuVerb.COPY, () -> Window.setClipboard(this.supplier.get(), this.copyGroup)).keepOpen();
        this.paste = new MenuIcon(MenuVerb.PASTE, () ->
        {
            MapType data = Window.getClipboardMap(this.copyGroup);

            if (data != null)
            {
                this.send(data);
            }
        }).keepOpen();
        this.reset = new MenuIcon(MenuVerb.RESET, () -> this.send(new MapType())).keepOpen();
        this.save = new MenuIcon(MenuVerb.SAVE, this::saveCurrent).keepOpen();

        this.bar.register(this.copy);
        this.bar.register(this.paste);
        this.bar.register(this.reset);
        this.bar.register(this.save);

        this.entries = new UISearchList<>(new UIStringList((l) -> this.send(this.data.getMap(l.get(0)))));
        this.entries.search.filename();
        this.entries.search.placeholder(UIKeys.POSE_CONTEXT_NAME);

        this.bar.relative(this).w(1F).h(UIContextMenuBar.HEIGHT);

        this.add(this.bar);
        this.add(this.entries);

        this.fillPoses();
    }

    public UIDataContextMenu tooltips(String copyGroup, IKey copy, IKey paste, IKey reset, IKey save, IKey name)
    {
        this.copyGroup = copyGroup;
        this.copy.label(copy);
        this.paste.label(paste);
        this.reset.label(reset);
        this.save.label(save);
        this.entries.search.placeholder(name);

        return this;
    }

    private void saveCurrent()
    {
        String name = this.entries.search.getText();

        if (!name.isEmpty())
        {
            this.manager.saveData(this.group, name, this.supplier.get());

            this.data = this.manager.getData(this.group);

            this.fillPoses();
            this.entries.search.setText("");
        }
    }

    private void send(MapType map)
    {
        if (this.callback != null)
        {
            this.callback.accept(map);
        }
    }

    private void fillPoses()
    {
        this.entries.list.clear();
        this.entries.list.add(this.data.keys());
        this.entries.list.sort();

        this.scrollToCurrent();
    }

    /**
     * If the current copyable data matches a saved entry exactly, select it and
     * jump the list straight to it (no smooth scroll), so opening the menu lands
     * on the preset that's already applied.
     */
    private void scrollToCurrent()
    {
        MapType current = this.supplier == null ? null : this.supplier.get();

        if (current == null)
        {
            return;
        }

        for (String key : this.data.keys())
        {
            MapType map = this.data.getMap(key);

            if (BaseType.equals(current, map))
            {
                this.entries.list.setCurrentScroll(key);

                break;
            }
        }
    }

    @Override
    public void render(UIContext context)
    {
        /* The list has no real size until the menu is shown, so the scroll can
         * only be positioned once we're actually rendering — jump to the matching
         * entry on the first frame. */
        if (!this.scrolledToCurrent)
        {
            this.scrolledToCurrent = true;
            this.scrollToCurrent();
        }

        super.render(context);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        if (context.canGoBack())
        {
            this.bar.register(new MenuIcon(MenuVerb.BACK, context::backContextMenu));
        }

        this.bar.sync(true);

        int top = this.bar.getTotalHeight();

        this.entries.relative(this).y(top).w(1F).h(1F, -top);

        this.xy(context.mouseX(), context.mouseY())
            .w(Math.max(UISimpleContextMenu.MIN_WIDTH, this.bar.getContentWidth()))
            .h(top + UISimpleContextMenu.FILTER_HEIGHT + UIStringList.DEFAULT_HEIGHT * ROWS)
            .bounds(context.menu.overlay, 5);
    }
}
