package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIEntryList;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
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
    public final UITrackpad shift;
    public final UIIcon mirror;
    private ShapeKeys keys = new ShapeKeys();
    private ModelConfig config;
    private final Consumer<Consumer<ShapeKeys>> edit;
    private Runnable boundary = () -> {};
    public Runnable selectionChanged = () -> {};
    private Supplier<String> presetGroup;
    private final DoubleClick<ShapeController> doubleClick = new DoubleClick<>(true);

    public static IKey key(String name) { return L10n.lang("bbs.ui.shape_controllers." + name); }

    public UIShapeControllers(Consumer<Consumer<ShapeKeys>> edit)
    {
        this.edit = edit;
        this.list = new UIEntryList<>(picked ->
        {
            this.refresh(); this.selectionChanged.run();
            ShapeController c = this.selected();
            if (c != null && this.doubleClick.hit(c))
            {
                this.boundary.run();
                this.reset(c, ShapeController.HEIGHT, ShapeController.TILT, ShapeController.SHIFT);
                this.boundary.run();
            }
        }, c -> c.name.get());
        this.list.h(64);
        this.height = new UITrackpad(v -> this.setAxis(ShapeController.HEIGHT, v.floatValue()));
        this.tilt = new UITrackpad(v -> this.setAxis(ShapeController.TILT, v.floatValue()));
        this.shift = new UITrackpad(v -> this.setAxis(ShapeController.SHIFT, v.floatValue()));
        this.height.increment(0.05F); this.tilt.increment(0.05F); this.shift.increment(0.05F);
        this.height.tooltip(key("height")).tooltipImmediate();
        this.tilt.tooltip(key("tilt")).tooltipImmediate();
        this.shift.tooltip(key("shift")).tooltipImmediate();
        for (UITrackpad field : List.of(this.height, this.shift, this.tilt))
        {
            field.getEvents().register(mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent.class, e -> this.boundary.run());
            field.getEvents().register(mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent.class, e -> this.boundary.run());
        }
        /* The pose editor's mirror edit (Shift+F): one switch for bones and controllers alike. */
        this.mirror = UI.icon(Icons.CONVERT, b -> this.toggleMirror());
        this.mirror.highlight(BBSSettings.poseMirrorEdit::get, Direction.BOTTOM);
        this.mirror.tooltip(key("mirror"));
        this.keys().register(Keys.TRANSFORMATIONS_MIRROR_EDIT, this::toggleMirror).strict().category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        this.column(4).vertical().stretch();
        this.add(this.list, UI.row(this.height, this.shift, this.tilt, this.mirror));
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
        this.list.setCurrent(controller == null ? List.of() : List.of(controller)); this.refresh(); this.selectionChanged.run();
    }
    public void fill(ModelConfig config, ShapeKeys keys)
    {
        List<ShapeController> controllers = config == null ? List.of() : config.shapeControllers.getAllTyped();
        String id = this.selected() == null ? "" : this.selected().getId();
        this.config = config;
        this.keys = keys;
        this.list.setList(controllers);
        ShapeController current = controllers.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(controllers.isEmpty() ? null : controllers.get(0));
        this.list.setCurrent(current == null ? List.of() : List.of(current));
        this.list.h(Math.max(1, Math.min(5, controllers.size())) * 16);
        this.refresh();
    }
    public void refresh(ShapeKeys keys) { this.keys = keys; this.refresh(); }
    private void refresh()
    {
        ShapeController selected = this.selected();
        float[] values = selected == null ? new float[ShapeController.AXES] : selected.read(this.keys);
        this.axisField(this.height, selected, ShapeController.HEIGHT, values);
        this.axisField(this.tilt, selected, ShapeController.TILT, values);
        this.axisField(this.shift, selected, ShapeController.SHIFT, values);
    }
    private void axisField(UITrackpad field, ShapeController selected, int axis, float[] values)
    {
        field.setEnabled(selected != null && selected.hasAxis(axis));
        if (!field.isUserEditing()) field.setValue(values[axis]);
    }
    public void setAxis(int axis, float value)
    {
        ShapeController selected = this.selected();
        if (selected == null) return;
        ShapeController mirror = this.mirrorOf(selected);
        /* Mirrored across X: the same height, tilt and shift go the other way. */
        this.edit.accept(keys ->
        {
            selected.set(keys, axis, value);
            if (mirror != null) mirror.set(keys, axis, axis == ShapeController.HEIGHT ? value : -value);
        });
        this.refresh();
    }

    /** Back to rest on the given axes (the mirror partner follows); the caller brackets it as one undo step. */
    public void reset(ShapeController controller, int... axes)
    {
        if (controller != this.selected()) this.select(controller);
        for (int axis : axes) if (controller.hasAxis(axis)) this.setAxis(axis, 0F);
    }

    /** The paired controller while mirror edit is on. */
    public ShapeController mirrorOf(ShapeController controller)
    {
        return !BBSSettings.poseMirrorEdit.get() || this.config == null || controller == null ? null
            : controller.mirror(this.list.getList(), this.config.getFlippedParts());
    }

    private void toggleMirror()
    {
        BBSSettings.poseMirrorEdit.set(!BBSSettings.poseMirrorEdit.get());
        UIUtils.playClick();
    }
    public boolean isEditing() { return this.height.isUserEditing() || this.tilt.isUserEditing() || this.shift.isUserEditing(); }
    @Override public void render(UIContext context) { this.refresh(); super.render(context); }
}
