package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIPanelBase;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.List;

/**
 * Model Editor — browse every available model, preview it, and edit its {@code config.json}. The model
 * is chosen through the shared model picker; its config is split across section tabs (General, and the
 * richer ones to come). Editing geometry is a far-future slice.
 */
public class UIModelEditorPanel extends UIDashboardPanel
{
    public static final int SIDEBAR_WIDTH = 180;

    public UIFormRenderer renderer;
    public UIButton pick;
    public UIPanelBase<UIElement> sections;
    public UIScrollView general;

    private final ModelForm form = new ModelForm();

    public UIModelEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.renderer = new UIFormRenderer();
        this.renderer.form = this.form;
        this.renderer.relative(this).w(1F, -SIDEBAR_WIDTH).h(1F);

        this.pick = new UIButton(UIKeys.FORMS_EDITOR_MODEL_PICK_MODEL, (b) -> this.openPicker());
        this.pick.relative(this).x(1F, -SIDEBAR_WIDTH).y(0).w(SIDEBAR_WIDTH).h(20);

        this.general = new UIScrollView(ScrollDirection.VERTICAL);
        this.general.column().scroll().vertical().stretch().padding(6).height(20);

        this.sections = new UIPanelBase<>(Direction.TOP);
        this.sections.relative(this).x(1F, -SIDEBAR_WIDTH).y(20).w(SIDEBAR_WIDTH).h(1F, -20);
        this.sections.registerPanel(this.general, UIKeys.FORMS_EDITORS_GENERAL, Icons.GEAR);
        this.sections.setPanel(this.general);

        this.add(this.renderer, this.pick, this.sections);
    }

    private void openPicker()
    {
        UIListOverlayPanel picker = new UIListOverlayPanel(UIKeys.FORMS_EDITOR_MODEL_MODELS, this::setModel);

        picker.addValues(BBSModClient.getModels().getAvailableKeys());
        picker.list.list.sort();
        picker.setValue(this.form.model.get());

        UIOverlay.addOverlay(this.getContext(), picker);
    }

    private void setModel(String id)
    {
        this.form.model.set(id);
    }

    @Override
    public void appear()
    {
        super.appear();

        if (this.form.model.get().isEmpty())
        {
            List<String> keys = BBSModClient.getModels().getAvailableKeys();

            keys.sort(String::compareToIgnoreCase);

            if (!keys.isEmpty())
            {
                this.setModel(keys.get(0));
            }
        }
    }
}
