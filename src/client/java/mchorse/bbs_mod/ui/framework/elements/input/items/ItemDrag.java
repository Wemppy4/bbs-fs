package mchorse.bbs_mod.ui.framework.elements.input.items;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.DragGesture;
import mchorse.bbs_mod.ui.utils.cells.DragGhost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * One drag of items in progress: what's carried and where it would land. It knows nothing
 * of rows or cells — whoever paints the items reports the target (an object of their
 * choosing and a slot in it) while painting, and resolves the drop on release.
 *
 * @param <T> what's carried
 */
public class ItemDrag<T> extends DragGesture
{
    private final List<T> items = new ArrayList<>();
    private final BiPredicate<T, T> same;

    private Object target;
    private int insertion = -1;

    public ItemDrag()
    {
        this(null);
    }

    /** @param same what makes two items the same one; {@link Object#equals} when null */
    public ItemDrag(BiPredicate<T, T> same)
    {
        this.same = same;
    }

    /** Arm a drag of the given items from where the button went down. */
    public void start(List<T> items, int x, int y)
    {
        this.reset();
        this.press(x, y);

        this.items.addAll(items);
    }

    @Override
    public void reset()
    {
        super.reset();

        this.items.clear();
        this.clearTarget();
    }

    public List<T> getItems()
    {
        return Collections.unmodifiableList(this.items);
    }

    public boolean isDragging(T item)
    {
        if (!this.isActive() || item == null)
        {
            return false;
        }

        for (T t : this.items)
        {
            if (this.same == null ? Objects.equals(t, item) : this.same.test(t, item))
            {
                return true;
            }
        }

        return false;
    }

    /** Whether the drop should leave the originals where they are — Ctrl is held. */
    public boolean isCopy()
    {
        return Window.isCtrlPressed();
    }

    /* Drop target, reported by the host while painting */

    public void setTarget(Object target)
    {
        this.target = target;
    }

    public void setTarget(Object target, int insertion)
    {
        this.target = target;
        this.insertion = insertion;
    }

    public void clearTarget()
    {
        this.target = null;
        this.insertion = -1;
    }

    /** Where the drop would go; null when the cursor is over nothing that takes it. */
    public Object getTarget()
    {
        return this.target;
    }

    public boolean isTarget(Object target)
    {
        return this.isActive() && target != null && target.equals(this.target);
    }

    /** The slot within the target the drop lands in, or -1 when there is none. */
    public int getInsertion()
    {
        return this.insertion;
    }

    public boolean hasTarget()
    {
        return this.target != null || this.insertion != -1;
    }

    /** The carried items beside the cursor, as a small stack of cards; the host paints the front one. */
    public void renderGhost(UIContext context, int w, int h, boolean landing, DragGhost.Painter painter)
    {
        if (this.isActive() && !this.items.isEmpty())
        {
            DragGhost.render(context, context.mouseX, context.mouseY, w, h, this.items.size(), landing, painter);
        }
    }
}
