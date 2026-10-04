package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.model_editor.UIEntryList;
import mchorse.bbs_mod.ui.utils.UI;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;

/** The animator-facing controls: no authoring widgets or separate animation values. */
public class UIShapeControllers extends UIElement
{
    public final UIEntryList<ShapeController> list;
    public final UITrackpad height;
    public final UITrackpad tilt;
    private ShapeKeys keys = new ShapeKeys();
    private final Consumer<Consumer<ShapeKeys>> edit;
    private Runnable boundary = () -> {};
    public Runnable selectionChanged = () -> {};
    private Supplier<String> presetGroup;

    public static IKey key(String name) { return L10n.lang("bbs.ui.shape_controllers." + name); }

    public UIShapeControllers(Consumer<Consumer<ShapeKeys>> edit)
    {
        this.edit = edit;
        this.list = new UIEntryList<>(picked -> { this.refresh(); this.selectionChanged.run(); }, c -> c.name.get());
        this.list.h(64);
        this.height = new UITrackpad(v -> this.setAxis(0, v.floatValue()));
        this.tilt = new UITrackpad(v -> this.setAxis(1, v.floatValue()));
        this.height.increment(0.05F); this.tilt.increment(0.05F);
        this.height.tooltip(key("height")).tooltipImmediate();
        this.tilt.tooltip(key("tilt")).tooltipImmediate();
        for (UITrackpad field : java.util.Arrays.asList(this.height, this.tilt))
        {
            field.getEvents().register(mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent.class, e -> this.boundary.run());
            field.getEvents().register(mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent.class, e -> this.boundary.run());
        }
        this.column(4).vertical().stretch();
        this.add(this.list, UI.row(this.height, this.tilt));
        this.list.context(this::presetMenu);
    }

    public void boundary(Runnable boundary) { this.boundary = boundary; }
    public void endGesture() { this.boundary.run(); }
    public ShapeController selected() { return this.list.getCurrentFirst(); }
    public ShapeKeys shapeKeys() { return this.keys; }
    public void presets(Supplier<String> group) { this.presetGroup = group; }
    public UIDataContextMenu presetMenu()
    {
        return this.presetGroup == null ? null : UIShapeKeys.presetMenu(this.presetGroup.get(), () -> this.keys.toData(), data ->
        {
            this.boundary.run();
            this.edit.accept(keys -> keys.fromData(data));
            this.boundary.run();
            this.refresh();
        });
    }
    public void openPresets(UIContext context) { context.replaceContextMenu(this.presetMenu()); }
    public void select(ShapeController controller)
    {
        this.list.setCurrent(controller == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(controller)); this.refresh(); this.selectionChanged.run();
    }
    public void fill(List<ShapeController> controllers, ShapeKeys keys)
    {
        String id = this.selected() == null ? "" : this.selected().getId();
        this.keys = keys;
        this.list.setList(controllers);
        ShapeController current = controllers.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(controllers.isEmpty() ? null : controllers.get(0));
        this.list.setCurrent(current == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(current));
        this.list.h(Math.max(1, Math.min(5, controllers.size())) * 16);
        this.refresh();
    }
    public void refresh(ShapeKeys keys) { this.keys = keys; this.refresh(); }
    private void refresh()
    {
        ShapeController selected = this.selected();
        this.height.setEnabled(selected != null && selected.bindings.getAllTyped().stream().anyMatch(b -> b.height.get() != 0));
        this.tilt.setEnabled(selected != null && selected.bindings.getAllTyped().stream().anyMatch(b -> b.tilt.get() != 0));
        float[] values = selected == null ? new float[2] : selected.read(this.keys);
        if (!this.height.isUserEditing()) this.height.setValue(values[0]);
        if (!this.tilt.isUserEditing()) this.tilt.setValue(values[1]);
    }
    public void setAxis(int axis, float value)
    {
        ShapeController selected = this.selected();
        if (selected == null) return;
        this.edit.accept(keys -> selected.set(keys, axis, value));
        this.refresh();
    }
    public boolean isEditing() { return this.height.isUserEditing() || this.tilt.isUserEditing(); }
    @Override public void render(UIContext context) { this.refresh(); super.render(context); }
}
