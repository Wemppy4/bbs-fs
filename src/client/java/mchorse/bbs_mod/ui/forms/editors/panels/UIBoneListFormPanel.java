package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.utils.presets.DataManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.settings.values.ui.ValueModelDebug;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The shared skeleton of the per-bone solver panels (IK, physics, constraints): a searchable bone
 * tree with a preset menu on the left, and fields on the right that always stand for the selected
 * bone. The panel holds no data of its own — every read and write goes to the form's bone
 * properties, and undo picks the writes up itself.
 */
public abstract class UIBoneListFormPanel extends UIFormPanel<ModelForm>
{
    public UIBoneTreeList bones;
    public UISearchList<String> bonesSearch;

    protected List<String> availableBones = Collections.emptyList();
    protected String selectedBone = "";
    protected ModelInstance modelInstance;
    protected String presetGroup = "";

    /** A trackpad for one axis of an angle, tinted with that axis' colour. */
    protected static UISliderTrackpad axisTrackpad(Consumer<Double> callback, int color, IKey tooltip)
    {
        UISliderTrackpad trackpad = new UISliderTrackpad(callback).angle180();

        trackpad.textbox.setColor(color);
        trackpad.tooltip(tooltip);

        return trackpad;
    }

    public UIBoneListFormPanel(UIForm editor)
    {
        super(editor);

        this.bones = new UIBoneTreeList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);

            this.boneSelection().set(this.selectedBone);
            this.updateFields();
        });
        this.bones.background();

        this.bonesSearch = new UISearchList<>(this.bones);
        this.bonesSearch.label(UIKeys.GENERAL_SEARCH);
        /* Search box plus eight rows is the minimum; the list takes whatever the sections below
         * leave in the panel, so folding them away hands the room to the bones. */
        this.bonesSearch.h(20 + UIConstants.LIST_ITEM_HEIGHT * 8).expand();
    }

    /** The solver's debug toggle with its settings gear beside it, on one row. */
    protected UIElement debugRow(UIToggle debug, ValueModelDebug config)
    {
        UIIcon settings = new UIIcon(Icons.GEAR, (b) -> this.getContext().replaceContextMenu(new UIDebugOverlayContextMenu(config)));

        settings.tooltip(UIKeys.MODEL_DEBUG_CONFIGURE);
        settings.wh(20, 14);

        UIElement row = new UIElement();

        row.row(0).preferred(0).height(14);
        row.add(debug, settings);

        return row;
    }

    /** Hang the preset copy/paste menu on the bone list; each panel names its own store and labels. */
    protected void bonePresets(DataManager manager, String copyId, IKey copy, IKey paste, IKey reset, IKey save, IKey name)
    {
        this.bones.context(() -> new UIDataContextMenu(manager, this.presetGroup, this::toPresetData, this::applyPresetData).tooltips(copyId, copy, paste, reset, save, name));
    }

    protected abstract MapType toPresetData();

    protected abstract void applyPresetData(MapType map);

    /** Push the selected bone's values into the fields. */
    protected abstract void updateFields();

    /** Grey the whole panel out while there is no model to pick bones from. */
    protected abstract void setElementsEnabled(boolean enabled);

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelInstance model = ModelFormRenderer.getModel(form);

        this.modelInstance = model;
        this.presetGroup = this.resolvePresetGroup(form, model);
        this.selectedBone = "";

        if (model == null || model.model == null)
        {
            this.availableBones = Collections.emptyList();
            this.bones.clear();
            this.setElementsEnabled(false);
            this.updateFields();
        }
        else
        {
            List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());

            bones.removeIf(model.getDisabledBones()::contains);
            this.availableBones = bones;

            this.bones.fillBones(model.model, model.getDisabledBones());

            /* The fill resets the list's filter state, but the search box keeps its
             * text across startEdit — reapply so what you see matches the query. */
            this.bones.filter(this.bonesSearch.search.getText());
            this.setElementsEnabled(true);

            /* Land on the bone the animator is working on: the panel is rebuilt on many editor
             * actions, and the bone they came from another tab with is the one they mean here too. */
            if (!this.pickBoneInList(this.boneSelection().get()))
            {
                this.selectBone(bones.isEmpty() ? "" : bones.get(0));
            }
        }

        this.options.resize();
    }

    protected void selectBone(String bone)
    {
        this.selectedBone = bone == null ? "" : bone;

        if (!this.selectedBone.isEmpty())
        {
            this.bones.setCurrentScroll(this.selectedBone);
        }

        this.updateFields();
    }

    @Override
    public boolean pickBoneInList(String bone)
    {
        if (bone == null || bone.isEmpty() || !this.availableBones.contains(bone))
        {
            return false;
        }

        this.selectBone(bone);
        this.boneSelection().set(bone);

        return true;
    }

    protected FormBone selectedFormBone()
    {
        return this.form == null || this.selectedBone.isEmpty() ? null : this.form.bones.getBone(this.selectedBone);
    }

    protected <V> V readBone(Function<FormBone, V> getter, V fallback)
    {
        FormBone bone = this.selectedFormBone();

        return bone == null ? fallback : getter.apply(bone);
    }

    /** Edit the selected bone, creating its entry only now that something is actually written to it. */
    protected void editBone(Consumer<FormBone> edit)
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            return;
        }

        edit.accept(this.form.bones.getOrCreate(this.selectedBone));
    }

    /**
     * Hang the reset verb on a field standing for one of the selected bone's own
     * values. Resolved through {@link #selectedFormBone()} on every right click,
     * so it follows the selection and never conjures a bone entry just by being
     * looked at.
     */
    protected void resetBone(UIElement element, Function<FormBone, BaseValue> getter)
    {
        UIValues.resettable(element, () -> this.readBone(getter, null), this::updateFields);
    }

    /** Presets are grouped by the model, so a skeleton's rigs are offered to every form wearing it. */
    protected String resolvePresetGroup(ModelForm form, ModelInstance model)
    {
        String group = model != null ? model.getPoseGroup() : "";

        if (group == null || group.isEmpty())
        {
            group = form == null ? "" : form.model.get();
        }

        return group == null ? "" : group;
    }
}
