package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.ui.utils.IBoneSelectionHost;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class UIFormPanel <T extends Form> extends UIElement
{
    private static final float DEFAULT_OPTIONS_WIDTH = 0.2F;

    /**
     * Fold state per section id, for the session. Panels are rebuilt from
     * scratch whenever the editor is (a viewport bone click alone does it), so
     * a section built at its default every time would keep re-folding under the
     * user; this remembers what they last left open.
     */
    private static final Map<String, Boolean> sectionFolds = new HashMap<>();

    protected UIForm editor;
    protected T form;

    public UIScrollView options;
    public UISplitter draggable;

    public UIFormPanel(UIForm editor)
    {
        this.editor = editor;

        /* The share is of the editor around this panel (it sits 20px short of it), keyed per panel class. */
        this.draggable = UISplitter.fraction("form_panel." + this.getClass().getSimpleName(), this.getDefaultOptionsWidth(), 0F, 0.5F);
        this.draggable.measure(this, this::getParent).fromEnd().onChange(() ->
        {
            this.options.w(this.draggable.getValue()).resize();
            this.draggable.resize();
        });

        this.options = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
        this.options.scroll.cancelScrolling();
        this.options.relative(this).x(1F).w(this.draggable.getValue()).minW(120).h(1F).anchorX(1F);

        this.draggable.relative(this.options).x(0F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.add(this.options, this.draggable);
    }

    /**
     * The options column's default share of the panel width, used until the
     * user drags the divider (their choice is then remembered per panel class).
     * Panels with denser controls (the IK panel's per-axis rows) override this.
     */
    protected float getDefaultOptionsWidth()
    {
        return DEFAULT_OPTIONS_WIDTH;
    }

    /**
     * A collapsible section whose fold state outlives panel rebuilds: it opens
     * as the user last left it ({@code defaultExpanded} only on first sight),
     * keyed by {@code id} across the session.
     */
    protected UISection section(IKey title, String id, boolean defaultExpanded)
    {
        return new UISection(title).remember(sectionFolds, id, defaultExpanded);
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

    private final BoneSelection detachedSelection = new BoneSelection();

    /**
     * The bone the animator is working on, owned by the editor this panel is shown in. Resolved
     * through the widget tree on every use: these panels are rebuilt whenever a body part is
     * clicked, so nothing they hold themselves survives.
     *
     * <p>A panel's own {@code selectedBone} field is what it currently displays, derived from
     * this and written back into it — not a second source of truth.</p>
     */
    protected BoneSelection boneSelection()
    {
        IBoneSelectionHost host = this.getAncestor(IBoneSelectionHost.class);

        return host == null ? this.detachedSelection : host.getBoneSelection();
    }

    /**
     * Eyedropper backend for this panel's {@link UIBonePicker}s: arms the form editor's
     * viewport stencil pick and accepts only bones of the form this panel edits — a
     * click on another body part (or a miss) reports null, i.e. cancels.
     */
    protected UIBonePicker.Viewport viewportBonePicking()
    {
        return new UIBonePicker.Viewport()
        {
            @Override
            public void startPicking(Consumer<String> callback)
            {
                UIFormEditor formEditor = UIFormPanel.this.getParent(UIFormEditor.class);

                if (formEditor == null)
                {
                    callback.accept(null);

                    return;
                }

                formEditor.startBonePicking((pair) -> callback.accept(pair != null && pair.a == UIFormPanel.this.form ? pair.b : null));
            }

            @Override
            public void stopPicking()
            {
                UIFormEditor formEditor = UIFormPanel.this.getParent(UIFormEditor.class);

                if (formEditor != null)
                {
                    formEditor.stopBonePicking();
                }
            }
        };
    }
}
