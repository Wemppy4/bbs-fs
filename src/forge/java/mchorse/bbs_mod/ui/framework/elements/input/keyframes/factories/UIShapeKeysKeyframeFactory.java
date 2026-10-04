package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.shapes.UIShapeKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;

import java.util.Set;

public class UIShapeKeysKeyframeFactory extends UIKeyframeFactory<ShapeKeys>
{
    private UIShapeKeys shapeKeys;
    private ShapeKeys displayed;
    private ModelInstance displayedModel;
    public final ModelForm form;
    public final mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers controls;

    public void beginControllerGesture() { this.editor.beginValueGesture(); }
    public void endControllerGesture() { this.editor.endValueGesture(); }

    public UIShapeKeysKeyframeFactory(UITrackValue<ShapeKeys> track, UIKeyframes editor)
    {
        super(track, editor);

        UIKeyframeSheet sheet = track.sheet;
        this.form = (ModelForm) FormUtils.getForm(sheet.property);
        ModelInstance model = ((ModelFormRenderer) FormUtilsClient.getRenderer(form)).getModel();
        this.displayedModel = model;
        Set<String> shapeKeys = model == null ? java.util.Collections.emptySet() : model.model.getShapeKeys();
        this.displayed = track.getValue();
        this.controls = new mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers(edit ->
        {
            this.track.edit(edit);
            this.displayed = this.getDisplayValue();
            this.controlsRefresh();
        });
        this.controls.fill(model == null ? java.util.Collections.emptyList() : model.config.shapeControllers.getAllTyped(), this.displayed);
        this.controls.boundary(this::endControllerGesture);
        this.controls.presets(() -> this.displayedModel == null ? "" : this.displayedModel.getPoseGroup());
        if (model != null && !model.config.shapeControllers.getAllTyped().isEmpty()) this.scroll.add(this.controls);

        this.shapeKeys = new UIShapeKeysEditor(this);

        if (!shapeKeys.isEmpty())
        {
            this.displayed = track.getValue();
            this.shapeKeys.setShapeKeys(model.getPoseGroup(), shapeKeys, this.displayed);
            this.scroll.add(this.shapeKeys);
        }
    }

    private void controlsRefresh() { this.controls.refresh(this.displayed); }

    @Override
    public void update()
    {
        ModelInstance model = ModelFormRenderer.getModel(this.form);
        if (model != this.displayedModel)
        {
            this.endControllerGesture();
            this.displayedModel = model;
            this.controls.fill(model == null ? java.util.Collections.emptyList() : model.config.shapeControllers.getAllTyped(), this.displayed);
            this.shapeKeys.setShapeKeys(model == null ? "" : model.getPoseGroup(), model == null ? java.util.Collections.emptySet() : model.model.getShapeKeys(), this.displayed);
            this.controls.removeFromParent();
            this.shapeKeys.removeFromParent();
            if (model != null && !model.config.shapeControllers.getAllTyped().isEmpty()) this.scroll.add(this.controls);
            if (model != null && !model.model.getShapeKeys().isEmpty()) this.scroll.add(this.shapeKeys);
            this.scroll.resize();
        }
        if (this.displayed != null && !this.shapeKeys.value.isUserEditing() && !this.controls.isEditing())
        {
            this.displayed = this.getDisplayValue();
            this.shapeKeys.refreshValue(this.displayed);
            this.controlsRefresh();
        }
    }

    public static class UIShapeKeysEditor extends UIShapeKeys
    {
        private UIShapeKeysKeyframeFactory editor;

        public UIShapeKeysEditor(UIShapeKeysKeyframeFactory editor)
        {
            this.editor = editor;
        }

        @Override
        protected void changedShapeKeys(Runnable runnable)
        {
            super.changedShapeKeys(runnable);
            this.editor.track.setValue(this.editor.displayed);
        }

        @Override
        protected void setValue(float v)
        {
            this.editor.track.edit(value ->
            {
                for (String key : this.list.getCurrent()) value.shapeKeys.put(key, v);
            });
        }
    }
}
