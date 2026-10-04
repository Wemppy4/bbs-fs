package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.spline.SplineSource;
import mchorse.bbs_mod.ui.utils.SplineFormTool;
import mchorse.bbs_mod.ui.utils.UISplinePointsEditor;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.spline.ModelSplineRuntime;
import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.cubic.spline.SplinePoint;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.UISplinePointList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelSplineManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.ui.utils.UISplineControlFields;

/** Spline controls are form values: fields, gestures, undo and animation share their identity. */
public class UIModelSplineFormPanel extends UIBoneListFormPanel implements SplineFormTool
{
    public final UISplinePointsEditor pointEditor;

    public final UIToggle debug;
    public final UISplinePointList points;
    public final UIPropTransform position;
    private final UISection advanced;
    private final UITrackpad chainLength;
    private final UILabel chainPreview;
    private final Map<String, UIBoneTreeList.Marker[]> boneMarkers = new HashMap<>();
    private final UIToggle enabled;
    private final UIToggle fit;
    private final UIToggle moveModel;
    private final UISliderTrackpad influence;
    private final UITrackpad progress;
    private final UITrackpad twist;
    private final UILabel status;
    private final UIElement fields;
    private String chainId = "";

    public static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.editors.model.spline." + name);
    }

    public UIModelSplineFormPanel(UIForm editor)
    {
        super(editor);
        this.bones.tooltip(key("tip"));
        this.bones.markers(this.boneMarkers::get, key("chain_markers"));
        this.bonePresets(ModelSplineManager.INSTANCE, "_CopyModelSpline",
            key("context.copy"), key("context.paste"), key("context.reset"), key("context.save"), key("context.name"),
            this::toPresetData, this::applyPresetData);
        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_DEBUG, b -> BBSSettings.ikDebug.enabled.set(b.getValue()));
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());
        this.chainLength = new UITrackpad(v -> this.setChainLength(v.intValue()));
        this.chainLength.limit(0).integer();
        this.chainPreview = UI.label(IKey.EMPTY, UIConstants.LIST_ITEM_HEIGHT, Colors.LIGHTER_GRAY);
        this.chainPreview.labelAnchor(0F, 0.5F);
        this.enabled = new UIToggle(key("enabled"), b -> this.setChainEnabled(b.getValue()));
        this.fit = new UIToggle(key("fit"), b -> { if (this.chain() != null) this.chain().fit.set(b.getValue()); });
        this.fit.tooltip(key("fit_tooltip"));
        var controls = new UISplineControlFields(edit ->
        {
            if (this.chain() != null) BaseValue.edit(this.form.splineIK, value -> edit.accept(this.chain().original()));
        });
        this.influence = controls.influence;
        this.progress = controls.progress;
        this.moveModel = new UIToggle(key("move_model"), b -> { if (this.chain() != null) this.chain().moveModel.set(b.getValue()); });
        this.moveModel.tooltip(key("move_model_tooltip"));
        this.twist = controls.twist;
        this.position = new UIDeltaPropTransform()
        {
            @Override
            protected void applyToSelection(Consumer<Transform> consumer)
            {
                SplineIK chain = UIModelSplineFormPanel.this.chain();
                if (chain == null) return;
                for (String id : UIModelSplineFormPanel.this.pointEditor.selected())
                {
                    SplinePoint point = chain.points.get(id);
                    if (point != null) consumer.accept(point.position.getOriginalValue());
                }
            }
        };
        this.position.noScale().setRotationVisible(false);
        this.position.callbacks(
            () -> { if (this.point() != null) this.form.splineIK.preNotify(); },
            () -> { if (this.point() != null) this.form.splineIK.postNotify(); },
            () -> { if (this.point() != null) this.form.splineIK.preNotify(IValueListener.FLAG_UNMERGEABLE); }
        );
        this.position.enableHotkeys(() -> this.editor.view == this && this.getGizmoTransform() != null, op -> op == TransformOp.TRANSLATE);
        this.position.hotkeyDrag(() -> this.editor.editor == null ? null : this.editor.editor.buildHotkeyDrag(this.position));
        this.status = UI.label(IKey.EMPTY, UIConstants.CONTROL_HEIGHT, Colors.ORANGE);
        this.status.labelAnchor(0, 0.5F);
        UISection properties = this.section(key("properties"), "spline.properties", true);
        this.advanced = this.section(key("advanced"), "spline.advanced", false);
        this.advanced.fields.add(this.moveModel, UI.labelRow(key("twist"), this.twist), this.fit);
        this.pointEditor = new UISplinePointsEditor(this::chain,
            id -> this.chain().points.get(id).position.getOriginalValue(), this.position,
            () -> this.initialPointPosition(this.chain()));
        this.points = this.pointEditor.points;
        this.fields = UI.column(UIConstants.MARGIN,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH, this.chainLength), this.chainPreview, this.status,
            UI.labelRow(key("influence"), this.influence), UI.labelRow(key("progress_short"), this.progress),
            this.pointEditor);
        properties.fields.add(this.enabled, this.fields);
        this.options.add(this.debug, this.bonesSearch, properties, this.advanced);
    }

    private MapType toPresetData()
    {
        MapType data = new MapType();

        if (this.form != null && !this.form.splines.getAllTyped().isEmpty())
            data.put("splines", this.form.splines.toData());

        return data;
    }

    private void applyPresetData(MapType data)
    {
        if (this.form == null) return;
        this.endPointEdit();
        /* A preset is the whole model's spline setup, including stable animation addresses. */
        BaseValue.edit(this.form.splines, IValueListener.FLAG_UNMERGEABLE, value -> value.fromData(data.getList("splines")));
        this.updateFields();
    }

    private void setChainLength(int length)
    {
        SplineIK chain = this.chain();
        if (chain == null || chain.chainLength.get() == length) return;
        this.endPointEdit();
        /* Structure changes keep the authored curve and its stable point addresses. */
        chain.chainLength.set(length);
        this.updateFields();
    }

    private void updateChainMarkers()
    {
        this.boneMarkers.clear();
        if (this.form == null) return;
        for (SplineIK spline : this.form.splines.getAllTyped())
        {
            for (String bone : ModelSplineRuntime.getChain(this.form, spline))
                this.boneMarkers.put(bone, new UIBoneTreeList.Marker[] {new UIBoneTreeList.Marker(0xFF5599FF, true)});
        }
        for (SplineIK spline : this.form.splines.getAllTyped())
            this.boneMarkers.put(spline.tip.get(), new UIBoneTreeList.Marker[] {
                new UIBoneTreeList.Marker(0xFF5599FF, false)});
    }

    @Override
    public void startEdit(ModelForm form)
    {
        this.endPointEdit();
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());
        super.startEdit(form);
    }

    @Override
    public void finishEdit()
    {
        this.endPointEdit();
        super.finishEdit();
    }

    public SplineIK chain() { return this.form == null ? null : this.form.splines.get(this.chainId); }
    public SplinePoint point() { return this.pointEditor.point(); }
    public String getChainId() { return this.chainId; }
    public String getPointId() { return this.pointEditor.pointId(); }
    public ModelForm getModelForm() { return this.form; }
    public Set<String> getSelectedPointIds() { return this.pointEditor.selected(); }
    public void setHoveredPoint(String point) { this.points.viewportHover = point; }

    public String getListHoveredPoint(UIContext context)
    {
        return this.points.hoveredPoint(context);
    }

    public void selectInViewport(String chain, String point, boolean extend)
    {
        if (!extend || !this.chainId.equals(chain))
        {
            this.select(chain, point);
            return;
        }
        this.pointEditor.selectInViewport(point, true);
    }

    public void select(String chain, String point)
    {
        this.endPointEdit();
        this.chainId = chain;
        SplineIK selected = this.chain();
        if (selected != null)
        {
            this.selectedBone = selected.tip.get();
            this.boneSelection().set(this.selectedBone);
            this.bones.setCurrentScroll(this.selectedBone);
        }
        this.updateFields();
        this.pointEditor.select(point);
    }

    private void endPointEdit()
    {
        this.pointEditor.endEdit();
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled && !this.selectedBone.isEmpty());
    }

    @Override
    protected void updateFields()
    {
        SplineIK chain = this.chain();
        if (chain == null || !chain.tip.get().equals(this.selectedBone))
        {
            this.endPointEdit();
            chain = null;
            if (this.form != null)
            {
                for (SplineIK candidate : this.form.splines.getAllTyped())
                {
                    if (!candidate.tip.get().equals(this.selectedBone)) continue;
                    chain = candidate;
                    break;
                }
            }
            this.chainId = chain == null ? "" : chain.getId();
            this.pointEditor.select("");
        }
        boolean on = chain != null;
        this.enabled.setEnabled(chain != null || this.availableBones.contains(this.selectedBone));
        this.enabled.setValue(on);
        this.fields.setVisible(on);
        this.advanced.setVisible(on);
        if (chain != null)
        {
            this.chainLength.setValue(chain.chainLength.get());
            String path = String.join(" → ", ModelSplineRuntime.getChain(this.form, chain));
            this.chainPreview.label = path.isEmpty() ? UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_EMPTY : IKey.constant(path);
            this.fit.setValue(chain.fit.get());
            this.influence.setValue(chain.influence.get());
            this.progress.setValue(chain.progress.get());
            this.moveModel.setValue(chain.moveModel.get());
            this.twist.setValue(chain.twist.get());
            String reason = ModelSplineRuntime.validate(this.form, chain);
            boolean showStatus = reason != null;
            this.status.label = showStatus ? key("error." + reason) : IKey.EMPTY;
            this.status.tooltip(this.status.label);
            this.status.setVisible(showStatus);
        }
        this.pointEditor.refresh();
        this.updateChainMarkers();
        this.options.resize();
    }

    private void setChainEnabled(boolean enabled)
    {
        this.endPointEdit();
        SplineIK chain = this.chain();
        if (enabled && chain == null) this.addChain();
        else if (!enabled && chain != null)
        {
            BaseValue.edit(this.form, IValueListener.FLAG_UNMERGEABLE, form ->
            {
                form.splines.getAllTyped().remove(chain);
                form.splineIK.getOriginalValue().controls.remove(chain.getId());
            });
            this.chainId = "";
            this.pointEditor.select("");
        }
        this.updateFields();
    }

    private void addChain()
    {
        if (this.form == null || !this.availableBones.contains(this.selectedBone)) return;
        this.endPointEdit();
        SplineIK chain = new SplineIK("");
        chain.name.set(key("title").get() + " " + (this.form.splines.getAllTyped().size() + 1));
        chain.tip.set(this.selectedBone);
        Vector3f origin = this.initialPointPosition(chain);
        if (origin == null) return;
        SplinePoint point = new SplinePoint("");
        point.position.getOriginalValue().translate.set(origin);
        chain.points.add(point);
        BaseValue.edit(this.form.splines, list -> list.add(chain));
        this.select(chain.getId(), point.getId());
    }

    private Vector3f initialPointPosition(SplineIK chain)
    {
        if (this.editor.editor == null) return null;
        return SplineEditorUtils.rootPosition(FormUtils.getRoot(this.form), this.editor.editor.renderer.getTargetEntity(),
            this.getContext() == null ? 0F : this.getContext().getTransition(), this.form, chain);
    }

    public void insertPoint(String chainId, int after, Vector3f position)
    {
        SplineIK chain = this.form == null ? null : this.form.splines.get(chainId);
        if (chain == null) return;
        if (!this.chainId.equals(chainId)) this.select(chainId, "");
        this.pointEditor.insert(after, position);
    }

    public boolean removeSelectedPoints()
    {
        return this.pointEditor.removeSelected();
    }

    @Override public List<SplineSource> splineSources()
    { return this.form == null ? java.util.Collections.emptyList() : new ArrayList<>(this.form.splines.getAllTyped()); }
    @Override public SplineSource activeSpline() { return this.chain(); }
    @Override public UISplinePointsEditor pointEditor() { return this.pointEditor; }
    @Override public void selectSpline(SplineSource source)
    { if (source instanceof SplineIK chain && !chain.getId().equals(this.chainId)) this.select(chain.getId(), ""); }

    @Override
    public UIPropTransform getGizmoTransform()
    {
        if (this.point() == null) return null;
        ModelInstance model = ModelFormRenderer.getModel(this.form);
        return model == null || model.model == null || model.model.getBone(ModelSplineRuntime.getRoot(this.form, this.chain())) == null ? null : this.position;
    }

    @Override
    public Matrix4f getGizmoOrigin(float transition, TransformSpace space)
    {
        if (this.getGizmoTransform() == null || this.editor.editor == null) return null;
        Matrix4f parent = SplineEditorUtils.parentMatrix(FormUtils.getRoot(this.form), this.editor.editor.renderer.getTargetEntity(), transition, this.form, this.chain());
        return parent == null ? null : parent.translate(this.point().position.getOriginalValue().translate);
    }

    @Override
    public void render(UIContext context)
    {
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());
        super.render(context);
    }
}
