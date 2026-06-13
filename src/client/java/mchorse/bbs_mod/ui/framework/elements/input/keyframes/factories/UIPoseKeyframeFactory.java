package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
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
import java.util.function.Consumer;

public class UIPoseKeyframeFactory extends UIKeyframeFactory<Pose>
{
    public UIPoseFactoryEditor poseEditor;

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
                this.poseEditor.setPose(keyframe.getValue(), model.poseGroup);
                this.poseEditor.fillGroups(model.model, model.flippedParts, false, model.disabledBones);
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

    @Override
    public void resize()
    {
        this.poseEditor.removeAll();

        if (this.getFlex().getW() > 240)
        {
            this.poseEditor.add(UI.row(
                UI.column(UI.label(UIKeys.POSE_CONTEXT_FIX), this.poseEditor.fix, UI.row(this.poseEditor.color, this.poseEditor.lighting), this.poseEditor.transform),
                UI.column(UI.label(UIKeys.FORMS_EDITOR_BONE), this.poseEditor.groups)
            ));
        }
        else
        {
            this.poseEditor.add(UI.label(UIKeys.FORMS_EDITOR_BONE), this.poseEditor.groups, UI.label(UIKeys.POSE_CONTEXT_FIX), this.poseEditor.fix, UI.row(this.poseEditor.color, this.poseEditor.lighting), this.poseEditor.transform);
        }

        /* Ew... */
        for (UIElement child : this.scroll.getChildren(UIElement.class))
        {
            child.noCulling();
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
            apply(editor, keyframe, (pose) -> consumer.accept(pose.get(group)));
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
                    consumer.accept(pose.get(bone));
                }
            });
        }

        public UIPoseFactoryEditor(UIKeyframes editor, Keyframe<Pose> keyframe)
        {
            super();

            this.editor = editor;
            this.keyframe = keyframe;

            ((UIPoseTransforms) this.transform).setKeyframe(this);
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
            List<String> current = new ArrayList<>(this.groups.getCurrent());

            apply(this.editor, this.keyframe, (pose) -> pose.fromData(data));
            this.groups.setCurrent(current);
            this.pickBones(this.groups.getCurrent());
        }

        @Override
        protected void flipPose()
        {
            List<String> current = new ArrayList<>(this.groups.getCurrent());

            apply(this.editor, this.keyframe, (pose) -> pose.flip(this.flippedParts));
            this.groups.setCurrent(current);
            this.pickBones(this.groups.getCurrent());
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
        protected void setLighting(PoseTransform poseTransform, boolean value)
        {
            apply(this.editor, this.keyframe, this.getGroup(poseTransform), (poseT) -> poseT.lighting = value ? 0F : 1F);
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
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            UIPoseFactoryEditor.apply(this.editor.editor, this.editor.keyframe, this.editor.groups.getCurrent(), (poseT) -> consumer.accept(poseT));
        }

        @Override
        protected void applyDuringRecording(int tick, Consumer<Transform> consumer)
        {
            applyRecording(this.editor.editor, this.editor.keyframe, tick, this.editor.groups.getCurrent(), (poseT) -> consumer.accept(poseT));
        }

        @Override
        protected Transform getRecordedTransform(int tick)
        {
            UIKeyframeSheet sheet = this.editor.editor.getGraph().getSheet(this.editor.keyframe);
            Keyframe<Pose> recorded = UIReplaysEditorUtils.ensureKeyframe(sheet, tick);
            String bone = this.editor.getGroup();

            if (recorded == null || bone == null)
            {
                return null;
            }

            return recorded.getValue().get(bone);
        }

        public static void applyRecording(UIKeyframes editor, Keyframe keyframe, int tick, List<String> bones, Consumer<PoseTransform> consumer)
        {
            if (bones == null || bones.isEmpty())
            {
                return;
            }

            UIReplaysEditorUtils.forEachRecordedKeyframe(editor, keyframe, tick, (recorded) ->
            {
                Pose pose = (Pose) recorded.getValue();
                recorded.preNotify();

                for (String bone : bones)
                {
                    consumer.accept(pose.get(bone));
                }

                recorded.postNotify();
            });
        }

        @Override
        protected void reset()
        {
            UIPoseFactoryEditor.apply(this.editor.editor, this.editor.keyframe, this.editor.groups.getCurrent(), (poseT) ->
            {
                poseT.translate.set(0F, 0F, 0F);
                poseT.scale.set(1F, 1F, 1F);
                poseT.rotate.set(0F, 0F, 0F);
            });
            this.refillTransform();
        }
    }
}
