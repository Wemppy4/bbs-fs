package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/**
 * Editor for the {@code wind} keyframe track: keyframe the global wind (strength, direction, and the
 * turbulence trio), layered over the form's physics wind config at playback. Mirrors
 * {@link UIPhysicsKeyframeFactory} but the wind is a single global control, so there is no chain list.
 */
public class UIWindKeyframeFactory extends UIKeyframeFactory<WindControl>
{
    public UITrackpad strength;
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad z;
    public UITrackpad turbulence;
    public UITrackpad turbulenceSpeed;
    public UITrackpad turbulenceScale;

    private boolean syncing;

    public UIWindKeyframeFactory(Keyframe<WindControl> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.strength = new UITrackpad((v) -> this.edit((control) -> control.strength = v.floatValue()));
        this.strength.limit(0D, 10D).increment(0.25D).values(0.1D, 0.01D, 0.5D);

        this.x = this.axis((v) -> this.edit((control) -> control.x = v.floatValue()), Colors.RED);
        this.y = this.axis((v) -> this.edit((control) -> control.y = v.floatValue()), Colors.GREEN);
        this.z = this.axis((v) -> this.edit((control) -> control.z = v.floatValue()), Colors.BLUE);

        this.turbulence = new UITrackpad((v) -> this.edit((control) -> control.turbulence = v.floatValue()));
        this.turbulence.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.2D);

        this.turbulenceSpeed = new UITrackpad((v) -> this.edit((control) -> control.turbulenceSpeed = v.floatValue()));
        this.turbulenceSpeed.limit(0D, 10D).increment(0.1D).values(0.1D, 0.05D, 0.5D);

        this.turbulenceScale = new UITrackpad((v) -> this.edit((control) -> control.turbulenceScale = v.floatValue()));
        this.turbulenceScale.limit(0D, 10D).increment(0.1D).values(0.1D, 0.05D, 0.5D);

        this.scroll.add(UI.column(
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_STRENGTH, this.strength).marginTop(UIConstants.SECTION_GAP),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_DIRECTION).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.x, this.y, this.z),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE, this.turbulence).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SPEED, this.turbulenceSpeed).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SCALE, this.turbulenceScale).marginTop(UIConstants.SECTION_GAP)
        ));

        this.display();
    }

    private void display()
    {
        WindControl control = this.keyframe.getValue();

        if (control == null)
        {
            control = WindControl.DEFAULT;
        }

        this.syncing = true;

        try
        {
            this.strength.setValue(control.strength);
            this.x.setValue(control.x);
            this.y.setValue(control.y);
            this.z.setValue(control.z);
            this.turbulence.setValue(control.turbulence);
            this.turbulenceSpeed.setValue(control.turbulenceSpeed);
            this.turbulenceScale.setValue(control.turbulenceScale);
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(Consumer<WindControl> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            WindControl control = (WindControl) selected.getValue();

            if (control == null)
            {
                return;
            }

            selected.preNotify();
            consumer.accept(control);
            selected.postNotify();
        });
    }

    private UITrackpad axis(Consumer<Double> callback, int color)
    {
        UITrackpad trackpad = new UITrackpad(callback).increment(0.1D).values(0.1D, 0.05D, 0.5D);

        trackpad.textbox.setColor(color);

        return trackpad;
    }
}
