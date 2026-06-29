package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Model Editor — a proper data panel (tabs, right icon bar, save) over models. Each tab is an open model;
 * the picker in the icon bar chooses one. The editor area is split into a resizable settings pane on the
 * left (binding straight to the live model's {@link ModelConfig}, so edits show in the preview at once)
 * and the orbit preview on the right. Models are assets, so create/rename/delete are intentionally off.
 */
public class UIModelEditorPanel extends UIDataDashboardPanel<ModelConfig>
{
    public UIScrollView general;
    public UIFormRenderer renderer;
    public UIDraggable splitter;

    private final ModelForm form = new ModelForm();

    /** The model id whose instance we're waiting on; models load asynchronously, so the fill is deferred. */
    private String pendingId;
    private int splitWidth = 220;

    public UIModelEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.enableTabs();

        this.general = new UIScrollView(ScrollDirection.VERTICAL);
        this.general.column().scroll().vertical().stretch().padding(6).height(20);

        this.renderer = new UIFormRenderer();
        this.renderer.form = this.form;

        this.splitter = new UIDraggable((context) ->
        {
            this.splitWidth = MathUtils.clamp(context.mouseX - this.editor.area.x, 160, this.editor.area.w - 160);
            this.layoutPanes();
            this.resize();
        }).cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);

        this.layoutPanes();

        this.editor.add(this.general, this.renderer, this.splitter);

        UIIcon pick = new UIIcon(Icons.SEARCH, (b) -> this.openPicker());

        pick.tooltip(UIKeys.FORMS_EDITOR_MODEL_PICK_MODEL, Direction.LEFT);
        this.iconBar.add(pick);
    }

    private void layoutPanes()
    {
        this.general.relative(this.editor).x(0).y(0).w(this.splitWidth).h(1F);
        this.splitter.relative(this.editor).x(this.splitWidth).y(0).w(4).h(1F);
        this.renderer.relative(this.editor).x(this.splitWidth + 4).y(0).w(1F, -(this.splitWidth + 4)).h(1F);
    }

    @Override
    public ContentType getType()
    {
        return ContentType.MODELS;
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.MODEL_EDITOR_TITLE;
    }

    @Override
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return false;
    }

    @Override
    public void requestData(String id)
    {
        this.pendingId = id;
        this.tryLoadPending();
    }

    @Override
    public void update()
    {
        super.update();

        this.tryLoadPending();
    }

    private void tryLoadPending()
    {
        if (this.pendingId == null)
        {
            return;
        }

        ModelInstance instance = BBSModClient.getModels().getModel(this.pendingId);

        if (instance != null)
        {
            this.form.model.set(this.pendingId);
            this.pendingId = null;
            this.fill(instance.config);
        }
    }

    @Override
    public void fill(ModelConfig data)
    {
        super.fill(data);

        /* Models are assets — duplicating, renaming or deleting them isn't supported here. */
        this.overlay.dupe.setEnabled(false);
        this.overlay.rename.setEnabled(false);
        this.overlay.remove.setEnabled(false);
    }

    @Override
    protected void fillData(ModelConfig data)
    {
        this.rebuildGeneral(data);
    }

    @Override
    public void appear()
    {
        super.appear();

        if (this.data == null && this.pendingId == null)
        {
            List<String> keys = BBSModClient.getModels().getAvailableKeys();

            keys.sort(String::compareToIgnoreCase);

            if (!keys.isEmpty())
            {
                this.pickData(keys.get(0));
            }
        }
    }

    private void openPicker()
    {
        UIListOverlayPanel picker = new UIListOverlayPanel(UIKeys.FORMS_EDITOR_MODEL_MODELS, this::pickData);

        picker.addValues(BBSModClient.getModels().getAvailableKeys());
        picker.list.list.sort();
        picker.setValue(this.form.model.get());

        UIOverlay.addOverlay(this.getContext(), picker);
    }

    private void rebuildGeneral(ModelConfig config)
    {
        this.general.removeAll();

        if (config != null)
        {
            this.general.add(
                this.toggle(UIKeys.MODEL_EDITOR_PROCEDURAL, config.procedural),
                this.toggle(UIKeys.MODEL_EDITOR_CULLING, config.culling),
                this.toggle(UIKeys.MODEL_EDITOR_ON_CPU, config.onCpu),
                this.row(UIKeys.MODEL_EDITOR_UI_SCALE, this.floatField(config.uiScale)),
                this.row(UIKeys.MODEL_EDITOR_SCALE, this.vectorField(config.scale)),
                this.row(UIKeys.MODEL_EDITOR_POSE_GROUP, this.stringField(config.poseGroup)),
                this.row(UIKeys.MODEL_EDITOR_ANCHOR, this.stringField(config.anchor)),
                this.row(UIKeys.MODEL_EDITOR_TEXTURE, this.textureField(config.texture))
            );
        }

        this.general.resize();
    }

    private UIToggle toggle(IKey label, ValueBoolean value)
    {
        UIToggle toggle = new UIToggle(label, value.get(), (t) -> value.set(t.getValue()));

        toggle.resetFlex();

        return toggle;
    }

    private UIElement row(IKey label, UIElement control)
    {
        UIElement row = new UIElement();

        row.row(0).preferred(0).height(20);
        row.add(UI.label(label).labelAnchor(0, 0.5F), control);

        return row;
    }

    private UITrackpad floatField(ValueFloat value)
    {
        UITrackpad trackpad = new UITrackpad((v) -> value.set(v.floatValue()));

        trackpad.limit(value.getMin(), value.getMax()).delayedInput();
        trackpad.setValue(value.get());
        trackpad.w(100);

        return trackpad;
    }

    private UITextbox stringField(ValueString value)
    {
        UITextbox textbox = new UITextbox(10000, value::set);

        textbox.setText(value.get());
        textbox.w(100);

        return textbox;
    }

    private UIButton textureField(ValueLink value)
    {
        UIButton button = new UIButton(UIKeys.TEXTURE_PICK_TEXTURE, (b) -> UITexturePicker.open(this.getContext(), value.get(), value::set));

        button.w(100);

        return button;
    }

    private UIElement vectorField(ValueVector3f value)
    {
        UIElement fields = new UIElement();

        fields.row(2).height(20);
        fields.w(100);
        fields.add(this.component(value, 0), this.component(value, 1), this.component(value, 2));

        return fields;
    }

    private UITrackpad component(ValueVector3f value, int axis)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            Vector3f vector = value.get();

            if (axis == 0) vector.x = v.floatValue();
            else if (axis == 1) vector.y = v.floatValue();
            else vector.z = v.floatValue();

            value.set(vector);
        });

        Vector3f vector = value.get();

        trackpad.setValue(axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z);
        trackpad.delayedInput();

        return trackpad;
    }
}
