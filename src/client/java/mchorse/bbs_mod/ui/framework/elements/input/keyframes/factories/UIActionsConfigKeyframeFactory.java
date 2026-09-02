package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIActionsConfigKeyframeFactory extends UIKeyframeFactory<ActionsConfig>
{
    public UIActionsConfigEditor actionsEditor;

    /* Which arrangement the fields are in (null until the first layout), so a resize
     * that stays on the same side of the threshold doesn't rebuild the subtree */
    private Boolean wide;

    public UIActionsConfigKeyframeFactory(Keyframe<ActionsConfig> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        ModelForm form = (ModelForm) FormUtils.getForm(editor.getGraph().getSheet(keyframe).property);
        ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(form);

        this.actionsEditor = new UIActionsConfigEditor(() ->
        {
            this.keyframe.preNotify();
        }, () ->
        {
            renderer.resetAnimator();
            this.keyframe.postNotify();
        });
        this.actionsEditor.setConfigs(keyframe.getValue(), form);

        this.scroll.add(this.actionsEditor);
    }

    @Override
    public void resize()
    {
        boolean wide = this.getFlex().getW() > 240;

        if (this.wide == null || this.wide != wide)
        {
            this.wide = wide;
            this.rebuild(wide);
        }

        super.resize();
    }

    private void rebuild(boolean wide)
    {
        this.actionsEditor.removeAll();

        if (wide)
        {
            this.actionsEditor.add(UI.row(
                UI.column(
                    UI.label(UIKeys.FORMS_EDITORS_MODEL_ACTIONS), this.actionsEditor.actions,
                    UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_SPEED, this.actionsEditor.speed).marginTop(UIConstants.SECTION_GAP),
                    this.actionsEditor.loop.marginTop(UIConstants.SECTION_GAP)
                ),
                UI.column(
                    UI.label(UIKeys.FORMS_EDITORS_ACTIONS_ANIMATIONS), this.actionsEditor.animations,
                    UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_FADE, this.actionsEditor.fade),
                    UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_TICK, this.actionsEditor.tick)
                )
            ));
        }
        else
        {
            this.actionsEditor.add(UI.label(UIKeys.FORMS_EDITORS_MODEL_ACTIONS), this.actionsEditor.actions);
            this.actionsEditor.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_ANIMATIONS).marginTop(UIConstants.SECTION_GAP), this.actionsEditor.animations, this.actionsEditor.loop.marginTop(UIConstants.SECTION_GAP));
            this.actionsEditor.add(UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_SPEED, this.actionsEditor.speed).marginTop(UIConstants.SECTION_GAP));
            this.actionsEditor.add(UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_FADE, this.actionsEditor.fade));
            this.actionsEditor.add(UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_TICK, this.actionsEditor.tick));
        }
    }
}