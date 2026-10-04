package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.pose.Transform;
import java.util.List;
import static mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers.key;

/** Model-owned authoring, saved and undone with the rest of config.json. */
public class UIModelShapeControllers extends UIElement
{
    public final UIShapeControllers controls;
    private final UIElement settings = new UIElement();
    private final UIModelEditorPanel panel;
    private ModelInstance model;
    private UIPropTransform position;
    private boolean placing;
    private final UIIcon add;
    private final UIIcon remove;
    private final UIEntryList<ShapeController.Binding> bindings;
    private final UIElement bindingSettings = new UIElement();
    private final UIIcon removeBinding;

    public UIModelShapeControllers(UIModelEditorPanel panel)
    {
        this.panel = panel;
        this.controls = new UIShapeControllers(this.panel::editPreviewShapeKeys);
        this.controls.boundary(this.panel::closeModelEdit);
        this.controls.presets(() -> this.model == null ? "" : this.model.getPoseGroup());
        this.controls.selectionChanged = this::fillSettings;
        this.bindings = new UIEntryList<>(picked -> this.fillBinding(), b -> b.shape.get());
        this.bindings.h(UIConstants.LIST_ITEM_HEIGHT * 4).expand();
        this.bindingSettings.column(4).vertical().stretch();
        this.removeBinding = new UIIcon(Icons.REMOVE, b -> this.removeBinding());
        this.removeBinding.tooltip(UIKeys.GENERAL_REMOVE);
        this.add = new UIIcon(Icons.ADD, b -> this.addController());
        this.add.tooltip(UIKeys.GENERAL_ADD);
        this.remove = new UIIcon(Icons.REMOVE, b -> this.removeController());
        this.remove.tooltip(UIKeys.GENERAL_REMOVE);
        this.column(4).vertical().stretch();
        this.settings.column(4).vertical().stretch();
        this.add(UI.strip(this.add, this.remove), this.controls, this.settings);
    }
    private ModelForm form() { return (ModelForm) this.panel.renderer.form; }
    public void fill(ModelInstance model)
    {
        if (this.model != model)
        {
            this.form().shapeKeys.set(new ShapeKeys());
            this.placing = false;
        }
        this.model = model;
        this.controls.fill(model == null ? java.util.Collections.emptyList() : model.config.shapeControllers.getAllTyped(), this.form().shapeKeys.get());
        this.controls.list.broken(c -> model != null && (!c.bone.get().isEmpty() && model.getModel().getBone(c.bone.get()) == null
            || c.bindings.getAllTyped().stream().anyMatch(b -> !model.model.getShapeKeys().contains(b.shape.get()))));
        this.add.setEnabled(model != null && !model.model.getShapeKeys().isEmpty());
        this.fillSettings();
    }
    private void addController()
    {
        if (this.model == null) return;
        ShapeController controller = new ShapeController("");
        controller.name.set("shape_controller_" + (this.model.config.shapeControllers.getAllTyped().size() + 1));
        BaseValue.edit(this.model.config.shapeControllers, IValueListener.FLAG_UNMERGEABLE, list -> { list.add(controller); list.sync(); });
        this.controls.fill(this.model.config.shapeControllers.getAllTyped(), this.form().shapeKeys.get());
        this.controls.select(controller);
    }
    private void removeController()
    {
        ShapeController selected = this.controls.selected();
        if (selected == null || this.model == null) return;
        BaseValue.edit(this.model.config.shapeControllers, IValueListener.FLAG_UNMERGEABLE, list -> { list.getAllTyped().remove(selected); list.sync(); });
        this.controls.fill(this.model.config.shapeControllers.getAllTyped(), this.form().shapeKeys.get());
        this.fillSettings();
    }
    private void fillSettings()
    {
        this.settings.removeAll(); this.position = null;
        ShapeController c = this.controls.selected();
        this.remove.setEnabled(c != null);
        if (c == null) { this.invalidateLayout(); return; }
        UIBonePicker bone = new UIBonePicker().bind(c.bone::get, c.bone::set, UIKeys.MODEL_EDITOR_PICK_BONE)
            .menu(menu -> menu.bones(this.model.getModel(), null).none().set(c.bone.get())).viewport(this.panel.renderer);
        bone.tooltip(key("bone_hint"));
        Transform transform = new Transform();
        transform.translate.set(c.position.get()).div(16F);
        this.position = new UIPropTransform();
        this.position.noScale().setRotationVisible(false);
        this.position.callbacks(c.position::preNotify, () ->
        {
            c.position.get().set(transform.translate).mul(16F);
            c.position.postNotify();
        }, () -> c.position.preNotify(IValueListener.FLAG_UNMERGEABLE));
        this.position.setTransform(transform);
        UIPropTransform position = this.position;
        position.hotkeyDrag(() ->
        {
            ModelSlotTarget target = this.panel.shownTarget();
            return target == null ? null : this.panel.renderer.buildGizmoDrag(target);
        });
        position.enableHotkeys(() ->
        {
            ModelSlotTarget target = this.panel.shownTarget();
            return target != null && target.editor() == position;
        }, op -> op == TransformOp.TRANSLATE);
        UIToggle place = new UIToggle(key("place"), this.placing, v -> this.placing = v.getValue());
        UIIcon addBinding = new UIIcon(Icons.ADD, b -> this.addBinding());
        addBinding.tooltip(UIKeys.GENERAL_ADD);
        ShapeController.Binding picked = this.bindings.getCurrentFirst();
        this.bindings.setList(c.bindings.getAllTyped());
        this.bindings.setCurrent(picked != null && c.bindings.getAllTyped().contains(picked) ? java.util.Collections.singletonList(picked)
            : c.bindings.getAllTyped().isEmpty() ? java.util.Collections.emptyList() : java.util.Collections.singletonList(c.bindings.getAllTyped().get(0)));
        this.settings.add(UI.labelRow(key("name"), UIValues.textbox(() -> c.name)), UI.labelRow(key("bone"), bone),
            place, this.position, UI.label(key("bindings")), UI.strip(addBinding, this.removeBinding), this.bindings, this.bindingSettings);
        this.fillBinding();
        this.invalidateLayout();
    }

    private void addBinding()
    {
        ShapeController c = this.controls.selected();
        if (c == null) return;
        ShapeController.Binding binding = new ShapeController.Binding("");
        var shapes = this.model.model.getShapeKeys().stream().sorted().collect(java.util.stream.Collectors.toList());
        if (!shapes.isEmpty()) binding.shape.set(shapes.get(0));
        BaseValue.edit(c.bindings, IValueListener.FLAG_UNMERGEABLE, list -> { list.add(binding); list.sync(); });
        this.bindings.setCurrent(binding);
        this.fillSettings();
    }

    private void removeBinding()
    {
        ShapeController c = this.controls.selected();
        ShapeController.Binding binding = this.bindings.getCurrentFirst();
        if (c == null || binding == null) return;
        BaseValue.edit(c.bindings, IValueListener.FLAG_UNMERGEABLE, list -> { list.getAllTyped().remove(binding); list.sync(); });
        this.fillSettings();
    }

    private void fillBinding()
    {
        this.bindingSettings.removeAll();
        ShapeController.Binding binding = this.bindings.getCurrentFirst();
        this.removeBinding.setEnabled(binding != null);
        if (binding != null)
        {
            UIButton shape = new UIButton(() -> binding.shape.get(), b -> this.getContext().replaceContextMenu(menu ->
            {
                for (String name : this.model.model.getShapeKeys().stream().sorted().collect(java.util.stream.Collectors.toList()))
                    menu.action(Icons.SHAPES, IKey.raw(name), () -> binding.shape.set(name));
            }));
            UITrackpad height = UIValues.trackpad(() -> binding.height);
            UITrackpad tilt = UIValues.trackpad(() -> binding.tilt);
            height.tooltip(key("height_gain")).tooltipImmediate();
            tilt.tooltip(key("tilt_gain")).tooltipImmediate();
            for (UITrackpad field : java.util.Arrays.asList(height, tilt))
            {
                field.getEvents().register(UITrackpadDragStartEvent.class, e -> this.panel.closeModelEdit());
                field.getEvents().register(UITrackpadDragEndEvent.class, e -> this.panel.closeModelEdit());
            }
            this.bindingSettings.add(shape, UI.row(height, tilt));
        }
        this.invalidateLayout();
    }
    public ModelSlotTarget target()
    {
        ShapeController c = this.controls.selected();
        return !this.placing || c == null || this.position == null ? null
            : new ModelSlotTarget(c.bone.get(), ModelSlotKind.SHAPE_CONTROLLER, this.position, null, mchorse.bbs_mod.ui.utils.SplineEditorUtils.HANDLES);
    }
}
