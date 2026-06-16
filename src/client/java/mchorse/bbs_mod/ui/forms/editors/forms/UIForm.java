package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.UIBodyPartFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIGeneralFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIPanelBase;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.TransformSpace;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public abstract class UIForm <T extends Form> extends UIPanelBase<UIFormPanel<T>>
{
    public UIFormEditor editor;

    public T form;
    public UIFormPanel<T> defaultPanel;
    public UIGeneralFormPanel generalPanel;
    public UIBodyPartFormPanel bodyPartPanel;

    private UIPropTransform general;

    public UIForm()
    {
        super(Direction.LEFT);

        this.keys().register(Keys.FILM_CONTROLLER_CYCLE_EDITORS, this::cyclePanels);
    }

    public UIPropTransform getEditableTransform()
    {
        this.setPanel(this.generalPanel);

        return this.general;
    }

    private void cyclePanels()
    {
        int index = this.panels.indexOf(this.view);
        int newIndex = MathUtils.cycler(index + (Window.isShiftPressed() ? -1 : 1), this.panels);

        this.setPanel(this.panels.get(newIndex));
        UIUtils.playClick();
    }

    public Matrix4f getOrigin(float transition)
    {
        return this.getOrigin(transition, FormUtils.getPath(this.form), this.generalPanel != null ? this.generalPanel.transform.getSpace() : TransformSpace.PARENT);
    }

    /**
     * Always returns the bone's full local matrix (including its own rotation),
     * irrespective of the UI's space toggle. Required for sampling-based
     * gizmo helpers that need the rotation to be visible in the matrix &mdash;
     * the rotation-stripped &quot;origin&quot; variant doesn't move when
     * {@code transform.rotate} is perturbed, so axis extraction would silently
     * fall back to identity.
     */
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.getOrigin(transition, FormUtils.getPath(this.form), TransformSpace.LOCAL);
    }

    protected Matrix4f getOrigin(float transition, String path, TransformSpace space)
    {
        Form root = FormUtils.getRoot(this.form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(this.editor.renderer.getTargetEntity(), transition);
        Matrix4f matrix = space == TransformSpace.LOCAL ? map.get(path).matrix() : map.get(path).origin();

        if (matrix == null)
        {
            return Matrices.EMPTY_4F;
        }

        return space == TransformSpace.WORLD ? new Matrix4f().translation(matrix.getTranslation(new Vector3f())) : matrix;
    }

    protected void registerDefaultPanels()
    {
        UIGeneralFormPanel panel = new UIGeneralFormPanel(this);

        this.registerPanel(panel, UIKeys.FORMS_EDITORS_GENERAL, Icons.GEAR);

        this.generalPanel = panel;
        this.general = panel.transform;
        this.general.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.general));

        this.bodyPartPanel = new UIBodyPartFormPanel(this);
    }

    public void setEditor(UIFormEditor editor)
    {
        this.editor = editor;
    }

    public void startEdit(T form)
    {
        this.startEdit(form, null);
    }

    /**
     * Switching the form rebuilds the editor from scratch, so {@code preferredPanel} carries the tab
     * that was open before the rebuild. When a panel of that class still exists here it stays active;
     * otherwise the editor opens on its default panel.
     */
    public void startEdit(T form, Class<?> preferredPanel)
    {
        this.form = form;

        this.updateBodyPartPanel(form);

        for (UIFormPanel<T> panel : this.panels)
        {
            panel.startEdit(form);
        }

        this.setPanel(this.findPanel(preferredPanel));
    }

    /**
     * Registers the body part panel as the last tab when the edited form is attached to a parent
     * body part. The editor is rebuilt from scratch on every form switch, so the panel only ever
     * needs adding (never removing) &mdash; the root form simply never gets it.
     */
    private void updateBodyPartPanel(T form)
    {
        boolean hasParent = form != null && form.getParent() instanceof BodyPart;

        if (hasParent && !this.panels.contains(this.bodyPartPanel))
        {
            this.registerPanel(this.bodyPartPanel, UIKeys.FORMS_EDITORS_BODY_PART_TITLE, Icons.WRENCH);
        }
    }

    /**
     * Routes a viewport bone pick (Ctrl + click on a parent bone) to the body part panel, which sets
     * the bone this form attaches to. No-op when the form has no parent (panel not registered).
     */
    public void pickBodyPartBone(Pair<Form, String> pair)
    {
        if (this.panels.contains(this.bodyPartPanel))
        {
            this.bodyPartPanel.pickBone(pair);
        }
    }

    private UIFormPanel<T> findPanel(Class<?> panelClass)
    {
        if (panelClass != null)
        {
            for (UIFormPanel<T> panel : this.panels)
            {
                if (panel.getClass() == panelClass)
                {
                    return panel;
                }
            }
        }

        return this.defaultPanel;
    }

    public void finishEdit()
    {
        for (UIFormPanel<T> panel : this.panels)
        {
            panel.finishEdit();
        }
    }

    public void pickBone(String bone)
    {
        if (this.view != null)
        {
            this.view.pickBone(bone);
        }
    }

    public Class<?> getActivePanelClass()
    {
        return this.view == null ? null : this.view.getClass();
    }

    /**
     * Pick a bone that was selected in the 3D viewport. {@code preferredPanel} carries the tab that was
     * open before the form was switched: when it's a bone-list panel (IK/physics/constraints) that
     * actually contains the bone, keep the user on it and select the bone there; otherwise fall back to
     * the pose editor and select the bone in its transform.
     */
    public void pickBoneFromViewport(String bone, Class<?> preferredPanel)
    {
        if (preferredPanel != null)
        {
            for (UIFormPanel<T> panel : this.panels)
            {
                if (panel.getClass() == preferredPanel)
                {
                    if (panel.pickBoneInList(bone))
                    {
                        this.setPanel(panel);

                        return;
                    }

                    break;
                }
            }
        }

        this.setPanel(this.defaultPanel);
        this.pickBone(bone);
    }

    @Override
    protected void renderBackground(UIContext context, int x, int y, int w, int h)
    {
        context.batcher.box(x, y, x + w, y + h, BBSSettings.baseSurface());
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putInt("panel", this.panels.indexOf(this.view));
        data.putDouble("scroll", this.view.options.scroll.getScroll());
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.setPanel(this.panels.get(data.getInt("panel")));
        this.view.options.scroll.setScroll(data.getDouble("scroll"));
    }
}
