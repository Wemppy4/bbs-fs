package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIGeneralFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMaterialFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIPanelBase;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public abstract class UIForm <T extends Form> extends UIPanelBase<UIFormPanel<T>>
{
    public UIFormEditor editor;

    public T form;
    public UIFormPanel<T> defaultPanel;
    public UIGeneralFormPanel generalPanel;

    private UIPropTransform general;

    public UIForm()
    {
        super(Direction.LEFT);

        this.keys().register(Keys.FILM_CONTROLLER_CYCLE_EDITORS, this::cyclePanels);
    }

    public UIPropTransform getEditableTransform()
    {
        UIPoseEditor poseEditor = this.getPoseEditor();

        if (poseEditor != null)
        {
            return poseEditor.transform;
        }

        this.setPanel(this.generalPanel);

        return this.general;
    }

    /**
     * The pose editor this form edits bones through, or null when the form has no skeleton.
     *
     * <p>Everything that places the gizmo on a BONE rather than on the form goes through this one
     * answer — which is why a mob form, whose bones are vanilla model parts, gets the same gizmo,
     * the same world-space paste and the same Ctrl+click bone toggling as a model form without a
     * second copy of any of it.</p>
     */
    public UIPoseEditor getPoseEditor()
    {
        return null;
    }

    /** The path the gizmo sits on: the selected bone, or the form itself when there is none. */
    protected String bonePath()
    {
        UIPoseEditor poseEditor = this.getPoseEditor();
        String bone = poseEditor == null ? null : poseEditor.groups.list.getCurrentFirst();
        String path = FormUtils.getPath(this.form);

        return bone == null || bone.isEmpty() ? path : StringUtils.combinePaths(path, bone);
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
        return this.getOrigin(transition, this.bonePath(), this.getGizmoSpace());
    }

    /** The space the gizmo should be drawn in (the active panel's transform space). */
    public TransformSpace getGizmoSpace()
    {
        UIPoseEditor poseEditor = this.getPoseEditor();

        if (poseEditor != null)
        {
            return poseEditor.transform.getSpace();
        }

        return this.generalPanel != null ? this.generalPanel.transform.getSpace() : TransformSpace.LOCAL;
    }

    /** Always the bone's FULL local matrix (its own rotation included), whatever the
     *  picker says. 🔴 The sampling helpers need it: the rotation-stripped origin variant
     *  doesn't move when {@code transform.rotate} is perturbed, so axis extraction would
     *  silently collapse to identity. */
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), TransformSpace.LOCAL);
    }

    /** The twin of {@link #getOriginMatrix}: always the ORIGIN flavour, the frame before
     *  the edited thing's own rotation. The pair is what a drag snapshot carries as its
     *  two bone frames ({@code GizmoDrag#setFrameAxes}). */
    public Matrix4f getParentOriginMatrix(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), TransformSpace.PARENT);
    }

    /** Origin for the body part gizmo mode: the edited form's OWN root frame, ignoring any
     *  type-specific override like the model's selected pose bone. The drag math still holds
     *  — the attach bone is constant w.r.t. the edited transform and the form's own cancels
     *  in the derivatives. */
    public Matrix4f getBodyPartGizmoOrigin(float transition, TransformSpace space)
    {
        return this.getOrigin(transition, FormUtils.getPath(this.form), space);
    }

    /** The frame's placement matrix: its own for LOCAL, the origin flavour (the frame
     *  before its own rotation) for every other — {@link TransformSpace#placesOnOwnFrame}
     *  is the one place that call is made, so no caller can answer it differently. */
    protected Matrix4f getOrigin(float transition, String path, TransformSpace space)
    {
        Form root = FormUtils.getRoot(this.form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(this.editor.renderer.getTargetEntity(), transition);
        Matrix4f matrix = space.placesOnOwnFrame() ? map.get(path).matrix() : map.get(path).origin();

        return matrix == null ? Matrices.EMPTY_4F : matrix;
    }

    /**
     * The bone's EVALUATED channel rotation (radians) from the same capture
     * {@link #getOrigin(float, String, boolean)} reads, or {@code null} — feeds
     * the gizmo's additive overlay-editing base.
     */
    protected Vector3f getEvaluatedRotation(float transition, String path)
    {
        Form root = FormUtils.getRoot(this.form);

        return FormUtilsClient.getRenderer(root).collectMatrices(this.editor.renderer.getTargetEntity(), transition).get(path).evaluatedRotation();
    }

    protected void registerDefaultPanels()
    {
        this.registerPanel(new UIMaterialFormPanel(this), UIKeys.FORMS_EDITORS_MATERIAL, Icons.MATERIAL);

        UIGeneralFormPanel panel = new UIGeneralFormPanel(this);

        this.registerPanel(panel, UIKeys.FORMS_EDITORS_GENERAL, Icons.GEAR);

        this.generalPanel = panel;
        this.general = panel.transform;
        this.general.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.general));
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

        for (UIFormPanel<T> panel : this.panels)
        {
            panel.startEdit(form);
        }

        this.setPanel(this.findPanel(preferredPanel));
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

    /**
     * Toggle a bone in the pose editor's multi-selection without rebuilding the panel,
     * so a viewport Ctrl+click accumulates a selection instead of resetting it. Returns
     * whether this form actually owns the bone and handled the toggle (only model forms
     * with a pose editor do). See {@link mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm}.
     */
    public boolean toggleBoneSelection(String bone)
    {
        UIPoseEditor poseEditor = this.getPoseEditor();

        if (poseEditor == null || !poseEditor.hasBone(bone))
        {
            return false;
        }

        poseEditor.selectBone(bone, true);

        return true;
    }

    /**
     * The additive euler base under the pose editor's channels for the picked bone: the bone's
     * EVALUATED channels (rest + actions + pose stack) minus the pose track's own contribution, so
     * gizmo deltas compose at the effective angles. Null for anything that isn't the pose panel of
     * a posed form — only that edits a pose-stacked track.
     */
    public Vector3f poseRotationBase(UIPropTransform transform, float transition)
    {
        UIPoseEditor poseEditor = this.getPoseEditor();

        if (poseEditor == null || transform != poseEditor.transform || !(this.form instanceof IPosedForm posedForm))
        {
            return null;
        }

        String bone = poseEditor.groups.list.getCurrentFirst();

        if (bone == null)
        {
            return null;
        }

        return FormUtils.additivePoseRotationBase(posedForm.getPose(), bone, this.getEvaluatedRotation(transition, this.bonePath()));
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
    public void render(UIContext context)
    {
        if (this.view != null)
        {
            this.view.options.area.render(context.batcher, BBSSettings.deepSurface());
        }

        super.render(context);
    }

    @Override
    protected void renderBackground(UIContext context, int x, int y, int w, int h)
    {
        context.batcher.box(x, y, x + w, y + h, BBSSettings.deepSurface());
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
