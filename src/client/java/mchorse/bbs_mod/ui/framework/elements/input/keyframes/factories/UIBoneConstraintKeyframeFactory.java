package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Editor for a bone's constraints keyframe: the rotation limits, laid out the way the form
 * editor's "Constraints" tab lays them out. The value is the bone property's own type, so what
 * this edits is exactly what the form stores statically.
 */
public class UIBoneConstraintKeyframeFactory extends UIKeyframeFactory<BoneConstraint>
{
    public UIToggle limitX;
    public UIToggle limitY;
    public UIToggle limitZ;
    public UISliderTrackpad minX;
    public UISliderTrackpad minY;
    public UISliderTrackpad minZ;
    public UISliderTrackpad maxX;
    public UISliderTrackpad maxY;
    public UISliderTrackpad maxZ;

    /** The angle pairs, shown only for the axes whose switch is on — as in the form editor's tab. */
    private UIElement limitRowX;
    private UIElement limitRowY;
    private UIElement limitRowZ;

    private boolean syncing;

    public UIBoneConstraintKeyframeFactory(Keyframe<BoneConstraint> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        IKey axis = IKey.constant("%s (%s)");

        this.limitX = this.axisToggle(UIKeys.GENERAL_X, (c, v) -> c.limitX = v);
        this.limitY = this.axisToggle(UIKeys.GENERAL_Y, (c, v) -> c.limitY = v);
        this.limitZ = this.axisToggle(UIKeys.GENERAL_Z, (c, v) -> c.limitZ = v);

        this.minX = this.axisTrackpad((v) -> this.edit((c) -> c.minX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X));
        this.minY = this.axisTrackpad((v) -> this.edit((c) -> c.minY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y));
        this.minZ = this.axisTrackpad((v) -> this.edit((c) -> c.minZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z));
        this.maxX = this.axisTrackpad((v) -> this.edit((c) -> c.maxX = v.floatValue()), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X));
        this.maxY = this.axisTrackpad((v) -> this.edit((c) -> c.maxY = v.floatValue()), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y));
        this.maxZ = this.axisTrackpad((v) -> this.edit((c) -> c.maxZ = v.floatValue()), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z));

        this.limitRowX = UI.row(this.minX, this.maxX);
        this.limitRowY = UI.row(this.minY, this.maxY);
        this.limitRowZ = UI.row(this.minZ, this.maxZ);

        this.scroll.add(UI.column(
            this.axisHeader(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_AXIS.format(UIKeys.GENERAL_X), this.limitX).marginTop(UIConstants.SECTION_GAP),
            this.limitRowX,
            this.axisHeader(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_AXIS.format(UIKeys.GENERAL_Y), this.limitY),
            this.limitRowY,
            this.axisHeader(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_AXIS.format(UIKeys.GENERAL_Z), this.limitZ),
            this.limitRowZ
        ));

        this.display();
    }

    private void display()
    {
        BoneConstraint c = this.keyframe.getValue();

        if (c == null)
        {
            c = BoneConstraint.DEFAULT;
        }

        this.syncing = true;

        try
        {
            this.limitX.setValue(c.limitX);
            this.limitY.setValue(c.limitY);
            this.limitZ.setValue(c.limitZ);
            this.limitRowX.setVisible(c.limitX);
            this.limitRowY.setVisible(c.limitY);
            this.limitRowZ.setVisible(c.limitZ);
            this.resize();
            this.minX.setValue(c.minX);
            this.minY.setValue(c.minY);
            this.minZ.setValue(c.minZ);
            this.maxX.setValue(c.maxX);
            this.maxY.setValue(c.maxY);
            this.maxZ.setValue(c.maxZ);
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(Consumer<BoneConstraint> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            BoneConstraint c = (BoneConstraint) selected.getValue();

            if (c == null)
            {
                return;
            }

            selected.preNotify();
            consumer.accept(c);
            selected.postNotify();
        });
    }

    /** One axis' switch: flipping it re-lays the editor, since its angles come and go with it. */
    private UIToggle axisToggle(IKey axis, BiConsumer<BoneConstraint, Boolean> setter)
    {
        UIToggle toggle = new UIToggle(IKey.EMPTY, (b) ->
        {
            this.edit((c) -> setter.accept(c, b.getValue()));
            this.display();
        });

        toggle.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LIMIT.format(axis));

        return toggle;
    }

    /** An axis' header: its name on the left, its switch pinned right. */
    private UIElement axisHeader(IKey label, UIToggle limit)
    {
        UIElement row = new UIElement();

        row.row(UIConstants.MARGIN).preferred(0).height(UIConstants.CONTROL_HEIGHT);
        row.add(UI.label(label, UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F), limit.w(26));

        return row;
    }

    private UISliderTrackpad axisTrackpad(Consumer<Double> callback, int color, IKey tooltip)
    {
        UISliderTrackpad trackpad = new UISliderTrackpad(callback).angle180();

        trackpad.textbox.setColor(color);
        trackpad.tooltip(tooltip);

        return trackpad;
    }
}
