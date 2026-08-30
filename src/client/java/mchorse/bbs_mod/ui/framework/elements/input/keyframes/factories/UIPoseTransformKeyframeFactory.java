package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.function.Consumer;

public class UIPoseTransformKeyframeFactory extends UIKeyframeFactory<PoseTransform>
{
    public UISliderTrackpad fix;
    public UIColor color;
    public UIColor overlay;
    public UISliderTrackpad lighting;
    public UIPropTransform transform;

    public UIPoseTransformKeyframeFactory(Keyframe<PoseTransform> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.transform = new UIPoseTransforms(this);
        this.transform.enableHotkeys();
        this.transform.setTransform(keyframe.getValue());

        this.keys().register(Keys.TRANSFORMATIONS_TOGGLE_FIX, this::toggleFix).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);

        this.fix = new UISliderTrackpad((v) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(editor, keyframe, (poseT) -> poseT.fix = v.floatValue());
            }
        });
        this.fix.limit(0D, 1D).increment(0.1D).values(0.1, 0.05D, 0.2D);
        this.fix.tooltip(UIKeys.POSE_CONTEXT_FIX_TOOLTIP);
        this.fix.setValue(keyframe.getValue().fix);

        this.color = new UIColor((c) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(editor, keyframe, (poseT) -> poseT.color.set(c));
            }
        });
        this.color.withAlpha();
        this.color.setColor(keyframe.getValue().color.getARGBColor());

        this.overlay = new UIColor((c) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(editor, keyframe, (poseT) -> poseT.overlay.set(c));
            }
        });
        this.overlay.withAlpha();
        this.overlay.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_OVERLAY_TOOLTIP);
        this.overlay.setColor(keyframe.getValue().overlay.getARGBColor());

        /* A 0..1 slider, like every other bone panel — this used to be a toggle writing 1F/0F
         * inverted, which was the only place where glow wasn't a value you could dial in. */
        this.lighting = new UISliderTrackpad((v) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(editor, keyframe, (poseT) -> poseT.lighting = v.floatValue());
            }
        });
        this.lighting.limit(0D, 1D);
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_GLOW_TOOLTIP);
        this.lighting.setValue(keyframe.getValue().lighting);

        /* Same rows in the same order, and the material section built by UIPoseEditor itself —
         * this panel is that one without the bone list, so it has to read as the same panel. */
        this.scroll.add(
            UI.labelRow(UIKeys.POSE_CONTEXT_FIX, this.fix),
            this.transform,
            UIPoseEditor.materialSection(this.color, this.overlay, this.lighting)
        );
    }

    private void toggleFix()
    {
        if (!(this.transform.getTransform() instanceof PoseTransform))
        {
            return;
        }
        float next = this.fix.getValue() >= 0.5F ? 0F : 1F;
        this.fix.setValue(next);
        UIPoseTransforms.apply(this.editor, this.keyframe, (poseT) -> poseT.fix = next);
    }

    public static class UIPoseTransforms extends UIKeyframePropTransform
    {
        private UIPoseTransformKeyframeFactory editor;

        public UIPoseTransforms(UIPoseTransformKeyframeFactory editor)
        {
            this.editor = editor;
        }

        @Override
        protected UIKeyframes getKeyframes()
        {
            return this.editor.editor;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            apply(this.editor.editor, this.editor.keyframe, (poseT) -> consumer.accept(poseT));
        }

        @Override
        protected Transform getAutoKeyTransform(int tick)
        {
            UIKeyframeSheet sheet = this.editor.editor.getGraph().getSheet(this.editor.keyframe);
            Keyframe<PoseTransform> target = sheet == null ? null : sheet.ensureKeyframe(tick);

            return target == null ? null : target.getValue();
        }

        public static void apply(UIKeyframes editor, Keyframe keyframe, Consumer<PoseTransform> consumer)
        {
            UIReplaysEditorUtils.forEachSelectedKeyframe(editor, keyframe, (selected) ->
            {
                PoseTransform transform = (PoseTransform) selected.getValue();

                selected.preNotify();
                consumer.accept(transform);
                selected.postNotify();
            });
        }
    }
}
