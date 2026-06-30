package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.config.ArmorSlotValue;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.WeldValue;
import mchorse.bbs_mod.cubic.weld.CubeFace;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.ui.ValueStringMap;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISimpleTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Model Editor — a proper data panel (tabs, right icon bar, save) over models. Each tab is an open model;
 * the picker in the icon bar chooses one. The editor area is split into a resizable settings pane on the
 * left (binding straight to the live model's {@link ModelConfig}, so edits show in the preview at once)
 * and the orbit preview on the right. Models are assets, so create/rename/delete are intentionally off.
 */
public class UIModelEditorPanel extends UIDataDashboardPanel<ModelConfig>
{
    public UIScrollView general;
    public UIFormRenderer renderer;
    public UIDraggable splitter;

    private final ModelForm form = new ModelForm();

    /** The model id whose instance we're waiting on; models load asynchronously, so the fill is deferred. */
    private String pendingId;
    private int splitWidth = 220;

    /** Cube faces in enum order; the face picker adds its icons in this order so the index maps straight back. */
    private static final CubeFace[] FACES = CubeFace.values();

    /** The live instance backing the current tab, kept so weld edits can re-resolve its bindings. */
    private ModelInstance bound;

    /** Working rows for the bone maps ({@code [from, to]} pairs); seeded from the config on load so that
     *  a half-filled row survives a section rebuild. Committed back to the value on every edit. */
    private final List<String[]> flippedEntries = new ArrayList<>();
    private final List<String[]> pickingEntries = new ArrayList<>();

    public UIModelEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.enableTabs();

        this.general = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);

        this.renderer = new UIFormRenderer();
        this.renderer.form = this.form;

        this.splitter = new UIDraggable((context) ->
        {
            this.splitWidth = MathUtils.clamp(context.mouseX - this.editor.area.x, 160, this.editor.area.w - 160);
            this.layoutPanes();
            this.resize();
        }).cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);

        this.layoutPanes();

        this.editor.add(this.general, this.renderer, this.splitter);

        UIIcon pick = new UIIcon(Icons.SEARCH, (b) -> this.openPicker());

        pick.tooltip(UIKeys.FORMS_EDITOR_MODEL_PICK_MODEL, Direction.LEFT);
        this.iconBar.add(pick);
    }

    private void layoutPanes()
    {
        this.general.relative(this.editor).x(0).y(0).w(this.splitWidth).h(1F);
        this.splitter.relative(this.editor).x(this.splitWidth).y(0).w(4).h(1F);
        this.renderer.relative(this.editor).x(this.splitWidth + 4).y(0).w(1F, -(this.splitWidth + 4)).h(1F);
    }

    @Override
    public ContentType getType()
    {
        return ContentType.MODELS;
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.MODEL_EDITOR_TITLE;
    }

    @Override
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return false;
    }

    @Override
    public void requestData(String id)
    {
        this.pendingId = id;
        this.tryLoadPending();
    }

    @Override
    public void update()
    {
        super.update();

        this.tryLoadPending();
    }

    private void tryLoadPending()
    {
        if (this.pendingId == null)
        {
            return;
        }

        ModelInstance instance = BBSModClient.getModels().getModel(this.pendingId);

        if (instance != null)
        {
            this.bound = instance;
            this.form.model.set(this.pendingId);
            this.pendingId = null;
            this.fill(instance.config);
        }
    }

    @Override
    public void fill(ModelConfig data)
    {
        super.fill(data);

        /* Models are assets — duplicating, renaming or deleting them isn't supported here. */
        this.overlay.dupe.setEnabled(false);
        this.overlay.rename.setEnabled(false);
        this.overlay.remove.setEnabled(false);
    }

    @Override
    protected void fillData(ModelConfig data)
    {
        this.seedMap(this.flippedEntries, data == null ? null : data.flippedParts);
        this.seedMap(this.pickingEntries, data == null ? null : data.pickingOverrides);

        this.rebuildSections(data);
    }

    private void seedMap(List<String[]> entries, ValueStringMap value)
    {
        entries.clear();

        if (value != null)
        {
            for (Map.Entry<String, String> entry : value.get().entrySet())
            {
                entries.add(new String[] {entry.getKey(), entry.getValue()});
            }
        }
    }

    @Override
    public void appear()
    {
        super.appear();

        if (this.data == null && this.pendingId == null)
        {
            List<String> keys = BBSModClient.getModels().getAvailableKeys();

            keys.sort(String::compareToIgnoreCase);

            if (!keys.isEmpty())
            {
                this.pickData(keys.get(0));
            }
        }
    }

    private void openPicker()
    {
        UIListOverlayPanel picker = new UIListOverlayPanel(UIKeys.FORMS_EDITOR_MODEL_MODELS, this::pickData);

        picker.addValues(BBSModClient.getModels().getAvailableKeys());
        picker.list.list.sort();
        picker.setValue(this.form.model.get());

        UIOverlay.addOverlay(this.getContext(), picker);
    }

    private void rebuildSections(ModelConfig config)
    {
        this.general.removeAll();

        if (config != null)
        {
            this.general.add(
                this.generalSection(config),
                this.itemsSection(config),
                this.armorSection(config),
                this.firstPersonSection(config),
                this.lookAtSection(config),
                this.sneakingSection(config),
                this.mapsSection(config),
                this.weldsSection(config),
                this.bonesSection(config)
            );
        }

        this.general.resize();
    }

    private UISection generalSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.FORMS_EDITORS_GENERAL);

        section.fields.add(
            this.toggleRefresh(UIKeys.MODEL_EDITOR_PROCEDURAL, config.procedural),
            this.toggle(UIKeys.MODEL_EDITOR_CULLING, config.culling),
            this.toggleRefresh(UIKeys.MODEL_EDITOR_ON_CPU, config.onCpu),
            UI.label(UIKeys.MODEL_EDITOR_UI_SCALE), this.floatField(config.uiScale),
            UI.label(UIKeys.MODEL_EDITOR_SCALE), UI.row(this.component(config.scale, 0), this.component(config.scale, 1), this.component(config.scale, 2)),
            UI.label(UIKeys.MODEL_EDITOR_POSE_GROUP), this.stringField(config.poseGroup),
            UI.label(UIKeys.MODEL_EDITOR_ANCHOR), this.bonePicker(config.anchor::get, config.anchor::set, () -> {}),
            UI.label(UIKeys.MODEL_EDITOR_TEXTURE), this.textureField(config.texture)
        );

        return section;
    }

    private UISection lookAtSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_LOOK_AT);

        section.fields.add(
            UI.label(UIKeys.MODEL_EDITOR_LOOK_AT_HEAD), this.bonePicker(config.lookAt.head::get, config.lookAt.head::set, config::rebuild),
            this.lookAtPitch(config),
            UI.label(UIKeys.MODEL_EDITOR_LOOK_AT_LIMIT), this.lookAtLimit(config)
        );

        return section;
    }

    private UIToggle lookAtPitch(ModelConfig config)
    {
        return new UIToggle(UIKeys.MODEL_EDITOR_LOOK_AT_PITCH, config.lookAt.pitch.get(), (t) ->
        {
            config.lookAt.pitch.set(t.getValue());
            config.rebuild();
        });
    }

    private UITrackpad lookAtLimit(ModelConfig config)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            config.lookAt.headLimit.set(v.floatValue());
            config.rebuild();
        });

        trackpad.setValue(config.lookAt.headLimit.get());
        trackpad.delayedInput();

        return trackpad;
    }

    /* Attachment slots (items in hand, armor, first-person) — a bone plus a transform. */

    private UISimpleTransform slotTransform(ModelConfig config, ArmorSlotValue slot)
    {
        UISimpleTransform transform = new UISimpleTransform(config::rebuild);

        transform.setTransform(slot.transform.get());
        transform.w(1F);

        return transform;
    }

    private UISection itemsSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_ITEMS);

        this.fillItemList(section, config, config.itemsMain, UIKeys.MODEL_EDITOR_ITEMS_MAIN);
        this.fillItemList(section, config, config.itemsOff, UIKeys.MODEL_EDITOR_ITEMS_OFF);

        return section;
    }

    private void fillItemList(UISection section, ModelConfig config, ModelConfig.ItemSlotList list, IKey label)
    {
        section.fields.add(UI.label(label).marginTop(4));

        for (ArmorSlotValue slot : list.getAllTyped())
        {
            section.fields.add(this.itemEntry(config, list, slot));
        }

        section.fields.add(new UIButton(UIKeys.MODEL_EDITOR_ITEM_ADD, (b) ->
        {
            list.add(new ArmorSlotValue(String.valueOf(list.getList().size())));
            list.sync();
            config.rebuild();
            this.rebuildSections(config);
        }));
    }

    private UIElement itemEntry(ModelConfig config, ModelConfig.ItemSlotList list, ArmorSlotValue slot)
    {
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) ->
        {
            list.getAllTyped().remove(slot);
            list.sync();
            config.rebuild();
            this.rebuildSections(config);
        });

        remove.tooltip(UIKeys.MODEL_EDITOR_ITEM_REMOVE, Direction.LEFT);
        remove.wh(20, 20);

        UIElement head = new UIElement();

        head.row(UIConstants.MARGIN).preferred(0);
        head.add(this.bonePicker(slot.group::get, slot.group::set, config::rebuild), remove);

        UIElement entry = UI.column(head, this.slotTransform(config, slot));

        entry.marginBottom(6);

        return entry;
    }

    private UISection armorSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_ARMOR);

        for (ArmorType type : ArmorType.values())
        {
            section.fields.add(this.armorRow(config, type));
        }

        return section;
    }

    private UIElement armorRow(ModelConfig config, ArmorType type)
    {
        ArmorSlotValue slot = config.armorSlots.slot(type);

        UIElement column = UI.column(
            UI.label(this.armorTypeLabel(type)),
            this.bonePicker(slot.group::get, slot.group::set, () ->
            {
                config.rebuild();
                this.rebuildSections(config);
            })
        );

        if (slot.isActive())
        {
            column.add(this.slotTransform(config, slot));
        }

        column.marginBottom(6);

        return column;
    }

    private IKey armorTypeLabel(ArmorType type)
    {
        return switch (type)
        {
            case HELMET -> UIKeys.MODEL_EDITOR_ARMOR_HELMET;
            case CHEST -> UIKeys.MODEL_EDITOR_ARMOR_CHEST;
            case LEGGINGS -> UIKeys.MODEL_EDITOR_ARMOR_LEGGINGS;
            case LEFT_ARM -> UIKeys.MODEL_EDITOR_ARMOR_LEFT_ARM;
            case RIGHT_ARM -> UIKeys.MODEL_EDITOR_ARMOR_RIGHT_ARM;
            case LEFT_LEG -> UIKeys.MODEL_EDITOR_ARMOR_LEFT_LEG;
            case RIGHT_LEG -> UIKeys.MODEL_EDITOR_ARMOR_RIGHT_LEG;
            case LEFT_BOOT -> UIKeys.MODEL_EDITOR_ARMOR_LEFT_BOOT;
            case RIGHT_BOOT -> UIKeys.MODEL_EDITOR_ARMOR_RIGHT_BOOT;
        };
    }

    private UISection firstPersonSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_FIRST_PERSON);

        section.fields.add(this.fpRow(config, UIKeys.MODEL_EDITOR_ITEMS_MAIN, config.fpMain));
        section.fields.add(this.fpRow(config, UIKeys.MODEL_EDITOR_ITEMS_OFF, config.fpOffhand));

        return section;
    }

    private UIElement fpRow(ModelConfig config, IKey label, ArmorSlotValue slot)
    {
        UIElement column = UI.column(
            UI.label(label),
            this.bonePicker(slot.group::get, slot.group::set, () ->
            {
                config.rebuild();
                this.rebuildSections(config);
            })
        );

        if (slot.isActive())
        {
            column.add(this.slotTransform(config, slot));
        }

        column.marginBottom(6);

        return column;
    }

    /* Bone maps (flip mirror pairs, picking overrides) — rows of "from -> to" bones. */

    private UISection mapsSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_MAPS);

        this.fillMap(section, config, config.flippedParts, this.flippedEntries, UIKeys.MODEL_EDITOR_FLIPPED_PARTS);
        this.fillMap(section, config, config.pickingOverrides, this.pickingEntries, UIKeys.MODEL_EDITOR_PICKING_OVERRIDES);

        return section;
    }

    private void fillMap(UISection section, ModelConfig config, ValueStringMap value, List<String[]> entries, IKey label)
    {
        section.fields.add(UI.label(label).marginTop(4));

        for (String[] pair : entries)
        {
            section.fields.add(this.mapEntry(config, value, entries, pair));
        }

        section.fields.add(new UIButton(UIKeys.MODEL_EDITOR_MAP_ADD, (b) ->
        {
            entries.add(new String[] {"", ""});
            this.rebuildSections(config);
        }));
    }

    private UIElement mapEntry(ModelConfig config, ValueStringMap value, List<String[]> entries, String[] pair)
    {
        Runnable commit = () -> this.commitMap(value, entries);

        UIIcon remove = new UIIcon(Icons.REMOVE, (b) ->
        {
            entries.remove(pair);
            this.commitMap(value, entries);
            this.rebuildSections(config);
        });

        remove.tooltip(UIKeys.MODEL_EDITOR_MAP_REMOVE, Direction.LEFT);
        remove.wh(20, 20);

        UIElement row = new UIElement();

        row.row(UIConstants.MARGIN).preferred(0);
        row.add(
            this.bonePicker(() -> pair[0], (v) -> pair[0] = v, commit),
            this.bonePicker(() -> pair[1], (v) -> pair[1] = v, commit),
            remove
        );

        return row;
    }

    private void commitMap(ValueStringMap value, List<String[]> entries)
    {
        Map<String, String> map = value.get();

        map.clear();

        for (String[] pair : entries)
        {
            if (!pair[0].trim().isEmpty())
            {
                map.put(pair[0], pair[1]);
            }
        }
    }

    /* The sneaking pose is picked from the model's pose presets rather than edited here. */

    private UISection sneakingSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_SNEAKING);

        boolean has = !config.sneakingPose.get().isEmpty();

        section.fields.add(new UIButton(has ? UIKeys.MODEL_EDITOR_SNEAKING_SET : UIKeys.MODEL_EDITOR_SNEAKING_PICK, (b) -> this.openPosePicker(config)));

        if (has)
        {
            section.fields.add(new UIButton(UIKeys.MODEL_EDITOR_SNEAKING_CLEAR, (b) ->
            {
                config.sneakingPose.set(new Pose());
                this.rebuildSections(config);
            }));
        }

        return section;
    }

    private void openPosePicker(ModelConfig config)
    {
        if (this.bound == null)
        {
            return;
        }

        MapType poses = PoseManager.INSTANCE.getData(this.bound.getPoseGroup());

        this.getContext().replaceContextMenu((menu) ->
        {
            for (String name : poses.keys())
            {
                menu.action(Icons.POSE, IKey.constant(name), () ->
                {
                    Pose pose = new Pose();

                    pose.fromData(poses.getMap(name));
                    config.sneakingPose.set(pose);
                    this.rebuildSections(config);
                });
            }
        });
    }

    private UISection bonesSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_BONES);

        UIScrollView list = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);

        list.h(160);

        UITextbox search = new UITextbox(100, (query) -> this.fillBones(list, config, query));

        search.placeholder(UIKeys.GENERAL_SEARCH);

        section.fields.add(search, list);
        this.fillBones(list, config, "");

        return section;
    }

    private void fillBones(UIScrollView list, ModelConfig config, String query)
    {
        list.removeAll();

        if (this.bound != null)
        {
            Set<String> hidden = config.disabledBones.get();
            String filter = query.trim().toLowerCase();

            for (String bone : this.bound.getModel().getGroupKeysInHierarchyOrder())
            {
                if (filter.isEmpty() || bone.toLowerCase().contains(filter))
                {
                    list.add(this.boneToggle(bone, hidden));
                }
            }
        }

        list.resize();
    }

    private UIToggle boneToggle(String bone, Set<String> hidden)
    {
        return new UIToggle(IKey.raw(bone), !hidden.contains(bone), (t) ->
        {
            if (t.getValue())
            {
                hidden.remove(bone);
            }
            else
            {
                hidden.add(bone);
            }
        });
    }

    private UISection weldsSection(ModelConfig config)
    {
        UISection section = new UISection(UIKeys.MODEL_EDITOR_WELDS);

        for (WeldValue weld : config.welds.getAllTyped())
        {
            section.fields.add(this.weldEntry(config, weld));
        }

        section.fields.add(new UIButton(UIKeys.MODEL_EDITOR_WELD_ADD, (b) -> this.addWeld(config)));

        return section;
    }

    private UIElement weldEntry(ModelConfig config, WeldValue weld)
    {
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeWeld(config, weld));

        remove.tooltip(UIKeys.MODEL_EDITOR_WELD_REMOVE, Direction.LEFT);
        remove.wh(20, 20);

        UIElement angle = new UIElement();

        angle.row(UIConstants.MARGIN).preferred(0);
        angle.add(this.weldAngle(weld), remove);

        UIElement entry = UI.column(
            UI.row(this.bonePicker(weld.sourceBone::get, weld.sourceBone::set, this::invalidateWelds), this.facePicker(weld.sourceFace, this::invalidateWelds)),
            UI.row(this.bonePicker(weld.targetBone::get, weld.targetBone::set, this::invalidateWelds), this.facePicker(weld.targetFace, this::invalidateWelds)),
            UI.label(UIKeys.MODEL_EDITOR_WELD_MAX_ANGLE),
            angle
        );

        entry.marginBottom(6);

        return entry;
    }

    private UIButton bonePicker(Supplier<String> get, Consumer<String> set, Runnable onChange)
    {
        UIButton[] ref = new UIButton[1];
        UIButton button = new UIButton(this.boneLabel(get.get()), (b) ->
        {
            if (this.bound == null)
            {
                return;
            }

            String current = get.get();

            this.getContext().replaceContextMenu((menu) ->
            {
                menu.action(Icons.REMOVE, UIKeys.GENERAL_NONE, current == null || current.isEmpty(), () -> this.pickBone(ref[0], set, onChange, ""));

                for (String bone : this.bound.getModel().getGroupKeysInHierarchyOrder())
                {
                    menu.action(Icons.LIMB, IKey.constant(bone), bone.equals(current), () -> this.pickBone(ref[0], set, onChange, bone));
                }
            });
        });

        ref[0] = button;

        return button;
    }

    private void pickBone(UIButton button, Consumer<String> set, Runnable onChange, String bone)
    {
        set.accept(bone);
        button.label = this.boneLabel(bone);
        onChange.run();
    }

    private IKey boneLabel(String bone)
    {
        return bone == null || bone.isEmpty() ? UIKeys.MODEL_EDITOR_PICK_BONE : IKey.raw(bone);
    }

    private UIIcons facePicker(ValueString value, Runnable onChange)
    {
        UIIcons icons = new UIIcons((b) ->
        {
            value.set(FACES[b.getValue()].name().toLowerCase());
            onChange.run();
        });

        icons.add(Icons.FORWARD, UIKeys.MODEL_EDITOR_FACE_FRONT);
        icons.add(Icons.BACKWARD, UIKeys.MODEL_EDITOR_FACE_BACK);
        icons.add(Icons.ARROW_RIGHT, UIKeys.MODEL_EDITOR_FACE_RIGHT);
        icons.add(Icons.ARROW_LEFT, UIKeys.MODEL_EDITOR_FACE_LEFT);
        icons.add(Icons.ARROW_UP, UIKeys.MODEL_EDITOR_FACE_TOP);
        icons.add(Icons.ARROW_DOWN, UIKeys.MODEL_EDITOR_FACE_BOTTOM);

        CubeFace current = CubeFace.fromName(value.get());

        icons.setValue(current == null ? 0 : current.ordinal());

        return icons;
    }

    private UITrackpad weldAngle(WeldValue weld)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            weld.maxAngle.set(v.floatValue());
            this.invalidateWelds();
        });

        trackpad.setValue(weld.maxAngle.get());
        trackpad.delayedInput();

        return trackpad;
    }

    private void addWeld(ModelConfig config)
    {
        config.welds.add(new WeldValue(String.valueOf(config.welds.getList().size())));
        config.welds.sync();
        this.refresh();
        this.rebuildSections(config);
    }

    private void removeWeld(ModelConfig config, WeldValue weld)
    {
        config.welds.getAllTyped().remove(weld);
        config.welds.sync();
        this.refresh();
        this.rebuildSections(config);
    }

    private void invalidateWelds()
    {
        if (this.bound != null)
        {
            this.bound.invalidateWelds();
        }
    }

    private UIToggle toggle(IKey label, ValueBoolean value)
    {
        return new UIToggle(label, value.get(), (t) -> value.set(t.getValue()));
    }

    /** A toggle for a setting that changes the render path/baked geometry (procedural, on_cpu) — refreshes. */
    private UIToggle toggleRefresh(IKey label, ValueBoolean value)
    {
        return new UIToggle(label, value.get(), (t) ->
        {
            value.set(t.getValue());
            this.refresh();
        });
    }

    /**
     * Rebuild the live instance's baked state so a config edit that changed the render path or geometry
     * shows in the preview without saving: re-resolve welds + derived caches, re-bake VAOs, and reset the
     * renderer's cached animator (the procedural/non-procedural choice). The plain scalar reads (scale,
     * texture, culling...) already update every frame, so they don't go through here.
     */
    private void refresh()
    {
        if (this.bound == null)
        {
            return;
        }

        this.bound.invalidateWelds();
        this.bound.delete();
        this.bound.setup();
        this.resetAnimator();
    }

    private void resetAnimator()
    {
        if (FormUtilsClient.getRenderer(this.form) instanceof ModelFormRenderer renderer)
        {
            renderer.resetAnimator();
        }
    }

    private UITrackpad floatField(ValueFloat value)
    {
        UITrackpad trackpad = new UITrackpad((v) -> value.set(v.floatValue()));

        trackpad.limit(value.getMin(), value.getMax()).delayedInput();
        trackpad.setValue(value.get());

        return trackpad;
    }

    private UITextbox stringField(ValueString value)
    {
        UITextbox textbox = new UITextbox(10000, value::set);

        textbox.setText(value.get());

        return textbox;
    }

    private UIButton textureField(ValueLink value)
    {
        return new UIButton(UIKeys.TEXTURE_PICK_TEXTURE, (b) -> UITexturePicker.open(this.getContext(), value.get(), value::set));
    }

    private UITrackpad component(ValueVector3f value, int axis)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            Vector3f vector = value.get();

            if (axis == 0) vector.x = v.floatValue();
            else if (axis == 1) vector.y = v.floatValue();
            else vector.z = v.floatValue();

            value.set(vector);
        });

        Vector3f vector = value.get();

        trackpad.setValue(axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z);
        trackpad.delayedInput();

        return trackpad;
    }
}
