package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;

import java.util.List;

/**
 * Model Editor — browse every available model and preview it. Picking a model from the list on the
 * left shows it in the orbit preview on the right (drag to rotate, scroll to zoom). Editing the
 * model's {@code config.json} through panels is the next slice.
 */
public class UIModelEditorPanel extends UIDashboardPanel
{
    public UIStringList models;
    public UIFormRenderer renderer;

    private final ModelForm form = new ModelForm();

    public UIModelEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.models = new UIStringList((selected) ->
        {
            if (!selected.isEmpty())
            {
                this.pickModel(selected.get(0));
            }
        });
        this.models.background();
        this.models.relative(this).w(140).h(1F);

        this.renderer = new UIFormRenderer();
        this.renderer.form = this.form;
        this.renderer.relative(this).x(140).w(1F, -140).h(1F);

        this.add(this.models, this.renderer);
    }

    private void pickModel(String id)
    {
        this.form.model.set(id);
    }

    @Override
    public void appear()
    {
        super.appear();

        this.refresh();
    }

    public void refresh()
    {
        List<String> keys = BBSModClient.getModels().getAvailableKeys();

        this.models.setList(keys);
        this.models.sort();

        if (this.form.model.get().isEmpty() && !keys.isEmpty())
        {
            String first = keys.get(0);

            this.models.setCurrent(first);
            this.pickModel(first);
        }
    }
}
