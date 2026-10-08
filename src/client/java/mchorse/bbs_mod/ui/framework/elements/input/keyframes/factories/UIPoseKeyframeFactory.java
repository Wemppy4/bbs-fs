package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.mob.MobRig;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UIPoseKeyframeFactory extends UIKeyframeFactory<Pose>
{
    @Override
    public Transform getGizmoTransform(Pose value)
    {
        String bone = this.poseEditor.getGroup();
        return bone == null ? null : value.getOrCreate(bone);
    }

    public UIPoseFactoryEditor poseEditor;

    /* Which arrangement the fields are in (null until the first layout), so a resize
     * that stays on the same side of the threshold doesn't rebuild the subtree */
    private Boolean wide;

    public UIPoseKeyframeFactory(UITrackValue<Pose> track, UIKeyframes editor)
    {
        super(track, editor);

        this.poseEditor = new UIPoseFactoryEditor(editor, track);

        UIKeyframeSheet sheet = track.sheet;

        if (FormUtils.getForm(sheet.property) instanceof ModelForm modelForm)
        {
            ModelInstance model = ((ModelFormRenderer) FormUtilsClient.getRenderer(modelForm)).getModel();

            if (model != null)
            {
                this.poseEditor.setPose(track.getValue(), model.getPoseGroup());
                this.poseEditor.fillGroups(model.model, model.getFlippedParts(), false, model.getDisabledBones());
            }
        }
        else if (FormUtils.getForm(sheet.property) instanceof MobForm mobForm)
        {
            MobRig rig = MobFormRenderer.getRig(mobForm);

            this.poseEditor.setPose(track.getValue(), mobForm.mobID.get());

            if (rig == null)
            {
                this.poseEditor.fillGroups(FormUtilsClient.getRenderer(mobForm).getBones(), false);
            }
            else
            {
                /* No flipped-parts table: vanilla part names are already left_/right_, which is
                 * exactly what Pose's own mirror rule matches. */
                this.poseEditor.fillGroups(rig, null, false, null);
            }
        }

        this.scroll.add(this.poseEditor);
    }

    /**
     * Only the choice of arrangement lives here — this popup is the one that knows its own width,
     * since the user resizes it. Building the arrangement is {@link UIPoseEditor}'s own job, so this
     * editor stays identical to the form editor's pose panel instead of drifting from it.
     */
    @Override
    public void resize()
    {
        boolean wide = this.getFlex().getW() > UIPoseEditor.WIDE_WIDTH;

        if (this.wide == null || this.wide != wide)
        {
            this.wide = wide;
            this.poseEditor.buildLayout(wide);

            /* Ew... */
            for (UIElement child : this.scroll.getChildren(UIElement.class))
            {
                child.noCulling();
            }
        }

        super.resize();
    }

    @Override
    public void update() { this.poseEditor.refreshValue(); }

    public static class UIPoseFactoryEditor extends UIPoseEditor
    {
        private UIKeyframes editor;
        private UITrackValue<Pose> track;

        public static void apply(UITrackValue<Pose> track, Consumer<Pose> consumer)
        {
            track.edit(consumer);
        }

        public static void apply(UITrackValue<Pose> track, String group, Consumer<PoseTransform> consumer)
        {
            apply(track, (pose) -> consumer.accept(pose.getOrCreate(group)));
        }

        /**
         * Applies the consumer to each named bone on every selected keyframe pose (one notify round per track).
         */
        public static void apply(UITrackValue<Pose> track, List<String> boneNames, Consumer<PoseTransform> consumer)
        {
            if (boneNames == null || boneNames.isEmpty())
            {
                return;
            }

            apply(track, (pose) ->
            {
                for (String bone : boneNames)
                {
                    consumer.accept(pose.getOrCreate(bone));
                }
            });
        }

        /**
         * Like {@link #apply(UITrackValue, List, Consumer)} but hands the bone
         * name alongside its {@link PoseTransform}, so callers can decide per bone (e.g.
         * mirror editing via {@link UIPoseEditor#applyToBone}).
         */
        public static void applyBones(UITrackValue<Pose> track, List<String> boneNames, BiConsumer<String, PoseTransform> consumer)
        {
            if (boneNames == null || boneNames.isEmpty())
            {
                return;
            }

            apply(track, (pose) ->
            {
                for (String bone : boneNames)
                {
                    consumer.accept(bone, pose.getOrCreate(bone));
                }
            });
        }

        public UIPoseFactoryEditor(UIKeyframes editor, UITrackValue<Pose> track)
        {
            super();

            this.editor = editor;
            this.track = track;

            /* This popup is short and the user resizes it, so the list asks for less than the form
             * editor's does — it expands into the leftover anyway, and this is the floor it hits
             * when the fields alone already fill the popup. */
            this.groups.list.h(UIStringList.DEFAULT_HEIGHT * 4);

            ((UIPoseTransforms) this.transform).setEditor(this);
        }

        /**
         * This editor is shown in a popup, which is not under the film editor in the widget tree —
         * the timeline that spawned it is, so the bone selection is looked up from there.
         */
        @Override
        protected UIElement selectionAnchor()
        {
            return this.editor;
        }

        public void refreshValue()
        {
            if (this.transform.isUserEditing() || this.fix.isUserEditing() || this.lighting.isUserEditing()) return;
            this.refreshPose(this.track.getValue());
        }

        private String getGroup(PoseTransform transform)
        {
            return CollectionUtils.getKey(this.getPose().transforms, transform);
        }

        @Override
        protected UIPropTransform createTransformEditor()
        {
            return new UIPoseTransforms().enableHotkeys();
        }

        @Override
        protected void pastePose(MapType data)
        {
            List<String> current = new ArrayList<>(this.groups.list.getCurrent());

            apply(this.track, (pose) -> pose.fromData(data));
            this.groups.list.setCurrent(current);
            this.pickBones(this.groups.list.getCurrent());
        }

        @Override
        protected void flipPose()
        {
            List<String> current = new ArrayList<>(this.groups.list.getCurrent());

            apply(this.track, (pose) -> pose.flip(this.flippedParts));
            this.groups.list.setCurrent(current);
            this.pickBones(this.groups.list.getCurrent());
        }

        @Override
        protected void setFix(PoseTransform transform, float value)
        {
            apply(this.track, this.getGroup(transform), (poseT) -> poseT.fix = value);
        }

        @Override
        protected void setBoneVisible(PoseTransform transform, boolean value)
        {
            apply(this.track, this.getGroup(transform), (poseT) -> poseT.visible = value);
        }

        @Override
        protected void setColor(PoseTransform transform, int value)
        {
            apply(this.track, this.getGroup(transform), (poseT) -> poseT.color.set(value));
        }

        @Override
        protected void setLighting(PoseTransform poseTransform, float value)
        {
            apply(this.track, this.getGroup(poseTransform), (poseT) -> poseT.lighting = value);
        }

        @Override
        protected void setOverlay(PoseTransform poseTransform, int value)
        {
            apply(this.track, this.getGroup(poseTransform), (poseT) -> poseT.overlay.set(value));
        }
    }

    public static class UIPoseTransforms extends UIKeyframePropTransform
    {
        private UIPoseFactoryEditor editor;

        public void setEditor(UIPoseFactoryEditor editor)
        {
            this.editor = editor;
        }

        @Override
        protected boolean supportsMirror()
        {
            return true;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            Map<String, UIPoseEditor.BoneEdit> targets = this.editor.resolveBoneEdits(this.isMirrorEdit(), this.isAlternateInvert());

            UIPoseFactoryEditor.applyBones(this.editor.track, new ArrayList<>(targets.keySet()),
                (bone, poseT) -> this.editor.applyToBone(targets.get(bone), poseT, consumer));
        }

        @Override
        protected UIKeyframes getKeyframes()
        {
            return this.editor.editor;
        }

        @Override
        protected void reset()
        {
            this.applyToTarget((poseT) ->
            {
                poseT.translate.set(0F, 0F, 0F);
                poseT.scale.set(1F, 1F, 1F);
                poseT.resetRotation();
            });
            this.refillTransform();
        }

    }
}
