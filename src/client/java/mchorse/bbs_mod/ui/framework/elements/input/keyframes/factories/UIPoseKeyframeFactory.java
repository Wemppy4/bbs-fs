package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UIPoseKeyframeFactory extends UIKeyframeFactory<Pose>
{
    public UIPoseFactoryEditor poseEditor;

    /* Which arrangement the fields are in (null until the first layout), so a resize
     * that stays on the same side of the threshold doesn't rebuild the subtree */
    private Boolean wide;

    public UIPoseKeyframeFactory(Keyframe<Pose> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.poseEditor = new UIPoseFactoryEditor(editor, keyframe);

        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

        if (FormUtils.getForm(sheet.property) instanceof ModelForm modelForm)
        {
            ModelInstance model = ((ModelFormRenderer) FormUtilsClient.getRenderer(modelForm)).getModel();

            if (model != null)
            {
                this.poseEditor.setPose(keyframe.getValue(), model.getPoseGroup());
                this.poseEditor.fillGroups(model.model, model.getFlippedParts(), false, model.getDisabledBones());
            }
        }
        else if (FormUtils.getForm(sheet.property) instanceof MobForm mobForm)
        {
            List<String> bones = FormUtilsClient.getRenderer(mobForm).getBones();

            this.poseEditor.setPose(keyframe.getValue(), "");
            this.poseEditor.fillGroups(bones, false);
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

    public static class UIPoseFactoryEditor extends UIPoseEditor
    {
        private UIKeyframes editor;
        private Keyframe<Pose> keyframe;

        public static void apply(UIKeyframes editor, Keyframe keyframe, Consumer<Pose> consumer)
        {
            UIReplaysEditorUtils.forEachSelectedKeyframe(editor, keyframe, (selected) ->
            {
                Pose pose = (Pose) selected.getValue();

                selected.preNotify();
                consumer.accept(pose);
                selected.postNotify();
            });
        }

        public static void apply(UIKeyframes editor, Keyframe keyframe, String group, Consumer<PoseTransform> consumer)
        {
            apply(editor, keyframe, (pose) -> consumer.accept(pose.getOrCreate(group)));
        }

        /**
         * Applies the consumer to each named bone on every selected keyframe pose (one keyframe notify round).
         */
        public static void apply(UIKeyframes editor, Keyframe keyframe, List<String> boneNames, Consumer<PoseTransform> consumer)
        {
            if (boneNames == null || boneNames.isEmpty())
            {
                return;
            }

            apply(editor, keyframe, (pose) ->
            {
                for (String bone : boneNames)
                {
                    consumer.accept(pose.getOrCreate(bone));
                }
            });
        }

        /**
         * Like {@link #apply(UIKeyframes, Keyframe, List, Consumer)} but hands the bone
         * name alongside its {@link PoseTransform}, so callers can decide per bone (e.g.
         * mirror editing via {@link UIPoseEditor#applyToBone}).
         */
        public static void applyBones(UIKeyframes editor, Keyframe keyframe, List<String> boneNames, BiConsumer<String, PoseTransform> consumer)
        {
            if (boneNames == null || boneNames.isEmpty())
            {
                return;
            }

            apply(editor, keyframe, (pose) ->
            {
                for (String bone : boneNames)
                {
                    consumer.accept(bone, pose.getOrCreate(bone));
                }
            });
        }

        public UIPoseFactoryEditor(UIKeyframes editor, Keyframe<Pose> keyframe)
        {
            super();

            this.editor = editor;
            this.keyframe = keyframe;

            /* This popup is short and the user resizes it, so the list asks for less than the form
             * editor's does — it expands into the leftover anyway, and this is the floor it hits
             * when the fields alone already fill the popup. */
            this.groups.list.h(UIStringList.DEFAULT_HEIGHT * 4);

            ((UIPoseTransforms) this.transform).setKeyframe(this);
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

            apply(this.editor, this.keyframe, (pose) -> pose.fromData(data));
            this.groups.list.setCurrent(current);
            this.pickBones(this.groups.list.getCurrent());
        }

        @Override
        protected void flipPose()
        {
            List<String> current = new ArrayList<>(this.groups.list.getCurrent());

            apply(this.editor, this.keyframe, (pose) -> pose.flip(this.flippedParts));
            this.groups.list.setCurrent(current);
            this.pickBones(this.groups.list.getCurrent());
        }

        @Override
        protected void setFix(PoseTransform transform, float value)
        {
            apply(this.editor, this.keyframe, this.getGroup(transform), (poseT) -> poseT.fix = value);
        }

        @Override
        protected void setColor(PoseTransform transform, int value)
        {
            apply(this.editor, this.keyframe, this.getGroup(transform), (poseT) -> poseT.color.set(value));
        }

        @Override
        protected void setLighting(PoseTransform poseTransform, float value)
        {
            apply(this.editor, this.keyframe, this.getGroup(poseTransform), (poseT) -> poseT.lighting = value);
        }

        @Override
        protected void setOverlay(PoseTransform poseTransform, int value)
        {
            apply(this.editor, this.keyframe, this.getGroup(poseTransform), (poseT) -> poseT.overlay.set(value));
        }
    }

    public static class UIPoseTransforms extends UIKeyframePropTransform
    {
        private UIPoseFactoryEditor editor;

        public void setKeyframe(UIPoseFactoryEditor editor)
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

            UIPoseFactoryEditor.applyBones(this.editor.editor, this.editor.keyframe, new ArrayList<>(targets.keySet()),
                (bone, poseT) -> this.editor.applyToBone(targets.get(bone), poseT, consumer));
        }

        @Override
        protected UIKeyframes getKeyframes()
        {
            return this.editor.editor;
        }

        @Override
        protected Transform getAutoKeyTransform(int tick)
        {
            UIKeyframeSheet sheet = this.editor.editor.getGraph().getSheet(this.editor.keyframe);
            Keyframe<Pose> target = sheet == null ? null : sheet.ensureKeyframe(tick);
            String bone = this.editor.getGroup();

            if (target == null || bone == null)
            {
                return null;
            }

            return target.getValue().getOrCreate(bone);
        }

        @Override
        protected void reset()
        {
            UIPoseFactoryEditor.apply(this.editor.editor, this.editor.keyframe, this.editor.groups.list.getCurrent(), (poseT) ->
            {
                poseT.translate.set(0F, 0F, 0F);
                poseT.scale.set(1F, 1F, 1F);
                poseT.resetRotation();
            });
            this.refillTransform();
        }

        @Override
        public void endGesture()
        {
            /* Film pose edits land on the selected keyframe(s) (not via a notifier callback),
             * so seal those to close the undo block — consecutive drags stay distinct. */
            UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor.editor, this.editor.keyframe,
                (selected) -> selected.preNotify(IValueListener.FLAG_UNMERGEABLE));
        }
    }
}
