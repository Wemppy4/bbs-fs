package mchorse.bbs_mod.ui.utils.bones;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.utils.UIConstants;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The bone picker: a context menu popup (right where the click happened, Blender
 * style) with a search box over the hierarchy tree. Typing filters to flat matches,
 * Enter takes the first one, clicking a row picks it; a click outside or Escape
 * dismisses without picking — exactly the lifecycle of the old plain menus.
 *
 * <p>Configure with the builder methods after construction: {@link #bones} or
 * {@link #attachments} to fill the tree, then optionally {@link #none} (must come
 * after the fill so the entry lands on top), {@link #disabled} and {@link #set}.
 * Open through {@code context.replaceContextMenu(picker)}.</p>
 */
public class UIBonePickerContextMenu extends UIContextMenu
{
    public static final int WIDTH = 200;
    public static final int MAX_ROWS = 18;

    public final UISearchList<String> search;
    public final UIBoneTreeList bones;

    private final Consumer<String> callback;

    public UIBonePickerContextMenu(Consumer<String> callback)
    {
        this.callback = callback;

        this.bones = new UIBoneTreeList((list) -> this.pick(list.isEmpty() ? null : list.get(0)));
        this.bones.background();
        this.bones.scroll.scrollSpeed *= 2;

        this.search = new UISearchList<>(this.bones);
        this.search.label(UIKeys.GENERAL_SEARCH);
        this.search.relative(this).xy(4, 4).w(1F, -8).h(1F, -8);

        this.add(this.search);
    }

    public UIBonePickerContextMenu bones(IModel model, Collection<String> hidden)
    {
        this.bones.fillBones(model, hidden);

        return this;
    }

    public UIBonePickerContextMenu attachments(Form form, Collection<String> keys)
    {
        this.bones.fillAttachments(form, keys);

        return this;
    }

    /** Plain bone names without hierarchy — for forms whose bones have no model tree. */
    public UIBonePickerContextMenu list(Collection<String> bones)
    {
        this.bones.fillFlat(bones);

        return this;
    }

    /** Add the "no bone" entry (empty id) on top. Call after the fill method. */
    public UIBonePickerContextMenu none()
    {
        this.bones.prepend("", UIKeys.GENERAL_NONE.get());

        return this;
    }

    public UIBonePickerContextMenu disabled(Predicate<String> predicate)
    {
        this.bones.disabled(predicate);

        return this;
    }

    public UIBonePickerContextMenu set(String current)
    {
        this.bones.setCurrentScroll(current);

        return this;
    }

    private void pick(String id)
    {
        if (id == null || this.bones.isDisabled(id))
        {
            return;
        }

        if (this.callback != null)
        {
            this.callback.accept(id);
        }

        this.dismiss();
    }

    @Override
    public boolean isEmpty()
    {
        return this.bones.getList().isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        /* Popup height hugs the actual bone count so short lists don't open a mostly
         * empty box; long ones cap out and scroll. */
        int rows = Math.min(this.bones.getList().size(), MAX_ROWS);
        int h = 4 + 20 + rows * UIConstants.LIST_ITEM_HEIGHT + 4;

        this.xy(context.mouseX(), context.mouseY()).wh(WIDTH, h).bounds(context.menu.overlay, 5);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        /* Enter takes the topmost (best) match — the search textbox lets the key
         * fall through, so this fires while typing too. */
        if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
        {
            this.pick(this.bones.getFirstVisible());

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.getContext().focus(this.search.search);
    }
}
