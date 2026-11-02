package mchorse.bbs_mod.ui.dashboard.list;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class UIDataPathList extends UIList<DataPath>
{
    /**
     * A list of paths.
     */
    private Set<DataPath> hierarchy = new HashSet<>();

    /**
     * Path in which current list is located. It's expected to be
     * something like "abc/def/ghi" (i.e. without trailing slash).
     */
    private DataPath path = new DataPath(true);

    /**
     * Icon that is used to render "files."
     */
    private Icon fileIcon = Icons.FILE;

    private DataPath selectedFolder;
    private Runnable changed = () -> {};

    public UIDataPathList(Consumer<List<DataPath>> callback)
    {
        super(callback);
        this.scroll.scrollItemSize = 16;
    }

    public void setFileIcon(Icon icon)
    {
        this.fileIcon = icon;
    }

    public DataPath getPath()
    {
        return this.path;
    }

    public DataPath getPath(String name)
    {
        if (this.path.strings.isEmpty())
        {
            return new DataPath(name);
        }

        DataPath copy = this.path.copy();

        copy.combine(new DataPath(name));

        return copy;
    }

    public void onChanged(Runnable changed)
    {
        this.changed = changed;
    }

    public Collection<DataPath> getHierarchy()
    {
        return java.util.Collections.unmodifiableSet(this.hierarchy);
    }

    public void fill(Collection<String> hierarchy)
    {
        this.hierarchy.clear();

        for (String string : hierarchy)
        {
            this.hierarchy.add(new DataPath(string));
        }

        while (!this.path.strings.isEmpty() && this.hierarchy.stream().noneMatch(p -> p.startsWith(this.path)))
        {
            this.path.copy(this.path.getParent());
        }

        this.goTo(this.path.copy());
    }

    public void goTo(DataPath path)
    {
        this.path.copy(path);
        this.deselect();
        this.selectedFolder = path.strings.isEmpty() ? null : path.copy();
        this.updateStrings();
        this.changed.run();
    }

    private void updateStrings()
    {
        this.list.clear();

        for (DataPath dataPath : this.hierarchy)
        {
            if (!dataPath.folder && (this.isFiltering() || dataPath.startsWith(this.path, 1)))
            {
                this.list.add(dataPath);
            }
        }

        this.sort();
        this.refilter();
    }

    @Override
    public void filter(String query)
    {
        super.filter(query);
        this.deselect();
        this.selectedFolder = null;
        this.updateStrings();
    }

    @Override
    public DataPath getCurrentFirst()
    {
        DataPath file = super.getCurrentFirst();
        return file == null ? this.selectedFolder : file;
    }

    public boolean hasInHierarchy(String path)
    {
        return this.hasInHierarchy(new DataPath(path));
    }

    public boolean hasInHierarchy(DataPath path)
    {
        return this.hierarchy.contains(path);
    }

    /**
     * Add file path to this hierarchy.
     */
    public void addFile(String path)
    {
        this.hierarchy.add(new DataPath(path));
        this.setCurrentFile(path);
    }

    public void removeFile(String path)
    {
        this.hierarchy.remove(new DataPath(path));
        this.deselect();
        this.selectedFolder = null;
        this.updateStrings();
        this.changed.run();
    }

    public void setCurrentFile(String path)
    {
        if (path == null)
        {
            return;
        }

        DataPath dataPath = new DataPath(path);

        if (this.visible().contains(dataPath))
        {
            this.setCurrent(dataPath);
            this.moveSelection(0);
            this.selectedFolder = null;
            return;
        }

        if (dataPath.strings.size() == 1)
        {
            this.goTo(DataPath.EMPTY);
            this.setCurrentScroll(dataPath);
            this.selectedFolder = null;
        }
        else
        {
            this.goTo(dataPath.getParent());
            this.setCurrentScroll(dataPath);
            this.selectedFolder = null;
        }
    }

    /* UIList overrides */

    @Override
    protected boolean mouseClickedContextMenu(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 1 && !context.hasContextMenu())
        {
            int index = this.getIndexAtCursor(context);

            if (this.exists(index) && !this.current.contains(index))
            {
                this.setIndex(index);
            }
        }

        return super.mouseClickedContextMenu(context);
    }

    @Override
    protected boolean sortElements()
    {
        this.list.sort((a, b) ->
        {
            return NaturalOrderComparator.compare(true, a.toString(), b.toString());
        });

        return true;
    }

    @Override
    protected void renderElementPart(UIContext context, DataPath element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (this.preview != null)
        {
            super.renderElementPart(context, element, i, x, y, hover, selected);
            return;
        }

        context.batcher.icon(this.fileIcon, mchorse.bbs_mod.ui.framework.elements.utils.RowStyle.iconColor(hover || selected),
            x + 2, y + this.rowHeight() / 2F, 0F, 0.5F);
        String label = context.batcher.getFont().limitToWidth(element.toString(), Math.max(1, this.area.w - 20 - this.doneSlot(element)));
        context.batcher.textShadow(label, x + 16, y + (this.rowHeight() - context.batcher.getFont().getHeight()) / 2,
            mchorse.bbs_mod.ui.framework.elements.utils.RowStyle.textColor(hover || selected));
    }

    @Override
    protected String elementToString(UIContext context, int i, DataPath element)
    {
        return element.toString();
    }
}
