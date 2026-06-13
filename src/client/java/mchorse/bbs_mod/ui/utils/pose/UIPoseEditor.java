package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIPoseEditor extends UIElement
{
    private static String lastLimb = "";

    public UIPoseBoneStringList groups;
    public UITrackpad fix;
    public UIColor color;
    public UIToggle lighting;
    public UIPropTransform transform;

    private String group = "";
    private Pose pose;
    protected IModel model;
    protected Map<String, String> flippedParts;

    private int suppressPoseSync;

    public UIPoseEditor()
    {
        this.groups = new UIPoseBoneStringList(this::pickBones);
        this.groups.background().h(UIStringList.DEFAULT_HEIGHT * 8 - 8);
        this.groups.scroll.cancelScrolling();
        this.groups.context(() ->
        {
            UIDataContextMenu menu = new UIDataContextMenu(PoseManager.INSTANCE, this.group, () -> this.pose.toData(), this::pastePose);
            UIIcon flip = new UIIcon(Icons.CONVERT, (b) -> this.flipPose());

            flip.tooltip(UIKeys.POSE_CONTEXT_FLIP_POSE);
            menu.row.addBefore(menu.save, flip);

            return menu;
        });
        this.fix = new UITrackpad((v) -> this.applyFixToSelection(v.floatValue()));
        this.fix.limit(0D, 1D).increment(0.1D).values(0.1, 0.05D, 0.2D);
        this.fix.tooltip(UIKeys.POSE_CONTEXT_FIX_TOOLTIP);
        this.fix.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setFix(p, (float) this.fix.getValue()));
            });
        });
        this.color = new UIColor((c) -> this.applyColorToSelection(c));
        this.color.withAlpha();
        this.color.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setColor(p, this.color.picker.color.getARGBColor()));
            });
        });
        this.lighting = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING, (b) -> this.applyLightingToSelection(b.getValue()));
        this.lighting.h(UIConstants.CONTROL_HEIGHT);
        this.lighting.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setLighting(p, this.lighting.getValue()));
            });
        });
        this.transform = this.createTransformEditor();

        this.keys().register(Keys.TRANSFORMATIONS_TOGGLE_FIX, this::toggleFix).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);

        this.column().vertical().stretch();
        this.add(this.groups, UI.label(UIKeys.POSE_CONTEXT_FIX), this.fix, UI.row(this.color, this.lighting), this.transform.marginTop(4));
    }

    private void applyChildren(Consumer<PoseTransform> consumer)
    {
        if (this.model == null)
        {
            return;
        }

        for (String bone : this.groups.getCurrent())
        {
            Collection<String> keys = this.model.getAllChildrenKeys(bone);

            for (String key : keys)
            {
                consumer.accept(this.pose.get(key));
            }
        }
    }

    public Pose getPose()
    {
        return this.pose;
    }

    /**
     * First selected bone name (for keyframe paths and legacy callers).
     */
    public String getGroup()
    {
        return this.groups.getCurrentFirst();
    }

    private void beginSuppressPoseSync()
    {
        this.suppressPoseSync++;
    }

    private void endSuppressPoseSync()
    {
        this.suppressPoseSync--;
    }

    protected void pastePose(MapType data)
    {
        this.restoreSelectionAfter(() -> this.pose.fromData(data));
    }

    protected void flipPose()
    {
        this.restoreSelectionAfter(() -> this.pose.flip(this.flippedParts));
    }

    private void restoreSelectionAfter(Runnable action)
    {
        List<String> current = new ArrayList<>(this.groups.getCurrent());

        action.run();
        this.groups.setCurrent(current);
        this.pickBones(this.groups.getCurrent());
    }

    public void setPose(Pose pose, String group)
    {
        this.pose = pose;
        this.group = group;
    }

    public void fillGroups(Collection<String> groups, boolean reset)
    {
        this.model = null;
        this.flippedParts = null;

        this.fillInGroups(groups, reset, true);
    }

    public void fillGroups(IModel model, Map<String, String> flippedParts, boolean reset)
    {
        this.fillGroups(model, flippedParts, reset, null);
    }

    public void fillGroups(IModel model, Map<String, String> flippedParts, boolean reset, Collection<String> disabledBones)
    {
        this.model = model;
        this.flippedParts = flippedParts;

        if (model == null)
        {
            this.fillInGroups(Collections.emptyList(), reset, false);
            return;
        }

        List<String> bones = new ArrayList<>(model.getGroupKeysInHierarchyOrder());
        if (disabledBones != null && !disabledBones.isEmpty())
        {
            bones.removeIf(disabledBones::contains);
        }
        this.fillInGroups(bones, reset, false);
    }

    private void fillInGroups(Collection<String> groups, boolean reset, boolean sort)
    {
        this.groups.clear();
        this.groups.add(groups);
        if (sort)
        {
            this.groups.sort();
        }

        this.fix.setVisible(!groups.isEmpty());
        this.color.setVisible(!groups.isEmpty());
        this.transform.setVisible(!groups.isEmpty());

        List<String> list = this.groups.getList();
        int i = Math.max(reset ? 0 : list.indexOf(lastLimb), 0);

        this.groups.setCurrentScroll(CollectionUtils.getSafe(list, i));
        this.pickBones(this.groups.getCurrent());
    }

    public void selectBone(String bone)
    {
        lastLimb = bone;

        this.groups.setCurrentScroll(bone);
        this.pickBones(this.groups.getCurrent());
    }

    /* Subclass overridable methods */

    protected UIPropTransform createTransformEditor()
    {
        return new UIPosePropTransform();
    }

    /**
     * Applies transform edits from the primary bone to the rest of the multi-selection.
     */
    private class UIPosePropTransform extends UIPropTransform
    {
        UIPosePropTransform()
        {
            this.enableHotkeys();
        }

        @Override
        public void rejectChanges()
        {
            UIPoseEditor.this.beginSuppressPoseSync();

            try
            {
                super.rejectChanges();
            }
            finally
            {
                UIPoseEditor.this.endSuppressPoseSync();
            }

            UIPoseEditor.this.syncPoseTransformToSelection();
        }

        @Override
        public void setT(Axis axis, double x, double y, double z)
        {
            super.setT(axis, x, y, z);
            UIPoseEditor.this.syncPoseTransformToSelection();
        }

        @Override
        public void setS(Axis axis, double x, double y, double z)
        {
            super.setS(axis, x, y, z);
            UIPoseEditor.this.syncPoseTransformToSelection();
        }

        @Override
        public void setR(Axis axis, double x, double y, double z)
        {
            super.setR(axis, x, y, z);
            UIPoseEditor.this.syncPoseTransformToSelection();
        }
    }

    protected void pickBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            this.pickBones(Collections.emptyList());
            return;
        }

        this.pickBones(Collections.singletonList(bone));
    }

    protected void pickBones(List<String> bones)
    {
        if (bones == null || bones.isEmpty())
        {
            lastLimb = "";
            this.fix.setValue(0F);
            this.color.setColor(Colors.WHITE);
            this.lighting.setValue(false);
            this.transform.setTransform(null);

            return;
        }

        String primary = bones.get(0);

        lastLimb = primary;

        PoseTransform poseTransform = this.pose.get(primary);

        this.fix.setValue(poseTransform.fix);
        this.color.setColor(poseTransform.color.getARGBColor());
        this.lighting.setValue(poseTransform.lighting == 0F);
        this.transform.setTransform(poseTransform);
    }

    private void syncPoseTransformToSelection()
    {
        if (this.suppressPoseSync > 0)
        {
            return;
        }

        List<String> bones = this.groups.getCurrent();

        if (bones.size() <= 1)
        {
            return;
        }

        if (!(this.transform.getTransform() instanceof PoseTransform primary))
        {
            return;
        }

        for (String bone : bones)
        {
            PoseTransform pt = this.pose.get(bone);

            if (pt != primary)
            {
                pt.copy(primary);
            }
        }
    }

    private void forEachSelectedPose(Consumer<PoseTransform> consumer)
    {
        for (String bone : this.groups.getCurrent())
        {
            consumer.accept(this.pose.get(bone));
        }
    }

    private void applyFixToSelection(float value)
    {
        this.forEachSelectedPose((pt) -> this.setFix(pt, value));
        this.fix.setValue(value);
    }

    private void applyColorToSelection(int argb)
    {
        this.forEachSelectedPose((pt) -> this.setColor(pt, argb));
        this.color.setColor(argb);
    }

    private void applyLightingToSelection(boolean value)
    {
        this.forEachSelectedPose((pt) -> this.setLighting(pt, value));
        this.lighting.setValue(value);
    }

    private void toggleFix()
    {
        if (this.groups.getCurrent().isEmpty())
        {
            return;
        }

        float next = this.fix.getValue() >= 0.5F ? 0F : 1F;

        this.applyFixToSelection(next);
    }

    protected void setFix(PoseTransform transform, float value)
    {
        transform.fix = value;
    }

    protected void setColor(PoseTransform transform, int value)
    {
        transform.color.set(value);
    }

    protected void setLighting(PoseTransform poseTransform, boolean value)
    {
        poseTransform.lighting = value ? 0F : 1F;
    }
}
