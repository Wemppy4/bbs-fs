package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.spline.SplineSource;
import mchorse.bbs_mod.cubic.spline.SplineArray;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.*;
import mchorse.bbs_mod.ui.framework.elements.input.drag.*;
import mchorse.bbs_mod.ui.utils.*;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.*;
import java.util.List;
import java.util.function.Consumer;

public class UISplineFormPanel extends UIFormPanel<SplineForm> implements SplineFormTool
{
    public final UISplinePointsEditor points;
    private final UIToggle closed;
    private UIElement repeatCount;
    private UIElement repeatDistance;
    private UIIcons repeatMode;
    private int shownRepeatMode = -1;

    public UISplineFormPanel(UIForm editor)
    {
        super(editor);
        UIPropTransform transform = new UIDeltaPropTransform()
        {
            @Override protected void applyToSelection(Consumer<Transform> edit)
            {
                for (String id : UISplineFormPanel.this.points.selected()) edit.accept(UISplineFormPanel.this.form.curve.getOriginalValue().point(id));
            }
        };
        transform.callbacks(() -> this.form.curve.preNotify(), () -> this.form.curve.postNotify(),
            () -> this.form.curve.preNotify(IValueListener.FLAG_UNMERGEABLE));
        transform.hotkeyDrag(() -> this.editor.editor == null ? null : this.editor.editor.buildHotkeyDrag(transform));
        this.points = new UISplinePointsEditor(() -> this.form, id -> this.form.curve.getOriginalValue().point(id), transform, Vector3f::new);
        transform.enableHotkeys(() -> this.editor.view == this && this.points.point() != null, op -> op == TransformOp.TRANSLATE);
        this.closed = new UIToggle(L10n.lang("bbs.ui.spline.closed"), b -> this.form.closed.set(b.getValue()));
        this.options.add(this.points, this.closed, this.createRepeatSection());
    }

    private static IKey repeatKey(String name) { return L10n.lang("bbs.ui.spline.repeat." + name); }

    private UISection createRepeatSection()
    {
        UISection section = this.section(repeatKey("title"), "spline.repeat", false);
        this.repeatMode = new UIIcons(icons ->
        {
            this.form.repeatMode.set(icons.getValue());
            this.updateRepeatMode();
        });
        this.repeatMode.add(Icons.LIST, repeatKey("mode.0"));
        this.repeatMode.add(Icons.HORIZONTAL, repeatKey("mode.1"));
        UIValues.resettable(this.repeatMode, () -> this.form.repeatMode, this::updateRepeatMode);

        UITrackpad count = UIValues.trackpad(() -> this.form.repeatCount);
        count.limit(1, SplineArray.MAX_COPIES, true);
        this.repeatCount = UI.labelRow(repeatKey("count"), count);
        UITrackpad distance = UIValues.trackpad(() -> this.form.repeatDistance);
        distance.limit(0.001, Float.MAX_VALUE, false).tooltip(repeatKey("distance_tip"));
        this.repeatDistance = UI.labelRow(repeatKey("distance"), distance);
        UITrackpad start = UIValues.trackpad(() -> this.form.repeatStart);
        UITrackpad end = UIValues.trackpad(() -> this.form.repeatEnd);
        start.limit(0, 100, false).tooltip(repeatKey("start_tip"));
        end.limit(0, 100, false).tooltip(repeatKey("end_tip"));
        UITrackpad offset = UIValues.trackpad(() -> this.form.repeatOffset);
        offset.tooltip(repeatKey("offset_tip"));
        UIButton rotation = UIPathFields.rotationButton(() -> this.form.repeatRotation.get(), value -> this.form.repeatRotation.set(value));
        UIValues.resettable(rotation, () -> this.form.repeatRotation, null);

        section.fields.add(
            UIValues.toggle(repeatKey("enabled"), () -> this.form.repeatEnabled).tooltip(repeatKey("enabled_tip")),
            this.repeatMode.labelRow(repeatKey("mode")), this.repeatCount, this.repeatDistance,
            rotation,
            UI.labelRow(UIPathFields.key("start"), start),
            UI.labelRow(UIPathFields.key("end"), end),
            UI.labelRow(repeatKey("offset"), offset)
        );
        return section;
    }

    private void updateRepeatMode()
    {
        int mode = this.form.repeatMode.get();
        this.repeatMode.setValue(mode);
        if (mode == this.shownRepeatMode) return;
        this.shownRepeatMode = mode;
        this.repeatCount.setVisible(mode == 0);
        this.repeatDistance.setVisible(mode == 1);
        this.options.invalidateLayout();
    }

    @Override public void render(UIContext context)
    {
        this.updateRepeatMode();
        super.render(context);
    }

    @Override public void startEdit(SplineForm form)
    {
        super.startEdit(form);
        this.points.refresh();
        this.closed.setValue(form.closed.get());
        this.updateRepeatMode();
    }
    @Override public void finishEdit() { this.points.endEdit(); super.finishEdit(); }
    @Override public List<SplineSource> splineSources() { return this.form == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(this.form); }
    @Override public SplineSource activeSpline() { return this.form; }
    @Override public UISplinePointsEditor pointEditor() { return this.points; }
    @Override public void selectSpline(SplineSource source) {}
    @Override public UIPropTransform getGizmoTransform() { return this.points.point() == null ? null : this.points.position; }
    @Override public Matrix4f getGizmoOrigin(float transition, TransformSpace space)
    {
        if (this.getGizmoTransform() == null || this.editor.editor == null) return null;
        Matrix4f matrix = SplineEditorUtils.parentMatrix(FormUtils.getRoot(this.form), this.editor.editor.renderer.getTargetEntity(), transition, this.form, this.form);
        return matrix == null ? null : matrix.translate(this.points.point().position.getOriginalValue().translate);
    }
}
