package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** The picked key's rows at the top of every value panel; greyed out while no key is picked. */
public class UIKeyframeParameters extends UIElement
{
    private final UIKeyframes editor;
    private Keyframe keyframe;
    public UIToggle enabled;
    public UITrackpad tick, duration, motionShift;
    public UIIcon interp;
    private boolean draggingMotionShift;
    private UIBezierHandles handles;

    public UIKeyframeParameters(UIKeyframes editor, boolean numeric)
    {
        this.editor = editor;
        this.column(UIConstants.MARGIN).vertical().stretch();
        this.enabled = new UIToggle(IKey.EMPTY, b -> this.editor.setSelectedEnabled(b.getValue()));
        this.enabled.wh(26, UIConstants.CONTROL_HEIGHT).tooltip(UIKeys.KEYFRAMES_ENABLED);

        this.tick = new UITrackpad(this::setTick);
        this.tick.tooltip(UIKeys.KEYFRAMES_TICK);
        this.tick.getEvents().register(UITrackpadDragStartEvent.class, (e) -> this.editor.cacheKeyframes());
        this.tick.getEvents().register(UITrackpadDragEndEvent.class, (e) ->
        {
            if (e.cancelled) this.editor.cancelKeyframes();
            else this.editor.submitKeyframes();
            this.editor.getGraph().pickSelected();
        });
        this.duration = new UITrackpad((v) -> this.setDuration(v.floatValue()));
        this.duration.limit(0, Float.MAX_VALUE).tooltip(UIKeys.KEYFRAMES_FORCED_DURATION);
        this.interp = new UIIcon(Icons.GRAPH, (b) ->
        {
            if (this.keyframe == null) return;
            Interpolation interp = new Interpolation("", this.keyframe.getInterpolation().getMap());
            interp.fromData(this.keyframe.getInterpolation().toData());
            UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interp);

            this.getContext().replaceContextMenu(menu.callback(() -> this.editor.getGraph().setInterpolation(interp)));
        });
        this.interp.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        this.interp.tooltip(new InterpolationTooltip(0F, 0.5F, () -> this.keyframe == null ? null : this.keyframe.getInterpolation()));
        this.interp.keys().register(Keys.KEYFRAMES_INTERP, this.interp::clickItself).category(UIKeys.KEYFRAMES_KEYS_CATEGORY);

        this.motionShift = new UITrackpad(v -> this.editor.getGraph().setMotionShift(v.floatValue() / 100F, !this.draggingMotionShift));
        this.motionShift.limit(-49, 49).tooltip(UIKeys.KEYFRAMES_MOTION_SHIFT);
        this.motionShift.getEvents().register(UITrackpadDragStartEvent.class, e ->
        {
            this.draggingMotionShift = true;
            this.editor.cacheKeyframes();
        });
        this.motionShift.getEvents().register(UITrackpadDragEndEvent.class, e ->
        {
            this.draggingMotionShift = false;
            if (e.cancelled) this.editor.cancelKeyframes();
            else this.editor.submitKeyframes();
            this.editor.getGraph().pickSelected();
        });
        this.add(UI.row(UIConstants.MARGIN, 0, 0, this.interp, this.enabled, this.tick));
        this.add(UI.row(UIConstants.MARGIN, 0, 0, this.duration, this.motionShift));

        if (numeric)
        {
            this.handles = new UIBezierHandles(null);
            this.add(this.handles.createColumn());
        }

        this.setKeyframe(null);
    }

    public void setKeyframe(Keyframe keyframe)
    {
        boolean changed = this.keyframe != keyframe;
        boolean has = keyframe != null;

        this.keyframe = keyframe;
        this.enabled.setEnabled(has);
        this.tick.setEnabled(has);
        this.duration.setEnabled(has);
        this.interp.setEnabled(has);
        this.motionShift.setEnabled(has && keyframe.supportsMotionShift());
        if (changed && this.handles != null) this.handles.setKeyframe(keyframe);
        this.update();
    }

    public void update()
    {
        if (this.keyframe == null) return;
        this.enabled.setValue(this.keyframe.isEnabled());
        if (!this.tick.isUserEditing()) this.tick.setValue(TimeUtils.toTime(this.keyframe.getTick()));
        if (!this.duration.isUserEditing()) this.duration.setValue(TimeUtils.toTime(this.keyframe.getDuration()));
        if (!this.motionShift.isUserEditing()) this.motionShift.setValue(this.keyframe.getMotionShift() * 100F);
        if (this.handles != null) this.handles.update();
    }

    public void setTick(double tick)
    {
        if (this.keyframe == null) return;
        double time = BBSSettings.editorSnapToTicks.get() ? TimeUtils.fromTime(tick)
            : (BBSSettings.editorSeconds.get() ? tick * 20D : tick);

        if (!Double.isFinite(time)) return;
        boolean dragging = this.tick.isDragging();
        if (!dragging) this.editor.cacheKeyframes();
        this.editor.getGraph().moveSelectedBy((float) time - this.keyframe.getTick(), false);
        if (!dragging) this.editor.submitKeyframes();
        else this.editor.triggerChange();
    }

    public void setDuration(float value)
    {
        this.editor.getGraph().setDuration(value);
    }

}
