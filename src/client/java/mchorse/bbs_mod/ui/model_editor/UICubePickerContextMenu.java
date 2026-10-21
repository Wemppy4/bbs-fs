package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One cube picked out of several, in a popup at the cursor the way the bone picker opens: a row
 * per cube, named the way the model editor's tree names it ({@link UIModelTree#cubeLabel}). While
 * the cursor is on a row, that cube is outlined in the preview ({@link #hover}), so a row that only
 * reads "Cube 9" still says which cube it is.
 *
 * <p>Over a bone's cubes it has a search box; over the cubes a click went through it has a title
 * instead, and each row names its bone as well, since those come from two bones at once.</p>
 */
public class UICubePickerContextMenu extends UIContextMenu
{
    private static final int WIDTH = 160;
    private static final int MAX_ROWS = 12;

    /** The search box's height, the bone picker's. */
    private static final int SEARCH_HEIGHT = 20;

    private final CubeList cubes;
    private final UISearchList<ModelNode> search;
    private final Consumer<ModelNode> callback;
    private Consumer<ModelNode> hover = (node) -> {};

    /** With {@code title} null, a search box over the rows; else the title, and the bones named on the rows. */
    public UICubePickerContextMenu(IKey title, Function<ModelNode, String> labels, Consumer<ModelNode> callback)
    {
        this.callback = callback;
        this.cubes = new CubeList((list) -> this.pick(list.isEmpty() ? null : list.get(0)), labels, title != null);
        this.cubes.background();

        if (title == null)
        {
            this.search = new UISearchList<>(this.cubes);
            this.search.label(UIKeys.GENERAL_SEARCH);
            this.search.relative(this).xy(4, 4).w(1F, -8).h(1F, -8);
            this.add(this.search);
        }
        else
        {
            UIElement label = UI.label(title, UIConstants.LIST_ITEM_HEIGHT).labelAnchor(0F, 0.5F).color(Colors.GRAY);

            this.search = null;
            label.relative(this).xy(4, 4).w(1F, -8);
            this.cubes.relative(this).xy(4, 4 + UIConstants.LIST_ITEM_HEIGHT).w(1F, -8).h(1F, -8 - UIConstants.LIST_ITEM_HEIGHT);
            this.add(label, this.cubes);
        }
    }

    /** The cubes, in the order they're listed. */
    public UICubePickerContextMenu cubes(List<ModelNode> cubes)
    {
        this.cubes.setList(cubes);

        return this;
    }

    /** The cube picked now, marked in the list; null for none. */
    public UICubePickerContextMenu set(ModelNode current)
    {
        if (current != null)
        {
            this.cubes.setCurrentScroll(current);
        }

        return this;
    }

    /** Told every frame which cube the cursor is on in the list. */
    public UICubePickerContextMenu hover(Consumer<ModelNode> hover)
    {
        this.hover = hover;

        return this;
    }

    private void pick(ModelNode node)
    {
        if (node == null)
        {
            return;
        }

        this.callback.accept(node);
        this.dismiss();
    }

    @Override
    public boolean isEmpty()
    {
        return this.cubes.getList().isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        int rows = Math.min(this.cubes.getList().size(), MAX_ROWS);
        int top = this.search == null ? UIConstants.LIST_ITEM_HEIGHT : SEARCH_HEIGHT;

        this.xy(context.mouseX(), context.mouseY()).wh(WIDTH, 4 + top + rows * UIConstants.LIST_ITEM_HEIGHT + 4).bounds(context.menu.overlay, 5);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        /* Enter takes the topmost row, the bone picker's way: what the search left first. */
        if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
        {
            this.pick(this.cubes.first());

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        if (this.search != null)
        {
            this.getContext().focus(this.search.search);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        ModelNode hovered = this.cubes.atCursor(context);

        if (hovered != null)
        {
            this.hover.accept(hovered);
        }
    }

    /** The rows: a cube's name, and with {@code bones} its bone's on the far end, quieter. */
    private static class CubeList extends UIList<ModelNode>
    {
        private final Function<ModelNode, String> labels;
        private final boolean bones;

        private CubeList(Consumer<List<ModelNode>> callback, Function<ModelNode, String> labels, boolean bones)
        {
            /* Rows are addresses, rebuilt each time: the same cube is the same row. */
            super(callback, Objects::equals);

            this.labels = labels;
            this.bones = bones;
            this.scroll.scrollItemSize = UIConstants.LIST_ITEM_HEIGHT;
        }

        private ModelNode atCursor(UIContext context)
        {
            int index = this.getIndexAtCursor(context);

            return index < 0 ? null : this.list.get(index);
        }

        private ModelNode first()
        {
            List<ModelNode> visible = this.visible();

            return visible.isEmpty() ? null : visible.get(0);
        }

        @Override
        protected String elementToString(UIContext context, int i, ModelNode element)
        {
            return this.labels.apply(element);
        }

        @Override
        protected void renderElementPart(UIContext context, ModelNode element, int i, int x, int y, boolean hover, boolean selected)
        {
            super.renderElementPart(context, element, i, x, y, hover, selected);

            if (this.bones)
            {
                FontRenderer font = context.batcher.getFont();

                context.batcher.textShadow(element.group(), x + this.area.w - 4 - font.getWidth(element.group()), y + (this.scroll.scrollItemSize - font.getHeight()) / 2, Colors.GRAY);
            }
        }
    }
}
