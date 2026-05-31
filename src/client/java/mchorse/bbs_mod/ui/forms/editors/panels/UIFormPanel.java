package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.MathUtils;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public abstract class UIFormPanel <T extends Form> extends UIElement
{
    private static final float DEFAULT_OPTIONS_WIDTH = 0.2F;

    private static Map<Class, Float> widths = new HashMap<>();

    protected UIForm editor;
    protected T form;

    public UIScrollView options;
    public UIDraggable draggable;

    public UIFormPanel(UIForm editor)
    {
        this.editor = editor;

        this.options = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
        this.options.scroll.cancelScrolling();
        this.options.relative(this).x(1F).w(widths.getOrDefault(this.getClass(), DEFAULT_OPTIONS_WIDTH)).minW(140).h(1F).anchorX(1F);

        this.draggable = new UIDraggable((context) ->
        {
            float f = (this.options.area.ex() - context.mouseX) / (float) this.getParent().area.w;
            float w = MathUtils.clamp(f, 0, 0.5F);

            this.options.w(w).resize();
            widths.put(this.getClass(), w);
            this.draggable.resize();
        });
        this.draggable.cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);

        this.draggable.relative(this.options).x(0F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.add(this.options, this.draggable);
    }

    public void startEdit(T form)
    {
        this.form = form;
    }

    public void finishEdit()
    {}

    public void pickBone(String bone)
    {}

    /**
     * Try to select the given bone within this panel's own bone list. Panels that present a
     * list of bones (IK, physics, constraints) override this to keep themselves active when a
     * body part is picked in the viewport, instead of bouncing the user back to the pose editor.
     *
     * @return true if the bone was found in the list and selected.
     */
    public boolean pickBoneInList(String bone)
    {
        return false;
    }
}
