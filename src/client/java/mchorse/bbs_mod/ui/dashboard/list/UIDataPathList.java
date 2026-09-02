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
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
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

    private DataPath previousPath;
    private boolean openOnSingleClick = true;
    private Consumer<List<DataPath>> openCallback;
    private ArrayList<DataPath> openArgs = new ArrayList<>(1);

    public UIDataPathList(Consumer<List<DataPath>> callback)
    {
        super(null);

        this.openCallback = callback;
        this.scroll.scrollItemSize = 16;
    }

    public UIDataPathList openOnSingleClick(boolean openOnSingleClick)
    {
        this.openOnSingleClick = openOnSingleClick;

        return this;
    }

    private void open(DataPath dataPath)
    {
        if (this.openCallback == null)
        {
            return;
        }

        this.openArgs.clear();
        this.openArgs.add(dataPath);

        this.openCallback.accept(this.openArgs);
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

    public void fill(Collection<String> hierarchy)
    {
        this.hierarchy.clear();

        for (String string : hierarchy)
        {
            this.hierarchy.add(new DataPath(string));
        }

        this.goTo(DataPath.EMPTY);
    }

    private void goTo(DataPath path)
    {
        this.path.copy(path);
        this.previousPath = null;

        this.filter("");
        this.deselect();
        this.updateStrings();
    }

    private void updateStrings()
    {
        Set<DataPath> paths = new HashSet<>();

        if (!this.path.strings.isEmpty())
        {
            DataPath copy = this.path.copy();

            copy.strings.add("..");
            paths.add(copy);
        }

        for (DataPath dataPath : this.hierarchy)
        {
            if (dataPath.startsWith(this.path, 1))
            {
                paths.add(dataPath);
            }
            else if (dataPath.startsWith(this.path) && !dataPath.equals(this.path))
            {
                DataPath to = dataPath.getTo(this.path.strings.size() + 1);

                paths.add(to);
            }
        }

        this.list.clear();
        this.list.addAll(paths);

        this.sort();
        this.update();
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
        DataPath dataPath = this.getFilename(path);

        if (dataPath != null)
        {
            this.hierarchy.add(dataPath);

            this.add(dataPath);
            this.sort();
            this.setCurrentFile(path);
        }
    }

    /**
     * Removes given path from the hierarchy and currently displayed list.
     */
    public void removeFile(String path)
    {
        DataPath dataPath = this.getFilename(path);

        if (dataPath != null && this.hasInHierarchy(path))
        {
            this.hierarchy.remove(dataPath);

            this.remove(dataPath);
            this.deselect();
        }
    }

    /**
     * Get the filename of the path. It returns filename only if
     * given path matches the current path in the hierarchy, otherwise
     * it will return {@code null}.
     */
    private DataPath getFilename(String path)
    {
        DataPath dataPath = new DataPath(path);

        if (dataPath.startsWith(this.path, 1))
        {
            return dataPath;
        }

        return null;
    }

    public void setCurrentFile(String path)
    {
        if (path == null)
        {
            return;
        }

        DataPath dataPath = new DataPath(path);

        if (dataPath.strings.size() == 1)
        {
            this.goTo(DataPath.EMPTY);
            this.setCurrentScroll(dataPath);
        }
        else
        {
            this.goTo(dataPath.getParent());
            this.setCurrentScroll(dataPath);
        }
    }

    /* UIList overrides */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int index = this.getIndexAtCursor(context);

            if (this.exists(index))
            {
                this.applySelectionOnClick(index);

                DataPath dataPath = this.list.get(index);

                if (dataPath.folder)
                {
                    if (Objects.equals(this.previousPath, dataPath))
                    {
                        DataPath newPath;

                        if (dataPath.getLast().equals(".."))
                        {
                            newPath = this.path.getParent();
                        }
                        else
                        {
                            newPath = dataPath;
                        }

                        this.goTo(newPath);
                    }
                }
                else if (this.openOnSingleClick || Objects.equals(this.previousPath, dataPath))
                {
                    this.open(dataPath);
                }

                this.previousPath = dataPath.copy();

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

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
            if (a.folder && !b.folder) return -1;
            if (b.folder && !a.folder) return 1;

            if (a.toString().endsWith("/..")) return -1;
            if (b.toString().endsWith("/..")) return 1;

            return NaturalOrderComparator.compare(true, a.toString(), b.toString());
        });

        return true;
    }

    @Override
    protected void renderElementPart(UIContext context, DataPath element, int i, int x, int y, boolean hover, boolean selected)
    {
        context.batcher.icon(element.folder ? Icons.FOLDER : this.fileIcon, x, y);

        super.renderElementPart(context, element, i, x + 12, y, hover, selected);
    }

    @Override
    protected String elementToString(UIContext context, int i, DataPath element)
    {
        return element.getLast() + (element.folder ? "/" : "");
    }
}
