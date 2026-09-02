package mchorse.bbs_mod.ui.film.clips.modules;

import mchorse.bbs_mod.camera.clips.overwrite.PathClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIPointsModule extends UIAbstractModule
{
    /* Input */
    public PathClip path;
    public Consumer<Integer> picker;

    /* GUI */
    public Scroll scroll = new Scroll(this.area, 20);

    /**
     * Currently selected button (shouldn't be deselected, i.e. can't be -1)
     */
    public int index = 0;

    public UIPointsModule(IUIClipsDelegate editor, Consumer<Integer> picker)
    {
        super(editor);

        this.picker = picker;

        this.scroll.direction = ScrollDirection.HORIZONTAL;
        this.scroll.cancelScrolling();
        /* The strip is all buttons, so a handle has nowhere to sit: the edge
         * shadows tell that there's more to either side */
        this.scroll.noScrollbar();

        this.context((menu) ->
        {
            menu.icon(MenuVerb.ADD, this::addPoint).label(UIKeys.CAMERA_PANELS_POINTS_CONTEXT_ADD);
            menu.icon(MenuVerb.REMOVE, this::removePoint).label(UIKeys.CAMERA_PANELS_POINTS_CONTEXT_REMOVE);
            menu.action(Icons.SHIFT_BACKWARD, UIKeys.CAMERA_PANELS_POINTS_CONTEXT_MOVE_BACK, this::moveBack);
            menu.action(Icons.SHIFT_FORWARD, UIKeys.CAMERA_PANELS_POINTS_CONTEXT_MOVE_FORWARD, this::moveForward);
        });
    }

    public void setIndex(int index)
    {
        this.index = index;
        this.scroll.scrollIntoView(index * this.scroll.scrollItemSize);
    }

    public void moveBack()
    {
        if (this.index == 0)
        {
            return;
        }

        this.path.points.move(this.index, this.index - 1);

        this.index = this.index - 1;
    }

    public void moveForward()
    {
        if (this.index >= this.path.size() - 1)
        {
            return;
        }

        this.path.points.move(this.index, this.index + 1);

        this.index = this.index - 1;
    }

    public void addPoint()
    {
        this.path.points.add(this.index + 1, new Position(this.editor.getCamera()));

        this.index = MathUtils.clamp(this.index + 1, 0, this.path.points.size() - 1);

        this.scroll.setSize(this.path.size());
        this.scroll.scrollTo(this.index * this.scroll.scrollItemSize);

        if (this.picker != null)
        {
            this.picker.accept(this.index);
        }
    }

    public void removePoint()
    {
        if (this.path.points.size() == 1 && this.index >= 0)
        {
            return;
        }

        this.path.points.remove(this.index);

        this.index = Math.max(this.index - 1, 0);
        this.scroll.setSize(this.path.size());
        this.scroll.scrollTo(this.index * this.scroll.scrollItemSize);

        if (this.picker != null)
        {
            this.picker.accept(this.index);
        }
    }

    /**
     * Setup the path clip and also fill or reset this module's fields based
     * on the path clip.
     */
    public void fill(PathClip path)
    {
        this.path = path;
        this.index = 0;
        this.scroll.setSize(path.size());
        this.scroll.clamp();
    }

    /**
     * Mouse was clicked
     *
     * This method responsible for adding and removing points in the path
     * clip and initiating scrolling.
     */
    @Override
    public boolean subMouseClicked(UIContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.area.isInside(context))
        {
            if (context.mouseButton == 2 || (context.mouseButton == 0 && Window.isCtrlPressed()))
            {
                this.scroll.dragging = true;

                return true;
            }
            else if (context.mouseButton == 0)
            {
                int index = this.scroll.getIndex(mouseX, mouseY);
                int size = this.path.size();

                if (index >= 0 && index < size)
                {
                    /* Pick a point */
                    this.index = index;

                    if (this.picker != null)
                    {
                        this.picker.accept(index);
                    }
                }

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        return this.scroll.mouseScroll(context);
    }

    /**
     * Mouse button was released
     *
     * If scrolling was initiated on click, this method will be responsible for
     * selecting a point in the path or shifting the playback timeline to the
     * location of the of current path point.
     */
    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.scroll.mouseReleased(context);

        return super.subMouseReleased(context);
    }

    /**
     * Draw the module
     *
     * This method will draw the background, button labels (+/-) and also alls
     * the buttons. It also responsible for scrolling.
     */
    @Override
    public void render(UIContext context)
    {
        if (this.path == null)
        {
            return;
        }

        /* Scroll this view */
        this.scroll.drag(context);

        int x = this.area.x;
        int y = this.area.y;
        int c = this.path.size();

        /* Draw background and buttons */
        context.batcher.box(x, y, x + this.area.w, y + this.area.h, Colors.A50);
        context.batcher.clip(this.area, context);

        for (int i = 0; i < c; i++)
        {
            String label = String.valueOf(i);
            int xx = this.area.x + i * this.scroll.scrollItemSize - (int) this.scroll.getScroll();
            int w = context.batcher.getFont().getWidth(label);

            context.batcher.box(xx, y, xx + 20, y + 20, this.index == i ? 0xffcc1170 : 0xffff2280);
            context.batcher.box(xx + 19, y, xx + 20, y + 20, Colors.A12);
            context.batcher.textShadow(label, xx + 10 - w / 2, y + 6);
        }

        context.batcher.unclip(context);

        this.scroll.renderScrollbar(context.batcher);

        super.render(context);
    }
}